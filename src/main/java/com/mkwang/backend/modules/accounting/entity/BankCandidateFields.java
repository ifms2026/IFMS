package com.mkwang.backend.modules.accounting.entity;
import jakarta.persistence.*;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;
@Getter @MappedSuperclass
public abstract class BankCandidateFields {
 @Id protected Long id;
 @Column(name="reference_code") protected String referenceCode;
 @Column(name="candidate_date") protected LocalDate candidateDate;
 protected BigDecimal amount;
 @Column(name="bank_account") protected String bankAccount;
}
