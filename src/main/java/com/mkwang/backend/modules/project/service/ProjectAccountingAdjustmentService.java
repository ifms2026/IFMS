package com.mkwang.backend.modules.project.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.project.repository.*;
import com.mkwang.backend.modules.project.entity.PhaseCategoryBudgetId;
import com.mkwang.backend.modules.project.entity.ProjectPhase;
import com.mkwang.backend.modules.project.entity.PhaseCategoryBudget;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor
public class ProjectAccountingAdjustmentService {
    private final ProjectRepository projects;private final ProjectPhaseRepository phases;
    private final PhaseCategoryBudgetRepository categories;private final ProjectFinancialGuard guard;
    public record CostCounterResponse(Long projectId,String projectCode,BigDecimal projectCost,BigDecimal availableBudget,
        BigDecimal plannedBudget,Map<Long,BigDecimal> phaseCosts,Map<String,BigDecimal> categoryCosts,Map<String,String> scopeLabels) {}
    @Transactional(readOnly=true)
    public CostCounterResponse costCounters(Long projectId) {
        var project=projects.findById(projectId).orElseThrow(()->new ResourceNotFoundException("Project not found"));
        Map<Long,BigDecimal> phaseCosts=new LinkedHashMap<>();Map<String,BigDecimal> categoryCosts=new LinkedHashMap<>();
        Map<String,String> scopeLabels=new LinkedHashMap<>();
        for(ProjectPhase phase:phases.findByProject_IdOrderByCreatedAtAsc(projectId)) {
            phaseCosts.put(phase.getId(),phase.getCurrentSpent());
            scopeLabels.put("phase:"+phase.getId(),phase.getName());
            for(PhaseCategoryBudget category:categories.findByIdPhaseId(phase.getId())) {
                categoryCosts.put(phase.getId()+":"+category.getCategory().getId(),category.getCurrentSpent());
                scopeLabels.put("category:"+phase.getId()+":"+category.getCategory().getId(),phase.getName()+" · "+category.getCategory().getName());
            }
        }
        return new CostCounterResponse(projectId,project.getProjectCode(),project.getTotalSpent(),project.getAvailableBudget(),project.getTotalBudget(),phaseCosts,categoryCosts,scopeLabels);
    }
    @Transactional
    public CostCounterResponse reconcileCosts(Long projectId,BigDecimal expectedProject,Map<Long,BigDecimal> expectedPhases,Map<String,BigDecimal> expectedCategories,
            BigDecimal observedCost,BigDecimal observedAvailable) {
        var project=projects.findByIdForUpdate(projectId).orElseThrow(()->new ResourceNotFoundException("Project not found"));
        if(project.getTotalSpent().compareTo(observedCost)!=0||project.getAvailableBudget().compareTo(observedAvailable)!=0)
            throw new BadRequestException("Bộ đếm đã đổi; cần xem trước lại");
        var current=costCounters(projectId);
        if(!current.phaseCosts().keySet().equals(expectedPhases.keySet())||!current.categoryCosts().keySet().equals(expectedCategories.keySet()))
            throw new BadRequestException("Cơ cấu giai đoạn/danh mục đã thay đổi");
        BigDecimal available=project.getAvailableBudget().add(project.getTotalSpent()).subtract(expectedProject);
        guard.assertLimit(projectId,null,null,expectedProject,project.getTotalBudget());
        if(available.signum()<0)throw new BadRequestException("Ngân sách khả dụng sẽ âm");
        for(var phase:phases.findByProject_IdOrderByCreatedAtAsc(projectId)) {
            BigDecimal cost=expectedPhases.get(phase.getId());guard.assertLimit(projectId,phase.getId(),null,cost,phase.getBudgetLimit());
            phase.setCurrentSpent(cost);
            for(var category:categories.findByIdPhaseId(phase.getId())) {
                BigDecimal categoryCost=expectedCategories.get(phase.getId()+":"+category.getCategory().getId());
                guard.assertLimit(projectId,phase.getId(),category.getCategory().getId(),categoryCost,category.getBudgetLimit());
                category.setCurrentSpent(categoryCost);
            }
        }
        project.setTotalSpent(expectedProject);project.setAvailableBudget(available);
        return costCounters(projectId);
    }
    @Transactional
    public void reverseCost(Long projectId,Long phaseId,Long categoryId,BigDecimal amount) {
        var project=projects.findByIdForUpdate(projectId).orElseThrow(()->new ResourceNotFoundException("Project not found"));
        var phase=phases.findById(phaseId).orElseThrow(()->new ResourceNotFoundException("Phase not found"));
        var category=categories.findById(new PhaseCategoryBudgetId(phaseId,categoryId)).orElseThrow(()->new BadRequestException("Thiếu hạn mức danh mục để đối chiếu"));
        if(amount.signum()<=0||project.getTotalSpent().compareTo(amount)<0||phase.getCurrentSpent().compareTo(amount)<0||category.getCurrentSpent().compareTo(amount)<0) throw new BadRequestException("Bộ đếm chi phí không đủ căn cứ để đảo; cần đối chiếu lịch sử");
        project.setTotalSpent(project.getTotalSpent().subtract(amount));project.setAvailableBudget(project.getAvailableBudget().add(amount));
        phase.setCurrentSpent(phase.getCurrentSpent().subtract(amount));category.setCurrentSpent(category.getCurrentSpent().subtract(amount));
    }
    @Transactional
    public void reverseFunding(Long projectId,BigDecimal amount) {
        var project=projects.findByIdForUpdate(projectId).orElseThrow(()->new ResourceNotFoundException("Project not found"));
        if(project.getAvailableBudget().subtract(amount).compareTo(guard.unrecognized(projectId,null,null))<0) throw new BadRequestException("Vốn đang bảo đảm cam kết/tạm ứng, chưa thể thu hồi");
        project.setAvailableBudget(project.getAvailableBudget().subtract(amount));
    }
    @Transactional
    public void checkReopenAdvance(Long projectId,Long phaseId,Long categoryId,BigDecimal amount) {
        var project=projects.findByIdForUpdate(projectId).orElseThrow(()->new ResourceNotFoundException("Project not found"));
        var phase=phases.findById(phaseId).orElseThrow(()->new ResourceNotFoundException("Phase not found"));
        var category=categories.findById(new PhaseCategoryBudgetId(phaseId,categoryId)).orElseThrow(()->new BadRequestException("Thiếu hạn mức danh mục"));
        guard.assertLimit(projectId,null,null,project.getTotalSpent().add(amount),project.getTotalBudget());
        guard.assertLimit(projectId,phaseId,null,phase.getCurrentSpent().add(amount),phase.getBudgetLimit());
        guard.assertLimit(projectId,phaseId,categoryId,category.getCurrentSpent().add(amount),category.getBudgetLimit());
    }
}
