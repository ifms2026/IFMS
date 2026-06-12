package com.mkwang.backend.modules.project.repository;

import com.mkwang.backend.modules.project.entity.PhaseStatus;
import com.mkwang.backend.modules.project.entity.ProjectPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectPhaseRepository extends JpaRepository<ProjectPhase, Long> {

    List<ProjectPhase> findByProject_IdOrderByCreatedAtAsc(Long projectId);

    List<ProjectPhase> findByProject_IdAndStatusOrderByCreatedAtAsc(Long projectId, PhaseStatus status);
}


