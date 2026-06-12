# System Config & Redis Caching

Tài liệu này mô tả chi tiết kiến trúc và cách thức hoạt động của module System Config trong IFMS, đặc biệt là cơ chế caching sử dụng Redis hỗ trợ đọc/ghi hiệu suất cao.

---

## 1. Tổng quan System Config

`SystemConfig` là bảng cấu hình dạng **Key-Value**, được thiết kế để lưu trữ các tham số hệ thống có thể thay đổi trong lúc vận hành mà không cần phải restart server (VD: các hạn mức giao dịch, bảo trì hệ thống, thời gian chờ OTP...).

**Cấu trúc dữ liệu:**
- `key` (String, PK): Định danh config, thường viết hoa (e.g. `WITHDRAW_LIMIT_EMPLOYEE`).
- `value` (String): Giá trị lưu dưới dạng chuỗi (sẽ được parse sang số, boolean,... thông qua các helper methods).
- `description` (String): Mô tả ý nghĩa của tham số cho người quản trị.

---

## 2. Kiến trúc Redis Caching

Các cầu hình hệ thống (`SystemConfig`) được đọc (Read) vô cùng thường xuyên nhưng lại rất hiếm khi được thay đổi (Write/Update). Việc truy vấn trực tiếp vào DB Database cho mỗi request là không hiệu quả. Vì vậy, IFMS áp dụng **Redis Caching** cho toàn bộ module này.

### 2.1 Cấu hình TTL (Time-To-Live)

Mặc định, Redis Cache trong dự án được cấu hình với TTL là **1 giờ** (chỉ số chuẩn cho JWT, v.v.). Tuy nhiên, do tính chất của `system_configs` là ít thay đổi, dự án áp dụng chiến lược overriding TTL để đẩy thời gian hết hạn lên tới **24 giờ**.

Trong `application.yml`:
```yaml
app:
  cache:
    system-config-ttl-ms: ${SYSTEM_CONFIG_CACHE_TTL_MS:86400000}  # 24h — configs change rarely
```

Trong `RedisConfig.java`, một config builder riêng được cấu hình cho cache-name `"system_configs"`:
```java
return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(cacheConfig)
        // Set TTL riêng biệt 24h
        .withCacheConfiguration("system_configs", cacheConfig.entryTtl(Duration.ofMillis(systemConfigCacheTtlMs)))
        .transactionAware()
        .build();
```

---

## 3. Hoạt động của SystemConfigService

Service layer `SystemConfigService` đã đóng gói hoàn toàn việc tương tác với DB và Redis, các client (bên thứ ba) không cần lo lắng về việc khi nào nên cache hay xoá cache.

### a. `get(key)` → Đọc cấu hình
Sử dụng **`@Cacheable(cacheNames = "system_configs", key = "#key")`**.
- **Lần đọc đầu tiên:** Cache Miss → Truy vấn từ DB (`findById`) → Kết quả được lưu vào Redis → Trả về kết quả.
- **Lần đọc tiếp theo (trong vòng 24h):** Cache Hit → Trả về dữ liệu từ Memory (Redis) tốc độ rất nhanh, không trigger Query vào Database.

### b. `set(key, value)` & `update(key, value)` → Ghi/Cập nhật cấu hình
Sử dụng **`@CachePut(cacheNames = "system_configs", key = "#key")`**.
- Ghi dữ liệu vào Database (`Repository.save`).
- **Update ngay vào Cache** (`CachePut`). Khi Admin thay đổi hạn mức, các transaction diễn ra ngay sau đó sẽ lập tức được áp dụng giá trị mới trực tiếp từ Cache. Không cần đợi qua 24H hay phải chờ expire.

### c. `evict(key)` & `evictAll()` → Dọn dẹp cache chủ động
Sử dụng **`@CacheEvict`**. Dọn dẹp thủ công cache memory thông qua REST endpoint khi quản trị viên thực hiện thao tác bulk insert DB (thay vì qua API), tránh data drift (dữ liệu sai lệch giữa DB và Cache).

---

## 4. Helper API trong Code

Thay vì phải gọi `get()` và ép kiểu liên tục, service cung cấp sẵn các Wrapper Methods để dễ dàng parse String từ config:
- `getAsInt(String key, int defaultValue)`
- `getAsLong(String key, long defaultValue)`
- `getAsBoolean(String key, boolean defaultValue)`
- `getAsBigDecimal(String key, BigDecimal defaultValue)`

**Ví dụ, lấy hạn mức auto-approve rút tiền thông qua role:**
```java
String configKey = "WITHDRAW_LIMIT_" + user.getRole().getName().toUpperCase();
// Helper tự động gọi get(configKey) → Tự load qua cache ở layer dưới
BigDecimal autoApproveLimit = systemConfigService.getAsBigDecimal(configKey, BigDecimal.ZERO);
```

---

## 5. REST API cho Admin

Module `config` support 6 API Endpoint cho việc quản trị (Yêu cầu quyền hạn truy cập `SYSTEM_CONFIG_MANAGE`):

| HTTP Method | Endpoint | Use Case | Caching Mechanism |
|---|---|---|---|
| GET | `/api/v1/system-configs` | Danh sách cấu hình hệ thống | Bypass Cache, query all từ Db |
| GET | `/api/v1/system-configs/{key}` | Chi tiết một cấu hình | `@Cacheable`, return qua cache |
| PUT | `/api/v1/system-configs/{key}` | Sửa giá trị cấu hình theo Key | `@CachePut`, update đồng thời |
| POST | `/api/v1/system-configs/{key}` | Tạo mới/Upsert cấu hình | `@CachePut`, db & cache |
| DELETE| `/api/v1/system-configs/{key}/cache` | Xoá cache cho 1 key | `@CacheEvict` cho specific key |
| DELETE| `/api/v1/system-configs/cache` | Xoá toàn bộ cache | `@CacheEvict` (allEntries) |

### Use Case
Khi quản trị viên thay đổi cấu hình rút tiền từ Manager: `PUT /api/v1/system-configs/WITHDRAW_LIMIT_MANAGER`, giá trị trong request là 10.000.000. Service sẽ ghi data thẳng vào H2 Postgres sau đó gọi Redis set value cho khóa `system_configs::WITHDRAW_LIMIT_MANAGER`. Những User Manager thao tác đặt lệnh Rút Tiền ở transaction ngay kế tiếp sẽ tự động bị rule kiểm tra 10Tr, mà không một Query DB nào được sinh ra.
