package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.accounting.dto.response.BankCandidateResponse;
import com.mkwang.backend.modules.accounting.repository.*;
import com.mkwang.backend.modules.accounting.mapper.BankCandidateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.time.LocalDate;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor
public class BankCandidateQueryService {
 private final UnusedTopupCandidateRepository topups;private final UnmatchedMovementCandidateRepository movements;private final BankCandidateMapper mapper;
 @Transactional(readOnly=true)
 public PageResponse<BankCandidateResponse> find(boolean topup,String account,String search,LocalDate from,LocalDate to,BigDecimal amount,int page,int limit){
  if(page<1||limit<1||limit>100||((long)page-1)*limit>Integer.MAX_VALUE||from!=null&&to!=null&&from.isAfter(to))throw new BadRequestException("Khoảng ngày hoặc phân trang không hợp lệ");
  if(amount!=null&&(amount.signum()==0||topup&&amount.signum()<0))throw new BadRequestException("Số tiền tìm kiếm không hợp lệ");
  var pageable=PageRequest.of(page-1,limit,Sort.by(Sort.Direction.DESC,"candidateDate","id"));
  Page<BankCandidateResponse> result;
  if(topup){var spec=BankCandidateSpecification.<com.mkwang.backend.modules.accounting.entity.UnusedTopupCandidate>filter(account,search,from,to,amount);result=topups.findAll(spec,pageable).map(mapper::toResponse);}
  else {var spec=BankCandidateSpecification.<com.mkwang.backend.modules.accounting.entity.UnmatchedMovementCandidate>filter(null,search,from,to,amount);result=movements.findAll(spec,pageable).map(mapper::toResponse);}
  return PageResponse.<BankCandidateResponse>builder().items(result.getContent()).total(result.getTotalElements()).page(page).size(limit).totalPages(result.getTotalPages()).build();
 }
}
