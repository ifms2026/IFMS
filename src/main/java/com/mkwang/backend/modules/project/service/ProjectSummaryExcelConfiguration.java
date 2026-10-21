package com.mkwang.backend.modules.project.service;
import com.mkwang.backend.modules.spreadsheet.service.*;
import com.mkwang.backend.modules.spreadsheet.repository.ExcelExportSpecification;
import com.mkwang.backend.modules.project.entity.Project;
import com.mkwang.backend.modules.project.repository.*;
import com.mkwang.backend.modules.request.service.RequestExposureService;
import com.mkwang.backend.modules.wallet.service.WalletService;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Configuration @RequiredArgsConstructor
public class ProjectSummaryExcelConfiguration {
    private final ProjectRepository projects;private final ProjectMemberRepository members;
    private final RequestExposureService exposure;private final WalletService wallets;
    @Bean ExcelDataset projectSummaryExcel(){
        var columns=List.of(column("code","Dự án","TEXT",false),column("name","Tên dự án","TEXT",false),column("department","Phòng ban","TEXT",false),
                column("status","Trạng thái","TEXT",false),column("budget","Ngân sách kế hoạch","MONEY",false),column("fund","Số dư ví dự án","MONEY",false),
                column("held","Tiền đang giữ trong ví","MONEY",false),column("cost","Chi phí đã xác nhận","MONEY",false),
                column("commitment","Cam kết chưa ghi chi phí","MONEY",false),column("openAdvance","Tạm ứng còn mở","MONEY",false));
        return new ExportExcelDataset("project-financial-summary","Số dư hiện tại theo dự án phụ trách",Set.of("MANAGER","TEAM_LEADER","CFO","ACCOUNTANT"),columns,(actor,filters,max)->{
            Map<String,Object> scope=new HashMap<>();
            if("MANAGER".equals(actor.getRole().getName()))scope.put("department.id",actor.getDepartment().getId());
            if("TEAM_LEADER".equals(actor.getRole().getName()))scope.put("id",members.findProjectIdsByLeader(actor.getId()));
            var spec=ExcelExportSpecification.<Project>filter(Map.of(),scope);
            return projects.findAll(spec,PageRequest.of(0,max+1,Sort.by("id"))).stream().map(p->{
                var wallet=wallets.getWallet(WalletOwnerType.PROJECT,p.getId());var open=exposure.get(p.getId());
                return values("code",p.getProjectCode(),"name",p.getName(),"department",p.getDepartment().getName(),"status",p.getStatus(),
                        "budget",p.getTotalBudget(),"fund",wallet.getBalance(),"held",wallet.getLockedBalance(),"cost",p.getTotalSpent(),
                        "commitment",open.stream().map(e->e.committed()).reduce(BigDecimal.ZERO,BigDecimal::add),
                        "openAdvance",open.stream().map(e->e.openAdvance()).reduce(BigDecimal.ZERO,BigDecimal::add));
            }).toList();
        });
    }
}
