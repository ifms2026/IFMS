package com.mkwang.backend.modules.project.entity;

/**
 * Enum representing the status of a project.
 */
public enum ProjectStatus {
  PLANNING, // Đang lên kế hoạch
  ACTIVE, // Đang thực hiện
  PAUSED, // Tạm dừng (Chặn chi tiêu)
  CLOSED // Đã đóng (Thu hồi vốn dư)
}
