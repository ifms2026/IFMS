# Business Code Generation — IFMS

Tất cả business codes được sinh qua `BusinessCodeGenerator` (Strategy pattern + DB sequences).

## Code Format Table

| Type | Format | Example | Sequence |
|------|--------|---------|---------|
| EMPLOYEE | `MK{SEQ:03d}` | MK011 | `seq_employee_code` (starts 11) |
| DEPARTMENT | `{NORMALIZED_CODE}` | IT, FIN, HR | — (input param) |
| PROJECT | `PRJ-{SLUG}-{YYYY}-{SEQ:03d}` | PRJ-ERP-2026-001 | `seq_project_code` |
| PHASE | `PH-{SLUG}-{SEQ:02d}` | PH-UIUX-01 | `seq_phase_code` |
| REQUEST | `REQ-{DEPT}-{MMYY}-{SEQ:03d}` | REQ-IT-0426-001 | `seq_request_code` |
| TRANSACTION | `TXN-{8 hex}` | TXN-8829145A | — (random) |
| PERIOD | `PR-{YYYY}-{MM}` | PR-2026-04 | — (derived) |
| PAYSLIP | `PSL-{EMP_CODE}-{MMYY}` | PSL-MK001-0426 | — (derived) |

---

## Generation Architecture

### BusinessCodeType Enum

Mỗi type là 1 enum constant implement abstract `format()` method:

```java
EMPLOYEE("seq_employee_code") {
    @Override
    public String format(long seq, CodeFormatUtils utils, String... params) {
        String prefix = (params.length > 0) ? params[0] : "MK";
        return prefix + padLeft(seq, 3);
    }
},

PROJECT("seq_project_code") {
    @Override
    public String format(long seq, CodeFormatUtils utils, String... params) {
        String slug = (params.length > 0) ? params[0] : "GEN";
        return "PRJ-" + slug + "-" + LocalDate.now().getYear() + "-" + padLeft(seq, 3);
    }
}
```

### SequenceService

Lấy `nextval()` từ PostgreSQL sequences:

```java
@Service
public class SequenceService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long getNextValue(BusinessCodeType codeType) {
        String sql = SQL_CACHE.get(codeType);
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
```

**Pre-cache:** SQL strings được cache sẵn → zero allocation per call.

### CodeFormatUtils

Utility singleton cho:

```java
@Component
public class CodeFormatUtils {
    // Sanitize slug: uppercase + strip non-alphanumeric
    public String sanitizeSlug(String input, int maxLen)
    
    // Generate random hex string
    public String randomHex(int length)
    
    // Zero-pad number
    public static String padLeft(long val, int width)
}
```

---

## Usage Example

```java
// Tạo employee code MK011
String empCode = businessCodeGenerator.generate(BusinessCodeType.EMPLOYEE);

// Tạo project code PRJ-ERP-2026-001
String projectCode = businessCodeGenerator.generate(
    BusinessCodeType.PROJECT, 
    "ERP"  // slug param
);

// Tạo request code REQ-IT-0426-001
String requestCode = businessCodeGenerator.generate(
    BusinessCodeType.REQUEST,
    "IT"   // department code param
);
```

---

## Database Sequences

PostgreSQL sequences (created via Flyway migrations):

```sql
CREATE SEQUENCE seq_employee_code START WITH 11 INCREMENT BY 1;
CREATE SEQUENCE seq_project_code START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_phase_code START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_request_code START WITH 1 INCREMENT BY 1;
```

**Note:** `seq_employee_code` starts 11 (V2 migration), restarted V11 (stale fix).
