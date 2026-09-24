# Implementation Plan: Payroll Template & Import
> API Spec lines 2939–3012 + `confirm-overwrite` (dependency at line 3012)

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/accountant/payroll/template` | Download Excel template |
| POST | `/accountant/payroll/:periodId/import` | Import payroll from Excel |
| POST | `/accountant/payroll/:periodId/confirm-overwrite` | Clear payslips for re-import |

---

## 1. Maven dependency

`pom.xml` — add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
```

Apache POI is needed for both generating the template workbook and parsing the uploaded file.

---

## 2. New DTOs

### `dto/response/ImportPayrollEntryResponse.java`

```java
public record ImportPayrollEntryResponse(
    Long id,                     // null if row failed validation
    String payslipCode,          // null if row failed
    Long userId,                 // null if row failed
    String fullName,
    String employeeCode,
    BigDecimal baseSalary,
    BigDecimal bonus,
    BigDecimal allowance,
    BigDecimal deduction,
    BigDecimal advanceDeduct,    // always 0 at import; set later by auto-netting
    BigDecimal finalNetSalary,
    PayslipStatus status,        // null if row failed
    String importStatus,         // "ok" | "error"
    String importError           // null if "ok", message if "error"
) {}
```

### `dto/response/ImportPayrollErrorResponse.java`

```java
public record ImportPayrollErrorResponse(
    int row,
    String field,
    String message
) {}
```

### `dto/response/ImportPayrollResponse.java`

```java
public record ImportPayrollResponse(
    Long periodId,
    String periodCode,
    PayrollStatus status,
    int totalRows,
    int successCount,
    int errorCount,
    List<ImportPayrollEntryResponse> entries,
    List<ImportPayrollErrorResponse> errors,
    BigDecimal totalNetPayroll
) {}
```

---

## 3. `application.yml` additions

```yaml
app:
  payroll:
    max-import-size-mb: 10
```

Inject in `PayrollManagementServiceImpl` with:
```java
@Value("${app.payroll.max-import-size-mb}")
private int maxImportSizeMb;
```

---

## 4. Repository additions

### `PayslipRepository`

Add two methods:

```java
boolean existsByPeriodId(Long periodId);

@Modifying
@Query("DELETE FROM Payslip p WHERE p.period.id = :periodId")
void deleteAllByPeriodId(@Param("periodId") Long periodId);
```

`deleteAllByPeriodId` needs `@Modifying` + `@Transactional` (or inherit from service `@Transactional`).

### `ProfileService` — cross-domain lookup

`PayrollManagementServiceImpl` needs to look up `UserProfile` by `employeeCode`. Per convention, it must inject `ProfileService`, not `UserProfileRepository` directly.

`ProfileService` does not have this method yet. Add to the interface and impl:

**`ProfileService.java`** — add:
```java
/**
 * Find UserProfile by employeeCode.
 * Returns empty if no profile with that code exists.
 */
Optional<UserProfile> findProfileByEmployeeCode(String employeeCode);
```

**`ProfileServiceImpl.java`** — implement:
```java
@Override
public Optional<UserProfile> findProfileByEmployeeCode(String employeeCode) {
    return userProfileRepository.findByEmployeeCode(employeeCode);
}
```

Also add to `UserProfileRepository`:
```java
Optional<UserProfile> findByEmployeeCode(String employeeCode);
```

---

## 5. Service interface — `PayrollManagementService`

Add three method signatures:

```java
byte[] generatePayrollTemplate();

ImportPayrollResponse importPayroll(Long periodId, MultipartFile file);

void confirmOverwrite(Long periodId);
```

---

## 6. Service implementation — `PayrollManagementServiceImpl`

### New injections

```java
private final ProfileService profileService;

@Value("${app.payroll.max-import-size-mb}")
private int maxImportSizeMb;
```

### 6a. `generatePayrollTemplate()`

```java
@Override
@PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
public byte[] generatePayrollTemplate() {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
        Sheet sheet = workbook.createSheet("Payroll");

        // Bold header style
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        String[] columns = {"employeeCode", "employeeName", "baseSalary", "bonus", "allowance", "deduction"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 6000);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    } catch (IOException e) {
        throw new InternalSystemException("Failed to generate payroll template: " + e.getMessage());
    }
}
```

### 6b. `importPayroll(Long periodId, MultipartFile file)`

