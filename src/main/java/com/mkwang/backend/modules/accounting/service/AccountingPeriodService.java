package com.mkwang.backend.modules.accounting.service;

import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.accounting.dto.response.AccountingPeriodResponse;
import com.mkwang.backend.modules.accounting.dto.response.AccountingPeriodClosePreviewResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Service
public class AccountingPeriodService {
    @PersistenceContext private EntityManager em;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ACCOUNTANT','CFO')")
    public List<AccountingPeriodResponse> list() {
        @SuppressWarnings("unchecked")
        List<Object[]> data = em.createNativeQuery("""
                SELECT months.period, COALESCE(p.closed,false), EXTRACT(EPOCH FROM p.closed_at), p.closed_by, p.close_reason
                FROM (SELECT posting_period AS period FROM accounting_journals UNION SELECT period FROM accounting_periods) months
                LEFT JOIN accounting_periods p ON p.period=months.period ORDER BY months.period DESC
                """).getResultList();
        return data.stream().map(row -> new AccountingPeriodResponse((String)row[0], (Boolean)row[1],
                row[2] == null ? null : Instant.ofEpochSecond(((Number)row[2]).longValue()),
                row[3] == null ? null : ((Number)row[3]).longValue(), (String)row[4])).toList();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ACCOUNTANT','CFO')")
    public AccountingPeriodClosePreviewResponse previewClose(String period) {
        YearMonth month = parsePeriod(period);
        return inspectClose(period, month);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ACCOUNTANT','CFO')")
    public void close(String period, String reason, boolean reviewAcknowledged) {
        YearMonth month = parsePeriod(period);
        if (!reviewAcknowledged) throw new BadRequestException("Cần xem và xác nhận kết quả kiểm tra trước khi đóng kỳ");
        AccountingPeriodClosePreviewResponse preview = inspectClose(period, month);
        if (!preview.eligible()) throw new BadRequestException(String.join("; ", preview.blockingIssues()));
        if (reason == null || reason.isBlank()) throw new BadRequestException("Cần lý do đóng kỳ");
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
                .setParameter("key","IFMS_ACCOUNTING_PERIOD:"+period).getSingleResult();
        // Recheck after taking the same lock used by writers, so a preview cannot go stale before close.
        preview = inspectClose(period, month);
        if (!preview.eligible()) throw new BadRequestException(String.join("; ", preview.blockingIssues()));
        var principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long actor = principal instanceof UserDetailsAdapter adapter ? adapter.getUser().getId() : null;
        int changed = em.createNativeQuery("""
                INSERT INTO accounting_periods(period,closed,closed_at,closed_by,close_reason)
                VALUES (:period,true,now(),:actor,:reason)
                ON CONFLICT(period) DO UPDATE SET closed=true,closed_at=now(),closed_by=:actor,close_reason=:reason
                WHERE NOT accounting_periods.closed
                """).setParameter("period",period).setParameter("actor",actor).setParameter("reason",reason).executeUpdate();
        if (changed == 0) throw new BadRequestException("Kỳ đã đóng");
    }

    private YearMonth parsePeriod(String period) {
        YearMonth month;
        try { month = YearMonth.parse(period); }
        catch (java.time.format.DateTimeParseException error) { throw new BadRequestException("Kỳ phải có dạng YYYY-MM"); }
        return month;
    }

    private AccountingPeriodClosePreviewResponse inspectClose(String period, YearMonth month) {
        java.util.ArrayList<String> blockers = new java.util.ArrayList<>();
        if (!month.isBefore(YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            blockers.add("Chỉ đóng kỳ đã kết thúc");
        }
        Number alreadyClosed = (Number) em.createNativeQuery("SELECT COUNT(*) FROM accounting_periods WHERE period=:period AND closed")
                .setParameter("period",period).getSingleResult();
        if (alreadyClosed.longValue() > 0) blockers.add("Kỳ đã được đóng trước đó");
        Number journalCount = (Number) em.createNativeQuery("SELECT COUNT(*) FROM accounting_journals WHERE posting_period=:period")
                .setParameter("period",period).getSingleResult();
        Number invalid = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM accounting_journals j
                LEFT JOIN (SELECT journal_id, COUNT(*) AS n, SUM(debit_amount) AS d, SUM(credit_amount) AS c
                           FROM accounting_journal_lines GROUP BY journal_id) s ON s.journal_id=j.id
                WHERE j.posting_period=:period AND (COALESCE(s.n,0)<2 OR s.d<>s.c OR s.d<>j.total_amount)
                """).setParameter("period",period).getSingleResult();
        if (invalid.longValue() != 0) blockers.add("Có bút toán thiếu dòng hoặc không cân bằng");
        Number missingSources = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM accounting_journals
                WHERE posting_period=:period AND (source_type IS NULL OR source_id IS NULL OR source_snapshot IS NULL)
                """).setParameter("period",period).getSingleResult();
        if (missingSources.longValue() != 0) blockers.add("Có journal thiếu mã nguồn hoặc bản chụp nguồn");
        Number unmatchedTopups = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM accounting_journals j
                WHERE j.posting_period=:period AND j.event_type='SYSTEM_TOPUP'
                  AND NOT EXISTS (
                    SELECT 1 FROM bank_statement_matches m
                    JOIN bank_statement_lines s ON s.id=m.statement_line_id
                    LEFT JOIN bank_booking_confirmations c ON c.statement_line_id=s.id
                    WHERE m.transaction_id=j.wallet_transaction_id
                      AND s.direction='IN' AND (s.state='BOOKED' OR c.statement_line_id IS NOT NULL))
                """).setParameter("period",period).getSingleResult();
        if (unmatchedTopups.longValue() != 0) blockers.add("Có khoản nạp quỹ chưa khớp sao kê IN/BOOKED");
        return new AccountingPeriodClosePreviewResponse(period,blockers.isEmpty(),journalCount.longValue(),
                invalid.longValue(),missingSources.longValue(),unmatchedTopups.longValue(),List.copyOf(blockers),
                List.of("Tạm ứng chưa quyết toán được chuyển tiếp để theo dõi ở kỳ sau; không tự động ghi thành chi phí."));
    }
}
