package com.mkwang.backend.modules.request.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.request.entity.*;
import com.mkwang.backend.modules.request.repository.*;
import com.mkwang.backend.modules.request.mapper.RequestExcelMapper;
import com.mkwang.backend.modules.project.service.ProjectQueryService;
import com.mkwang.backend.modules.project.repository.ProjectPhaseRepository;
import com.mkwang.backend.modules.project.repository.PhaseCategoryBudgetRepository;
import com.mkwang.backend.modules.file.service.FileStorageService;
import com.mkwang.backend.modules.file.entity.FileStorage;
import com.mkwang.backend.modules.spreadsheet.service.*;
import com.mkwang.backend.modules.spreadsheet.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.*;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Component @RequiredArgsConstructor
public class EmployeeRequestExcelDataset implements ExcelDataset {
    private final RequestRepository requests;private final AdvanceBalanceRepository advances;
    private final RequestService service;private final RequestExcelMapper mapper;private final FileStorageService files;
    private final ProjectQueryService projects;private final ProjectPhaseRepository phases;
    private final PhaseCategoryBudgetRepository categoryBudgets;private final jakarta.validation.Validator validator;
    public String code(){return "requests";}public String label(){return "Yêu cầu cá nhân · gửi mới chờ duyệt";}
    public Set<String> roles(){return Set.of("EMPLOYEE");}
    public List<ExcelColumnResponse> columns(){return List.of(column("type","Loại: ADVANCE/EXPENSE/REIMBURSE","TEXT",true),column("projectId","Dự án","TEXT",true),column("phaseId","Giai đoạn","TEXT",true),column("categoryId","Danh mục","TEXT",true),column("advanceBalanceId","Khoản tạm ứng gốc nếu quyết toán","TEXT",false),column("amount","Số tiền","MONEY",true),column("description","Nội dung","TEXT",true),column("documentIds","ID chứng từ đã upload, cách nhau bằng ;","TEXT",false));}
    public void authorize(User actor,Long scope){if(scope!=null) throw new BadRequestException("Yêu cầu chỉ được nhập cho chính bạn");}
    private List<FileStorage> documents(ExcelRowResponse row,User actor) {
        String value=row.values().get("documentIds");
        if(value==null||value.isBlank()) return List.of();
        var tokens=value.split(";");if(tokens.length>20) throw new BadRequestException("Tối đa 20 chứng từ cho một dòng");
        List<FileStorage> result=new ArrayList<>();Set<Long> seen=new HashSet<>();
        for(String token:tokens) {
            Long id=id(Map.of("documentId",token.trim()),"documentId");
            if(id==null||!seen.add(id)) throw new BadRequestException("Mã chứng từ bị trùng hoặc rỗng");
            FileStorage file=files.getOwned(id,actor.getId());
            if(requests.existsByAttachments_File_Id(id)) throw new BadRequestException("Chứng từ đã gắn với yêu cầu khác");
            result.add(file);
        }
        return result;
    }
    public List<ExcelRowErrorResponse> validate(List<ExcelRowResponse> rows,User actor,Long scope){
        Set<Long> used=new HashSet<>();
        return ExcelValidation.rows(rows,row->null,row->{
            var docs=documents(row,actor);for(var file:docs) if(!used.add(file.getId())) throw new BadRequestException("Một chứng từ không được gắn vào nhiều dòng yêu cầu");
            var request=mapper.toRequest(row,docs);
            var errors=validator.validate(request);if(!errors.isEmpty()) throw new BadRequestException(errors.iterator().next().getMessage());
            service.validateImportRequest(request,actor.getId());
        });
    }
    public String commit(ExcelRowResponse row,User actor,Long scope){return "REQUEST:"+service.createRequest(mapper.toRequest(row,documents(row,actor)),actor.getId()).getId();}
    public List<Map<String,Object>> references(User actor,Long scope){
        var result=new ArrayList<Map<String,Object>>();
        for(var p:projects.getProjects(actor,null)) {
            result.add(values("kind","PROJECT","id",p.id(),"name",p.name()));
            for(var phase:phases.findByProject_IdOrderByCreatedAtAsc(p.id())) {
                result.add(values("kind","PHASE","id",phase.getId(),"name",phase.getName(),"projectId",p.id()));
                for(var budget:categoryBudgets.findByIdPhaseId(phase.getId())) {
                    result.add(values("kind","CATEGORY","id",budget.getCategory().getId(),"name",budget.getCategory().getName(),
                            "projectId",p.id(),"phaseId",phase.getId()));
                }
            }
        }
        for(var balance:advances.findByUserIdAndStatusNot(actor.getId(),AdvanceBalanceStatus.SETTLED)) {
            var req=balance.getAdvanceRequest();
            result.add(values("kind","ADVANCE","id",balance.getId(),"name",req.getRequestCode(),"projectId",req.getProject().getId(),"phaseId",req.getPhase()==null?null:req.getPhase().getId(),"categoryId",req.getCategory()==null?null:req.getCategory().getId(),"remainingAmount",balance.getRemainingAmount()));
        }
        return result;
    }
    public List<ExcelColumnResponse> exportColumns(){var cols=new ArrayList<>(columns());cols.add(column("requestCode","Mã yêu cầu","TEXT",false));cols.add(column("status","Trạng thái","TEXT",false));return cols;}
    public List<Map<String,Object>> export(User actor,Long scope,Map<String,String> filters,int max){
        var spec=com.mkwang.backend.modules.spreadsheet.repository.ExcelExportSpecification.<Request>filter(filters)
                .and(RequestSpecification.hasRequester(actor.getId()));
        return requests.findAll(spec,PageRequest.of(0,max+1,Sort.by("id"))).stream().map(r->values("type",r.getType(),"projectId",r.getProject()==null?null:r.getProject().getId(),"phaseId",r.getPhase()==null?null:r.getPhase().getId(),"categoryId",r.getCategory()==null?null:r.getCategory().getId(),"advanceBalanceId",r.getAdvanceBalance()==null?null:r.getAdvanceBalance().getId(),"amount",r.getAmount(),"description",r.getDescription(),"documentIds",r.getAttachmentFiles().stream().map(f->f.getId().toString()).collect(java.util.stream.Collectors.joining(";")),"requestCode",r.getRequestCode(),"status",r.getStatus())).toList();
    }
}
