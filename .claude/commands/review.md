# Review code against IFMS conventions

Review the files specified (or all staged/modified files if none specified).

**Scope:** $ARGUMENTS  _(if empty, review all files shown by `git diff --name-only HEAD`)_

## Checklist

Go through each Java file in scope and verify the following. Report every violation with file path, line number, and a short fix suggestion.

### 1. Controller layer
- [ ] Every public endpoint returns `ResponseEntity<ApiResponse<T>>` — no raw objects or void
- [ ] `@Tag`, `@Operation` present on controller class / methods
- [ ] `@PreAuthorize` present on all non-public endpoints
- [ ] Request bodies annotated with `@Valid`

### 2. DTO layer
- [ ] All field names are **camelCase** (no snake_case, UPPER_CASE, or PascalCase fields)
- [ ] Request DTOs have Bean Validation annotations where appropriate
- [ ] Response DTOs have `@Data @Builder @NoArgsConstructor @AllArgsConstructor`

### 3. Service layer
- [ ] `@Transactional` declared on ServiceImpl (class or method level)
- [ ] No raw `RuntimeException`, `IllegalArgumentException`, `IllegalStateException`, etc. — only subclasses of `BaseException`
- [ ] Business validation is in entity methods, not duplicated in service

### 4. Entity layer
- [ ] Extends `BaseEntity` (unless append-only: AuditLog, LedgerEntry, RequestHistory)
- [ ] Append-only entities have no `@Setter` and all columns `updatable = false`
- [ ] Money fields use `BigDecimal` with `precision=19, scale=2`
- [ ] Enums use `@Enumerated(EnumType.STRING)`

### 5. Flyway
- [ ] Any schema change (new column/table/index/constraint) has a corresponding new migration file in `src/main/resources/db/migration/`
- [ ] No existing migration file was modified

### 6. Security
- [ ] No hardcoded credentials, secrets, or tokens
- [ ] No SQL built by string concatenation (use JPA / `@Query` with parameters)

---

After the checklist, provide a **summary section**:
- **Violations found:** list each one
- **Looks good:** list areas that are clean
- **Suggested fixes:** concrete code snippets for any violation
