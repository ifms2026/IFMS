package com.mkwang.backend.modules.request.service;
import com.mkwang.backend.modules.request.dto.response.BudgetExposureResponse;
import com.mkwang.backend.modules.request.repository.*;
import com.mkwang.backend.modules.request.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
@Service @RequiredArgsConstructor
public class RequestExposureService {
    private final RequestRepository requests;private final AdvanceBalanceRepository advances;
    @Transactional(readOnly=true)
    public List<BudgetExposureResponse> get(Long projectId) {
        var result=new ArrayList<BudgetExposureResponse>();
        for(var request:requests.findByProject_IdAndStatusIn(projectId,List.of(RequestStatus.APPROVED_BY_TEAM_LEADER))) {
            if(request.getPhase()!=null&&request.getCategory()!=null) result.add(new BudgetExposureResponse(
                    request.getPhase().getId(),request.getCategory().getId(),request.getReservedAmount(),BigDecimal.ZERO));
        }
        for(var balance:advances.findByAdvanceRequest_Project_IdAndStatusNot(projectId,AdvanceBalanceStatus.SETTLED)) {
            var request=balance.getAdvanceRequest();
            if(request.getPhase()!=null&&request.getCategory()!=null) result.add(new BudgetExposureResponse(
                    request.getPhase().getId(),request.getCategory().getId(),BigDecimal.ZERO,balance.getRemainingAmount()));
        }
        return result;
    }
}
