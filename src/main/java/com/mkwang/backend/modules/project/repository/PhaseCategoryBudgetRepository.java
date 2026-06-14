package com.mkwang.backend.modules.project.repository;

import com.mkwang.backend.modules.project.entity.PhaseCategoryBudget;
import com.mkwang.backend.modules.project.entity.PhaseCategoryBudgetId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PhaseCategoryBudgetRepository extends JpaRepository<PhaseCategoryBudget, PhaseCategoryBudgetId> {

    List<PhaseCategoryBudget> findByIdPhaseId(Long phaseId);

    List<PhaseCategoryBudget> findByIdCategoryId(Long categoryId);

    @Modifying
    @Query("DELETE FROM PhaseCategoryBudget pcb WHERE pcb.id.phaseId = :phaseId")
    void deleteByPhaseId(@Param("phaseId") Long phaseId);

    @Query("""
            SELECT COALESCE(SUM(pcb.currentSpent), 0)
            FROM PhaseCategoryBudget pcb
            WHERE pcb.phase.project.id = :projectId
            """)
    BigDecimal sumCurrentSpentByProjectId(@Param("projectId") Long projectId);

    @Query("""
            SELECT pcb.phase.project.id, COALESCE(SUM(pcb.currentSpent), 0)
            FROM PhaseCategoryBudget pcb
            WHERE pcb.phase.project.id IN :projectIds
            GROUP BY pcb.phase.project.id
            """)
    List<Object[]> sumCurrentSpentByProjectIds(@Param("projectIds") List<Long> projectIds);

    @Query("""
            SELECT pcb.id.phaseId, COALESCE(SUM(pcb.currentSpent), 0)
            FROM PhaseCategoryBudget pcb
            WHERE pcb.id.phaseId IN :phaseIds
            GROUP BY pcb.id.phaseId
            """)
    List<Object[]> sumCurrentSpentByPhaseIds(@Param("phaseIds") List<Long> phaseIds);
}

