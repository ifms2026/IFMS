package com.mkwang.backend.modules.request.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.request.entity.*;
import com.mkwang.backend.modules.request.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
@Service @RequiredArgsConstructor
public class RequestHistoricalEvidenceService {
    private final RequestRepository requests;private final AdvanceBalanceRepository advances;
    private final com.mkwang.backend.modules.user.service.UserService users;
    private final com.mkwang.backend.modules.project.service.ProjectQueryService projects;
    private final com.mkwang.backend.modules.request.mapper.RequestAccountingMapper mapper;
    @Transactional
    public Request require(Long id,boolean lock) {
        var request=(lock?requests.findByIdForUpdate(id):requests.findById(id)).orElseThrow(()->new ResourceNotFoundException("Request not found"));
        if(request.getStatus()!=RequestStatus.PAID&&request.getStatus()!=RequestStatus.ACCOUNTANT_VERIFIED) throw new BadRequestException("Nguồn chưa hoàn tất nghiệp vụ được ghi sổ");
        if(request.getApprovedAmount()==null||request.getApprovedAmount().signum()<=0) throw new BadRequestException("Thiếu số tiền phê duyệt");
        if(requests.findHistoriesByRequestId(id).stream().noneMatch(h->h.getAction()==RequestAction.APPROVE&&h.getActor()!=null)) throw new BadRequestException("Thiếu dấu vết phê duyệt có người chịu trách nhiệm");
        return request;
    }
    @Transactional
    public AdvanceBalance lockAdvance(Long id) {
        var observed=advance(id);users.lockFinancialScope(observed.getUser().getId());
        projects.lockFinancialProject(observed.getAdvanceRequest().getProject().getId());
        return advances.findByIdForUpdate(id).orElseThrow(()->new BadRequestException("Không có tạm ứng nguồn"));
    }
    @Transactional(readOnly=true)
    public java.util.List<com.mkwang.backend.modules.request.dto.response.RequestAccountingContextResponse> historicalRequests(String search,int max) {
        var spec=com.mkwang.backend.modules.request.repository.RequestSpecification.filterHistorical(search);
        var page=requests.findAll(spec,org.springframework.data.domain.PageRequest.of(0,max+1,org.springframework.data.domain.Sort.by("id").descending()));
        if(page.getTotalElements()>max)throw new BadRequestException("Có quá nhiều yêu cầu trùng mã tìm kiếm; nhập mã cụ thể hơn");
        return page.stream()
            .filter(r->r.getStatus()==RequestStatus.PAID || r.getStatus()==RequestStatus.ACCOUNTANT_VERIFIED)
            .map(mapper::toContext).toList();
    }
    @Transactional(readOnly=true)
    public java.util.List<com.mkwang.backend.modules.request.dto.response.VerifiedCostSourceResponse> verifiedCosts(Long projectId) {
        var spec=com.mkwang.backend.modules.request.repository.RequestSpecification.filterVerifiedCosts(projectId);
        return requests.findAll(spec).stream().map(r->new com.mkwang.backend.modules.request.dto.response.VerifiedCostSourceResponse(
            r.getId(),projectId,r.getPhase()==null?null:r.getPhase().getId(),r.getCategory()==null?null:r.getCategory().getId(),
            r.getType().name(),r.getStatus().name(),r.getApprovedAmount())).toList();
    }
    @Transactional(readOnly=true)
    public java.util.List<java.util.Map<String,Object>> employeeAdvances(Long employee) {
        return advances.findByUserIdOrderByIdAsc(employee,org.springframework.data.domain.PageRequest.of(0,500)).stream()
            .map(a->com.mkwang.backend.modules.spreadsheet.service.ExcelValues.values("id",a.getId(),"code",a.getAdvanceRequest().getRequestCode(),"employee",a.getUser().getFullName(),"remaining",a.getRemainingAmount(),"legacy",a.getLegacyUnclassifiedAmount())).toList();
    }
    @Transactional(readOnly=true)
    public AdvanceBalance advance(Long id){return advances.findById(id).orElseThrow(()->new BadRequestException("Không có tạm ứng nguồn"));}
    @Transactional(readOnly=true)
    public AdvanceBalance advanceForRequest(Long id){return advances.findByAdvanceRequest_Id(id).orElseThrow(()->new BadRequestException("Thiếu dư tạm ứng gốc"));}
    @Transactional
    public void classifyPayroll(Long id,Long employee,BigDecimal amount) {
        var balance=advances.findByIdForUpdate(id).orElseThrow(()->new BadRequestException("Thiếu tạm ứng nguồn"));
        if(!employee.equals(balance.getUser().getId()))throw new BadRequestException("Tạm ứng khác nhân viên");
        balance.classifyLegacyPayroll(amount);
    }
}
