package com.mkwang.backend.modules.accounting.dto.request;
import com.mkwang.backend.modules.accounting.entity.AccountingJournalEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public record BackfillJournalBatchRequest(@NotEmpty @Size(max=100) @Valid List<OperationRequest> operations,
        @AssertTrue boolean acknowledgeHistoricalEvidence) {
    public record OperationRequest(@NotNull AccountingJournalEvent event,@NotNull Long sourceId,
            @NotNull @PastOrPresent LocalDate businessDate,@NotNull @PastOrPresent LocalDate postingDate,
            @NotBlank @Size(min=10,max=1000)String evidenceRef,
            @Valid List<VerifyFinancialDocumentRequest> documents,@Valid List<PayrollAllocationRequest> payrollAllocations) {}
    public record PayrollAllocationRequest(@NotNull Long advanceBalanceId,@NotNull @DecimalMin("0.01")BigDecimal amount,
            @NotBlank String evidenceRef) {}
}
