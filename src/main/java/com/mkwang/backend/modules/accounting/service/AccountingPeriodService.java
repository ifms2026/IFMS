package com.mkwang.backend.modules.accounting.service;

import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.accounting.dto.response.AccountingPeriodResponse;
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
    public void close(String period, String reason) {
        YearMonth month;
        try { month = YearMonth.parse(period); }
        catch (java.time.format.DateTimeParseException error) { throw new BadRequestException("Kỳ phải có dạng YYYY-MM"); }
        if (month.atEndOfMonth().isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new BadRequestException("Chỉ đóng kỳ đã kết thúc");
        }
        if (reason == null || reason.isBlank()) throw new BadRequestException("Cần lý do đóng kỳ");
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
                .setParameter("key","IFMS_ACCOUNTING_PERIOD:"+period).getSingleResult();
        Number invalid = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM accounting_journals j
                LEFT JOIN (SELECT journal_id, COUNT(*) AS n, SUM(debit_amount) AS d, SUM(credit_amount) AS c
                           FROM accounting_journal_lines GROUP BY journal_id) s ON s.journal_id=j.id
                WHERE j.posting_period=:period AND (COALESCE(s.n,0)<2 OR s.d<>s.c OR s.d<>j.total_amount)
                """).setParameter("period",period).getSingleResult();
        if (invalid.longValue() != 0) throw new BadRequestException("Kỳ có bút toán thiếu hoặc lệch, cần đối chiếu trước");
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
}
