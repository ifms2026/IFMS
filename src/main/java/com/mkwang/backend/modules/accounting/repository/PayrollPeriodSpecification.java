package com.mkwang.backend.modules.accounting.repository;

import com.mkwang.backend.modules.accounting.entity.PayrollPeriod;
import com.mkwang.backend.modules.accounting.entity.PayrollStatus;
import org.springframework.data.jpa.domain.Specification;

public class PayrollPeriodSpecification {

    private PayrollPeriodSpecification() {
    }

    public static Specification<PayrollPeriod> hasYear(Integer year) {
        return (root, query, cb) -> cb.equal(root.get("year"), year);
    }

    public static Specification<PayrollPeriod> hasStatus(PayrollStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<PayrollPeriod> filter(Integer year, PayrollStatus status) {
        Specification<PayrollPeriod> specification = Specification.where(null);

        if (year != null) {
            specification = specification.and(hasYear(year));
        }

        if (status != null) {
            specification = specification.and(hasStatus(status));
        }

        return specification;
    }
}

