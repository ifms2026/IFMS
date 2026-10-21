package com.mkwang.backend.modules.project.service;
import com.mkwang.backend.modules.project.repository.*;
import com.mkwang.backend.modules.user.service.UserService;
import com.mkwang.backend.modules.spreadsheet.service.ExcelDataset;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
@Configuration @RequiredArgsConstructor
public class ProjectExcelConfiguration {
    private final ProjectRepository projects;private final ProjectPhaseRepository phases;
    private final ProjectMemberRepository members;private final PhaseCategoryBudgetRepository budgets;
    private final ExpenseCategoryRepository categories;private final ManagerProjectService manager;
    private final TeamLeaderProjectService leader;private final CategoryBudgetService categoryService;
    private final UserService users;private final ProjectFinancialGuard guard;
    private ExcelDataset dataset(String kind){return new ProjectExcelDataset(kind,projects,phases,members,budgets,categories,manager,leader,categoryService,users,guard);}
    @Bean ExcelDataset projectsExcel(){return dataset("projects");}
    @Bean ExcelDataset phasesExcel(){return dataset("phases");}
    @Bean ExcelDataset projectMembersExcel(){return dataset("project-members");}
    @Bean ExcelDataset categoryBudgetsExcel(){return dataset("category-budgets");}
}
