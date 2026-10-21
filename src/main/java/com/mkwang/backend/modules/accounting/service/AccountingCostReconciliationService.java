package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.accounting.dto.request.ReconcileCostCountersRequest;
import com.mkwang.backend.modules.accounting.dto.response.CostCounterPreviewResponse;
import com.mkwang.backend.modules.accounting.entity.*;
import com.mkwang.backend.modules.accounting.repository.AccountingJournalRepository;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.profile.dto.request.VerifyMyPinRequest;
import com.mkwang.backend.modules.profile.service.ProfileService;
import com.mkwang.backend.modules.project.service.*;
import com.mkwang.backend.modules.request.service.RequestHistoricalEvidenceService;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
@Service @RequiredArgsConstructor
public class AccountingCostReconciliationService {
    private final ProjectAccountingAdjustmentService projects;private final ProjectQueryService projectQueries;
    private final RequestHistoricalEvidenceService sources;private final FinancialDocumentService documents;
    private final AccountingJournalRepository journals;private final ProfileService profiles;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    @PersistenceContext private EntityManager em;
    @Transactional @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public CostCounterPreviewResponse preview(Long projectId){return inspect(projectId);}
    private CostCounterPreviewResponse inspect(Long projectId){
        projectQueries.lockFinancialProject(projectId);
        var current=projects.costCounters(projectId);
        Map<Long,BigDecimal> phaseCosts=new LinkedHashMap<>();current.phaseCosts().keySet().forEach(k->phaseCosts.put(k,BigDecimal.ZERO));
        Map<String,BigDecimal> categoryCosts=new LinkedHashMap<>();current.categoryCosts().keySet().forEach(k->categoryCosts.put(k,BigDecimal.ZERO));
        List<String> errors=new ArrayList<>();BigDecimal verified=BigDecimal.ZERO;
        var costs=sources.verifiedCosts(projectId);
        for(var source:costs){
            if(source.amount()==null||source.amount().signum()<=0||source.phaseId()==null||source.categoryId()==null){errors.add("Nguồn #"+source.requestId()+" thiếu số tiền hoặc phạm vi");continue;}
            String event="EXPENSE".equals(source.type())?"EXPENSE_VERIFIED":"REIMBURSE_SETTLED";
            var journal=journals.findByEventTypeAndSourceTypeAndSourceId(AccountingJournalEvent.valueOf(event),"REQUEST",source.requestId().toString());
            if(journal.isEmpty()||journal.get().getTotalAmount().compareTo(source.amount())!=0
                ||!Objects.equals(journal.get().getProjectId(),projectId)||!Objects.equals(journal.get().getPhaseId(),source.phaseId())
                ||!Objects.equals(journal.get().getCategoryId(),source.categoryId())
                ||!documents.hasAllocation(source.requestId(),source.amount())){errors.add("Nguồn #"+source.requestId()+" chưa đủ bút toán/chứng từ đúng số tiền và phạm vi");continue;}
            if("EXPENSE".equals(source.type())&&"PAID".equals(source.status())&&
                journals.findByEventTypeAndSourceTypeAndSourceId(AccountingJournalEvent.EXPENSE_PAID,"REQUEST",source.requestId().toString()).isEmpty()){
                errors.add("Nguồn #"+source.requestId()+" thiếu bút toán thanh toán hoàn chi");continue;
            }
            String key=source.phaseId()+":"+source.categoryId();
            if(!phaseCosts.containsKey(source.phaseId())||!categoryCosts.containsKey(key)){errors.add("Nguồn #"+source.requestId()+" không còn hạn mức giai đoạn/danh mục");continue;}
            verified=verified.add(source.amount());phaseCosts.merge(source.phaseId(),source.amount(),BigDecimal::add);categoryCosts.merge(key,source.amount(),BigDecimal::add);
        }
        BigDecimal recorded=(BigDecimal)em.createNativeQuery("""
            SELECT COALESCE(SUM(l.debit_amount-l.credit_amount),0) FROM accounting_journal_lines l
            WHERE l.project_id=:project AND l.account_code='PROJECT_EXPENSE'
            """).setParameter("project",projectId).getSingleResult();
        if(recorded.compareTo(verified)!=0) errors.add("Sổ chi phí dự án không khớp nguồn hợp lệ; cần đối chiếu bút toán đảo/ghi bù trước");
        return new CostCounterPreviewResponse(projectId,current,verified,phaseCosts,categoryCosts,recorded,costs.size(),errors,errors.isEmpty());
    }
    @Transactional @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public CostCounterPreviewResponse apply(ReconcileCostCountersRequest input){
        var actor=((UserDetailsAdapter)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
        if(!profiles.verifyMyPin(actor.getId(),new VerifyMyPinRequest(input.pin())).isValid())throw new UnauthorizedException("PIN không đúng");
        if(!input.evidenceReviewed())throw new BadRequestException("Cần xác nhận đã đối chiếu nguồn và chứng từ");
        em.createNativeQuery("SELECT pg_advisory_xact_lock(89654322)").getSingleResult();
        var preview=inspect(input.projectId());
        if(!preview.eligible())throw new BadRequestException(String.join("; ",preview.errors()));
        if(preview.current().projectCost().compareTo(input.expectedCurrentCost())!=0||preview.current().availableBudget().compareTo(input.expectedAvailableBudget())!=0||preview.verifiedCost().compareTo(input.expectedVerifiedCost())!=0)
            throw new BadRequestException("Số liệu đã đổi; xem trước và xác nhận lại");
        if(preview.current().projectCost().compareTo(preview.verifiedCost())==0&&
                preview.current().phaseCosts().equals(preview.phaseCosts())&&preview.current().categoryCosts().equals(preview.categoryCosts()))
            throw new BadRequestException("Bộ đếm đã khớp, không tạo lần điều chỉnh bằng 0");
        var after=projects.reconcileCosts(input.projectId(),preview.verifiedCost(),preview.phaseCosts(),preview.categoryCosts(),input.expectedCurrentCost(),input.expectedAvailableBudget());
        String beforeJson,afterJson;try{beforeJson=json.writeValueAsString(preview.current());afterJson=json.writeValueAsString(after);}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new InternalSystemException("Không lưu được biên bản đối chiếu");}
        em.createNativeQuery("""
            INSERT INTO accounting_cost_reconciliations(project_id,before_snapshot,after_snapshot,source_count,reason,actor_id)
            VALUES (:project,CAST(:before AS jsonb),CAST(:after AS jsonb),:count,:reason,:actor)
            """).setParameter("project",input.projectId()).setParameter("before",beforeJson).setParameter("after",afterJson)
            .setParameter("count",preview.sourceCount()).setParameter("reason",input.reason()).setParameter("actor",actor.getId()).executeUpdate();
        return inspect(input.projectId());
    }
}
