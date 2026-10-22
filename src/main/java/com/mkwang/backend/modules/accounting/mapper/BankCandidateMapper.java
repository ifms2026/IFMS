package com.mkwang.backend.modules.accounting.mapper;
import org.springframework.stereotype.Component;
import com.mkwang.backend.modules.accounting.entity.BankCandidateFields;
import com.mkwang.backend.modules.accounting.dto.response.BankCandidateResponse;
@Component public class BankCandidateMapper {
 public BankCandidateResponse toResponse(BankCandidateFields row){
  return new BankCandidateResponse(row.getId(),row.getReferenceCode()+" · "+row.getAmount().abs().toPlainString()+" đ · "+row.getCandidateDate(),row.getReferenceCode(),row.getCandidateDate(),row.getAmount());
 }
}
