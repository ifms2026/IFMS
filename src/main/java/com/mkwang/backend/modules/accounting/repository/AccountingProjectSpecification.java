package com.mkwang.backend.modules.accounting.repository;
import com.mkwang.backend.modules.project.entity.Project;
import org.springframework.data.jpa.domain.Specification;
public final class AccountingProjectSpecification {
 private AccountingProjectSpecification(){}
 public static Specification<Project> search(String text){return (root,query,cb)->{String pattern="%"+text.toLowerCase(java.util.Locale.ROOT)+"%";return cb.or(cb.like(cb.lower(root.get("name")),pattern),cb.like(cb.lower(root.get("projectCode")),pattern));};}
 public static Specification<Project> filter(String text){Specification<Project> spec=Specification.where(null);if(text!=null&&!text.isBlank())spec=spec.and(search(text.trim()));return spec;}
}
