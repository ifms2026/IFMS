package com.mkwang.backend.modules.accounting.dto.request;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record ReverseAccountingJournalRequest(@NotBlank @Size(min=10,max=1000)String reason,
        @NotBlank String pin,@NotNull LocalDate postingDate,Long bankEvidenceId) {}
