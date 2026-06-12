-- =============================================================
-- V5: Stateless JWT — Drop jwt_tokens table
-- =============================================================
-- Hệ thống chuyển sang kiến trúc Stateless JWT.
-- Token không còn được lưu vào DB nữa.
-- Invalidation dùng tokenVersion (users.token_version) + Redis cache.

DROP TABLE IF EXISTS jwt_tokens;
