package com.mkwang.backend.modules.spreadsheet.mapper;

import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.organization.entity.Department;
import com.mkwang.backend.modules.project.entity.ExpenseCategory;
import com.mkwang.backend.modules.project.entity.Project;
import com.mkwang.backend.modules.project.entity.ProjectPhase;
import com.mkwang.backend.modules.request.entity.Request;
import com.mkwang.backend.modules.spreadsheet.dto.response.LookupOptionResponse;
import com.mkwang.backend.modules.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class FinancialLookupMapper {
    public LookupOptionResponse toOption(Object row) {
        if (row instanceof User user) {
            String code = user.getProfile() == null ? "" : Objects.toString(user.getProfile().getEmployeeCode(), "");
            return new LookupOptionResponse(user.getId(), (code.isBlank() ? "" : code + " · ") + user.getFullName());
        }
        if (row instanceof Project project) return new LookupOptionResponse(project.getId(), project.getProjectCode() + " · " + project.getName());
        if (row instanceof ProjectPhase phase) return new LookupOptionResponse(phase.getId(), phase.getPhaseCode() + " · " + phase.getName());
        if (row instanceof ExpenseCategory category) return new LookupOptionResponse(category.getId(), category.getName());
        if (row instanceof Department department) return new LookupOptionResponse(department.getId(), department.getCode() + " · " + department.getName());
        if (row instanceof Request request) return new LookupOptionResponse(request.getId(), request.getRequestCode() + " · " + request.getRequester().getFullName());
        throw new BadRequestException("Tham chiếu không hợp lệ");
    }
}
