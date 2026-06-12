package com.mkwang.backend.modules.request.repository;

import com.mkwang.backend.modules.request.entity.Request;
import com.mkwang.backend.modules.request.entity.RequestStatus;
import com.mkwang.backend.modules.request.entity.RequestType;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public class RequestSpecification {

    private RequestSpecification() {}

    public static Specification<Request> hasRequester(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("requester").get("id"), userId);
    }

    public static Specification<Request> hasType(RequestType type) {
        return (root, query, cb) ->
                type == null ? null : cb.equal(root.get("type"), type);
    }

    public static Specification<Request> hasStatus(RequestStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Request> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) return null;
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("requestCode")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }

    public static Specification<Request> filter (Long userId, RequestType type, RequestStatus status, String search) {
        return Specification.where(hasRequester(userId))
                .and(hasType(type))
                .and(hasStatus(status))
                .and(matchesSearch(search));
    }

    public static Specification<Request> hasTypeIn(List<RequestType> types) {
        return (root, query, cb) ->
                (types == null || types.isEmpty()) ? null : root.get("type").in(types);
    }

    public static Specification<Request> projectIdIn(List<Long> projectIds) {
        return (root, query, cb) ->
                (projectIds == null || projectIds.isEmpty())
                        ? cb.disjunction()
                        : root.get("project").get("id").in(projectIds);
    }

    public static Specification<Request> hasProjectId(Long projectId) {
        return (root, query, cb) ->
                projectId == null ? null : cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Request> hasProjectDepartment(Long departmentId) {
        return (root, query, cb) ->
                departmentId == null
                        ? cb.disjunction()
                        : cb.equal(root.get("project").get("department").get("id"), departmentId);
    }

    public static Specification<Request> filterForTlApprovals(
            List<Long> leaderProjectIds,
            RequestType type,
            Long projectId,
            String search) {

        List<RequestType> allowedTypes = (type != null)
                ? List.of(type)
                : List.of(RequestType.ADVANCE, RequestType.EXPENSE, RequestType.REIMBURSE);

        return Specification.where(hasStatus(RequestStatus.PENDING))
                .and(hasTypeIn(allowedTypes))
                .and(projectIdIn(leaderProjectIds))
                .and(hasProjectId(projectId))
                .and(matchesSearch(search));
    }

    public static Specification<Request> filterForManagerApprovals(Long departmentId, String search) {
        return Specification.where(hasStatus(RequestStatus.PENDING))
                .and(hasType(RequestType.PROJECT_TOPUP))
                .and(hasProjectDepartment(departmentId))
                .and(matchesSearch(search));
    }

    public static Specification<Request> filterForAccountantDisbursements(RequestType type, String search) {
        List<RequestType> allowedTypes = (type != null)
                ? List.of(type)
                : List.of(RequestType.ADVANCE, RequestType.EXPENSE, RequestType.REIMBURSE);

        return Specification.where(hasStatus(RequestStatus.APPROVED_BY_TEAM_LEADER))
                .and(hasTypeIn(allowedTypes))
                .and(matchesSearch(search));
    }

    public static Specification<Request> filterForCfoApprovals(String search) {
        return Specification.where(hasStatus(RequestStatus.PENDING))
                .and(hasType(RequestType.DEPARTMENT_TOPUP))
                .and(matchesSearch(search));
    }
}
