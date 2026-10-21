package com.mkwang.backend.modules.request.mapper;

import com.mkwang.backend.modules.request.dto.response.RequestAccountingContextResponse;
import com.mkwang.backend.modules.request.entity.Request;
import org.springframework.stereotype.Component;

@Component
public class RequestAccountingMapper {
    public RequestAccountingContextResponse toContext(Request request) {
        var project = request.getProject();
        var department = project == null ? request.getRequester().getDepartment() : project.getDepartment();
        var phase = request.getPhase();
        var category = request.getCategory();
        return new RequestAccountingContextResponse(request.getId(), request.getRequestCode(),
                request.getType().name(), request.getStatus().name(),
                request.getCreatedAt() == null ? null : request.getCreatedAt().toLocalDate(),
                department == null ? null : department.getId(), department == null ? null : department.getName(),
                project == null ? null : project.getId(), project == null ? null : project.getName(),
                phase == null ? null : phase.getId(), phase == null ? null : phase.getName(),
                category == null ? null : category.getId(), category == null ? null : category.getName(),
                request.getRequester().getId(), request.getRequester().getFullName(), request.getDescription(),
                request.getAttachmentFiles().stream().map(file -> new RequestAccountingContextResponse.DocumentResponse(
                        file.getId(), file.getFileName(), file.getUrl(), file.getCloudinaryPublicId())).toList(), request.getApprovedAmount());
    }
}
