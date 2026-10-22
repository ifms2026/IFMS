package com.mkwang.backend.modules.spreadsheet.service;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.spreadsheet.repository.*;
import com.mkwang.backend.modules.spreadsheet.dto.response.ExcelColumnResponse;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.organization.entity.Department;
import com.mkwang.backend.modules.project.entity.*;
import com.mkwang.backend.modules.accounting.service.CompanyFundService;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Configuration @RequiredArgsConstructor
public class RoleReportExcelConfiguration {
 @PersistenceContext private EntityManager em;
 private final CompanyFundService funds;
 private <T> List<T> rows(Class<T> type,String kind,User actor,Map<String,String> filters,String idKey,int max){
  var cb=em.getCriteriaBuilder();var q=cb.createQuery(type);var root=q.from(type);
  var spec=ScopedLookupSpecification.<T>filter(kind,actor,filters.get("search"),id(filters,idKey),null);
  if("project".equals(kind)&&id(filters,"departmentId")!=null)spec=spec.and(ExcelExportSpecification.dimension("department.id",id(filters,"departmentId")));
  if("employee".equals(kind)){root.fetch("profile",jakarta.persistence.criteria.JoinType.LEFT);root.fetch("department",jakarta.persistence.criteria.JoinType.LEFT);if(id(filters,"departmentId")!=null)spec=spec.and(ExcelExportSpecification.dimension("department.id",id(filters,"departmentId")));}
  q.select(root).where(spec.toPredicate(root,q,cb)).orderBy(cb.asc(root.get("id")));
  return em.createQuery(q).setMaxResults(max+1).getResultList();
 }
 private Map<Long,BigDecimal> sums(String hql,List<Long> ids){
  Map<Long,BigDecimal> result=new HashMap<>();if(ids.isEmpty())return result;
  for(Object[] row:em.createQuery(hql,Object[].class).setParameter("ids",ids).getResultList())result.put(((Number)row[0]).longValue(),(BigDecimal)row[1]);return result;
 }
 @Bean ExcelDataset departmentEmployeesReport(){
  var columns=List.of(column("code","Mã nhân viên","TEXT",false),column("name","Nhân viên","TEXT",false),column("email","Email","TEXT",false),column("department","Phòng ban","TEXT",false),column("status","Trạng thái","TEXT",false),column("outstanding","Tạm ứng còn phải quyết toán hiện tại","MONEY",false));
  return new ExportExcelDataset("department-employees","Nhân sự phòng ban và dư tạm ứng hiện tại",Set.of("MANAGER","CFO","ACCOUNTANT"),columns,(actor,filters,max)->{
   var users=rows(User.class,"employee",actor,filters,"employeeId",max);var ids=users.stream().map(User::getId).toList();
   var debt=sums("select a.user.id,sum(a.remainingAmount) from AdvanceBalance a where a.user.id in :ids and a.status<>'SETTLED' group by a.user.id",ids);
   return users.stream().map(u->values("code",u.getProfile()==null?null:u.getProfile().getEmployeeCode(),"name",u.getFullName(),"email",u.getEmail(),"department",u.getDepartment()==null?null:u.getDepartment().getName(),"status",u.getStatus(),"outstanding",debt.getOrDefault(u.getId(),BigDecimal.ZERO))).toList();
  });
 }
 @Bean ExcelDataset departmentBudgetReport(){
  var columns=List.of(column("code","Mã phòng ban","TEXT",false),column("name","Phòng ban","TEXT",false),column("quota","Ngân sách đã cấp phòng ban","MONEY",false),column("departmentCash","Số dư ví phòng ban","MONEY",false),column("departmentHeld","Tiền giữ ví phòng ban","MONEY",false),column("projectCash","Số dư các ví dự án","MONEY",false),column("projectCost","Chi phí dự án đã xác nhận","MONEY",false),column("outstanding","Tạm ứng dự án còn mở","MONEY",false));
  return new ExportExcelDataset("department-budgets","Ngân sách và tiền phòng ban · trạng thái hiện tại",Set.of("MANAGER","CFO","ACCOUNTANT"),columns,(actor,filters,max)->{
   var departments=rows(Department.class,"department",actor,filters,"departmentId",max);var ids=departments.stream().map(Department::getId).toList();
   var cash=sums("select w.ownerId,sum(w.balance) from Wallet w where w.ownerType='DEPARTMENT' and w.ownerId in :ids group by w.ownerId",ids);
   var held=sums("select w.ownerId,sum(w.lockedBalance) from Wallet w where w.ownerType='DEPARTMENT' and w.ownerId in :ids group by w.ownerId",ids);
   var projectCash=sums("select p.department.id,sum(w.balance) from Wallet w,Project p where w.ownerType='PROJECT' and w.ownerId=p.id and p.department.id in :ids group by p.department.id",ids);
   var cost=sums("select p.department.id,sum(p.totalSpent) from Project p where p.department.id in :ids group by p.department.id",ids);
   var debt=sums("select a.advanceRequest.project.department.id,sum(a.remainingAmount) from AdvanceBalance a where a.status<>'SETTLED' and a.advanceRequest.project.department.id in :ids group by a.advanceRequest.project.department.id",ids);
   return departments.stream().map(d->values("code",d.getCode(),"name",d.getName(),"quota",d.getTotalProjectQuota(),"departmentCash",cash.getOrDefault(d.getId(),BigDecimal.ZERO),"departmentHeld",held.getOrDefault(d.getId(),BigDecimal.ZERO),"projectCash",projectCash.getOrDefault(d.getId(),BigDecimal.ZERO),"projectCost",cost.getOrDefault(d.getId(),BigDecimal.ZERO),"outstanding",debt.getOrDefault(d.getId(),BigDecimal.ZERO))).toList();
  });
 }
 @Bean ExcelDataset projectCostDetailsReport(){
  var columns=List.of(column("project","Dự án","TEXT",false),column("phase","Giai đoạn","TEXT",false),column("category","Danh mục","TEXT",false),column("budget","Hạn mức","MONEY",false),column("counterCost","Chi phí bộ đếm đã xác nhận","MONEY",false),column("journalCost","Chi phí đã ghi journal","MONEY_INFO",false),column("difference","Chênh lệch cần đối chiếu","MONEY_INFO",false));
  return new ExportExcelDataset("project-cost-details","Chi phí theo giai đoạn/danh mục · đối chiếu hiện tại",Set.of("MANAGER","TEAM_LEADER","CFO","ACCOUNTANT"),columns,(actor,filters,max)->{
   var projects=rows(Project.class,"project",actor,filters,"projectId",max);if(projects.size()>max)throw new BadRequestException("Quá nhiều dự án; cần thu hẹp phạm vi");var ids=projects.stream().map(Project::getId).toList();if(ids.isEmpty())return List.of();
   var q=em.createQuery("select b from PhaseCategoryBudget b join fetch b.phase ph join fetch ph.project pr join fetch b.category where pr.id in :ids order by pr.id,ph.id,b.category.id",PhaseCategoryBudget.class).setParameter("ids",ids);
   Map<String,BigDecimal> costs=new HashMap<>();for(Object[] row:em.createQuery("select l.phaseId,l.categoryId,sum(l.debitAmount-l.creditAmount) from AccountingJournalLine l where l.accountCode='PROJECT_EXPENSE' and l.projectId in :ids group by l.phaseId,l.categoryId",Object[].class).setParameter("ids",ids).getResultList())costs.put(row[0]+":"+row[1],(BigDecimal)row[2]);
   // Filter before applying the limit so a filter cannot silently truncate valid rows.
   String hql="select b from PhaseCategoryBudget b join fetch b.phase ph join fetch ph.project pr join fetch b.category where pr.id in :ids";
   Long phase=id(filters,"phaseId"),category=id(filters,"categoryId");if(phase!=null)hql+=" and ph.id=:phase";if(category!=null)hql+=" and b.category.id=:category";hql+=" order by pr.id,ph.id,b.category.id";
   q=em.createQuery(hql,PhaseCategoryBudget.class).setParameter("ids",ids);if(phase!=null)q.setParameter("phase",phase);if(category!=null)q.setParameter("category",category);
   return q.setMaxResults(max+1).getResultList().stream().map(b->{var ph=b.getPhase();var amount=costs.getOrDefault(ph.getId()+":"+b.getCategory().getId(),BigDecimal.ZERO);return values("project",ph.getProject().getProjectCode()+" · "+ph.getProject().getName(),"phase",ph.getPhaseCode()+" · "+ph.getName(),"category",b.getCategory().getName(),"budget",b.getBudgetLimit(),"counterCost",b.getCurrentSpent(),"journalCost",amount,"difference",b.getCurrentSpent().subtract(amount));}).toList();
  });
 }
 @Bean ExcelDataset bankReconciliationSummary(){
  var cols=List.of(column("asOf","Mốc sao kê","TEXT",false),column("evidence","Căn cứ sao kê","TEXT",false),column("status","Phạm vi đối soát","TEXT",false),column("corporate","Tiền công ty tại mốc sao kê","MONEY_INFO",false),column("pending","Tiền đang chuyển tại mốc","MONEY_INFO",false),column("bank","Số dư ngân hàng theo sao kê","MONEY",false),column("difference","Chênh lệch ngân hàng","MONEY_INFO",false),column("integrityDifference","Chênh lệch kiểm soát tổng ví hiện tại","MONEY_INFO",false),column("generatedAt","Lập báo cáo lúc","TEXT",false));
  return new ExportExcelDataset("bank-reconciliation-summary","Kết quả đối soát tổng hợp · mốc sao kê đã xác nhận",Set.of("CFO","ACCOUNTANT"),cols,(actor,filters,max)->{var report=funds.getReconciliationReport();return List.of(values("asOf",report.getStatementAsOf(),"evidence",report.getStatementEvidenceRef(),"status",report.getBankComparisonStatus(),"corporate",report.getCorporateBalanceAtStatement(),"pending",report.getPendingCorporateAmount(),"bank",report.getExternalBankBalance(),"difference",report.getBankDiscrepancy(),"integrityDifference",report.getSystemDiscrepancy(),"generatedAt",report.getGeneratedAt()));});
 }
}
