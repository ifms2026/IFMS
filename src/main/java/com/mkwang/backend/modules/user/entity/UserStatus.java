package com.mkwang.backend.modules.user.entity;

/**
 * Enum representing the status of a user account.
 */
public enum UserStatus {
  ACTIVE, // Tài khoản đang hoạt động bình thường
  LOCKED, // Tài khoản bị khóa
  PENDING // Tài khoản đang chờ kích hoạt
}
