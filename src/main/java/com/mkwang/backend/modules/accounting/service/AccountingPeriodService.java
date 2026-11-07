package com.mkwang.backend.modules.accounting.service;

import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.accounting.dto.response.AccountingPeriodCloseIssueResponse;
import com.mkwang.backend.modules.accounting.dto.response.AccountingPeriodClosePreviewResponse;
import com.mkwang.backend.modules.accounting.dto.response.AccountingPeriodResponse;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountingPeriodService {
    private static final int DEFAULT_ISSUE_SIZE = 20;
    private static final int MAX_ISSUE_SIZE = 100;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ACCOUNTANT','CFO')")
    public List<AccountingPeriodResponse> list() {
        @SuppressWarnings("unchecked")
        List<Object[]> data = em.createNativeQuery("""
                SELECT months.period, COALESCE(p.closed,false), EXTRACT(EPOCH FROM p.closed_at), p.closed_by, p.close_reason
                FROM (SELECT posting_period AS period FROM accounting_journals UNION SELECT period FROM accounting_periods) months
                LEFT JOIN accounting_periods p ON p.period=months.period ORDER BY months.period DESC
                """).getResultList();
        return data.stream().map(row -> new AccountingPeriodResponse((String) row[0], (Boolean) row[1],
                row[2] == null ? null : Instant.ofEpochSecond(((Number) row[2]).longValue()),
                row[3] == null ? null : ((Number) row[3]).longValue(), (String) row[4])).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ACCOUNTANT','CFO')")
    public AccountingPeriodClosePreviewResponse previewClose(String period) {
        return previewClose(period, 1, DEFAULT_ISSUE_SIZE);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ACCOUNTANT','CFO')")
    public AccountingPeriodClosePreviewResponse previewClose(String period, int issuePage, int issueSize) {
        validateIssuePage(issuePage, issueSize);
        YearMonth month = parsePeriod(period);
        return inspectClose(period, month, issuePage, issueSize);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ACCOUNTANT','CFO')")
    public void close(String period, String reason, boolean reviewAcknowledged) {
        YearMonth month = parsePeriod(period);
        if (!reviewAcknowledged) throw new BadRequestException("Cần xem và xác nhận kết quả kiểm tra trước khi đóng kỳ");
        if (reason == null || reason.isBlank()) throw new BadRequestException("Cần lý do đóng kỳ");

        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
                .setParameter("key", "IFMS_ACCOUNTING_PERIOD:" + period).getSingleResult();

        // Journal inserts take this same database lock in V22. Rechecking after the lock
        // prevents a stale preview or a concurrent writer from slipping into a closed period.
        AccountingPeriodClosePreviewResponse preview = inspectClose(period, month, 1, DEFAULT_ISSUE_SIZE);
        if (!preview.eligible()) throw new BadRequestException(String.join("; ", preview.blockingIssues()));

        var principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long actor = principal instanceof UserDetailsAdapter adapter ? adapter.getUser().getId() : null;
        int changed = em.createNativeQuery("""
                INSERT INTO accounting_periods(period,closed,closed_at,closed_by,close_reason)
                VALUES (:period,true,now(),:actor,:reason)
                ON CONFLICT(period) DO UPDATE SET closed=true,closed_at=now(),closed_by=:actor,close_reason=:reason
                WHERE NOT accounting_periods.closed
                """).setParameter("period", period).setParameter("actor", actor).setParameter("reason", reason).executeUpdate();
        if (changed == 0) throw new BadRequestException("Kỳ đã đóng");
    }

    private void validateIssuePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_ISSUE_SIZE) {
            throw new BadRequestException("Trang ngoại lệ không hợp lệ (kích thước tối đa 100)");
        }
    }

    private YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period);
        } catch (java.time.format.DateTimeParseException error) {
            throw new BadRequestException("Kỳ phải có dạng YYYY-MM");
        }
    }

    private AccountingPeriodClosePreviewResponse inspectClose(String period, YearMonth month, int issuePage, int issueSize) {
        List<String> blockers = new ArrayList<>();
        if (!month.isBefore(YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh")))) blockers.add("Chỉ đóng kỳ đã kết thúc");

        Number alreadyClosed = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM accounting_periods WHERE period=:period AND closed")
                .setParameter("period", period).getSingleResult();
        if (alreadyClosed.longValue() > 0) blockers.add("Kỳ đã được đóng trước đó");

        Number journalCount = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM accounting_journals WHERE posting_period=:period")
                .setParameter("period", period).getSingleResult();

        String issueCte = issueCte();
        @SuppressWarnings("unchecked")
        List<Object[]> grouped = em.createNativeQuery(issueCte + " SELECT code, COUNT(*) FROM issues GROUP BY code")
                .setParameter("period", period).getResultList();
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : grouped) counts.put((String) row[0], ((Number) row[1]).longValue());

        long invalidJournalCount = count(counts, "UNBALANCED_JOURNAL");
        long missingSourceCount = count(counts, "MISSING_SOURCE");
        long unmatchedTopupCount = count(counts, "UNMATCHED_SYSTEM_TOPUP");
        long invalidSourceLinkCount = count(counts, "INVALID_SOURCE_LINK") + count(counts, "INVALID_REVERSAL_LINK");
        long sourceAmountMismatchCount = count(counts, "SOURCE_AMOUNT_MISMATCH")
                + count(counts, "MISSING_EXPENSE_EVIDENCE");
        long invalidAdvanceBalanceCount = count(counts, "INVALID_ADVANCE_BALANCE");
        long issueTotal = counts.values().stream().mapToLong(Long::longValue).sum();

        addBlocker(blockers, invalidJournalCount, "Có bút toán thiếu dòng hoặc không cân bằng");
        addBlocker(blockers, missingSourceCount, "Có journal thiếu hoặc không tìm thấy hồ sơ nguồn");
        addBlocker(blockers, invalidSourceLinkCount, "Có liên kết journal với yêu cầu, giao dịch hoặc bút toán gốc không hợp lệ");
        addBlocker(blockers, sourceAmountMismatchCount, "Có số tiền journal không khớp nguồn hoặc thiếu phân bổ chứng từ");
        addBlocker(blockers, invalidAdvanceBalanceCount, "Có số dư tạm ứng không khớp các lần quyết toán");
        addBlocker(blockers, unmatchedTopupCount, "Có khoản nạp quỹ chưa khớp sao kê IN/BOOKED cùng số tiền");

        @SuppressWarnings("unchecked")
        List<Object[]> issueRows = em.createNativeQuery(issueCte + """
                SELECT code,severity,journal_id,journal_code,event_type,source_type,source_id,amount,message,action_hint
                FROM issues
                ORDER BY code,journal_id NULLS LAST,source_id
                LIMIT :limit OFFSET :offset
                """)
                .setParameter("period", period)
                .setParameter("limit", issueSize)
                .setParameter("offset", (issuePage - 1) * issueSize)
                .getResultList();
        List<AccountingPeriodCloseIssueResponse> issues = issueRows.stream().map(row ->
                new AccountingPeriodCloseIssueResponse((String) row[0], (String) row[1],
                        row[2] == null ? null : ((Number) row[2]).longValue(), (String) row[3],
                        (String) row[4], (String) row[5], (String) row[6], (BigDecimal) row[7],
                        (String) row[8], (String) row[9])).toList();

        List<String> carryForward = carryForwardNotes(month);
        return new AccountingPeriodClosePreviewResponse(period, blockers.isEmpty(), journalCount.longValue(),
                invalidJournalCount, missingSourceCount, unmatchedTopupCount, invalidSourceLinkCount,
                sourceAmountMismatchCount, invalidAdvanceBalanceCount, issueTotal, issuePage, issueSize,
                issues, List.copyOf(blockers), carryForward);
    }

    private long count(Map<String, Long> counts, String code) {
        return counts.getOrDefault(code, 0L);
    }

    private void addBlocker(List<String> blockers, long count, String label) {
        if (count > 0) blockers.add(label + " · " + count);
    }

    private List<String> carryForwardNotes(YearMonth month) {
        LocalDate cutoff = month.atEndOfMonth();
        Number openAdvances = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM (
                  SELECT l.advance_balance_id
                  FROM accounting_journals j JOIN accounting_journal_lines l ON l.journal_id=j.id
                  WHERE j.posting_date<=:cutoff AND l.account_code='ADVANCE_RECEIVABLE' AND l.advance_balance_id IS NOT NULL
                  GROUP BY l.advance_balance_id HAVING SUM(l.debit_amount-l.credit_amount)>0
                ) open_balances
                """).setParameter("cutoff", cutoff).getSingleResult();
        Number unpaidExpense = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM accounting_journals verified
                WHERE verified.posting_date<=:cutoff AND verified.event_type='EXPENSE_VERIFIED'
                  AND NOT EXISTS (SELECT 1 FROM accounting_journals rev
                                  WHERE rev.event_type='REVERSAL' AND rev.original_journal_id=verified.id
                                    AND rev.posting_date<=:cutoff)
                  AND NOT EXISTS (SELECT 1 FROM accounting_journals paid
                                  WHERE paid.event_type='EXPENSE_PAID' AND paid.request_id=verified.request_id
                                    AND paid.posting_date<=:cutoff
                                    AND NOT EXISTS (SELECT 1 FROM accounting_journals revPaid
                                                    WHERE revPaid.event_type='REVERSAL' AND revPaid.original_journal_id=paid.id
                                                      AND revPaid.posting_date<=:cutoff))
                """).setParameter("cutoff", cutoff).getSingleResult();
        Number pendingStatements = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM bank_statement_lines s
                LEFT JOIN bank_booking_confirmations c ON c.statement_line_id=s.id
                WHERE s.booking_date<=:cutoff AND s.bank_account=(SELECT bank_account FROM company_funds WHERE id=1)
                  AND s.state='PENDING' AND c.statement_line_id IS NULL
                """).setParameter("cutoff", cutoff).getSingleResult();

        List<String> notes = new ArrayList<>();
        if (openAdvances.longValue() > 0) notes.add(openAdvances + " khoản tạm ứng còn dư tại ngày chốt được chuyển tiếp theo dõi, chưa tự ghi thành chi phí.");
        if (unpaidExpense.longValue() > 0) notes.add(unpaidExpense + " khoản hoàn chi đã được xác nhận nhưng chưa thanh toán còn theo dõi là khoản phải hoàn.");
        if (pendingStatements.longValue() > 0) notes.add(pendingStatements + " dòng sao kê đang chờ xác nhận ngân hàng được giữ ở trạng thái chờ, không tự coi là tiền đã ghi sổ.");
        if (notes.isEmpty()) notes.add("Không có tạm ứng còn dư, khoản hoàn chi chưa thanh toán hoặc sao kê chờ xác nhận tại ngày chốt.");
        return List.copyOf(notes);
    }

    private String issueCte() {
        return """
                WITH period_journals AS (
                  SELECT * FROM accounting_journals WHERE posting_period=:period
                ), issues AS (
                  SELECT 'UNBALANCED_JOURNAL'::text code,'BLOCKER'::text severity,j.id journal_id,j.journal_code,
                         j.event_type::text event_type,j.source_type,j.source_id,j.total_amount amount,
                         'Bút toán thiếu dòng hoặc tổng hai bên không bằng số tiền header.'::text message,
                         'Mở chi tiết bút toán, xác minh các dòng và lập bút toán điều chỉnh có liên kết.'::text action_hint
                  FROM period_journals j LEFT JOIN (
                    SELECT journal_id,COUNT(*) n,COALESCE(SUM(debit_amount),0) d,COALESCE(SUM(credit_amount),0) c
                    FROM accounting_journal_lines GROUP BY journal_id
                  ) s ON s.journal_id=j.id
                  WHERE COALESCE(s.n,0)<2 OR s.d<>s.c OR s.d<>j.total_amount

                  UNION ALL
                  SELECT 'MISSING_SOURCE','BLOCKER',j.id,j.journal_code,j.event_type::text,j.source_type,j.source_id,j.total_amount,
                         'Không tìm thấy hồ sơ nguồn đúng loại hoặc thiếu mã nguồn.'::text,
                         'Kiểm tra source_type/source_id và khôi phục liên kết từ hồ sơ gốc có căn cứ.'::text
                  FROM period_journals j
                  WHERE (j.event_type IN ('ADVANCE_DISBURSED','EXPENSE_VERIFIED','EXPENSE_PAID','REIMBURSE_SETTLED','DEPARTMENT_ALLOCATION','PROJECT_ALLOCATION')
                         AND (j.source_type<>'REQUEST' OR j.request_id IS NULL OR j.source_snapshot IS NULL
                              OR NOT EXISTS(SELECT 1 FROM requests r WHERE r.id::text=j.source_id)))
                     OR (j.event_type='PAYROLL_SETTLEMENT' AND (j.source_type<>'PAYSLIP' OR j.source_snapshot IS NULL
                         OR NOT EXISTS(SELECT 1 FROM payslips p WHERE p.id::text=j.source_id)))
                     OR (j.event_type IN ('SYSTEM_TOPUP','ADVANCE_RETURNED') AND (j.source_type<>'TRANSACTION' OR j.source_snapshot IS NULL
                         OR NOT EXISTS(SELECT 1 FROM transactions t WHERE t.id::text=j.source_id)))
                     OR (j.event_type='REVERSAL' AND (j.source_type<>'JOURNAL_REVERSAL' OR j.source_snapshot IS NULL
                         OR NOT EXISTS(SELECT 1 FROM accounting_journals o WHERE o.id::text=j.source_id)))
                     OR (j.event_type='OPENING_BALANCE' AND (j.source_type<>'OPENING_SCOPE' OR j.source_snapshot IS NULL
                         OR j.source_id !~ '^(COMPANY_FUND|DEPARTMENT|PROJECT|ADVANCE):[0-9]+$'
                         OR (split_part(j.source_id,':',1)='COMPANY_FUND' AND NOT EXISTS(SELECT 1 FROM company_funds f WHERE f.id::text=split_part(j.source_id,':',2)))
                         OR (split_part(j.source_id,':',1)='DEPARTMENT' AND NOT EXISTS(SELECT 1 FROM departments d WHERE d.id::text=split_part(j.source_id,':',2)))
                         OR (split_part(j.source_id,':',1)='PROJECT' AND NOT EXISTS(SELECT 1 FROM projects p WHERE p.id::text=split_part(j.source_id,':',2)))
                         OR (split_part(j.source_id,':',1)='ADVANCE' AND NOT EXISTS(SELECT 1 FROM advance_balances a WHERE a.id::text=split_part(j.source_id,':',2)))))
                     OR j.event_type NOT IN ('ADVANCE_DISBURSED','EXPENSE_VERIFIED','EXPENSE_PAID','REIMBURSE_SETTLED','ADVANCE_RETURNED',
                         'PAYROLL_SETTLEMENT','SYSTEM_TOPUP','DEPARTMENT_ALLOCATION','PROJECT_ALLOCATION','REVERSAL','OPENING_BALANCE')

                  UNION ALL
                  SELECT 'INVALID_SOURCE_LINK','BLOCKER',j.id,j.journal_code,j.event_type::text,j.source_type,j.source_id,j.total_amount,
                         'Loại hồ sơ, trạng thái hoặc liên kết nghiệp vụ không khớp sự kiện đã ghi.'::text,
                         'Đối chiếu loại yêu cầu, người nhận, khoản tạm ứng và giao dịch ví với hồ sơ gốc.'::text
                  FROM period_journals j LEFT JOIN requests r ON r.id::text=j.source_id
                  WHERE j.event_type IN ('ADVANCE_DISBURSED','EXPENSE_VERIFIED','EXPENSE_PAID','REIMBURSE_SETTLED','DEPARTMENT_ALLOCATION','PROJECT_ALLOCATION')
                    AND (r.id IS NULL OR j.source_type<>'REQUEST' OR j.request_id IS DISTINCT FROM r.id
                      OR (j.event_type='ADVANCE_DISBURSED' AND (r.type<>'ADVANCE' OR r.status NOT IN ('PAID','REVERSED')
                           OR j.advance_balance_id IS NULL OR NOT EXISTS(SELECT 1 FROM advance_balances a WHERE a.id=j.advance_balance_id AND a.advance_request_id=r.id)))
                      OR (j.event_type='EXPENSE_VERIFIED' AND (r.type<>'EXPENSE' OR r.status NOT IN ('ACCOUNTANT_VERIFIED','PAID','REVERSED')))
                      OR (j.event_type='EXPENSE_PAID' AND (r.type<>'EXPENSE' OR r.status NOT IN ('PAID','REVERSED')))
                      OR (j.event_type='REIMBURSE_SETTLED' AND (r.type<>'REIMBURSE' OR r.status NOT IN ('PAID','REVERSED')
                           OR r.advance_balance_id IS DISTINCT FROM j.advance_balance_id
                           OR NOT EXISTS(SELECT 1 FROM advance_balances a WHERE a.id=j.advance_balance_id AND a.id=r.advance_balance_id)))
                      OR (j.event_type='DEPARTMENT_ALLOCATION' AND (r.type<>'DEPARTMENT_TOPUP' OR r.status NOT IN ('PAID','REVERSED')))
                      OR (j.event_type='PROJECT_ALLOCATION' AND (r.type<>'PROJECT_TOPUP' OR r.status NOT IN ('PAID','REVERSED')))
                      OR j.employee_id IS DISTINCT FROM r.requester_id
                      OR (r.status='REVERSED' AND NOT EXISTS(SELECT 1 FROM accounting_journals rev WHERE rev.event_type='REVERSAL' AND rev.original_journal_id=j.id))
                      OR (j.event_type IN ('ADVANCE_DISBURSED','EXPENSE_PAID','DEPARTMENT_ALLOCATION','PROJECT_ALLOCATION') AND NOT EXISTS(
                          SELECT 1 FROM transactions t WHERE t.id=j.wallet_transaction_id AND t.reference_type='REQUEST'
                            AND t.reference_id=r.id AND t.status='SUCCESS'
                            AND t.type=CASE j.event_type WHEN 'DEPARTMENT_ALLOCATION' THEN 'DEPT_QUOTA_ALLOCATION'
                                WHEN 'PROJECT_ALLOCATION' THEN 'PROJECT_QUOTA_ALLOCATION' ELSE 'REQUEST_PAYMENT' END))
                      OR (j.event_type='EXPENSE_PAID' AND NOT EXISTS(SELECT 1 FROM accounting_journals verified
                          WHERE verified.request_id=r.id AND verified.event_type='EXPENSE_VERIFIED'))
                    )

                  UNION ALL
                  SELECT 'INVALID_SOURCE_LINK','BLOCKER',j.id,j.journal_code,j.event_type::text,j.source_type,j.source_id,j.total_amount,
                         'Giao dịch ví không đúng loại, trạng thái hoặc khoản tạm ứng tham chiếu.'::text,
                         'Đối chiếu transaction với reference_type, reference_id, wallet và hồ sơ tạm ứng.'::text
                  FROM period_journals j LEFT JOIN transactions t ON t.id::text=j.source_id
                  WHERE j.event_type IN ('SYSTEM_TOPUP','ADVANCE_RETURNED')
                    AND (t.id IS NULL OR j.source_type<>'TRANSACTION' OR t.status<>'SUCCESS'
                      OR (j.event_type='SYSTEM_TOPUP' AND t.type<>'SYSTEM_TOPUP')
                      OR (j.event_type='ADVANCE_RETURNED' AND (t.type<>'ADVANCE_RETURN' OR t.reference_type<>'ADVANCE_BALANCE'
                           OR t.reference_id IS DISTINCT FROM j.advance_balance_id
                           OR NOT EXISTS(SELECT 1 FROM advance_balances a WHERE a.id=j.advance_balance_id AND a.user_id=j.employee_id
                             AND a.advance_request_id IN (SELECT r.id FROM requests r WHERE r.project_id=j.project_id))))
                      OR (t.status='REVERSED' AND NOT EXISTS(SELECT 1 FROM accounting_journals rev WHERE rev.event_type='REVERSAL' AND rev.original_journal_id=j.id)))

                  UNION ALL
                  SELECT 'INVALID_SOURCE_LINK','BLOCKER',j.id,j.journal_code,j.event_type::text,j.source_type,j.source_id,j.total_amount,
                         'Phiếu lương không khớp nhân viên, trạng thái hoặc lịch sử thay thế/đảo.'::text,
                         'Mở phiếu lương gốc và phiếu thay thế, kiểm tra bút toán chi lương và liên kết đảo.'::text
                  FROM period_journals j LEFT JOIN payslips p ON p.id::text=j.source_id
                  WHERE j.event_type='PAYROLL_SETTLEMENT'
                    AND (p.id IS NULL OR j.source_type<>'PAYSLIP' OR j.employee_id IS DISTINCT FROM p.user_id
                      OR p.status NOT IN ('PAID','REVERSED')
                      OR (p.status='REVERSED' AND NOT EXISTS(SELECT 1 FROM accounting_journals rev WHERE rev.event_type='REVERSAL' AND rev.original_journal_id=j.id)))

                  UNION ALL
                  SELECT 'INVALID_REVERSAL_LINK','BLOCKER',j.id,j.journal_code,j.event_type::text,j.source_type,j.source_id,j.total_amount,
                         'Bút toán đảo không khớp bút toán gốc hoặc không đảo ngược chính xác các dòng.'::text,
                         'Không sửa journal trực tiếp; xác minh bút toán gốc và xử lý bằng correction có kiểm soát.'::text
                  FROM period_journals j LEFT JOIN accounting_journals o ON o.id=j.original_journal_id
                  WHERE j.event_type='REVERSAL' AND (o.id IS NULL OR o.event_type IN ('REVERSAL','OPENING_BALANCE')
                    OR j.source_id<>o.id::text OR j.total_amount<>o.total_amount OR j.posting_date<o.posting_date
                    OR EXISTS (
                      SELECT 1 FROM (
                        SELECT account_code,SUM(debit_amount) debit,SUM(credit_amount) credit
                        FROM accounting_journal_lines WHERE journal_id=o.id GROUP BY account_code
                      ) original_lines FULL JOIN (
                        SELECT account_code,SUM(debit_amount) debit,SUM(credit_amount) credit
                        FROM accounting_journal_lines WHERE journal_id=j.id GROUP BY account_code
                      ) reversed_lines USING(account_code)
                      WHERE COALESCE(reversed_lines.debit,0)<>COALESCE(original_lines.credit,0)
                         OR COALESCE(reversed_lines.credit,0)<>COALESCE(original_lines.debit,0)
                    ) OR (SELECT COUNT(*) FROM accounting_journal_lines WHERE journal_id=j.id)<2)

                  UNION ALL
                  SELECT 'SOURCE_AMOUNT_MISMATCH','BLOCKER',j.id,j.journal_code,j.event_type::text,j.source_type,j.source_id,j.total_amount,
                         'Số tiền journal không khớp số tiền được duyệt, giao dịch ví hoặc phiếu lương.'::text,
                         'Đối chiếu số tiền nguồn và các dòng journal; tạo correction nếu dữ liệu hợp lệ nhưng đã ghi sai.'::text
                  FROM period_journals j LEFT JOIN requests r ON r.id::text=j.source_id
                    LEFT JOIN transactions t ON t.id=j.wallet_transaction_id
                    LEFT JOIN payslips p ON p.id::text=j.source_id
                  WHERE (j.event_type IN ('ADVANCE_DISBURSED','EXPENSE_VERIFIED','REIMBURSE_SETTLED','DEPARTMENT_ALLOCATION','PROJECT_ALLOCATION')
                           AND j.total_amount IS DISTINCT FROM r.approved_amount)
                     OR (j.event_type IN ('EXPENSE_PAID','SYSTEM_TOPUP','ADVANCE_RETURNED','DEPARTMENT_ALLOCATION','PROJECT_ALLOCATION')
                           AND (t.id IS NULL OR j.total_amount IS DISTINCT FROM t.amount))
                     OR (j.event_type IN ('ADVANCE_DISBURSED','EXPENSE_PAID','DEPARTMENT_ALLOCATION','PROJECT_ALLOCATION')
                           AND t.amount IS DISTINCT FROM r.approved_amount)
                     OR (j.event_type='PAYROLL_SETTLEMENT' AND (p.id IS NULL OR j.total_amount IS DISTINCT FROM
                           (p.base_salary+COALESCE(p.bonus,0)+COALESCE(p.allowance,0))
                         OR COALESCE((SELECT SUM(l.debit_amount) FROM accounting_journal_lines l WHERE l.journal_id=j.id AND l.account_code='PAYROLL_EXPENSE'),0)
                           IS DISTINCT FROM (p.base_salary+COALESCE(p.bonus,0)+COALESCE(p.allowance,0))
                         OR COALESCE((SELECT SUM(l.credit_amount) FROM accounting_journal_lines l WHERE l.journal_id=j.id AND l.account_code='PAYROLL_DEDUCTION_CLEARING'),0)
                           IS DISTINCT FROM COALESCE(p.deduction,0)
                         OR COALESCE((SELECT SUM(l.credit_amount) FROM accounting_journal_lines l WHERE l.journal_id=j.id AND l.account_code='ADVANCE_RECEIVABLE'),0)
                           IS DISTINCT FROM COALESCE(p.advance_deduct,0)
                         OR COALESCE((SELECT SUM(l.credit_amount) FROM accounting_journal_lines l WHERE l.journal_id=j.id AND l.account_code='COMPANY_FUND_WALLET'),0)
                           IS DISTINCT FROM COALESCE(p.final_net_salary,0)
                         OR (COALESCE(p.final_net_salary,0)>0 AND (t.id IS NULL OR t.amount IS DISTINCT FROM p.final_net_salary
                             OR t.type<>'PAYSLIP_PAYMENT' OR t.reference_type<>'PAYSLIP' OR t.reference_id<>p.id))))

                  UNION ALL
                  SELECT 'MISSING_EXPENSE_EVIDENCE','BLOCKER',j.id,j.journal_code,j.event_type::text,j.source_type,j.source_id,j.total_amount,
                         'Tổng phân bổ chứng từ hiện hành không bằng số chi phí đã xác nhận.'::text,
                         'Mở danh sách chứng từ của yêu cầu và đối chiếu lịch sử phân bổ/đảo.'::text
                  FROM period_journals j
                  WHERE j.event_type IN ('EXPENSE_VERIFIED','REIMBURSE_SETTLED')
                    AND NOT EXISTS(SELECT 1 FROM accounting_journals rev WHERE rev.event_type='REVERSAL' AND rev.original_journal_id=j.id)
                    AND (SELECT COALESCE(SUM(a.amount),0) FROM financial_document_allocations a WHERE a.request_id=j.request_id)<>j.total_amount

                  UNION ALL
                  SELECT 'INVALID_ADVANCE_BALANCE','BLOCKER',j.id,j.journal_code,j.event_type::text,j.source_type,j.source_id,j.total_amount,
                         'Số dư còn phải quyết toán không khớp số đã ứng trừ các lần quyết toán, hoàn tiền và khấu trừ.'::text,
                         'Đối chiếu từng hoạt động theo mã tạm ứng; không tự điều chỉnh số dư thiếu chứng từ.'::text
                  FROM period_journals j JOIN advance_balances a ON a.id=j.advance_balance_id OR EXISTS(
                    SELECT 1 FROM accounting_journal_lines ref WHERE ref.journal_id=j.id AND ref.advance_balance_id=a.id)
                  WHERE (
                    a.remaining_amount IS DISTINCT FROM (a.original_amount-a.reimbursed_amount-a.cash_returned_amount-a.payroll_offset_amount-a.legacy_unclassified_amount)
                    OR a.remaining_amount<0 OR a.remaining_amount>a.original_amount
                    OR (a.status='SETTLED' AND a.remaining_amount<>0) OR (a.status<>'SETTLED' AND a.remaining_amount=0))

                  UNION ALL
                  SELECT 'UNMATCHED_SYSTEM_TOPUP','BLOCKER',j.id,j.journal_code,j.event_type::text,j.source_type,j.source_id,j.total_amount,
                         'Nạp quỹ chưa khớp sao kê IN đã xác nhận cùng tài khoản và số tiền.'::text,
                         'Nhập hoặc xác nhận sao kê IN rồi ghép đúng giao dịch nạp quỹ.'::text
                  FROM period_journals j
                  WHERE j.event_type='SYSTEM_TOPUP' AND NOT EXISTS (
                    SELECT 1 FROM bank_statement_matches m JOIN bank_statement_lines s ON s.id=m.statement_line_id
                    LEFT JOIN bank_booking_confirmations c ON c.statement_line_id=s.id
                    JOIN transactions t ON t.id=m.transaction_id
                    WHERE m.transaction_id=j.wallet_transaction_id AND s.direction='IN' AND s.amount=t.amount
                      AND s.bank_account=(SELECT bank_account FROM company_funds WHERE id=1)
                      AND (s.state='BOOKED' OR c.statement_line_id IS NOT NULL))
                )
                """;
    }
}
