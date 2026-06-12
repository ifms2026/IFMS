package com.mkwang.backend.modules.file.entity;

import com.mkwang.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * FileStorage entity - Stores Cloudinary file metadata.
 * Used for storing proof documents, avatars, etc.
 */
@Entity
@Table(name = "file_storages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileStorage extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Column(name = "cloudinary_public_id", unique = true, nullable = false)
  private String cloudinaryPublicId;

  @Column(name = "url", columnDefinition = "TEXT", nullable = false)
  private String url;

  @Column(name = "file_type", length = 100)
  private String fileType; // MIME type

  @Column(name = "size")
  private Long size; // Size in bytes
}
