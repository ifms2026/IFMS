package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.modules.spreadsheet.service.*;
import com.mkwang.backend.modules.spreadsheet.repository.ExcelExportSpecification;
import com.mkwang.backend.modules.accounting.repository.*;
import com.mkwang.backend.modules.accounting.entity.*;
import com.mkwang.backend.modules.request.entity.AdvanceBalance;
import com.mkwang.backend.modules.request.entity.AdvanceBalanceStatus;
import com.mkwang.backend.modules.request.repository.AdvanceBalanceRepository;
import com.mkwang.backend.modules.project.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Configuration @RequiredArgsConstructor
public class AccountingExcelConfiguration {
    private final AccountingJournalLineRepository lines;private final AccountingReportService reports;private final PayslipRepository payslips;
    private final AdvanceBalanceRepository advances;private final ProjectMemberRepository projectMembers;
    @Bean ExcelDataset journalsExcel(){
        var columns=List.of(column("journal","Bút toán","TEXT",false),column("date","Ngày ghi sổ","TEXT",false),column("event","Sự kiện","TEXT",false),column("source","Nguồn","TEXT",false),
                column("employeeId","Nhân viên","TEXT",false),column("project","Dự án","TEXT",false),column("phase","Giai đoạn","TEXT",false),column("category","Danh mục","TEXT",false),
                column("account","Tài khoản","TEXT",false),column("accountName","Tên tài khoản","TEXT",false),column("debit","Ghi nhận","MONEY",false),column("credit","Đối ứng","MONEY",false),column("effect","Diễn giải","TEXT",false));
        return new ExportExcelDataset("journals","Nhật ký bút toán kế toán · toàn bộ dòng",Set.of("ACCOUNTANT"),columns,(actor,filters,max)->{
            String account=filters.get("account");if(account!=null)InternalAccount.require(account);
            AccountingJournalEvent event=null;
            if(filters.get("event")!=null&&!filters.get("event").isBlank()) try { event=AccountingJournalEvent.valueOf(filters.get("event")); }
            catch(IllegalArgumentException error) { throw new com.mkwang.backend.common.exception.BadRequestException("Loại nghiệp vụ kế toán không hợp lệ"); }
            var spec=AccountingJournalLineSpecification.filter(account,date(filters,"from"),date(filters,"to"),id(filters,"employeeId"),
                    id(filters,"projectId"),id(filters,"departmentId"),id(filters,"phaseId"),id(filters,"categoryId"),event);
            return lines.findAll(spec,PageRequest.of(0,max+1,Sort.by("journal.postingDate").and(Sort.by("journal.id")).and(Sort.by("lineNumber")))).stream()
                    .map(l->values("journal",l.getJournal().getJournalCode(),"date",l.getJournal().getPostingDate(),"event",l.getJournal().getEventType(),
                            "source",l.getJournal().getSourceType()+":"+l.getJournal().getSourceId(),"employeeId",l.getEmployeeId(),"project",l.getProjectName(),
                            "phase",l.getPhaseName(),"category",l.getCategoryName(),"account",l.getAccountCode(),"accountName",l.getAccountName(),
                            "debit",l.getDebitAmount(),"credit",l.getCreditAmount(),"effect",l.getEffectDescription())).toList();
        });
    }
    @Bean ExcelDataset trialBalanceExcel(){
        var columns=List.of(column("account","Tài khoản","TEXT",false),column("name","Tên tài khoản","TEXT",false),column("opening","Số dư đầu (ghi nhận - đối ứng)","MONEY_INFO",false),
                column("debit","Phát sinh ghi nhận","MONEY",false),column("credit","Phát sinh đối ứng","MONEY",false),column("closing","Số dư cuối (ghi nhận - đối ứng)","MONEY_INFO",false));
        return new ExportExcelDataset("account-balances","Sổ tài khoản và bảng đối chiếu · bút toán đã ghi",Set.of("ACCOUNTANT"),columns,(actor,filters,max)->{
            AccountingJournalEvent event=null;
            if(filters.get("event")!=null&&!filters.get("event").isBlank()) try { event=AccountingJournalEvent.valueOf(filters.get("event")); }
            catch(IllegalArgumentException error) { throw new com.mkwang.backend.common.exception.BadRequestException("Loại nghiệp vụ kế toán không hợp lệ"); }
            var report=reports.getLedger(filters.get("account"),date(filters,"from"),date(filters,"to"),id(filters,"employeeId"),id(filters,"projectId"),
                    id(filters,"departmentId"),id(filters,"phaseId"),id(filters,"categoryId"),1,1);
            return report.accounts().stream().map(a->values("account",a.accountCode(),"name",a.accountName(),"opening",a.openingBalance(),"debit",a.debitAmount(),"credit",a.creditAmount(),"closing",a.closingBalance())).toList();
        });
    }
    @Bean ExcelDataset myPayslipsExcel(){
        var columns=List.of(column("code","Phiếu lương","TEXT",false),column("period","Kỳ lương","TEXT",false),column("status","Trạng thái","TEXT",false),
                column("base","Lương cơ bản gốc","MONEY_INFO",false),column("bonus","Thưởng gốc","MONEY_INFO",false),column("allowance","Phụ cấp gốc","MONEY_INFO",false),
                column("deduction","Khấu trừ khác gốc","MONEY_INFO",false),column("advanceOffset","Bù trừ tạm ứng gốc","MONEY_INFO",false),
                column("net","Thực lĩnh trên phiếu","MONEY_INFO",false),column("effectiveNet","Thực lĩnh còn hiệu lực","MONEY",false),column("replaces","Thay thế phiếu","TEXT",false));
        return new ExportExcelDataset("my-payslips","Phiếu lương đã chi và lịch sử điều chỉnh của tôi",Set.of("ADMIN","CFO","ACCOUNTANT","MANAGER","TEAM_LEADER","EMPLOYEE"),columns,(actor,filters,max)->{
            var spec=ExcelExportSpecification.<Payslip>filter(filters,Map.of("user.id",actor.getId(),"status",List.of(PayslipStatus.PAID,PayslipStatus.REVERSED)));
            return payslips.findAll(spec,PageRequest.of(0,max+1,Sort.by("id"))).stream().map(p->values("code",p.getPayslipCode(),"period",p.getPeriod().getPeriodCode(),"status",p.getStatus(),
                    "base",p.getBaseSalary(),"bonus",p.getBonus(),"allowance",p.getAllowance(),"deduction",p.getDeduction(),"advanceOffset",p.getAdvanceDeduct(),
                    "net",p.getFinalNetSalary(),"effectiveNet",p.getStatus()==PayslipStatus.PAID?p.getFinalNetSalary():java.math.BigDecimal.ZERO,"replaces",p.getReplacesPayslipId())).toList();
        });
    }

