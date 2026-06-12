package com.mkwang.backend.modules.organization.entity;

import com.mkwang.backend.common.base.BaseEntity;
import com.mkwang.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Department entity - Represents company departments.
 * Each department has a manager and budget allocation.
 */
@Entity
@Table(name = "departments", indexes = {
    @Index(name = "idx_departments_code", columnList = "code")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Department extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(unique = true, nullable = false, length = 20)
  private String code;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "manager_id")
  private User manager;

  // TỔNG NGÂN SÁCH ĐÃ NHẬN: Bằng tổng (Sum) của tất cả project_budget thuộc phòng này.
  // Cột này chỉ TĂNG lên mỗi khi Admin duyệt Top-up cho một dự án của phòng.
  @Column(name = "total_project_quota", precision = 19, scale = 2, columnDefinition = "DECIMAL(19,2) DEFAULT 0")
  @Builder.Default
  private BigDecimal totalProjectQuota = BigDecimal.ZERO;

  // TỔNG TIỀN CÒN LẠI: Bằng tổng tiền chưa tiêu của tất cả các dự án thuộc phòng.
  // Cột này TĂNG khi được Top-up, và GIẢM khi có một nhân viên trong phòng được duyệt chi tiền.
  @Column(name = "total_available_balance", precision = 19, scale = 2, columnDefinition = "DECIMAL(19,2) DEFAULT 0")
  @Builder.Default
  private BigDecimal totalAvailableBalance = BigDecimal.ZERO;

  // ======================== Business Logic ========================

  /**
   * Receive quota from System Fund (when CFO approves DEPARTMENT_TOPUP).
   */
  public void receiveQuota(BigDecimal amount) {
    this.totalProjectQuota = this.totalProjectQuota.add(amount);
    this.totalAvailableBalance = this.totalAvailableBalance.add(amount);
  }

  /**
   * Allocate budget to Project Fund (when Manager approves PROJECT_TOPUP).
   */
  public void allocateToProject(BigDecimal amount) {
    this.totalAvailableBalance = this.totalAvailableBalance.subtract(amount);
  }
}