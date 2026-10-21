package com.mkwang.backend.modules.accounting.service;

import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.accounting.dto.response.AccountLedgerResponse;
import com.mkwang.backend.modules.accounting.dto.response.AccountLedgerResponse.*;
import com.mkwang.backend.modules.accounting.entity.InternalAccount;
import com.mkwang.backend.modules.accounting.mapper.AccountingReportMapper;
import com.mkwang.backend.modules.accounting.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AccountingReportService {
    private final AccountingJournalLineRepository lines;
    private final AccountingReportRepository aggregates;
    private final AccountingReportMapper mapper;

    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccountLedgerResponse getLedger(String account, LocalDate from, LocalDate to, Long employeeId,
            Long projectId, Long departmentId, Long phaseId, Long categoryId, int page, int limit) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        from = from == null ? today.withDayOfMonth(1) : from;
        to = to == null ? today : to;
        if (from.isAfter(to) || page < 1 || limit < 1 || limit > 100) throw new BadRequestException("Khoảng ngày hoặc phân trang không hợp lệ");
        if (account != null) InternalAccount.require(account);
        var openingSpec = AccountingJournalLineSpecification.filter(account, null, from.minusDays(1), employeeId,
                projectId, departmentId, phaseId, categoryId);
        var movementSpec = AccountingJournalLineSpecification.filter(account, from, to, employeeId,
                projectId, departmentId, phaseId, categoryId);
        Map<String, BigDecimal[]> opening = sums(aggregates.aggregate(openingSpec));
        Map<String, BigDecimal[]> movement = sums(aggregates.aggregate(movementSpec));
        List<AccountBalanceResponse> balances = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO, totalCredit = BigDecimal.ZERO;
        for (InternalAccount registry : InternalAccount.values()) {
            if (account != null && !registry.name().equals(account)) continue;
            BigDecimal[] before = opening.getOrDefault(registry.name(), zeros());
            BigDecimal[] during = movement.getOrDefault(registry.name(), zeros());
            BigDecimal start = before[0].subtract(before[1]);
            balances.add(new AccountBalanceResponse(registry.name(), registry.label(), registry.debitNormal() ? "DEBIT" : "CREDIT",
                    start, during[0], during[1], start.add(during[0]).subtract(during[1])));
            totalDebit = totalDebit.add(during[0]); totalCredit = totalCredit.add(during[1]);
        }
        var data = lines.findAll(movementSpec, PageRequest.of(page - 1, limit,
                Sort.by("journal.postingDate").and(Sort.by("journal.id")).and(Sort.by("lineNumber"))));
        BigDecimal balanceBeforePage = BigDecimal.ZERO;
        if (account != null) {
            BigDecimal[] before = opening.getOrDefault(account, zeros());
            balanceBeforePage = before[0].subtract(before[1]);
            if (!data.isEmpty()) {
                var previous = sums(aggregates.aggregate(movementSpec.and(AccountingJournalLineSpecification.before(data.getContent().get(0)))));
                BigDecimal[] amounts = previous.getOrDefault(account, zeros());
                balanceBeforePage = balanceBeforePage.add(amounts[0]).subtract(amounts[1]);
            }
        }
        return new AccountLedgerResponse(from, to, Instant.now(), "RECORDED_JOURNALS_ONLY", balances,
                totalDebit, totalCredit, totalDebit.compareTo(totalCredit) == 0, balanceBeforePage,
                PageResponse.<AccountLedgerEntryResponse>builder().items(data.stream().map(mapper::toEntry).toList())
                        .page(page).size(limit).total(data.getTotalElements()).totalPages(data.getTotalPages()).build());
    }

    private BigDecimal[] zeros() { return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}; }
    private Map<String, BigDecimal[]> sums(List<Object[]> rows) {
        Map<String, BigDecimal[]> result = new HashMap<>();
        for (Object[] row : rows) result.put((String) row[0], new BigDecimal[]{(BigDecimal) row[1], (BigDecimal) row[2]});
        return result;
    }
}
