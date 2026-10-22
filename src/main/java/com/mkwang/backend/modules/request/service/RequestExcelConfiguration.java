package com.mkwang.backend.modules.request.service;
import com.mkwang.backend.modules.spreadsheet.service.*;
import com.mkwang.backend.modules.spreadsheet.repository.ExcelExportSpecification;
import com.mkwang.backend.modules.spreadsheet.dto.response.ExcelColumnResponse;
import com.mkwang.backend.modules.request.entity.*;
import com.mkwang.backend.modules.request.repository.*;
import com.mkwang.backend.modules.project.service.ProjectQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Configuration @RequiredArgsConstructor
public class RequestExcelConfiguration {
    private final RequestRepository requests;private final AdvanceBalanceRepository advances;private final ProjectQueryService projects;
    private static final Set<String> ALL=Set.of("ADMIN","CFO","ACCOUNTANT","MANAGER","TEAM_LEADER","EMPLOYEE");
    private List<ExcelColumnResponse> requestColumns(){return List.of(column("code","Mã yêu cầu","TEXT",false),column("type","Nghiệp vụ","TEXT",false),column("status","Trạng thái","TEXT",false),column("employee","Nhân viên","TEXT",false),column("project","Dự án","TEXT",false),column("phase","Giai đoạn","TEXT",false),column("category","Danh mục","TEXT",false),column("amount","Đề nghị","MONEY_INFO",false),column("approvedAmount","Đã duyệt","MONEY_INFO",false),column("reservedAmount","Còn giữ cho yêu cầu","MONEY",false),column("createdAt","Ngày tạo","TEXT",false));}
    private ExcelDataset requestExport(String code,String label,Set<String> roles,boolean personal){
        return new ExportExcelDataset(code,label,roles,requestColumns(),(actor,filters,max)->{
            var spec=ExcelExportSpecification.<Request>filter(filters);
            if(personal)spec=spec.and(ExcelExportSpecification.dimension("requester.id",actor.getId()));
            else spec=spec.and(com.mkwang.backend.modules.spreadsheet.repository.ScopedLookupSpecification.scope("request",actor));
            return requests.findAll(spec,PageRequest.of(0,max+1,Sort.by("id"))).stream().map(r->values("code",r.getRequestCode(),"type",r.getType(),"status",r.getStatus(),
                    "employee",r.getRequester().getFullName(),"project",r.getProject()==null?null:r.getProject().getName(),"phase",r.getPhase()==null?null:r.getPhase().getName(),
                    "category",r.getCategory()==null?null:r.getCategory().getName(),"amount",r.getAmount(),"approvedAmount",r.getApprovedAmount(),"reservedAmount",r.getReservedAmount(),"createdAt",r.getCreatedAt())).toList();
        });
    }
    @Bean ExcelDataset myRequestsHistoryExcel(){return requestExport("my-requests-history","Lịch sử yêu cầu của tôi",ALL,true);}
    @Bean ExcelDataset managedRequestsExcel(){return requestExport("managed-requests","Yêu cầu trong phạm vi phụ trách",Set.of("TEAM_LEADER","MANAGER","CFO","ACCOUNTANT"),false);}
    private List<ExcelColumnResponse> advanceColumns(){return List.of(column("employee","Nhân viên","TEXT",false),column("department","Phòng ban","TEXT",false),column("request","Tạm ứng gốc","TEXT",false),column("project","Dự án","TEXT",false),column("original","Đã giải ngân","MONEY",false),column("receipts","Chứng từ đã xác nhận","MONEY",false),column("cashReturned","Tiền hoàn thật","MONEY",false),column("payrollOffset","Bù trừ lương","MONEY",false),column("legacyUnknown","Lịch sử chưa phân loại","MONEY",false),column("remaining","Còn phải quyết toán","MONEY",false),column("status","Trạng thái","TEXT",false));}
    private ExcelDataset advanceExport(String code,String label,Set<String> roles,boolean personal){
        return new ExportExcelDataset(code,label,roles,advanceColumns(),(actor,filters,max)->{
            var spec=ExcelExportSpecification.<AdvanceBalance>filter(filters,personal?Map.of("user.id",actor.getId()):Map.of());
            return advances.findAll(spec,PageRequest.of(0,max+1,Sort.by("id"))).stream().map(a->values("employee",a.getUser().getFullName(),
                    "department",a.getUser().getDepartment()==null?null:a.getUser().getDepartment().getName(),"request",a.getAdvanceRequest().getRequestCode(),"project",a.getAdvanceRequest().getProject()==null?null:a.getAdvanceRequest().getProject().getName(),
                    "original",a.getOriginalAmount(),"receipts",a.getReimbursedAmount(),"cashReturned",a.getCashReturnedAmount(),"payrollOffset",a.getPayrollOffsetAmount(),"legacyUnknown",a.getLegacyUnclassifiedAmount(),"remaining",a.getRemainingAmount(),"status",a.getStatus())).toList();
        });
    }
    @Bean ExcelDataset myAdvanceExcel(){return advanceExport("my-advances","Tạm ứng và quyết toán của tôi",ALL,true);}
    @Bean ExcelDataset allAdvanceExcel(){return advanceExport("employee-advances","Công nợ tạm ứng theo nhân viên",Set.of("CFO","ACCOUNTANT"),false);}
}