    @Bean ExcelDataset outstandingAdvancesExcel(){
        var columns=List.of(column("request","Mã yêu cầu tạm ứng","TEXT",false),column("employee","Nhân viên","TEXT",false),
                column("department","Phòng ban","TEXT",false),column("project","Dự án","TEXT",false),column("phase","Giai đoạn","TEXT",false),
                column("category","Danh mục","TEXT",false),column("original","Đã giải ngân","MONEY",false),
                column("reimbursed","Đã quyết toán chứng từ","MONEY",false),column("returned","Đã hoàn tiền thật","MONEY",false),
                column("payrollOffset","Đã bù trừ qua lương","MONEY",false),column("unclassified","Lịch sử chưa phân loại","MONEY_INFO",false),
                column("remaining","Còn phải quyết toán","MONEY",false),column("status","Trạng thái","TEXT",false));
        return new ExportExcelDataset("advance-balances","Dư tạm ứng hiện tại theo nhân viên · từng khoản",Set.of("ADMIN","CFO","ACCOUNTANT","MANAGER","TEAM_LEADER","EMPLOYEE"),columns,(actor,filters,max)->{
            Map<String,Object> dimensions=new HashMap<>();
            switch(actor.getRole().getName()){
                case "EMPLOYEE"->dimensions.put("user.id",actor.getId());
                case "MANAGER"->{if(actor.getDepartment()==null)return List.of();dimensions.put("user.department.id",actor.getDepartment().getId());}
                case "TEAM_LEADER"->{List<Long> projectIds=projectMembers.findProjectIdsByLeader(actor.getId());if(projectIds.isEmpty())return List.of();dimensions.put("advanceRequest.project.id",projectIds);}
                default->{}
            }
            Long employeeId=id(filters,"employeeId");Long projectId=id(filters,"projectId");
            Long departmentId=id(filters,"departmentId");Long phaseId=id(filters,"phaseId");Long categoryId=id(filters,"categoryId");
            var requestedDimensions=new HashMap<String,Object>();
            if(employeeId!=null)requestedDimensions.put("user.id",employeeId);
            if(projectId!=null)requestedDimensions.put("advanceRequest.project.id",projectId);
            if(departmentId!=null)requestedDimensions.put("user.department.id",departmentId);
            if(phaseId!=null)requestedDimensions.put("advanceRequest.phase.id",phaseId);
            if(categoryId!=null)requestedDimensions.put("advanceRequest.category.id",categoryId);
            var spec=ExcelExportSpecification.<AdvanceBalance>filter(Map.of(),dimensions)
                    .and(ExcelExportSpecification.filter(Map.of(),requestedDimensions))
                    .and((root,query,cb)->cb.and(cb.greaterThan(root.get("remainingAmount"),java.math.BigDecimal.ZERO),cb.notEqual(root.get("status"),AdvanceBalanceStatus.SETTLED)));
            return advances.findAll(spec,PageRequest.of(0,max+1,Sort.by("createdAt").and(Sort.by("id")))).stream().map(balance->{
                var request=balance.getAdvanceRequest();var project=request.getProject();
                return values("request",request.getRequestCode(),"employee",balance.getUser().getFullName(),
                        "department",balance.getUser().getDepartment()==null?null:balance.getUser().getDepartment().getName(),
                        "project",project==null?null:project.getName(),"phase",request.getPhase()==null?null:request.getPhase().getName(),
                        "category",request.getCategory()==null?null:request.getCategory().getName(),"original",balance.getOriginalAmount(),
                        "reimbursed",balance.getReimbursedAmount(),"returned",balance.getCashReturnedAmount(),
                        "payrollOffset",balance.getPayrollOffsetAmount(),"unclassified",balance.getLegacyUnclassifiedAmount(),
                        "remaining",balance.getRemainingAmount(),"status",balance.getStatus());
            }).toList();
        });
    }
}
