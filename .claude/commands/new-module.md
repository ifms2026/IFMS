# Scaffold a new IFMS module

Scaffold a complete new module for the IFMS backend.

**Module name:** $ARGUMENTS

## Instructions

Create a full module under `src/main/java/com/mkwang/backend/modules/<moduleName>/` with the following structure:

```
modules/<moduleName>/
├── entity/
│   └── <Entity>.java
├── repository/
│   └── <Entity>Repository.java
├── service/
│   ├── <Entity>Service.java          (interface)
│   └── <Entity>ServiceImpl.java
├── controller/
│   └── <Entity>Controller.java
└── dto/
    ├── request/
    │   └── Create<Entity>Request.java
    └── response/
        └── <Entity>Response.java
```

### Rules to follow

**Entity:**
- Extend `com.mkwang.backend.common.base.BaseEntity`
- Use `@Entity @Table(name = "<table_name>")` — table name in snake_case plural
- Money fields: `BigDecimal` with `@Column(precision = 19, scale = 2)`
- Enums as `@Enumerated(EnumType.STRING)`
- Include relevant domain methods for business validation (not in service)

**Repository:**
- Extend `JpaRepository<Entity, Long>`
- If entity needs pessimistic locking: add `@Lock(LockModeType.PESSIMISTIC_WRITE)` query named `findByIdForUpdate`

**Service interface:**
- Define only the public operations needed

**ServiceImpl:**
- `@Service @RequiredArgsConstructor @Transactional`
- Only throw exceptions that extend `BaseException`:
  - `BadRequestException` — 400
  - `ResourceNotFoundException` — 404
  - `UnauthorizedException` — 401

**Controller:**
- `@RestController @RequestMapping("/api/v1/<resource>") @RequiredArgsConstructor`
- Add `@Tag(name = "...", description = "...")` for Swagger
- Every endpoint returns `ResponseEntity<ApiResponse<T>>`
- Secure endpoints with `@PreAuthorize("hasAuthority('PERMISSION_NAME')")`
- Add `@Operation(summary = "...")` on each method

**DTOs:**
- All field names in **camelCase**
- Request DTOs: use Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, etc.)
- Response DTOs: plain `@Data @Builder @NoArgsConstructor @AllArgsConstructor`

**Flyway migration:**
- Determine the next migration version by reading `src/main/resources/db/migration/` and finding the highest V number
- Create `src/main/resources/db/migration/V{N}__ADD_<TABLE_NAME>.sql` with the CREATE TABLE DDL matching the entity
- Use `IF NOT EXISTS` on the CREATE TABLE statement

After scaffolding, print a summary of every file created.
