package com.mkwang.backend.modules.accounting.dto.response;
import java.math.BigDecimal;
import java.time.LocalDate;
public record BankCandidateResponse(Long id,String label,String referenceCode,LocalDate date,BigDecimal amount) {}
