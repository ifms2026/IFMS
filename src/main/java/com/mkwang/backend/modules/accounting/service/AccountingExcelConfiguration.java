package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.modules.spreadsheet.service.*;
import com.mkwang.backend.modules.spreadsheet.repository.ExcelExportSpecification;
import com.mkwang.backend.modules.accounting.repository.*;
import com.mkwang.backend.modules.accounting.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Configuration @RequiredArgsConstructor
public class AccountingExcelConfiguration {
    private final AccountingJournalLineRepository lines;private final AccountingReportService reports;private final PayslipRepository payslips;
    @Bean ExcelDataset journalsExcel(){
        var columns=List.of(column("journal","Bút toán","TEXT",false),column("date","Ngày ghi sổ","TEXT",false),column("event","Sự kiện","TEXT",false),column("source","Nguồn","TEXT",false),
                column("employeeId","Nhân viên","TEXT",false),column("project","Dự án","TEXT",false),column("phase","Giai đoạn","TEXT",false),column("category","Danh mục","TEXT",false),
                column("account","Tài khoản","TEXT",false),column("accountName","Tên tài khoản","TEXT",false),column("debit","Ghi nhận","MONEY",false),column("credit","Đối ứng","MONEY",false),column("effect","Diễn giải","TEXT",false));
        return new ExportExcelDataset("journals","Nhật ký bút toán kế toán · toàn bộ dòng",Set.of("ACCOUNTANT"),columns,(actor,filters,max)->{
            String account=filters.get("account");if(account!=null)InternalAccount.require(account);
            var spec=AccountingJournalLineSpecification.filter(account,date(filters,"from"),date(filters,"to"),id(filters,"employeeId"),
                    id(filters,"projectId"),id(filters,"departmentId"),id(filters,"phaseId"),id(filters,"categoryId"));
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
}
