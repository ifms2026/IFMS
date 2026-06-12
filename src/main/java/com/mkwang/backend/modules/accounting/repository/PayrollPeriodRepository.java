package com.mkwang.backend.modules.accounting.repository;

import com.mkwang.backend.modules.accounting.entity.PayrollPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long>, JpaSpecificationExecutor<PayrollPeriod> {

    boolean existsByMonthAndYear(Integer month, Integer year);

    Optional<PayrollPeriod> findByPeriodCode(String periodCode);

    @Query("""
            select distinct period
            from PayrollPeriod period
            left join fetch period.payslips payslip
            left join fetch payslip.user user
            left join fetch user.profile profile
            left join fetch profile.avatarFile
            where period.id = :periodId
            """)
    Optional<PayrollPeriod> findDetailById(@Param("periodId") Long periodId);

    @Query("SELECT p FROM PayrollPeriod p ORDER BY p.createdAt DESC LIMIT 1")
    Optional<PayrollPeriod> findLatestPeriod();
}

