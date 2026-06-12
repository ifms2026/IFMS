package com.mkwang.backend.modules.project.entity;

/**
 * Enum representing the authorization level of a member within a project.
 * Used for permission logic (e.g. who can approve requests, manage budget).
 *
 * NOTE: This is NOT the display position (e.g. "Backend Dev").
 * Display position is stored in ProjectMember.position (free-text String).
 */
public enum ProjectRole {
    LEADER,  // Team Leader — toàn quyền duyệt/chia budget trong dự án
    MEMBER   // Thành viên — chỉ tạo Request chi tiêu
}

