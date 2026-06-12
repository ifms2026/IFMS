package com.mkwang.backend.modules.organization.repository;

import com.mkwang.backend.modules.organization.entity.Department;
import org.springframework.data.jpa.domain.Specification;

public class DepartmentSpecification {

    private DepartmentSpecification() {}

    public static Specification<Department> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) return null;
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("code")), pattern)
            );
        };
    }

    public static Specification<Department> filter(String search) {
        return Specification.where(matchesSearch(search));
    }
}
