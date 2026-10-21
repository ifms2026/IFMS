package com.mkwang.backend.modules.spreadsheet.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="excel_import_batches")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ExcelImportBatch {
    @Id private String id;
    @Column(nullable=false) private Long actorId;
    @Column(nullable=false) private String dataset;
    @Column(nullable=false) private String scopeKey;
    @Column(nullable=false) private String fileHash;
    @Column(nullable=false) private Integer schemaVersion;
    @Column(nullable=false,columnDefinition="TEXT") private String payload;
    @Column(nullable=false) private String state;
    @Column(columnDefinition="TEXT") private String result;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant expiresAt;
    private Instant committedAt;
    @Version private Long version;
}
