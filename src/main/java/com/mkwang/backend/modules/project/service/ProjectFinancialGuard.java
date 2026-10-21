package com.mkwang.backend.modules.project.service;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.request.service.RequestExposureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor
public class ProjectFinancialGuard {
    private final RequestExposureService exposure;
    public BigDecimal unrecognized(Long projectId,Long phaseId,Long categoryId) {
        return exposure.get(projectId).stream()
                .filter(row->phaseId==null||phaseId.equals(row.phaseId()))
                .filter(row->categoryId==null||categoryId.equals(row.categoryId()))
                .map(row->row.committed().add(row.openAdvance())).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    public void assertLimit(Long projectId,Long phaseId,Long categoryId,BigDecimal cost,BigDecimal limit) {
        if(limit.signum()<0||limit.compareTo(cost.add(unrecognized(projectId,phaseId,categoryId)))<0) {
            throw new BadRequestException("Hạn mức không đủ cho chi phí, cam kết và tạm ứng chưa quyết toán");
        }
    }
}
