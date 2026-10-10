package com.mkwang.backend.modules.ai.entity;

import com.mkwang.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Result of reading one receipt image/PDF with AI.
 * <p>
 * Created when the user uploads a file on the "create request" form; linked to the
 * request and stored file (requestId, fileId) once the request is submitted.
 * userId / requestId / fileId are plain ids (FKs exist in SQL) so the ai module does
 * not depend on the user, request and file entities.
 */
@Entity
@Table(name = "document_extractions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentExtraction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "file_hash", nullable = false, length = 64, updatable = false)
    private String fileHash;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "total_amount", precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "vendor_name")
    private String vendorName;

    @Column(name = "vendor_tax_code", length = 30)
    private String vendorTaxCode;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "vat_amount", precision = 19, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "expense_summary", columnDefinition = "text")
    private String expenseSummary;

    /** JSON array of {description, quantity, amount}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "line_items", columnDefinition = "jsonb")
    private String lineItems;

    /** JSON array of warning codes, e.g. ["INVOICE_DATE_IN_FUTURE"]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", columnDefinition = "jsonb")
    private String warnings;

    /** Raw JSON returned by the provider — kept for audit and accuracy measurement. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "file_id")
    private Long fileId;
}
