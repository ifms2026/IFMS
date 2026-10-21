package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.user.entity.*;
import com.mkwang.backend.modules.user.service.UserService;
import com.mkwang.backend.modules.accounting.entity.*;
import com.mkwang.backend.modules.accounting.repository.*;
import com.mkwang.backend.modules.spreadsheet.service.*;
import com.mkwang.backend.modules.spreadsheet.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Component @RequiredArgsConstructor
public class PayrollExcelDataset implements ExcelDataset {
    private final PayrollPeriodRepository periods;private final PayslipRepository payslips;private final UserService users;
    public boolean supportsDateFilter(){return false;}
    public String code(){return "payroll";}public String label(){return "Bảng lương · thay thế toàn bộ bản nháp";}
    public List<Map<String,Object>> scopes(User actor){
        return periods.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"year","month")).stream()
                .map(p->values("id",p.getId(),"label",p.getPeriodCode()+" · "+p.getStatus())).toList();
    }
    public Set<String> roles(){return Set.of("ACCOUNTANT");}public String scopeLabel(){return "Kỳ lương";}
    public List<ExcelColumnResponse> columns(){return List.of(column("employeeCode","Mã nhân viên","TEXT",true),column("employeeName","Họ tên để đối chiếu","TEXT",false),column("baseSalary","Lương cơ bản","MONEY",true),column("bonus","Thưởng","MONEY",false),column("allowance","Phụ cấp","MONEY",false),column("deduction","Khấu trừ khác","MONEY",false));}
    public void authorize(User actor,Long scope){if(scope==null||!periods.existsById(scope)) throw new BadRequestException("Chọn kỳ lương có thật");}
    private PayrollPeriod draft(Long id){
        var period=periods.findById(id).orElseThrow(()->new ResourceNotFoundException("PayrollPeriod not found"));
        if(!period.isEditable()||period.getPayslips().stream().anyMatch(p->p.getStatus()!=PayslipStatus.DRAFT)) throw new BadRequestException("Không nhập hoặc ghi đè kỳ lương đã chi");
        return period;
    }
    public List<ExcelRowErrorResponse> validate(List<ExcelRowResponse> rows,User actor,Long scope){
        draft(scope);
        return ExcelValidation.rows(rows,row->row.values().get("employeeCode"),row->{
            var v=row.values();User user=users.getUserByEmployeeCode(v.get("employeeCode"));
            if(user.getStatus()!=UserStatus.ACTIVE) throw new BadRequestException("Nhân viên không hoạt động");
            if(!v.get("employeeName").isBlank()&&!user.getFullName().equalsIgnoreCase(v.get("employeeName"))) throw new BadRequestException("Họ tên không khớp mã nhân viên");
            BigDecimal base=money(v,"baseSalary");
            if(base.signum()<=0) throw new BadRequestException("Lương cơ bản phải lớn hơn 0");
            if(base.add(money(v,"bonus")).add(money(v,"allowance")).subtract(money(v,"deduction")).signum()<0) throw new BadRequestException("Khấu trừ vượt thu nhập");
        });
    }
    public void beforeCommit(List<ExcelRowResponse> rows,User actor,Long scope){
        var period=periods.findByIdForUpdate(scope).orElseThrow(()->new ResourceNotFoundException("PayrollPeriod not found"));
        draft(scope);period.getPayslips().clear();period.setNettingApplied(false);periods.flush();
    }
    public String commit(ExcelRowResponse row,User actor,Long scope){
        var v=row.values();var period=draft(scope);var user=users.getUserByEmployeeCode(v.get("employeeCode"));
        var base=money(v,"baseSalary");var bonus=money(v,"bonus");var allowance=money(v,"allowance");var deduction=money(v,"deduction");
        var slip=Payslip.builder().period(period).user(user).payslipCode(String.format("PSL-%s-%02d%02d",v.get("employeeCode"),period.getMonth(),period.getYear()%100))
                .baseSalary(base).bonus(bonus).allowance(allowance).deduction(deduction).advanceDeduct(BigDecimal.ZERO)
                .finalNetSalary(base.add(bonus).add(allowance).subtract(deduction)).status(PayslipStatus.DRAFT).build();
        return "PAYSLIP:"+payslips.save(slip).getId();
    }
    public List<Map<String,Object>> references(User actor,Long scope){
        return users.getPayrollReferenceUsers(5000).stream()
                .map(user->values("kind","EMPLOYEE","id",user.getProfile().getEmployeeCode(),"name",user.getFullName()))
                .toList();
    }
    public List<ExcelColumnResponse> exportColumns(){var result=new ArrayList<>(columns());result.addAll(List.of(column("advanceDeduct","Khấu trừ tạm ứng","MONEY",false),column("finalNetSalary","Thực lĩnh","MONEY",false),column("status","Trạng thái","TEXT",false)));return result;}
    public List<Map<String,Object>> export(User actor,Long scope,Map<String,String> filters,int max){
        var p=periods.findById(scope).orElseThrow(()->new ResourceNotFoundException("PayrollPeriod not found"));
        return p.getPayslips().stream().filter(s->s.getStatus()!=PayslipStatus.REVERSED).limit(max+1L).map(s->values("employeeCode",s.getUser().getProfile()==null?null:s.getUser().getProfile().getEmployeeCode(),"employeeName",s.getUser().getFullName(),"baseSalary",s.getBaseSalary(),"bonus",s.getBonus(),"allowance",s.getAllowance(),"deduction",s.getDeduction(),"advanceDeduct",s.getAdvanceDeduct(),"finalNetSalary",s.getFinalNetSalary(),"status",s.getStatus())).toList();
    }
}
