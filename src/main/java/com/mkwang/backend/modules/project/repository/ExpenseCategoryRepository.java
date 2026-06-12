package com.mkwang.backend.modules.project.repository;

import com.mkwang.backend.modules.project.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    Optional<ExpenseCategory> findByName(String name);

    @Query("SELECT ec FROM ExpenseCategory ec LEFT JOIN FETCH ec.project " +
           "WHERE ec.project IS NULL OR ec.project.id = :projectId " +
           "ORDER BY ec.name ASC")
    List<ExpenseCategory> findAvailableForProject(@Param("projectId") Long projectId);
}

