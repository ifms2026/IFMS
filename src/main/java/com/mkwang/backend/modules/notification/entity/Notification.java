package com.mkwang.backend.modules.notification.entity;

import com.mkwang.backend.common.base.BaseEntity;
import com.mkwang.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * Notification entity - Stores notification history for persistence.
 * Ensures users don't lose notifications when offline.
 * Works with WebSocket for real-time delivery.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 30)
  @Builder.Default
  private NotificationType type = NotificationType.SYSTEM;

  @Column(name = "ref_id")
  private Long refId; // Reference ID of related entity (Request, Payslip, etc.)

  @Column(name = "ref_type", length = 50)
  private String refType; // Type of reference: "REQUEST", "PAYSLIP", "PROJECT", etc.

  @Column(name = "is_read", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
  @Builder.Default
  private Boolean isRead = false;

  /**
   * Mark notification as read
   */
  public void markAsRead() {
    this.isRead = true;
  }

  /**
   * Build a reference link for frontend navigation.
   * Logic dựa vào notification type + role của người nhận — không dùng refType thuần túy
   * vì cùng refType "REQUEST" nhưng approver phải navigate khác với requester.
   */
  public String getReferenceLink() {
    if (refId == null || type == null) return null;

    String roleName = resolveRecipientRole();

    return switch (type) {
      // REQUEST_SUBMITTED → gửi tới approver (TL / Manager / CFO / Accountant)
      case REQUEST_SUBMITTED -> switch (roleName != null ? roleName : "") {
        case "TEAM_LEADER" -> "/team-leader/approvals/" + refId;
        case "MANAGER"     -> "/manager/approvals/"     + refId;
        case "CFO"         -> "/cfo/approvals/"         + refId;
        case "ACCOUNTANT"  -> "/accountant/disbursements/" + refId;
        default            -> "/requests/" + refId;
      };

      // REQUEST_APPROVED_BY_TL → gửi tới Accountant cần giải ngân
      case REQUEST_APPROVED_BY_TL -> "ACCOUNTANT".equals(roleName)
          ? "/accountant/disbursements/" + refId
          : "/requests/" + refId;

      // Các notification gửi tới requester (employee xem yêu cầu của mình)
      case REQUEST_REJECTED, REQUEST_PAID -> "/requests/" + refId;

      // Flow 2: PROJECT_TOPUP → gửi tới TL (kết quả request của TL)
      case PROJECT_TOPUP_APPROVED, PROJECT_TOPUP_REJECTED -> "/requests/" + refId;

      // Flow 3: DEPT_TOPUP → gửi tới Manager (kết quả request của Manager)
      case DEPT_TOPUP_APPROVED, DEPT_TOPUP_REJECTED -> "/requests/" + refId;

      // Lương → employee xem phiếu lương
      case SALARY_PAID -> "/payroll/" + refId;

      default -> null;
    };
  }

  private String resolveRecipientRole() {
    try {
      return (user != null && user.getRole() != null) ? user.getRole().getName() : null;
    } catch (Exception e) {
      return null; // lazy load failed — degrade gracefully
    }
  }
}
