package com.mkwang.backend.modules.accounting.repository;
import com.mkwang.backend.modules.accounting.entity.BankCandidateFields;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Locale;
public final class BankCandidateSpecification {
 private BankCandidateSpecification() {}
 public static <T extends BankCandidateFields> Specification<T> account(String value){return (r,q,cb)->cb.equal(r.get("bankAccount"),value);}
 public static <T extends BankCandidateFields> Specification<T> search(String value){return (r,q,cb)->cb.like(cb.lower(r.get("referenceCode")),"%"+value.toLowerCase(Locale.ROOT)+"%");}
 public static <T extends BankCandidateFields> Specification<T> from(LocalDate value){return (r,q,cb)->cb.greaterThanOrEqualTo(r.get("candidateDate"),value);}
 public static <T extends BankCandidateFields> Specification<T> to(LocalDate value){return (r,q,cb)->cb.lessThanOrEqualTo(r.get("candidateDate"),value);}
 public static <T extends BankCandidateFields> Specification<T> amount(BigDecimal value){return (r,q,cb)->cb.equal(r.get("amount"),value);}
 public static <T extends BankCandidateFields> Specification<T> filter(String account,String search,LocalDate from,LocalDate to,BigDecimal amount){
  Specification<T> spec=Specification.where(null);
  if(account!=null)spec=spec.and(account(account));if(search!=null&&!search.isBlank())spec=spec.and(search(search.trim()));
  if(from!=null)spec=spec.and(from(from));if(to!=null)spec=spec.and(to(to));if(amount!=null)spec=spec.and(amount(amount));return spec;
 }
}
