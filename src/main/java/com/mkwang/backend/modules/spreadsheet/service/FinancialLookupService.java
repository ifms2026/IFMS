package com.mkwang.backend.modules.spreadsheet.service;
import com.mkwang.backend.common.dto.PageResponse;
import com.mkwang.backend.common.exception.*;
import com.mkwang.backend.modules.spreadsheet.dto.response.LookupOptionResponse;
import com.mkwang.backend.modules.spreadsheet.repository.ScopedLookupSpecification;
import com.mkwang.backend.modules.spreadsheet.mapper.FinancialLookupMapper;
import com.mkwang.backend.modules.auth.security.UserDetailsAdapter;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.project.entity.*;
import com.mkwang.backend.modules.organization.entity.Department;
import com.mkwang.backend.modules.request.entity.Request;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
@Service
public class FinancialLookupService {
 @PersistenceContext private EntityManager em;
 private final FinancialLookupMapper mapper;
 public FinancialLookupService(FinancialLookupMapper mapper){this.mapper=mapper;}
 @Transactional(readOnly=true) @PreAuthorize("isAuthenticated()")
 public PageResponse<LookupOptionResponse> find(String kind,String search,Long id,Long parent,int page,int limit){
  if(page<1||limit<1||limit>50||((long)page-1)*limit>Integer.MAX_VALUE)throw new BadRequestException("Phân trang không hợp lệ");
  var actor=((UserDetailsAdapter)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
  return switch(kind){case "employee"->query(User.class,kind,actor,search,id,parent,page,limit);case "project"->query(Project.class,kind,actor,search,id,parent,page,limit);case "phase"->query(ProjectPhase.class,kind,actor,search,id,parent,page,limit);case "category"->query(ExpenseCategory.class,kind,actor,search,id,parent,page,limit);case "department"->query(Department.class,kind,actor,search,id,parent,page,limit);case "request"->query(Request.class,kind,actor,search,id,parent,page,limit);default->throw new BadRequestException("Loại tham chiếu không hợp lệ");};
 }
 private <T> PageResponse<LookupOptionResponse> query(Class<T> type,String kind,User actor,String search,Long id,Long parent,int page,int limit){
  var cb=em.getCriteriaBuilder();var spec=ScopedLookupSpecification.<T>filter(kind,actor,search,id,parent);
  var cq=cb.createQuery(type);var root=cq.from(type);cq.select(root).where(spec.toPredicate(root,cq,cb)).orderBy(cb.asc(root.get("id")));
  var rows=em.createQuery(cq).setFirstResult((page-1)*limit).setMaxResults(limit).getResultList();
  var count=cb.createQuery(Long.class);var cr=count.from(type);count.select(cb.count(cr)).where(spec.toPredicate(cr,count,cb));long total=em.createQuery(count).getSingleResult();
  return PageResponse.<LookupOptionResponse>builder().items(rows.stream().map(mapper::toOption).toList()).total(total).page(page).size(limit).totalPages((int)((total+limit-1)/limit)).build();
 }
}
