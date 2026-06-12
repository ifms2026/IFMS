package com.mkwang.backend.modules.audit.context;

/**
 * AuditContextHolder — ThreadLocal lưu trữ ngữ cảnh audit cho mỗi request.
 * <p>
 * Lưu 2 thông tin:
 * <ul>
 *   <li>{@code traceId} — UUID gom nhóm tất cả audit log trong 1 HTTP request/transaction</li>
 *   <li>{@code actorId} — ID người đang thao tác (từ JWT)</li>
 * </ul>
 * CRITICAL: Phải gọi {@code clear()} trong {@code finally} block sau mỗi request
 * để tránh memory leak trong Tomcat thread pool.
 */
public final class AuditContextHolder {

    private record AuditContext(String traceId, Long actorId) {}

    private static final ThreadLocal<AuditContext> HOLDER = new ThreadLocal<>();

    private AuditContextHolder() {}

    /**
     * Set context cho request hiện tại.
     *
     * @param traceId UUID gom nhóm log của 1 request (được generate bởi AuditContextFilter)
     * @param actorId ID người thao tác (null nếu anonymous/system)
     */
    /**
     * Cập nhật actorId sau khi xác thực người dùng thành công.
     * Dùng cho các trường hợp user đã biết trong service layer nhưng chưa có JWT
     * (ví dụ: login endpoint — AuditContextFilter không thể đọc được actorId từ JwtFilter).
     * Giữ nguyên traceId đã được AuditContextFilter set.
     *
     * @param actorId ID người thao tác vừa xác thực được
     */
    public static void setActorId(Long actorId) {
        AuditContext current = HOLDER.get();
        if (current != null) {
            HOLDER.set(new AuditContext(current.traceId(), actorId));
        }
        // Nếu HOLDER chưa set (không có HTTP request context) → không làm gì
    }

    public static void set(String traceId, Long actorId) {
        HOLDER.set(new AuditContext(traceId, actorId));
    }

    public static String getTraceId() {
        AuditContext ctx = HOLDER.get();
        return ctx != null ? ctx.traceId() : null;
    }

    public static Long getActorId() {
        AuditContext ctx = HOLDER.get();
        return ctx != null ? ctx.actorId() : null;
    }

    /**
     * Dọn dẹp ThreadLocal — BẮT BUỘC gọi trong finally block sau mỗi request.
     */
    public static void clear() {
        HOLDER.remove();
    }
}
