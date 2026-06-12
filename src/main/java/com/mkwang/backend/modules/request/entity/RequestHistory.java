package com.mkwang.backend.modules.request.entity;

import com.mkwang.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * RequestHistory — append-only audit trail for the request approval workflow.
 * Records every action taken on a request (approve, reject, payout, cancel).
 *
 * Does NOT extend BaseEntity: only created_at is meaningful for an immutable record.
 * @Setter is intentionally omitted — entries must never be mutated after insert.
 */
@Entity
@Table(name = "request_histories")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "request_id", nullable = false, updatable = false)
  private Request request;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_id", nullable = false, updatable = false)
  private User actor;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 20, updatable = false)
  private RequestAction action;

  /**
   * Snapshot of Request.status immediately after this action was applied.
   * Allows full timeline reconstruction without replaying business logic.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "status_after_action", nullable = false, length = 25, updatable = false)
  private RequestStatus statusAfterAction;

  @Column(name = "comment", length = 500, updatable = false)
  private String comment;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
