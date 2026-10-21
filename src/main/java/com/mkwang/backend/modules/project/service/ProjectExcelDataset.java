package com.mkwang.backend.modules.project.service;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.user.entity.*;
import com.mkwang.backend.modules.user.service.UserService;
import com.mkwang.backend.modules.project.entity.*;
import com.mkwang.backend.modules.project.repository.*;
import com.mkwang.backend.modules.project.dto.request.*;
import com.mkwang.backend.modules.spreadsheet.service.*;
import com.mkwang.backend.modules.spreadsheet.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@RequiredArgsConstructor
public class ProjectExcelDataset implements ExcelDataset {
    private final String kind;
    private final ProjectRepository projects;private final ProjectPhaseRepository phases;
    private final ProjectMemberRepository members;private final PhaseCategoryBudgetRepository budgets;
    private final ExpenseCategoryRepository categories;
    private final ManagerProjectService manager;private final TeamLeaderProjectService leader;
    private final CategoryBudgetService categoryService;private final UserService users;
    private final ProjectFinancialGuard guard;
    public String code(){return kind;}
    public boolean supportsDateFilter(){return false;}
    public String label(){return switch(kind){case "projects"->"Dự án · tạo mới";case "phases"->"Giai đoạn dự án · tạo mới";case "project-members"->"Thành viên dự án · thêm mới";default->"Hạn mức danh mục · thiết lập";};}
    public Set<String> roles(){return Set.of(kind.equals("projects")?"MANAGER":"TEAM_LEADER");}
    public String scopeLabel(){return kind.equals("projects")?null:"Dự án phụ trách";}
    public List<ExcelColumnResponse> columns(){return switch(kind){
        case "projects"->List.of(column("name","Tên dự án","TEXT",true),column("description","Mô tả","TEXT",false),column("totalBudget","Ngân sách kế hoạch","MONEY",true),column("teamLeaderId","Trưởng dự án","TEXT",true));
        case "phases"->List.of(column("name","Tên giai đoạn","TEXT",true),column("budgetLimit","Hạn mức","MONEY",true),column("startDate","Ngày bắt đầu","TEXT",false),column("endDate","Ngày kết thúc","TEXT",false));
        case "project-members"->List.of(column("userId","Nhân viên","TEXT",true),column("position","Vị trí","TEXT",true));
        default->List.of(column("phaseId","Giai đoạn","TEXT",true),column("categoryId","Danh mục","TEXT",true),column("budgetLimit","Hạn mức","MONEY",true));
    };}
    public List<Map<String,Object>> scopes(User actor){
        if(kind.equals("projects")) return List.of();
        return projects.findMemberProjects(actor.getId(),null).stream()
                .filter(p->members.existsByProject_IdAndUser_IdAndProjectRole(p.getId(),actor.getId(),ProjectRole.LEADER))
                .map(p->values("id",p.getId(),"label",p.getProjectCode()+" · "+p.getName())).toList();
    }
    public void authorize(User actor,Long scope){
        if(kind.equals("projects")) {if(scope!=null||actor.getDepartment()==null) throw new BadRequestException("Manager cần có phòng ban");return;}
        if(scope==null||!members.existsByProject_IdAndUser_IdAndProjectRole(scope,actor.getId(),ProjectRole.LEADER)) throw new UnauthorizedException("Không phụ trách dự án này");
    }
    public List<Map<String,Object>> references(User actor,Long scope){
        var result=new ArrayList<Map<String,Object>>();
        if(kind.equals("projects")) {
            users.getActiveTeamLeadersByDepartmentId(actor.getDepartment().getId()).forEach(u->result.add(values("kind","TEAM_LEADER","id",u.getId(),"name",u.getFullName())));
        } else {
            phases.findByProject_IdOrderByCreatedAtAsc(scope).forEach(p->result.add(values("kind","PHASE","id",p.getId(),"name",p.getName())));
            categories.findAll().stream().filter(c->Boolean.TRUE.equals(c.getIsSystemDefault())||(c.getProject()!=null&&scope.equals(c.getProject().getId())))
                    .forEach(c->result.add(values("kind","CATEGORY","id",c.getId(),"name",c.getName())));
            leader.getAvailableMembers(actor,scope,null).forEach(u->result.add(values("kind","EMPLOYEE","id",u.id(),"name",u.fullName())));
        }
        return result;
    }
    public List<ExcelRowErrorResponse> validate(List<ExcelRowResponse> rows,User actor,Long scope){
        var errors=new ArrayList<>(ExcelValidation.rows(rows,row->switch(kind){case "projects","phases"->row.values().get("name").toLowerCase(Locale.ROOT);case "project-members"->row.values().get("userId");default->row.values().get("phaseId")+":"+row.values().get("categoryId");},row->{
            var v=row.values();
            switch(kind){
                case "projects"->{
                    var u=users.getUserById(id(v,"teamLeaderId"));
                    if(!"TEAM_LEADER".equals(u.getRole().getName())||u.getStatus()!=UserStatus.ACTIVE||u.getDepartment()==null||!u.getDepartment().getId().equals(actor.getDepartment().getId())) throw new BadRequestException("Trưởng dự án không hợp lệ trong phòng ban");
                    if(v.get("name").isBlank()||v.get("name").length()>255||money(v,"totalBudget").signum()<=0) throw new BadRequestException("Tên hoặc ngân sách không hợp lệ");
                }
                case "phases"->{
                    if(v.get("name").isBlank()||v.get("name").length()>255||money(v,"budgetLimit").signum()<=0) throw new BadRequestException("Tên hoặc hạn mức không hợp lệ");
                    var from=date(v,"startDate");var to=date(v,"endDate");
                    if(from!=null&&to!=null&&from.isAfter(to)) throw new BadRequestException("Ngày kết thúc phải sau ngày bắt đầu");
                }
                case "project-members"->{
                    Long userId=id(v,"userId");
                    if(userId==null||members.existsByProject_IdAndUser_Id(scope,userId)) throw new BadRequestException("Nhân viên đã có trong dự án");
                    if(leader.getAvailableMembers(actor,scope,null).stream().noneMatch(u->u.id().equals(userId))) throw new BadRequestException("Nhân viên ngoài danh sách được phép thêm");
                    if(v.get("position").isBlank()||v.get("position").length()>100) throw new BadRequestException("Vị trí không hợp lệ");
                }
                default->{
                    Long phaseId=id(v,"phaseId"),categoryId=id(v,"categoryId");
                    var phase=phases.findById(phaseId).orElseThrow(()->new BadRequestException("Giai đoạn không tồn tại"));
                    if(!phase.getProject().getId().equals(scope)) throw new BadRequestException("Giai đoạn khác dự án");
                    var category=categories.findById(categoryId).orElseThrow(()->new BadRequestException("Danh mục không tồn tại"));
                    if(!Boolean.TRUE.equals(category.getIsSystemDefault())&&(category.getProject()==null||!category.getProject().getId().equals(scope))) throw new BadRequestException("Danh mục khác dự án");
                    var current=budgets.findById(new PhaseCategoryBudgetId(phaseId,categoryId));
                    guard.assertLimit(scope,phaseId,categoryId,current.map(PhaseCategoryBudget::getCurrentSpent).orElse(BigDecimal.ZERO),money(v,"budgetLimit"));
                }
            }
        }));
        if(!errors.isEmpty()) return errors;
        if(kind.equals("phases")){
            BigDecimal sum=phases.findByProject_IdOrderByCreatedAtAsc(scope).stream().map(ProjectPhase::getBudgetLimit).reduce(BigDecimal.ZERO,BigDecimal::add);
            sum=sum.add(rows.stream().map(row->money(row.values(),"budgetLimit")).reduce(BigDecimal.ZERO,BigDecimal::add));
            if(sum.compareTo(projects.findById(scope).orElseThrow(()->new BadRequestException("Không có dự án")).getTotalBudget())>0) for(var row:rows) errors.add(new ExcelRowErrorResponse(row.row(),"budgetLimit","Tổng hạn mức giai đoạn vượt ngân sách kế hoạch dự án"));
        }
        if(kind.equals("category-budgets")){
            Map<Long,Map<Long,BigDecimal>> finalBudgets=new HashMap<>();
            for(var row:rows) {
                Long phaseId=id(row.values(),"phaseId");Long categoryId=id(row.values(),"categoryId");
                var perPhase=finalBudgets.computeIfAbsent(phaseId,key->{var map=new HashMap<Long,BigDecimal>();budgets.findByIdPhaseId(key).forEach(b->map.put(b.getCategory().getId(),b.getBudgetLimit()));return map;});
                perPhase.put(categoryId,money(row.values(),"budgetLimit"));
            }
            for(var row:rows) {Long phaseId=id(row.values(),"phaseId");BigDecimal total=finalBudgets.get(phaseId).values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
                if(total.compareTo(phases.findById(phaseId).orElseThrow().getBudgetLimit())>0) errors.add(new ExcelRowErrorResponse(row.row(),"budgetLimit","Tổng hạn mức danh mục vượt giai đoạn"));
            }
        }
        return errors;
    }
    public void beforeCommit(List<ExcelRowResponse> rows,User actor,Long scope){
        if(scope!=null) projects.findByIdForUpdate(scope).orElseThrow(()->new BadRequestException("Dự án không còn tồn tại"));
        if(!validate(rows,actor,scope).isEmpty()) throw new BadRequestException("Phạm vi hoặc hạn mức đã thay đổi; xem trước lại");
    }
    public List<ExcelRowResponse> orderForCommit(List<ExcelRowResponse> rows,Long scope){
        if(!kind.equals("category-budgets"))return rows;
        // Existing limits are decreased first so a valid same-file reallocation
        // is not rejected by an intermediate phase total.
        return rows.stream().sorted(Comparator.comparing(row->{
            Long phase=id(row.values(),"phaseId"),category=id(row.values(),"categoryId");
            BigDecimal previous=budgets.findById(new PhaseCategoryBudgetId(phase,category))
                    .map(PhaseCategoryBudget::getBudgetLimit).orElse(BigDecimal.ZERO);
            return money(row.values(),"budgetLimit").subtract(previous);
        })).toList();
    }
    public String commit(ExcelRowResponse row,User actor,Long scope){var v=row.values();return switch(kind){
        case "projects"->"PROJECT:"+manager.createProject(actor.getId(),new CreateManagerProjectRequest(v.get("name"),v.get("description"),money(v,"totalBudget"),id(v,"teamLeaderId"))).id();
        case "phases"->"PHASE:"+leader.createPhase(actor,scope,new CreatePhaseRequest(v.get("name"),money(v,"budgetLimit"),date(v,"startDate"),date(v,"endDate"))).id();
        case "project-members"->"MEMBER:"+leader.addMember(actor,scope,new AddProjectMemberRequest(id(v,"userId"),v.get("position"))).userId();
        default->{categoryService.updatePhaseCategoryBudgets(scope,new SetCategoryBudgetsRequest(id(v,"phaseId"),id(v,"categoryId"),money(v,"budgetLimit")),actor);yield "BUDGET:"+v.get("phaseId")+":"+v.get("categoryId");}
    };}
    public List<Map<String,Object>> export(User actor,Long scope,Map<String,String> filters,int max){
        return switch(kind){
            case "projects"->projects.findByDepartment_IdOrderByCreatedAtDesc(actor.getDepartment().getId()).stream().limit(max+1L).map(p->values("name",p.getName(),"description",p.getDescription(),"totalBudget",p.getTotalBudget(),"teamLeaderId",members.findByProject_IdAndProjectRole(p.getId(),ProjectRole.LEADER).stream().findFirst().map(m->m.getUser().getId()).orElse(null))).toList();
            case "phases"->phases.findByProject_IdOrderByCreatedAtAsc(scope).stream().limit(max+1L).map(p->values("name",p.getName(),"budgetLimit",p.getBudgetLimit(),"startDate",p.getStartDate(),"endDate",p.getEndDate())).toList();
            case "project-members"->members.findMembersWithProfileByProjectId(scope).stream().limit(max+1L).map(m->values("userId",m.getUser().getId(),"position",m.getPosition())).toList();
            default->phases.findByProject_IdOrderByCreatedAtAsc(scope).stream().flatMap(p->budgets.findByIdPhaseId(p.getId()).stream()).limit(max+1L).map(b->values("phaseId",b.getPhase().getId(),"categoryId",b.getCategory().getId(),"budgetLimit",b.getBudgetLimit())).toList();
        };
    }
}
