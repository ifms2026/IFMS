package com.mkwang.backend.modules.request.entity;

import com.mkwang.backend.modules.file.entity.FileStorage;
import jakarta.persistence.*;
import lombok.*;

/**
 * RequestAttachment entity - Join table for Request ↔ FileStorage many-to-many relationship.
 * Represents the attachments (invoices, PDFs, Excel files) uploaded for a request.
 *
 * Uses composite primary key (request_id, file_id) to ensure:
 * - One file can only belong to one request (business rule).
 * - Proper PK constraint as per Database.md specification.
 *
 * Mapped to table: request_attachments
 */
@Entity
@Table(name = "request_attachments")
@IdClass(RequestAttachmentId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestAttachment {

    /**
     * The request this attachment belongs to.
     * Part of composite PK.
     */
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    /**
     * The file storage record for this attachment.
     * Part of composite PK.
     * Unique constraint ensures one file cannot be attached to multiple requests.
     */
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false, unique = true)
    private FileStorage file;

    // Future expansion fields (if needed):
    // private LocalDateTime uploadedAt;
    // private User uploadedBy;
    // private String attachmentType; // INVOICE, RECEIPT, CONTRACT, etc.
}

