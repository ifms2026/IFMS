package com.mkwang.backend.modules.project.repository;

import com.mkwang.backend.modules.project.entity.Project;
import com.mkwang.backend.modules.project.entity.ProjectStatus;
import org.springframework.data.jpa.domain.Specification;

public class ProjectSpecification {

    private ProjectSpecification() {
    }

    public static Specification<Project> hasDepartmentId(Long departmentId) {
        return (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
    }

    public static Specification<Project> hasStatus(ProjectStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Project> matchesSearch(String search) {
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("projectCode")), pattern)
            );
        };
    }

    public static Specification<Project> fetchCurrentPhase() {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType()) {
                root.fetch("currentPhase", jakarta.persistence.criteria.JoinType.LEFT);
                query.distinct(true);
            }
            return null;
        };
    }

    public static Specification<Project> filter(Long departmentId, ProjectStatus status, String search) {
        Specification<Project> specification = Specification.where(fetchCurrentPhase())
                .and(hasDepartmentId(departmentId));

        if (status != null) {
            specification = specification.and(hasStatus(status));
        }
        if (search != null && !search.isBlank()) {
            specification = specification.and(matchesSearch(search.trim()));
        }

        return specification;
    }
}


