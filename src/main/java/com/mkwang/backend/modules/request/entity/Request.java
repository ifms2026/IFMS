package com.mkwang.backend.modules.request.entity;

import com.mkwang.backend.common.base.BaseEntity;
import com.mkwang.backend.modules.project.entity.ExpenseCategory;
import com.mkwang.backend.modules.file.entity.FileStorage;
import com.mkwang.backend.modules.project.entity.Project;
import com.mkwang.backend.modules.project.entity.ProjectPhase;
import com.mkwang.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Request entity — financial request in the approval workflow.
 *
 * Flow 1 (ADVANCE / EXPENSE / REIMBURSE): Member → Team Leader → Accountant
 * Flow 2 (PROJECT_TOPUP):                 Team Leader → Manager → Auto
 * Flow 3 (DEPARTMENT_TOPUP):              Manager → Admin → Auto
 *
 * NO escalation — each flow has exactly one approval level.
 */
@Entity
@Table(
    name = "requests",
    indexes = {
        @Index(name = "idx_requests_request_code",   columnList = "request_code"),
        @Index(name = "idx_requests_requester",      columnList = "requester_id, status"),
        @Index(name = "idx_requests_status_type",    columnList = "status, type"),
        @Index(name = "idx_requests_project_status", columnList = "project_id, status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Request extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "request_code", nullable = false, unique = true, length = 30)
  private String requestCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requester_id", nullable = false)
  private User requester;

  /**
   * NULL for DEPARTMENT_TOPUP (department-level request, no project involved).
   * Required for ADVANCE / EXPENSE / REIMBURSE / PROJECT_TOPUP.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id")
  private Project project;

  /**
   * NULL for PROJECT_TOPUP and DEPARTMENT_TOPUP.
   * Required for ADVANCE / EXPENSE / REIMBURSE.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "phase_id")
  private ProjectPhase phase;

  /**
   * NULL for PROJECT_TOPUP and DEPARTMENT_TOPUP.
   * Required for ADVANCE / EXPENSE / REIMBURSE.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private ExpenseCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private RequestType type;

  /**
   * REIMBURSE only: the advance balance record this request is settling.
   * NULL for all other request types.
   * A single advance can be partially settled by multiple REIMBURSE requests.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "advance_balance_id")
  private AdvanceBalance advanceBalance;

  @Column(name = "amount", precision = 19, scale = 2, nullable = false)
  private BigDecimal amount;

  /**
   * Set by the approver. May be less than amount (partial approval).
   * This is the amount actually disbursed / allocated.
   */
  @Column(name = "approved_amount", precision = 19, scale = 2)
  private BigDecimal approvedAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 25)
  @Builder.Default
  private RequestStatus status = RequestStatus.PENDING;

  @Column(name = "reject_reason", columnDefinition = "TEXT")
  private String rejectReason;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  /**
   * Timestamp when status transitioned to PAID.
   * Enables SLA reporting and "overdue disbursement" queries without joining RequestHistory.
   */
  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @Builder.Default
  private List<RequestAttachment> attachments = new ArrayList<>();

  @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<RequestHistory> histories = new ArrayList<>();

  // ======================== Business Logic ========================

  /**
   * Cancel is only allowed while PENDING (not yet approved by anyone).
   * Once approval happens, request enters approval workflow and cancellation not permitted.
   */
  public boolean isCancellable() {
    return status == RequestStatus.PENDING;
  }

  /**
   * Is request in progress (not terminal)?
   * Terminal states: PAID, REJECTED, CANCELLED
   */
  public boolean isPending() {
    return status != RequestStatus.PAID
        && status != RequestStatus.REJECTED
        && status != RequestStatus.CANCELLED;
  }

  /**
   * EXPENSE and REIMBURSE require invoice/receipt attachments.
   * ADVANCE does not — it is a pre-approval before spending occurs.
   */
  public boolean requiresProof() {
    return type == RequestType.EXPENSE || type == RequestType.REIMBURSE;
  }

  public boolean isPersonalExpense() {
    return type == RequestType.ADVANCE
        || type == RequestType.EXPENSE
        || type == RequestType.REIMBURSE;
  }

  public boolean isTopUp() {
    return type == RequestType.PROJECT_TOPUP || type == RequestType.DEPARTMENT_TOPUP;
  }

  // ======================== Attachment Helpers ========================

  public void addAttachment(FileStorage file) {
    this.attachments.add(RequestAttachment.builder()
        .request(this)
        .file(file)
        .build());
  }

  public void removeAttachment(Long fileId) {
    this.attachments.removeIf(att -> att.getFile().getId().equals(fileId));
  }

  public List<FileStorage> getAttachmentFiles() {
    return this.attachments.stream()
        .map(RequestAttachment::getFile)
        .toList();
  }
}
