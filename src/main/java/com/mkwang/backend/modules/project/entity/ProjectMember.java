package com.mkwang.backend.modules.project.entity;

import com.mkwang.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ProjectMember entity - Many-to-Many relationship between Project and User.
 * Uses composite primary key (projectId, userId).
 *
 * Two separate fields for "role":
 * - projectRole (Enum): Authorization level — LEADER or MEMBER. Used for permission logic.
 * - position (String): Display-only job title — "Backend Dev", "Tester", etc. Free-text, no logic impact.
 */
@Entity
@Table(name = "project_members")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMember {

  @EmbeddedId
  private ProjectMemberId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("projectId")
  @JoinColumn(name = "project_id")
  private Project project;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("userId")
  @JoinColumn(name = "user_id")
  private User user;

  /**
   * Authorization level in the project. Determines what the member can do:
   * - LEADER: Approve requests, manage phase/category budgets
   * - MEMBER: Create expense requests only
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "project_role", nullable = false, length = 20)
  @Builder.Default
  private ProjectRole projectRole = ProjectRole.MEMBER;

  /**
   * Display-only position/title in the project. Free-text, no impact on permissions.
   * Examples: "Backend Dev", "Tester", "BA", "AI Engineer", "DevOps"
   */
  @Column(name = "position", length = 100)
  private String position;

  @Column(name = "joined_at", nullable = false)
  @Builder.Default
  private LocalDateTime joinedAt = LocalDateTime.now();

  /**
   * Check if this member is the project leader (Team Leader role).
   */
  public boolean isLeader() {
    return projectRole == ProjectRole.LEADER;
  }
}
