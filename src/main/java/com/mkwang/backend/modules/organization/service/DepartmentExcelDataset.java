package com.mkwang.backend.modules.organization.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.user.service.UserService;
import com.mkwang.backend.modules.organization.repository.DepartmentRepository;
import com.mkwang.backend.modules.organization.dto.request.CreateDepartmentRequest;
import com.mkwang.backend.modules.spreadsheet.service.*;
import com.mkwang.backend.modules.spreadsheet.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Component @RequiredArgsConstructor
public class DepartmentExcelDataset implements ExcelDataset {
    private final DepartmentRepository departments;private final DepartmentService service;private final UserService users;
    public String code(){return "departments";}public String label(){return "Phòng ban · tạo mới";}
    public Set<String> roles(){return Set.of("ADMIN");}
    public List<ExcelColumnResponse> columns(){return List.of(column("code","Mã phòng ban","TEXT",true),column("name","Tên phòng ban","TEXT",true),column("managerId","Quản lý","TEXT",false),column("totalProjectQuota","Hạn mức dự kiến","MONEY",false));}
    public void authorize(User actor,Long scope){if(scope!=null) throw new BadRequestException("Không dùng scope cho phòng ban");}
    public List<ExcelRowErrorResponse> validate(List<ExcelRowResponse> rows,User actor,Long scope){
        var errors=new ArrayList<>(ExcelValidation.rows(rows,row->row.values().get("code").toUpperCase(Locale.ROOT),row->{
            var v=row.values();String code=v.get("code").toUpperCase(Locale.ROOT);
            if(!code.matches("[A-Z0-9_-]{1,20}")||v.get("name").isBlank()||v.get("name").length()>255) throw new BadRequestException("Tên hoặc mã phòng ban không hợp lệ");
            if(departments.existsByCode(code)||departments.existsByName(v.get("name"))) throw new BadRequestException("Mã hoặc tên phòng ban đã tồn tại");
            Long manager=id(v,"managerId");
            if(manager!=null&&(!"MANAGER".equals(users.getUserById(manager).getRole().getName())||users.getUserById(manager).getDepartment()!=null)) throw new BadRequestException("Chọn quản lý chưa được gán phòng ban khi tạo phòng mới");
            money(v,"totalProjectQuota");
        }));
        var names=new HashSet<String>();
        for(var row:rows) if(!names.add(row.values().get("name").toLowerCase(Locale.ROOT))) errors.add(new ExcelRowErrorResponse(row.row(),"name","Tên phòng ban trùng trong file"));
        return errors;
    }
    public String commit(ExcelRowResponse row,User actor,Long scope){var v=row.values();return "DEPARTMENT:"+service.createDepartment(new CreateDepartmentRequest(v.get("name"),v.get("code").toUpperCase(Locale.ROOT),id(v,"managerId"),money(v,"totalProjectQuota"))).getId();}
    public List<Map<String,Object>> export(User actor,Long scope,Map<String,String> filters,int max){
        return departments.findAll(com.mkwang.backend.modules.spreadsheet.repository.ExcelExportSpecification.filter(filters),PageRequest.of(0,max+1,Sort.by("id"))).stream().map(d->values("code",d.getCode(),"name",d.getName(),"managerId",d.getManager()==null?null:d.getManager().getId(),"totalProjectQuota",d.getTotalProjectQuota())).toList();
    }
}
