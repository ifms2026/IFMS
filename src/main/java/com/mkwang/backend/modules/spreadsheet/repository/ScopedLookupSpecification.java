package com.mkwang.backend.modules.spreadsheet.repository;
import com.mkwang.backend.modules.user.entity.User;
import com.mkwang.backend.modules.project.entity.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;
public final class ScopedLookupSpecification {
 private ScopedLookupSpecification(){}
 private static boolean finance(User actor){return Set.of("CFO","ACCOUNTANT").contains(actor.getRole().getName());}
 private static Predicate project(CriteriaBuilder cb,CriteriaQuery<?> query,Path<?> project,User actor){
  if(finance(actor))return cb.conjunction();
  if("MANAGER".equals(actor.getRole().getName()))return actor.getDepartment()==null?cb.disjunction():cb.equal(project.get("department").get("id"),actor.getDepartment().getId());
  var sq=query.subquery(Long.class);var member=sq.from(ProjectMember.class);
  Predicate p=cb.and(cb.equal(member.get("project").get("id"),project.get("id")),cb.equal(member.get("user").get("id"),actor.getId()));
  if("TEAM_LEADER".equals(actor.getRole().getName()))p=cb.and(p,cb.equal(member.get("projectRole"),ProjectRole.LEADER));
  sq.select(cb.literal(1L)).where(p);return cb.exists(sq);
 }
 public static <T> Specification<T> scope(String kind,User actor){return (root,query,cb)->{
  String role=actor.getRole().getName();
  if("employee".equals(kind)){
   if(finance(actor)||"ADMIN".equals(role))return cb.conjunction();
   if("MANAGER".equals(role))return actor.getDepartment()==null?cb.disjunction():cb.equal(root.get("department").get("id"),actor.getDepartment().getId());
   if("TEAM_LEADER".equals(role)){
    var sq=query.subquery(Long.class);var member=sq.from(ProjectMember.class);
    var leader=sq.from(ProjectMember.class);
    sq.select(cb.literal(1L)).where(cb.equal(member.get("user").get("id"),root.get("id")),cb.equal(member.get("project").get("id"),leader.get("project").get("id")),cb.equal(leader.get("user").get("id"),actor.getId()),cb.equal(leader.get("projectRole"),ProjectRole.LEADER));return cb.exists(sq);
   }
   return cb.equal(root.get("id"),actor.getId());
  }
  if("department".equals(kind))return finance(actor)||"ADMIN".equals(role)?cb.conjunction():actor.getDepartment()==null?cb.disjunction():cb.equal(root.get("id"),actor.getDepartment().getId());
  if("category".equals(kind))return cb.conjunction();
  if("project".equals(kind))return project(cb,query,root,actor);
  if("phase".equals(kind))return project(cb,query,root.get("project"),actor);
  if("request".equals(kind)){
   if(finance(actor))return cb.conjunction();
   if(Set.of("MANAGER","TEAM_LEADER").contains(role))return cb.or(cb.equal(root.get("requester").get("id"),actor.getId()),project(cb,query,root.join("project",JoinType.LEFT),actor));
   return cb.equal(root.get("requester").get("id"),actor.getId());
  }
  return cb.disjunction();
 };}
 public static <T> Specification<T> selected(Long id){return (r,q,cb)->cb.equal(r.get("id"),id);}
 public static <T> Specification<T> text(String kind,String text){return (r,q,cb)->{
  String name="employee".equals(kind)?"fullName":"request".equals(kind)?"description":"name";
  String code=switch(kind){case "project"->"projectCode";case "phase"->"phaseCode";case "request"->"requestCode";case "department"->"code";default->null;};
  String pattern="%"+text.toLowerCase(Locale.ROOT)+"%";
  Predicate p=cb.like(cb.lower(r.get(name)),pattern);
  if(code!=null)p=cb.or(p,cb.like(cb.lower(r.get(code)),pattern));
  if("employee".equals(kind)){var profile=r.join("profile",JoinType.LEFT);p=cb.or(p,cb.like(cb.lower(profile.get("employeeCode")),pattern));}
  return p;
 };}
 public static <T> Specification<T> parent(String kind,Long id){return (r,q,cb)->{
  if("phase".equals(kind)||"request".equals(kind))return cb.equal(r.get("project").get("id"),id);
  if("category".equals(kind)){var sq=q.subquery(Long.class);var b=sq.from(PhaseCategoryBudget.class);sq.select(cb.literal(1L)).where(cb.equal(b.get("category").get("id"),r.get("id")),cb.equal(b.get("phase").get("id"),id));return cb.exists(sq);}
  return cb.conjunction();
 };}
 public static <T> Specification<T> filter(String kind,User actor,String text,Long id,Long parent){
  Specification<T> spec=scope(kind,actor);
  if(id!=null)spec=spec.and(selected(id));
  if(text!=null&&!text.isBlank())spec=spec.and(text(kind,text.trim()));
  if(parent!=null)spec=spec.and(parent(kind,parent));return spec;
 }
}