```java
@Override
@Transactional
@PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
public ImportPayrollResponse importPayroll(Long periodId, MultipartFile file) {
    // 1. Load period
    PayrollPeriod period = payrollPeriodRepository.findById(periodId)
        .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod", "id", periodId));

    // 2. Period must be DRAFT
    if (!period.isEditable()) {
        throw new BadRequestException("Cannot import into a payroll period with status=" + period.getStatus());
    }

    // 3. Conflict: payslips already exist — caller must confirm-overwrite first
    if (payslipRepository.existsByPeriodId(periodId)) {
        throw new ConflictException("Period " + period.getPeriodCode() + " already has payslip data. " +
            "Call POST /accountant/payroll/" + periodId + "/confirm-overwrite to overwrite.");
    }

    // 4. Validate file
    validateImportFile(file);

    // 5. Parse workbook
    List<ImportPayrollEntryResponse> entries = new ArrayList<>();
    List<ImportPayrollErrorResponse> errors = new ArrayList<>();
    List<Payslip> validPayslips = new ArrayList<>();
    int totalRows = 0;

    try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
        Sheet sheet = workbook.getSheetAt(0);
        // Row 0 = header, data starts at row 1
        for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            totalRows++;

            ParsedPayrollRow parsed = parseRow(row, rowIdx + 1); // 1-based for error messages
            errors.addAll(parsed.errors());

            if (parsed.errors().isEmpty()) {
                // Look up employee by code
                Optional<UserProfile> profileOpt =
                    profileService.findProfileByEmployeeCode(parsed.employeeCode());

                if (profileOpt.isEmpty()) {
                    ImportPayrollErrorResponse err = new ImportPayrollErrorResponse(
                        rowIdx + 1, "employeeCode",
                        "Employee not found in system (row " + (rowIdx + 1) + ")"
                    );
                    errors.add(err);
                    entries.add(buildErrorEntry(parsed, err.message()));
                } else {
                    UserProfile profile = profileOpt.get();
                    BigDecimal finalNet = parsed.baseSalary()
                        .add(parsed.bonus())
                        .add(parsed.allowance())
                        .subtract(parsed.deduction());

                    String payslipCode = businessCodeGenerator.generate(
                        BusinessCodeType.PAYSLIP,
                        parsed.employeeCode(),
                        String.valueOf(period.getMonth()),
                        String.valueOf(period.getYear())
                    );

                    Payslip payslip = Payslip.builder()
                        .payslipCode(payslipCode)
                        .period(period)
                        .user(profile.getUser())
                        .baseSalary(parsed.baseSalary())
                        .bonus(parsed.bonus())
                        .allowance(parsed.allowance())
                        .deduction(parsed.deduction())
                        .advanceDeduct(BigDecimal.ZERO)
                        .finalNetSalary(finalNet)
                        .status(PayslipStatus.DRAFT)
                        .build();

                    validPayslips.add(payslip);

                    entries.add(new ImportPayrollEntryResponse(
                        null, payslipCode,
                        profile.getUser().getId(), profile.getUser().getFullName(),
                        parsed.employeeCode(),
                        parsed.baseSalary(), parsed.bonus(), parsed.allowance(),
                        parsed.deduction(), BigDecimal.ZERO, finalNet,
                        PayslipStatus.DRAFT, "ok", null
                    ));
                }
            } else {
                entries.add(buildErrorEntry(parsed, parsed.errors().get(0).message()));
            }
        }
    } catch (IOException | InvalidFormatException e) {
        throw new BadRequestException("Cannot read file: " + e.getMessage());
    }

    // 6. Save valid payslips and update ids in entries list
    List<Payslip> saved = payslipRepository.saveAll(validPayslips);

    // Update the entry records with persisted ids
    List<ImportPayrollEntryResponse> finalEntries = mergeSavedIds(entries, saved);

    BigDecimal totalNet = saved.stream()
        .map(Payslip::getFinalNetSalary)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    int successCount = saved.size();
    int errorCount = totalRows - successCount;

    return new ImportPayrollResponse(
        period.getId(), period.getPeriodCode(), period.getStatus(),
        totalRows, successCount, errorCount,
        finalEntries, errors, totalNet
    );
}
```

**Private helpers in `PayrollManagementServiceImpl`:**

