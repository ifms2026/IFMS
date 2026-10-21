package com.mkwang.backend.modules.user.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.user.entity.*;
import com.mkwang.backend.modules.user.repository.*;
import com.mkwang.backend.modules.user.dto.request.AdminCreateUserRequest;
import com.mkwang.backend.modules.organization.service.DepartmentService;
import com.mkwang.backend.modules.spreadsheet.service.*;
import com.mkwang.backend.modules.spreadsheet.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.*;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Component @RequiredArgsConstructor
public class UserExcelDataset implements ExcelDataset {
    private final UserRepository users; private final RoleRepository roles;
    private final UserService service; private final DepartmentService departments;
    public String code(){return "employees";} public String label(){return "Nhân sự · tạo mới và onboarding";}
    public Set<String> roles(){return Set.of("ADMIN");}
    public List<ExcelColumnResponse> columns(){return List.of(column("fullName","Họ tên","TEXT",true),column("email","Email","TEXT",true),column("roleId","Vai trò","TEXT",true),column("departmentId","Phòng ban","TEXT",false));}
    public void authorize(User actor,Long scope){if(scope!=null) throw new BadRequestException("Không dùng scope cho nhân sự");}
    private AdminCreateUserRequest request(ExcelRowResponse row){
        var v=row.values();return new AdminCreateUserRequest(v.get("fullName"),v.get("email").toLowerCase(Locale.ROOT),id(v,"roleId"),id(v,"departmentId"));
    }
    public List<ExcelRowErrorResponse> validate(List<ExcelRowResponse> rows,User actor,Long scope){
        return ExcelValidation.rows(rows,row->row.values().get("email").toLowerCase(Locale.ROOT),row->{
            var req=request(row);
            if(req.getFullName().isBlank()||req.getFullName().length()>255||!req.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new BadRequestException("Họ tên hoặc email không hợp lệ");
            if(users.existsByEmail(req.getEmail())) throw new BadRequestException("Email đã tồn tại");
            if(req.getRoleId()==null||!roles.existsById(req.getRoleId())) throw new BadRequestException("Vai trò không tồn tại");
            var role=roles.findById(req.getRoleId()).orElseThrow(()->new BadRequestException("Vai trò không tồn tại"));
            if(Set.of("EMPLOYEE","TEAM_LEADER").contains(role.getName())&&req.getDepartmentId()==null)throw new BadRequestException("Nhân viên và trưởng nhóm cần có phòng ban");
            if(req.getDepartmentId()!=null) departments.getDepartmentDetail(req.getDepartmentId());
        });
    }
    public String commit(ExcelRowResponse row,User actor,Long scope){return "USER:"+service.adminCreateUser(request(row)).getId();}
    public List<Map<String,Object>> references(User actor,Long scope){
        var result=new ArrayList<Map<String,Object>>();
        roles.findAll().forEach(role->result.add(values("kind","ROLE","id",role.getId(),"name",role.getName())));
        var depts=departments.getDepartments(null,1,20000);
        depts.getItems().forEach(dept->result.add(values("kind","DEPARTMENT","id",dept.getId(),"name",dept.getName())));
        return result;
    }
    public List<ExcelColumnResponse> exportColumns(){return List.of(column("employeeCode","Mã nhân viên","TEXT",false),column("fullName","Họ tên","TEXT",false),column("email","Email","TEXT",false),column("role","Vai trò","TEXT",false),column("department","Phòng ban","TEXT",false),column("status","Trạng thái","TEXT",false));}
    public List<Map<String,Object>> export(User actor,Long scope,Map<String,String> filters,int max){
        return users.findAll(com.mkwang.backend.modules.spreadsheet.repository.ExcelExportSpecification.filter(filters),PageRequest.of(0,max+1,Sort.by("id"))).stream().map(u->values("employeeCode",u.getProfile()==null?null:u.getProfile().getEmployeeCode(),"fullName",u.getFullName(),"email",u.getEmail(),"role",u.getRole().getName(),"department",u.getDepartment()==null?null:u.getDepartment().getName(),"status",u.getStatus())).toList();
    }
}
