package com.mkwang.backend.modules.ai.repository;

import com.mkwang.backend.modules.ai.entity.DocumentExtraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentExtractionRepository extends JpaRepository<DocumentExtraction, Long> {

    Optional<DocumentExtraction> findByIdAndUserId(Long id, Long userId);

    /** Same invoice (seller tax code + invoice number) already attached to a different request. */
    @Query("""
            SELECT COUNT(e) > 0 FROM DocumentExtraction e
            WHERE e.vendorTaxCode = :taxCode
              AND e.invoiceNumber = :invoiceNumber
              AND e.requestId IS NOT NULL
              AND e.requestId <> :requestId
            """)
    boolean existsInvoiceOnOtherRequest(@Param("taxCode") String taxCode,
                                        @Param("invoiceNumber") String invoiceNumber,
                                        @Param("requestId") Long requestId);
}
