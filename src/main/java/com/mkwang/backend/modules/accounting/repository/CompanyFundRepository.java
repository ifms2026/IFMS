package com.mkwang.backend.modules.accounting.repository;

import com.mkwang.backend.modules.accounting.entity.CompanyFund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyFundRepository extends JpaRepository<CompanyFund, Long> {

    /**
     * Returns the singleton CompanyFund record (id = 1).
     */
    default Optional<CompanyFund> findDefault() {
        return findById(1L);
    }
}