```java
private void validateImportFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
        throw new BadRequestException("Import file must not be empty");
    }
    String filename = StringUtils.hasText(file.getOriginalFilename())
        ? file.getOriginalFilename().toLowerCase() : "";
    if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
        throw new BadRequestException("Only .xlsx and .xls files are accepted");
    }
    long maxBytes = (long) maxImportSizeMb * 1024 * 1024;
    if (file.getSize() > maxBytes) {
        throw new BadRequestException("File size exceeds " + maxImportSizeMb + " MB limit");
    }
}

// Record for a parsed (but not yet validated against DB) row
private record ParsedPayrollRow(
    String employeeCode,
    String employeeName,
    BigDecimal baseSalary,
    BigDecimal bonus,
    BigDecimal allowance,
    BigDecimal deduction,
    List<ImportPayrollErrorResponse> errors
) {}

private ParsedPayrollRow parseRow(Row row, int displayRowNum) {
    List<ImportPayrollErrorResponse> rowErrors = new ArrayList<>();

    String employeeCode = readString(row, 0);
    String employeeName = readString(row, 1);
    BigDecimal baseSalary = readPositiveDecimal(row, 2, "baseSalary", displayRowNum, rowErrors);
    BigDecimal bonus      = readNonNegativeDecimal(row, 3, "bonus", displayRowNum, rowErrors);
    BigDecimal allowance  = readNonNegativeDecimal(row, 4, "allowance", displayRowNum, rowErrors);
    BigDecimal deduction  = readNonNegativeDecimal(row, 5, "deduction", displayRowNum, rowErrors);

    if (!StringUtils.hasText(employeeCode)) {
        rowErrors.add(new ImportPayrollErrorResponse(displayRowNum, "employeeCode",
            "employeeCode is required (row " + displayRowNum + ")"));
    }

    return new ParsedPayrollRow(employeeCode, employeeName,
        baseSalary, bonus, allowance, deduction, rowErrors);
}

private String readString(Row row, int col) {
    Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    if (cell == null) return null;
    cell.setCellType(CellType.STRING);
    return cell.getStringCellValue().trim();
}

private BigDecimal readPositiveDecimal(Row row, int col, String field,
                                       int displayRow, List<ImportPayrollErrorResponse> errs) {
    try {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            errs.add(new ImportPayrollErrorResponse(displayRow, field, field + " is required (row " + displayRow + ")"));
            return BigDecimal.ZERO;
        }
        BigDecimal val = BigDecimal.valueOf(cell.getNumericCellValue());
        if (val.compareTo(BigDecimal.ZERO) <= 0) {
            errs.add(new ImportPayrollErrorResponse(displayRow, field, field + " must be a positive number (row " + displayRow + ")"));
            return BigDecimal.ZERO;
        }
        return val;
    } catch (Exception e) {
        errs.add(new ImportPayrollErrorResponse(displayRow, field, field + " must be a valid number (row " + displayRow + ")"));
        return BigDecimal.ZERO;
    }
}

private BigDecimal readNonNegativeDecimal(Row row, int col, String field,
                                          int displayRow, List<ImportPayrollErrorResponse> errs) {
    try {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return BigDecimal.ZERO;
        BigDecimal val = BigDecimal.valueOf(cell.getNumericCellValue());
        if (val.compareTo(BigDecimal.ZERO) < 0) {
            errs.add(new ImportPayrollErrorResponse(displayRow, field, field + " must not be negative (row " + displayRow + ")"));
            return BigDecimal.ZERO;
        }
        return val;
    } catch (Exception e) {
        errs.add(new ImportPayrollErrorResponse(displayRow, field, field + " must be a valid number (row " + displayRow + ")"));
        return BigDecimal.ZERO;
    }
}

private ImportPayrollEntryResponse buildErrorEntry(ParsedPayrollRow parsed, String error) {
    BigDecimal finalNet = coalesceZero(parsed.baseSalary())
        .add(coalesceZero(parsed.bonus()))
        .add(coalesceZero(parsed.allowance()))
        .subtract(coalesceZero(parsed.deduction()));

    return new ImportPayrollEntryResponse(
        null, null, null, parsed.employeeName(), parsed.employeeCode(),
        coalesceZero(parsed.baseSalary()), coalesceZero(parsed.bonus()),
        coalesceZero(parsed.allowance()), coalesceZero(parsed.deduction()),
        BigDecimal.ZERO, finalNet, null, "error", error
    );
}

private BigDecimal coalesceZero(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
}

// After saveAll, back-fill the persisted ids into the "ok" entries
private List<ImportPayrollEntryResponse> mergeSavedIds(
        List<ImportPayrollEntryResponse> entries,
        List<Payslip> saved) {
    // Build a lookup: payslipCode → saved Payslip
    Map<String, Payslip> byCode = saved.stream()
        .collect(Collectors.toMap(Payslip::getPayslipCode, p -> p));

    return entries.stream().map(e -> {
        if ("ok".equals(e.importStatus()) && byCode.containsKey(e.payslipCode())) {
            Payslip p = byCode.get(e.payslipCode());
            return new ImportPayrollEntryResponse(
                p.getId(), e.payslipCode(), e.userId(), e.fullName(), e.employeeCode(),
                e.baseSalary(), e.bonus(), e.allowance(), e.deduction(),
                e.advanceDeduct(), e.finalNetSalary(), e.status(),
                e.importStatus(), e.importError()
            );
        }
        return e;
    }).toList();
}
```

### 6c. `confirmOverwrite(Long periodId)`

