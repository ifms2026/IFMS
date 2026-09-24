# Entity Conventions — IFMS

## Base Entity

Tất cả entities phải extend `BaseEntity` (MappedSuperclass tại `common/base/`):

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false, updatable = false)
    private Long createdBy;  // userId

    @Column(nullable = false)
    private Long updatedBy;  // userId
}
```

---

## Append-Only Entities

Các entities **không bao giờ UPDATE/DELETE** (audit trail, ledger):
- `AuditLog`
- `LedgerEntry`
- `RequestHistory`

Không extend `BaseEntity`, không có `@Setter`, tất cả columns `updatable = false`:

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EntryType type;  // DEBIT / CREDIT

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // ... other fields, all updatable = false
}
```

---

## Money Field Convention

Tiền luôn dùng `BigDecimal` với precision+scale rõ ràng:

```java
@Column(nullable = false, precision = 19, scale = 2)
private BigDecimal balance;  // 19 tổng chữ số, 2 sau dấu phẩy (tối đa ~$999 tỷ)
```

---

## Pessimistic Locking

Primary strategy: `findByXForUpdate()` (Pessimistic Write Lock)

```java
@Query("SELECT w FROM Wallet w WHERE w.id = :id FOR UPDATE")
Wallet findByIdForUpdate(Long id);
```

Safety net: `@Version` (Optimistic Locking) để detect concurrent modifications.

---

## Enum Mapping

Dùng `@Enumerated(EnumType.STRING)` để store enum name (dễ refactor, human-readable trong DB):

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private RequestStatus status;
```

---

## JSONB Fields

PostgreSQL JSONB columns dùng `@JdbcTypeCode(SqlTypes.JSON)`:

```java
@Column(columnDefinition = "jsonb", nullable = false)
@JdbcTypeCode(SqlTypes.JSON)
private Map<String, Object> metadata;
```

---

## Relationships

- ForeignKey columns luôn có index
- Nullable FK → `@Nullable`, required FK → `nullable = false`
- Cascade strategy phải rõ ràng (thường NONE hoặc PERSIST)

```java
@ManyToOne
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

---

## Business Logic in Entity

Validation logic thuộc entity (khỏi sử dụng ngoài):

```java
public void lock(BigDecimal amount) {
    if (amount.compareTo(getAvailableBalance()) > 0) {
        throw new InsufficientWalletBalanceException(amount, getAvailableBalance(), "lock");
    }
    this.lockedBalance = this.lockedBalance.add(amount);
}
```
