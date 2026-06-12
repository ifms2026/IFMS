package com.mkwang.backend.modules.project.repository;

import com.mkwang.backend.modules.project.entity.ProjectMember;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Specification class for filtering ProjectMember entities.
 *
 * Convention (CLAUDE.md):
 *  - Each predicate is a pure static method
 *  - {@link #filter} is the sole combiner — handles null-checks and composes predicates
 *    via {@code Specification.where().and()}
 *  - Used by Team Leader "team-members" list endpoint (2 optional filters: projectId, search)
 */
public class ProjectMemberSpecification {

    private ProjectMemberSpecification() {}

    /**
     * Restrict to members belonging to projects in the given list.
     * Always applied — ensures TL only sees members of their own projects.
     */
    public static Specification<ProjectMember> projectIdIn(List<Long> projectIds) {
        return (root, query, cb) ->
                root.get("project").get("id").in(projectIds);
    }

    /**
     * Restrict to members of a single project (optional filter from ?projectId=).
     */
    public static Specification<ProjectMember> hasProjectId(Long projectId) {
        return (root, query, cb) ->
                projectId == null ? null : cb.equal(root.get("project").get("id"), projectId);
    }

    /**
     * Exclude a specific user (typically the TL themselves) from results.
     */
    public static Specification<ProjectMember> excludeUser(Long excludedUserId) {
        return (root, query, cb) ->
                excludedUserId == null ? null : cb.notEqual(root.get("user").get("id"), excludedUserId);
    }

    /**
     * Full-name or employee-code substring search (case-insensitive).
     * Uses path navigation (root.get()) rather than root.join() to avoid
     * conflicting with the FETCH JOINs in fetchAssociations().
     * query.distinct(true) is already set by fetchAssociations().
     */
    public static Specification<ProjectMember> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) return null;
            String pattern = "%" + search.toLowerCase() + "%";

            // Use JOIN (not FETCH) for filtering — fetchAssociations handles the FETCH
            Join<Object, Object> user = root.join("user", JoinType.LEFT);
            Join<Object, Object> profile = user.join("profile", JoinType.LEFT);

            return cb.or(
                    cb.like(cb.lower(user.get("fullName")), pattern),
                    cb.like(cb.lower(profile.get("employeeCode")), pattern)
            );
        };
    }

    /**
     * Eagerly fetches user, profile, avatarFile, and project associations
     * to avoid N+1 queries when building the response.
     * Must be part of every query that materialises ProjectMember rows.
     */
    public static Specification<ProjectMember> fetchAssociations() {
        return (root, query, cb) -> {
            // Only apply FETCH on the data query, not on the COUNT query
            if (query != null && Long.class != query.getResultType()) {
                root.fetch("user", JoinType.LEFT)
                        .fetch("profile", JoinType.LEFT)
                        .fetch("avatarFile", JoinType.LEFT);
                root.fetch("project", JoinType.LEFT);
                query.distinct(true);
            }
            return null;
        };
    }

    /**
     * Sole combiner — applies all filters and composes them with AND.
     *
     * @param leaderProjectIds projects this TL manages (mandatory scope — never null/empty)
     * @param projectId        optional single-project filter
     * @param search           optional fullName / employeeCode search
     * @param currentUserId    TL's own userId — excluded from results
     */
    public static Specification<ProjectMember> filter(
            List<Long> leaderProjectIds,
            Long projectId,
            String search,
            Long currentUserId) {

        return Specification
                .where(fetchAssociations())
                .and(projectIdIn(leaderProjectIds))
                .and(hasProjectId(projectId))
                .and(excludeUser(currentUserId))
                .and(matchesSearch(search));
    }
}
