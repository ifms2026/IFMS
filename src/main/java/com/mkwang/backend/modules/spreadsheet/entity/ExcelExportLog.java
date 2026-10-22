package com.mkwang.backend.modules.spreadsheet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name="excel_export_logs", indexes={@Index(name="idx_excel_export_actor_created",columnList="actor_id,created_at")})
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ExcelExportLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="actor_id",nullable=false) private Long actorId;
    @Column(name="dataset_code",nullable=false,length=100) private String datasetCode;
    @Column(name="scope_key",nullable=false,length=100) private String scopeKey;
    @Column(name="filter_snapshot",nullable=false,columnDefinition="TEXT") private String filterSnapshot;
    @Column(name="row_count",nullable=false) private int rowCount;
    @Column(name="file_sha256",nullable=false,length=64) private String fileSha256;
    @Column(name="created_at",nullable=false) private Instant createdAt;
}