```java
@Override
@Transactional
@PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
public void confirmOverwrite(Long periodId) {
    PayrollPeriod period = payrollPeriodRepository.findById(periodId)
        .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod", "id", periodId));

    if (!period.isEditable()) {
        throw new BadRequestException("Cannot overwrite payroll period with status=" + period.getStatus());
    }

    payslipRepository.deleteAllByPeriodId(periodId);
}
```

---

## 7. Controller additions — `AccountantPayrollController`

### `GET /accountant/payroll/template`

> **Note:** Returns raw `byte[]` with file headers — NOT wrapped in `ApiResponse`. This is the only exception to the response-wrapping convention.

```java
@GetMapping("/template")
@Operation(summary = "Download payroll Excel template",
           description = "Returns an .xlsx file with header columns: employeeCode, employeeName, baseSalary, bonus, allowance, deduction.")
public ResponseEntity<byte[]> downloadPayrollTemplate() {
    byte[] template = payrollManagementService.generatePayrollTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    headers.setContentDispositionFormData("attachment", "payroll_template.xlsx");
    return ResponseEntity.ok().headers(headers).body(template);
}
```

Add imports: `org.springframework.http.HttpHeaders`, `org.springframework.http.MediaType`.

### `POST /accountant/payroll/{periodId}/import`

```java
@PostMapping("/{periodId}/import")
@Operation(summary = "Import payroll from Excel",
           description = "Parses .xlsx/.xls, maps employeeCode → user, creates payslip records. Returns per-row results.")
public ResponseEntity<ApiResponse<ImportPayrollResponse>> importPayroll(
        @PathVariable Long periodId,
        @RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(ApiResponse.success(
        payrollManagementService.importPayroll(periodId, file)
    ));
}
```

Add import: `org.springframework.web.multipart.MultipartFile`.

### `POST /accountant/payroll/{periodId}/confirm-overwrite`

```java
@PostMapping("/{periodId}/confirm-overwrite")
@Operation(summary = "Clear payslips for re-import",
           description = "Deletes all payslips in this period. Call after receiving 409 from import.")
public ResponseEntity<ApiResponse<Map<String, String>>> confirmOverwrite(
        @PathVariable Long periodId) {
    payrollManagementService.confirmOverwrite(periodId);
    return ResponseEntity.ok(ApiResponse.success(
        Map.of("message", "Previous payroll data cleared. Ready for re-import.")
    ));
}
```

---

## 8. Files summary

### New files

| File | Purpose |
|------|---------|
| `accounting/dto/response/ImportPayrollEntryResponse.java` | Per-row import result (ok + error rows) |
| `accounting/dto/response/ImportPayrollErrorResponse.java` | Validation error detail per row |
| `accounting/dto/response/ImportPayrollResponse.java` | Top-level import result |

### Modified files

| File | Change |
|------|--------|
| `pom.xml` | Add `poi-ooxml 5.3.0` |
| `src/main/resources/application.yml` | Add `app.payroll.max-import-size-mb: 10` |
| `accounting/service/PayrollManagementService.java` | Add `generatePayrollTemplate`, `importPayroll`, `confirmOverwrite` |
| `accounting/service/PayrollManagementServiceImpl.java` | Implement 3 methods + helpers; add `ProfileService` injection + `@Value` |
| `accounting/repository/PayslipRepository.java` | Add `existsByPeriodId`, `deleteAllByPeriodId` |
| `accounting/controller/AccountantPayrollController.java` | Add 3 new endpoints |
| `profile/service/ProfileService.java` | Add `findProfileByEmployeeCode(String)` |
| `profile/service/ProfileServiceImpl.java` | Implement `findProfileByEmployeeCode` |
| `profile/repository/UserProfileRepository.java` | Add `Optional<UserProfile> findByEmployeeCode(String)` |

### No Flyway migration needed
No schema changes required — `payroll_periods` and `payslips` tables already exist in V1.

---

## 9. Edge cases & invariants

| Case | Handling |
|------|---------|
| Period not DRAFT | `BadRequestException` |
| Period not found | `ResourceNotFoundException` |
| Payslips already exist on import | `ConflictException` → caller calls confirm-overwrite |
| Row missing `employeeCode` | Validation error, row skipped |
| Row `baseSalary ≤ 0` | Validation error, row skipped |
| `employeeCode` not in `user_profiles` | Import error on that row only, other rows proceed |
| File not `.xlsx`/`.xls` | `BadRequestException` before parsing |
| File size > max | `BadRequestException` before parsing |
| IOException from POI | `BadRequestException("Cannot read file: ...")` |
| Error rows | Not counted in `totalNetPayroll`, no `Payslip` record created |
| `advanceDeduct` | Always `0` at import time — populated later by `POST auto-netting` |
