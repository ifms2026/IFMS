package com.mkwang.backend.modules.request.mapper;

import com.mkwang.backend.modules.request.dto.response.AttachmentResponse;
import com.mkwang.backend.modules.request.dto.response.AccountantDisbursementDetailResponse;
import com.mkwang.backend.modules.request.dto.response.AccountantDisbursementSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.AccountantRejectResponse;
import com.mkwang.backend.modules.request.dto.response.CfoApprovalDetailResponse;
import com.mkwang.backend.modules.request.dto.response.CfoApprovalSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.CfoApproveResponse;
import com.mkwang.backend.modules.request.dto.response.CfoRejectResponse;
import com.mkwang.backend.modules.request.dto.response.DisburseResponse;
import com.mkwang.backend.modules.request.dto.response.ManagerApprovalDetailResponse;
import com.mkwang.backend.modules.request.dto.response.ManagerApprovalSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.ManagerApproveResponse;
import com.mkwang.backend.modules.request.dto.response.ManagerRejectResponse;
import com.mkwang.backend.modules.request.dto.response.RequestDetailResponse;
import com.mkwang.backend.modules.request.dto.response.RequestHistoryResponse;
import com.mkwang.backend.modules.request.dto.response.RequestSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.TlApprovalDetailResponse;
import com.mkwang.backend.modules.request.dto.response.TlApprovalSummaryResponse;
import com.mkwang.backend.modules.request.dto.response.TlApproveResponse;
import com.mkwang.backend.modules.request.dto.response.TlRejectResponse;
import com.mkwang.backend.modules.request.entity.Request;
import com.mkwang.backend.modules.request.entity.RequestAttachment;
import com.mkwang.backend.modules.request.entity.RequestHistory;
import com.mkwang.backend.modules.project.entity.ProjectPhase;
import com.mkwang.backend.modules.organization.entity.Department;
import com.mkwang.backend.modules.profile.entity.UserProfile;
import com.mkwang.backend.modules.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RequestMapper {

    public RequestSummaryResponse toSummaryResponse(Request request) {
        return RequestSummaryResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType())
                .status(request.getStatus())
                .amount(request.getAmount())
                .approvedAmount(request.getApprovedAmount())
                .description(request.getDescription())
                .rejectReason(request.getRejectReason())
                .projectId(request.getProject() != null ? request.getProject().getId() : null)
                .projectName(request.getProject() != null ? request.getProject().getName() : null)
                .phaseId(request.getPhase() != null ? request.getPhase().getId() : null)
                .phaseName(request.getPhase() != null ? request.getPhase().getName() : null)
                .categoryId(request.getCategory() != null ? request.getCategory().getId() : null)
                .categoryName(request.getCategory() != null ? request.getCategory().getName() : null)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    public RequestDetailResponse toDetailResponse(Request request, List<RequestHistoryResponse> timeline) {
        return RequestDetailResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType())
                .status(request.getStatus())
                .amount(request.getAmount())
                .approvedAmount(request.getApprovedAmount())
                .description(request.getDescription())
                .rejectReason(request.getRejectReason())
                .paidAt(request.getPaidAt())
                .projectId(request.getProject() != null ? request.getProject().getId() : null)
                .projectCode(request.getProject() != null ? request.getProject().getProjectCode() : null)
                .projectName(request.getProject() != null ? request.getProject().getName() : null)
                .phaseId(request.getPhase() != null ? request.getPhase().getId() : null)
                .phaseCode(request.getPhase() != null ? request.getPhase().getPhaseCode() : null)
                .phaseName(request.getPhase() != null ? request.getPhase().getName() : null)
                .categoryId(request.getCategory() != null ? request.getCategory().getId() : null)
                .categoryName(request.getCategory() != null ? request.getCategory().getName() : null)
                .advanceBalanceId(request.getAdvanceBalance() != null ? request.getAdvanceBalance().getId() : null)
                .requesterId(request.getRequester() != null ? request.getRequester().getId() : null)
                .requesterName(request.getRequester() != null ? request.getRequester().getFullName() : null)
                .attachments(request.getAttachments().stream().map(this::toAttachmentResponse).toList())
                .timeline(timeline)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    public RequestHistoryResponse toHistoryResponse(RequestHistory history) {
        return RequestHistoryResponse.builder()
                .id(history.getId())
                .action(history.getAction())
                .statusAfterAction(history.getStatusAfterAction())
                .actorId(history.getActor() != null ? history.getActor().getId() : null)
                .actorName(history.getActor() != null ? history.getActor().getFullName() : null)
                .comment(history.getComment())
                .createdAt(history.getCreatedAt())
                .build();
    }

    public AttachmentResponse toAttachmentResponse(RequestAttachment attachment) {
        return AttachmentResponse.builder()
                .fileId(attachment.getFile().getId())
                .fileName(attachment.getFile().getFileName())
                .cloudinaryPublicId(attachment.getFile().getCloudinaryPublicId())
                .url(attachment.getFile().getUrl())
                .fileType(attachment.getFile().getFileType())
                .size(attachment.getFile().getSize())
                .build();
    }

    public TlApprovalSummaryResponse toTlApprovalSummaryResponse(Request request) {
        User user = request.getRequester();
        UserProfile profile = user != null ? user.getProfile() : null;

        return TlApprovalSummaryResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType())
                .status(request.getStatus())
                .amount(request.getAmount())
                .description(request.getDescription())
                .requester(TlApprovalSummaryResponse.RequesterSnippet.builder()
                        .id(user != null ? user.getId() : null)
                        .fullName(user != null ? user.getFullName() : null)
                        .avatar(resolveAvatar(profile))
                        .employeeCode(profile != null ? profile.getEmployeeCode() : null)
                        .jobTitle(profile != null ? profile.getJobTitle() : null)
                        .email(user != null ? user.getEmail() : null)
                        .build())
                .project(request.getProject() != null
                        ? TlApprovalSummaryResponse.ProjectSnippet.builder()
                        .id(request.getProject().getId())
                        .projectCode(request.getProject().getProjectCode())
                        .name(request.getProject().getName())
                        .build()
                        : null)
                .phase(request.getPhase() != null
                        ? TlApprovalSummaryResponse.PhaseSnippet.builder()
                        .id(request.getPhase().getId())
                        .phaseCode(request.getPhase().getPhaseCode())
                        .name(request.getPhase().getName())
                        .budgetLimit(request.getPhase().getBudgetLimit())
                        .currentSpent(request.getPhase().getCurrentSpent())
                        .build()
                        : null)
                .categoryId(request.getCategory() != null ? request.getCategory().getId() : null)
                .categoryName(request.getCategory() != null ? request.getCategory().getName() : null)
                .createdAt(request.getCreatedAt())
                .build();
    }

    public TlApprovalDetailResponse toTlApprovalDetailResponse(Request request) {
        User user = request.getRequester();
        UserProfile profile = user != null ? user.getProfile() : null;
        ProjectPhase phase = request.getPhase();

        return TlApprovalDetailResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType())
                .status(request.getStatus())
                .amount(request.getAmount())
                .description(request.getDescription())
                .requester(TlApprovalDetailResponse.RequesterDetail.builder()
                        .id(user != null ? user.getId() : null)
                        .fullName(user != null ? user.getFullName() : null)
                        .avatar(resolveAvatar(profile))
                        .employeeCode(profile != null ? profile.getEmployeeCode() : null)
                        .jobTitle(profile != null ? profile.getJobTitle() : null)
                        .email(user != null ? user.getEmail() : null)
                        .build())
                .project(request.getProject() != null
                        ? TlApprovalDetailResponse.ProjectDetail.builder()
                        .id(request.getProject().getId())
                        .projectCode(request.getProject().getProjectCode())
                        .name(request.getProject().getName())
                        .build()
                        : null)
                .phase(phase != null
                        ? TlApprovalDetailResponse.PhaseDetail.builder()
                        .id(phase.getId())
                        .phaseCode(phase.getPhaseCode())
                        .name(phase.getName())
                        .budgetLimit(phase.getBudgetLimit())
                        .currentSpent(phase.getCurrentSpent())
                        .build()
                        : null)
                .categoryId(request.getCategory() != null ? request.getCategory().getId() : null)
                .categoryName(request.getCategory() != null ? request.getCategory().getName() : null)
                .attachments(request.getAttachments().stream().map(this::toAttachmentResponse).toList())
                .createdAt(request.getCreatedAt())
                .build();
    }

    public TlApproveResponse toTlApproveResponse(Request request, String comment) {
        return TlApproveResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .status(request.getStatus())
                .approvedAmount(request.getApprovedAmount())
                .comment(comment)
                .build();
    }

    public TlRejectResponse toTlRejectResponse(Request request) {
        return TlRejectResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .status(request.getStatus())
                .rejectReason(request.getRejectReason())
                .build();
    }

    public ManagerApprovalSummaryResponse toManagerApprovalSummaryResponse(Request request) {
        User user = request.getRequester();
        UserProfile profile = user != null ? user.getProfile() : null;

        return ManagerApprovalSummaryResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType())
                .status(request.getStatus())
                .amount(request.getAmount())
                .description(request.getDescription())
                .requester(ManagerApprovalSummaryResponse.RequesterSnippet.builder()
                        .id(user != null ? user.getId() : null)
                        .fullName(user != null ? user.getFullName() : null)
                        .avatar(resolveAvatar(profile))
                        .employeeCode(profile != null ? profile.getEmployeeCode() : null)
                        .jobTitle(profile != null ? profile.getJobTitle() : null)
                        .email(user != null ? user.getEmail() : null)
                        .build())
                .project(request.getProject() != null
                        ? ManagerApprovalSummaryResponse.ProjectSnippet.builder()
                        .id(request.getProject().getId())
                        .projectCode(request.getProject().getProjectCode())
                        .name(request.getProject().getName())
                        .availableBudget(request.getProject().getAvailableBudget())
                        .build()
                        : null)
                .createdAt(request.getCreatedAt())
                .build();
    }

    public ManagerApprovalDetailResponse toManagerApprovalDetailResponse(
            Request request,
            List<RequestHistoryResponse> timeline) {

        User user = request.getRequester();
        UserProfile profile = user != null ? user.getProfile() : null;
        Department department = request.getProject() != null ? request.getProject().getDepartment() : null;

        return ManagerApprovalDetailResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType())
                .status(request.getStatus())
                .amount(request.getAmount())
                .approvedAmount(request.getApprovedAmount())
                .description(request.getDescription())
                .rejectReason(request.getRejectReason())
                .requester(ManagerApprovalDetailResponse.RequesterDetail.builder()
                        .id(user != null ? user.getId() : null)
                        .fullName(user != null ? user.getFullName() : null)
                        .avatar(resolveAvatar(profile))
                        .employeeCode(profile != null ? profile.getEmployeeCode() : null)
                        .jobTitle(profile != null ? profile.getJobTitle() : null)
                        .email(user != null ? user.getEmail() : null)
                        .departmentName(user != null && user.getDepartment() != null ? user.getDepartment().getName() : null)
                        .build())
                .project(request.getProject() != null
                        ? ManagerApprovalDetailResponse.ProjectDetail.builder()
                        .id(request.getProject().getId())
                        .projectCode(request.getProject().getProjectCode())
                        .name(request.getProject().getName())
                        .availableBudget(request.getProject().getAvailableBudget())
                        .totalBudget(request.getProject().getTotalBudget())
                        .build()
                        : null)
                .department(department != null
                        ? ManagerApprovalDetailResponse.DepartmentDetail.builder()
                        .id(department.getId())
                        .name(department.getName())
                        .totalAvailableBalance(department.getTotalAvailableBalance())
                        .build()
                        : null)
                .timeline(timeline)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    public ManagerApproveResponse toManagerApproveResponse(Request request, String comment) {
        return ManagerApproveResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .status(request.getStatus())
                .approvedAmount(request.getApprovedAmount())
                .comment(comment)
                .build();
    }

    public ManagerRejectResponse toManagerRejectResponse(Request request) {
        return ManagerRejectResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .status(request.getStatus())
                .rejectReason(request.getRejectReason())
                .build();
    }

    public AccountantDisbursementSummaryResponse toAccountantDisbursementSummaryResponse(Request request) {
        User user = request.getRequester();
        UserProfile profile = user != null ? user.getProfile() : null;

        return AccountantDisbursementSummaryResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType())
                .status(request.getStatus())
                .amount(request.getAmount())
                .approvedAmount(request.getApprovedAmount())
                .description(request.getDescription())
                .requester(AccountantDisbursementSummaryResponse.RequesterBankSnippet.builder()
                        .id(user != null ? user.getId() : null)
                        .fullName(user != null ? user.getFullName() : null)
                        .avatar(resolveAvatar(profile))
                        .employeeCode(profile != null ? profile.getEmployeeCode() : null)
                        .jobTitle(profile != null ? profile.getJobTitle() : null)
                        .departmentName(user != null && user.getDepartment() != null ? user.getDepartment().getName() : null)
                        .bankName(profile != null ? profile.getBankName() : null)
                        .bankAccountNum(profile != null ? profile.getBankAccountNum() : null)
                        .bankAccountOwner(profile != null ? profile.getBankAccountOwner() : null)
                        .build())
                .project(request.getProject() != null
                        ? AccountantDisbursementSummaryResponse.ProjectSnippet.builder()
                        .id(request.getProject().getId())
                        .projectCode(request.getProject().getProjectCode())
                        .name(request.getProject().getName())
                        .build()
                        : null)
                .phase(request.getPhase() != null
                        ? AccountantDisbursementSummaryResponse.PhaseSnippet.builder()
                        .id(request.getPhase().getId())
                        .phaseCode(request.getPhase().getPhaseCode())
                        .name(request.getPhase().getName())
                        .build()
                        : null)
                .categoryId(request.getCategory() != null ? request.getCategory().getId() : null)
                .categoryName(request.getCategory() != null ? request.getCategory().getName() : null)
                .attachments(request.getAttachments().stream().map(this::toAttachmentResponse).toList())
                .createdAt(request.getCreatedAt())
                .build();
    }

    public AccountantDisbursementDetailResponse toAccountantDisbursementDetailResponse(
            Request request,
            List<RequestHistoryResponse> timeline) {

        User user = request.getRequester();
        UserProfile profile = user != null ? user.getProfile() : null;
        ProjectPhase phase = request.getPhase();

        return AccountantDisbursementDetailResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType())
                .status(request.getStatus())
                .amount(request.getAmount())
                .approvedAmount(request.getApprovedAmount())
                .description(request.getDescription())
                .rejectReason(request.getRejectReason())
                .paidAt(request.getPaidAt())
                .requester(AccountantDisbursementSummaryResponse.RequesterBankSnippet.builder()
                        .id(user != null ? user.getId() : null)
                        .fullName(user != null ? user.getFullName() : null)
                        .avatar(resolveAvatar(profile))
                        .employeeCode(profile != null ? profile.getEmployeeCode() : null)
                        .jobTitle(profile != null ? profile.getJobTitle() : null)
                        .departmentName(user != null && user.getDepartment() != null ? user.getDepartment().getName() : null)
                        .bankName(profile != null ? profile.getBankName() : null)
                        .bankAccountNum(profile != null ? profile.getBankAccountNum() : null)
                        .bankAccountOwner(profile != null ? profile.getBankAccountOwner() : null)
                        .build())
                .project(request.getProject() != null
                        ? AccountantDisbursementSummaryResponse.ProjectSnippet.builder()
                        .id(request.getProject().getId())
                        .projectCode(request.getProject().getProjectCode())
                        .name(request.getProject().getName())
                        .build()
                        : null)
                .phase(phase != null
                        ? AccountantDisbursementDetailResponse.PhaseDetail.builder()
                        .id(phase.getId())
                        .phaseCode(phase.getPhaseCode())
                        .name(phase.getName())
                        .budgetLimit(phase.getBudgetLimit())
                        .currentSpent(phase.getCurrentSpent())
                        .build()
                        : null)
                .categoryId(request.getCategory() != null ? request.getCategory().getId() : null)
                .categoryName(request.getCategory() != null ? request.getCategory().getName() : null)
                .attachments(request.getAttachments().stream().map(this::toAttachmentResponse).toList())
                .timeline(timeline)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    public DisburseResponse toDisburseResponse(Request request, String transactionCode) {
        return DisburseResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .status(request.getStatus())
                .transactionCode(transactionCode)
                .amount(request.getApprovedAmount())
                .disbursedAt(request.getPaidAt())
                .build();
    }

    public AccountantRejectResponse toAccountantRejectResponse(Request request) {
        return AccountantRejectResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .status(request.getStatus())
                .rejectReason(request.getRejectReason())
                .build();
    }

    public CfoApprovalSummaryResponse toCfoApprovalSummaryResponse(Request request) {
        User user = request.getRequester();
        UserProfile profile = user != null ? user.getProfile() : null;
        Department department = user != null ? user.getDepartment() : null;

        return CfoApprovalSummaryResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType())
                .status(request.getStatus())
                .amount(request.getAmount())
                .description(request.getDescription())
                .requester(CfoApprovalSummaryResponse.RequesterSnippet.builder()
                        .id(user != null ? user.getId() : null)
                        .fullName(user != null ? user.getFullName() : null)
                        .avatar(resolveAvatar(profile))
                        .employeeCode(profile != null ? profile.getEmployeeCode() : null)
                        .jobTitle(profile != null ? profile.getJobTitle() : null)
                        .email(user != null ? user.getEmail() : null)
                        .build())
                .department(department != null
                        ? CfoApprovalSummaryResponse.DepartmentSnippet.builder()
                        .id(department.getId())
                        .name(department.getName())
                        .code(department.getCode())
                        .totalAvailableBalance(department.getTotalAvailableBalance())
                        .build()
                        : null)
                .createdAt(request.getCreatedAt())
                .build();
    }

    public CfoApprovalDetailResponse toCfoApprovalDetailResponse(
            Request request, List<RequestHistoryResponse> timeline, java.math.BigDecimal cfBalance) {

        User user = request.getRequester();
        UserProfile profile = user != null ? user.getProfile() : null;
        Department department = user != null ? user.getDepartment() : null;

        return CfoApprovalDetailResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType())
                .status(request.getStatus())
                .amount(request.getAmount())
                .approvedAmount(request.getApprovedAmount())
                .description(request.getDescription())
                .rejectReason(request.getRejectReason())
                .requester(CfoApprovalDetailResponse.RequesterDetail.builder()
                        .id(user != null ? user.getId() : null)
                        .fullName(user != null ? user.getFullName() : null)
                        .avatar(resolveAvatar(profile))
                        .employeeCode(profile != null ? profile.getEmployeeCode() : null)
                        .jobTitle(profile != null ? profile.getJobTitle() : null)
                        .email(user != null ? user.getEmail() : null)
                        .departmentName(department != null ? department.getName() : null)
                        .build())
                .department(department != null
                        ? CfoApprovalDetailResponse.DepartmentDetail.builder()
                        .id(department.getId())
                        .name(department.getName())
                        .code(department.getCode())
                        .totalProjectQuota(department.getTotalProjectQuota())
                        .totalAvailableBalance(department.getTotalAvailableBalance())
                        .build()
                        : null)
                .companyFund(CfoApprovalDetailResponse.CompanyFundSnapshot.builder()
                        .balance(cfBalance)
                        .build())
                .timeline(timeline)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    public CfoApproveResponse toCfoApproveResponse(Request request, String comment) {
        return CfoApproveResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .status(request.getStatus())
                .approvedAmount(request.getApprovedAmount())
                .comment(comment)
                .build();
    }

    public CfoRejectResponse toCfoRejectResponse(Request request) {
        return CfoRejectResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .status(request.getStatus())
                .rejectReason(request.getRejectReason())
                .build();
    }

    private String resolveAvatar(UserProfile profile) {
        if (profile == null || profile.getAvatarFile() == null) {
            return null;
        }
        return profile.getAvatarFile().getUrl();
    }

}

