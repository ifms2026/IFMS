# Task 2 — AI đọc chứng từ, tự điền form tạo yêu cầu

> Bản demo của **F1 · Trích xuất & đối chiếu chứng từ** trong [da2-improvement-request.md](da2-improvement-request.md) / [da2-task-breakdown.md](da2-task-breakdown.md) (F1-1, F1-3, F1-4, F1-7; phần đối chiếu F1-5 làm ở mức tối thiểu).
> Lập ngày 2026-10-10.

## Trạng thái (2026-10-10)

| ID | Trạng thái | Ghi chú |
|---|---|---|
| AI-1 → AI-7 | ✅ Đã triển khai | Backend `modules/ai` + migration V20; FE `requests/new`, `components/request/*`, `lib/api/ai.ts` |
| AI-0 | ✅ Có key | `GEMINI_API_KEY`, `GEMINI_MODEL=gemini-3.5-flash`, `GEMINI_FALLBACK_MODELS` trong `D:\IFMS\.env` (không commit) |
| AI-8 | ⏳ Chờ bộ ảnh thật | Đã chạy thử 3 ảnh mẫu; cần 15–30 ảnh hoá đơn thật có ghi sẵn kết quả đúng để đo cho báo cáo |

**Đã kiểm tra với provider `mock`:** đọc + cache theo hash, giới hạn lượt, chặn file sai định dạng, gợi ý hạng mục chỉ trong danh sách của phase (chọn phase trước hoặc sau khi upload), liên kết bản trích xuất khi tạo request, cờ `RECEIPT_AMOUNT_MISMATCH` và `RECEIPT_REUSED`, `extractionId` lạ không chặn tạo request; trên UI: tự điền, cộng tổng nhiều file, *Bỏ qua* file, giữ nguyên ô user đã sửa, quyết toán tự điền và khoá dự án / phase / hạng mục.
**Đã kiểm tra với Gemini thật (`gemini-3.5-flash`, 6–12 giây/ảnh)** trên 3 ảnh hoá đơn mẫu (biên lai Grab, hoá đơn nhà hàng, hoá đơn GTGT): đúng tổng tiền, ngày, người bán, MST, số hoá đơn, VAT và dòng hàng ở cả 3 ảnh; hạng mục đúng 2/2 ảnh có mục phù hợp, ảnh văn phòng phẩm để trống vì không có hạng mục tương ứng trong phase.

**Model dự phòng:** `GEMINI_FALLBACK_MODELS` — model chính trả 503/5xx, 429 hoặc 404 thì tự thử model kế tiếp; model thực sự dùng được lưu ở cột `document_extractions.model`. Lúc kiểm tra (10/10/2026), `gemini-flash-latest` đang quá tải (503) và `gemini-2.5-*` không còn cấp cho key mới (404).

**Chạy:**
- Demo offline: `AI_PROVIDER=mock` — mock chọn mẫu theo tên file (`grab…`, `nha-hang…`, `khach-san…`, còn lại là văn phòng phẩm).
- Gemini: đặt `GEMINI_API_KEY` trong `.env` (provider mặc định là `gemini`); thiếu key thì tính năng tự tắt, form nhập tay như cũ.

**Khác với kế hoạch ban đầu:**
- `AiUnavailableException` trả **502** thay vì 503, vì `api-client` của FE chuyển mọi 503 sang trang bảo trì.
- Component trạng thái file đặt tên `components/request/receipt-scan-status.tsx`.
- Với Quyết toán tạm ứng, form tải **mọi** phase của dự án (không chỉ ACTIVE), vì phase của khoản tạm ứng gốc có thể đã đóng.

---

## 0. Quyết định đã chốt

| # | Quyết định | Lý do |
|---|---|---|
| 1 | AI scan bật cho **Chi phí (EXPENSE)** và **Hoàn ứng (REIMBURSE)**. Tạm ứng không dùng | Hai loại này bắt buộc có chứng từ (`RequestServiceImpl` dòng 1099); tạm ứng là xin tiền trước khi chi |
| 2 | Nhà cung cấp: **Google Gemini API, free tier**; thêm chế độ **`mock`** để demo offline | Miễn phí, đọc được ảnh và PDF, trả JSON đúng schema |
| 3 | Gọi **đồng bộ** (chờ 2–8 giây, có spinner), timeout 20 giây | User đang đứng trên form; bỏ được hàng đợi + SSE so với thiết kế F1 gốc |
| 4 | AI **chỉ điền ô trống**, không ghi đè thứ user đã nhập | Theo ranh giới đã chốt ở F1 |
| 5 | Hạng mục chi phí: AI **chỉ được chọn trong danh sách có sẵn của phase**; backend kiểm tra lại id | Danh sách hạng mục là danh sách đóng (`phase_category_budgets`) |
| 6 | AI lỗi, hết lượt hay bị tắt thì form **vẫn nhập tay được** như hiện tại | AI chỉ rút ngắn thao tác, không phải điều kiện để tạo yêu cầu |

> Ở free tier, Google có thể dùng dữ liệu gửi lên để cải thiện sản phẩm. Chỉ demo bằng hoá đơn mẫu, không dùng chứng từ thật.

---

## 1. Kết quả sau khi triển khai

### 1.1 Trường nào tự điền khi upload ảnh

| Trường trên form | Chi phí (EXPENSE) | Hoàn ứng (REIMBURSE) | Lấy từ |
|---|---|---|---|
| Loại yêu cầu | 👤 user chọn | 👤 user chọn | — |
| Dự án | 👤 user chọn (chọn sẵn nếu user chỉ thuộc 1 dự án) | 🔗 **tự điền** theo khoản tạm ứng | request tạm ứng gốc |
| Phase | 👤 user chọn (chọn sẵn phase ACTIVE) | 🔗 **tự điền** theo khoản tạm ứng | request tạm ứng gốc |
| Khoản tạm ứng cần hoàn | — | 👤 user chọn (chọn sẵn nếu chỉ có 1 khoản) | — |
| Đính kèm chứng từ | 👤 user tải lên | 👤 user tải lên | — |
| **Số tiền (VND)** | 🤖 **tự điền** | 🤖 **tự điền** + cảnh báo nếu vượt số còn nợ | Tổng thanh toán (gồm VAT); nhiều file thì cộng lại |
| **Ngày chi tiêu** | 🤖 **tự điền** | 🤖 **tự điền** | Ngày hoá đơn; nhiều file thì lấy ngày muộn nhất |
| **Tiêu đề** | 🤖 **tự điền** | 🤖 **tự điền** | Tên người bán + nội dung chính |
| **Mô tả chi tiết** | 🤖 điền phần chứng từ · 👤 user thêm mục đích chi | 🤖 điền phần chứng từ · 👤 user thêm mục đích chi | Số hoá đơn, MST, dòng hàng, VAT |
| **Hạng mục chi phí** | 🤖 **tự chọn trong danh sách của phase** | 🔗 **tự điền** theo khoản tạm ứng | Mục 2 |

🤖 = AI điền · 🔗 = điền theo quy tắc từ dữ liệu có sẵn, không gọi AI · 👤 = user tự nhập

Ô do AI hoặc quy tắc điền có nhãn **"✨ AI gợi ý"** (hoặc "🔗 Theo tạm ứng"). Nhãn biến mất khi user sửa ô đó.

### 1.2 Ví dụ

User chọn **Chi phí**, dự án *ERP*, phase *PH-ERP-01*, rồi tải lên ảnh hoá đơn Grab 185.000 ₫ ngày 08/10/2026. Sau khoảng 3 giây, form tự điền:

- Số tiền: **185.000**
- Ngày chi tiêu: **08/10/2026**
- Tiêu đề: **Chi phí Grab – di chuyển**
- Hạng mục chi phí: **Travel & Accommodation** (đúng mục có sẵn trong danh sách của phase)
- Mô tả: `Hoá đơn GrabCar #A-8F2K… · 1 chuyến · 185.000 ₫` → user chỉ gõ thêm "Đi gặp khách hàng ABC".

---

## 2. AI chọn đúng hạng mục có sẵn như thế nào

Hạng mục là **danh sách đóng theo phase**: `GET /projects/{phaseId}` chỉ trả về các mục đã được Trưởng nhóm cấp ngân sách cho phase đó. Hiện seed có 4 mục hệ thống: *Travel & Accommodation*, *Equipment & Software*, *Meals & Entertainment*, *Outsourcing & Services*, cộng với các mục riêng của từng dự án. AI không được tự đặt tên. Có 4 lớp bảo đảm:

1. **Backend tự lấy danh sách**, không tin danh sách FE gửi lên: từ `phaseId`, gọi `ProjectQueryService` để lấy `{id, name, description}` của các mục thuộc phase (kiểm tra quyền như endpoint hiện có). `description` tiếng Việt (ví dụ "Công tác phí, di chuyển, khách sạn, vé máy bay") giúp AI khớp hoá đơn tiếng Việt với tên tiếng Anh.
2. **Ép bằng JSON schema:** trường `categoryId` trong `responseSchema` của Gemini là `enum` gồm đúng các id của phase, cộng thêm giá trị `"NONE"`. Model không thể trả về một id ngoài danh sách.
3. **Kiểm tra lại ở backend:** id trả về phải thuộc danh sách vừa tải. Nếu không thuộc, hoặc là `"NONE"`, thì trả `category: null` và form để trống cho user chọn.
4. **Phase chưa chọn lúc upload:** lần gọi đầu chỉ trích xuất các trường và lưu kèm `expenseSummary`. Khi user chọn phase, FE gọi `POST /ai/receipts/{extractionId}/category-suggestion`. Endpoint này chỉ gửi **văn bản** (tóm tắt + danh sách hạng mục), không gửi lại ảnh, nên nhanh và tốn ít lượt.

Với **Hoàn ứng**, hạng mục **không cần AI**: lấy đúng hạng mục của request tạm ứng gốc. Đây là cách chính xác nhất vì khoản tạm ứng vốn đã gắn với dự án, phase và hạng mục đó.

---

## 3. Luồng màn hình mới — `/requests/new`

Đổi thứ tự trường để phase được chọn **trước** khi tải ảnh. Nhờ đó gợi ý hạng mục có ngay trong lần gọi đầu.

```
1. Loại yêu cầu ── Tạm ứng ──► form như hiện tại (không có AI)
        │
        ├─ Chi phí ──► 2. Dự án → Phase (chọn sẵn nếu chỉ có 1)
        │
        └─ Hoàn ứng ─► 2. Khoản tạm ứng cần hoàn ──► 🔗 tự điền Dự án / Phase / Hạng mục
                              │
3. Tải chứng từ ──► mỗi file: chip "Đang đọc chứng từ…" → "Đã đọc: 185.000 ₫ · 08/10/2026 · Grab"
                              │
4. Các ô AI đã điền (nhãn ✨) ──► user kiểm tra, sửa nếu cần
5. Mô tả: user thêm mục đích chi
6. Gửi yêu cầu (kèm extractionId của từng file)
```

- **Nhiều file:** số tiền = tổng các file đọc được. Chip của từng file có nút **Bỏ qua** để loại file đó khỏi tổng.
- **AI không đọc được một trường** (ảnh mờ, thiếu tổng tiền) thì trường đó để trống, chip ghi "Không đọc được số tiền", user nhập tay.
- **`GET /ai/status` trả về `enabled: false`** thì ẩn toàn bộ phần AI; form giống hệt hiện nay.

---

## 4. API

### 4.1 `GET /api/v1/ai/status`
```json
{ "success": true, "data": { "enabled": true, "provider": "gemini", "remainingToday": 27 } }
```

### 4.2 `POST /api/v1/ai/receipts/extract` — multipart
| Part | Bắt buộc | Ghi chú |
|---|---|---|
| `file` | ✓ | `image/jpeg`, `image/png`, `image/webp`, `application/pdf`; ≤ 10 MB (giới hạn multipart hiện có) |
| `phaseId` | — | Có thì trả gợi ý hạng mục luôn |

```jsonc
{
  "success": true,
  "data": {
    "extractionId": 42,
    "cached": false,                       // true nếu cùng file (hash) đã đọc trước đó
    "fields": {
      "totalAmount": 185000,               // null nếu không đọc được
      "invoiceDate": "2026-10-08",
      "vendorName": "GrabCar",
      "vendorTaxCode": "0312650437",
      "invoiceNumber": "A-8F2K91",
      "vatAmount": 13704,
      "currency": "VND",
      "lineItems": [ { "description": "GrabCar 4 chỗ", "quantity": 1, "amount": 185000 } ]
    },
    "suggestion": {
      "title": "Chi phí Grab – di chuyển",
      "descriptionDraft": "Hoá đơn GrabCar #A-8F2K91 · MST 0312650437\n- GrabCar 4 chỗ: 185.000 ₫\nVAT: 13.704 ₫",
      "category": { "id": 1, "name": "Travel & Accommodation" }   // null nếu không chắc / chưa có phase
    },
    "warnings": [ "INVOICE_DATE_IN_FUTURE" ]                        // xem mục 5.3
  }
}
```

### 4.3 `POST /api/v1/ai/receipts/{extractionId}/category-suggestion`
Body: `{ "phaseId": 7 }` → `{ "category": { "id": 1, "name": "…" } | null }`. Chỉ người tạo bản trích xuất mới gọi được.

### 4.4 Sửa API hiện có
| API / DTO | Thay đổi |
|---|---|
| `AttachmentRequest` | Thêm `extractionId` (tuỳ chọn) để liên kết bản trích xuất với file khi tạo request |
| `AdvanceBalanceItem` (`GET /requests/my-advance-balances`) | Thêm `projectId`, `projectName`, `phaseId`, `phaseName`, `categoryId`, `categoryName` từ request tạm ứng gốc, dùng để tự điền khi Hoàn ứng |

---

## 5. Backend

### 5.1 Module mới `modules/ai`

```
modules/ai/
├── controller/AiReceiptController.java          # /ai/status, /ai/receipts/**
├── service/
│   ├── ReceiptExtractionService.java            # interface
│   ├── ReceiptExtractionServiceImpl.java        # cache, hạn mức, validate, lưu, gọi extractor
│   └── extractor/
│       ├── ReceiptExtractor.java                # interface: extract(bytes, mime, categories) / suggestCategory(summary, categories)
│       ├── GeminiReceiptExtractor.java          # RestClient → generateContent, responseSchema
│       └── MockReceiptExtractor.java            # trả dữ liệu mẫu theo tên file, cho demo offline
├── entity/DocumentExtraction.java
├── repository/DocumentExtractionRepository.java
├── mapper/ReceiptExtractionMapper.java          # @Component
└── dto/
    ├── request/CategorySuggestionRequest.java
    └── response/ReceiptExtractionResponse.java, AiStatusResponse.java, CategorySuggestionResponse.java
```

- **Phân quyền:** `@PreAuthorize("hasAuthority('REQUEST_CREATE')")` trên các method của `ReceiptExtractionServiceImpl`.
- **Gọi domain khác qua Service interface:** `ProjectQueryService` (thêm method `getPhaseCategoryOptionsWithDescription(user, phaseId)`) để lấy danh sách hạng mục. Không inject repository của module `project`.
- **Chọn extractor** bằng `app.ai.provider` (`gemini` | `mock`). Nếu `gemini` mà không có API key thì `enabled = false`, app vẫn chạy bình thường.

### 5.2 Gọi Gemini

- `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`, header `x-goog-api-key`.
- `contents.parts`: `inline_data` (mime + base64 của file) và phần `text` (prompt).
- `generationConfig`: `temperature: 0`, `responseMimeType: "application/json"`, `responseSchema` gồm các trường ở mục 4.2. Trường `categoryId` là `enum` chứa id các hạng mục của phase cộng thêm `"NONE"`.
- **Prompt (ý chính):** đây là hoá đơn / biên lai tại Việt Nam; `totalAmount` là **tổng tiền thanh toán cuối cùng** (đã gồm VAT), số nguyên VND, bỏ dấu chấm phân cách nghìn; ngày ở dạng `dd/MM/yyyy` thì chuyển sang ISO; **không đọc được thì trả null, không đoán**; `categoryId` chọn mục phù hợp nhất theo tên và mô tả, không chắc thì `"NONE"`.
- Tên model, timeout, key đều lấy từ cấu hình. Kiểm tra lại tên model và hạn mức free trên AI Studio lúc lấy key.

### 5.3 Kiểm tra sau khi AI trả về

Không dựa vào mức "tự tin" do model tự báo. Backend tự kiểm tra; trường nào sai thì đặt về `null` và thêm cảnh báo:

| Kiểm tra | Xử lý |
|---|---|
| `totalAmount ≤ 0` hoặc > 1 tỷ | `null`, cảnh báo `AMOUNT_UNREADABLE` |
| Tổng các dòng hàng (+ VAT) lệch `totalAmount` > 2% | Giữ nguyên, cảnh báo `LINE_ITEMS_MISMATCH` |
| `invoiceDate` trong tương lai, hoặc cũ hơn 180 ngày | Giữ nguyên, cảnh báo `INVOICE_DATE_IN_FUTURE` / `INVOICE_DATE_TOO_OLD` |
| `vatAmount > totalAmount` | `vatAmount = null` |
| `categoryId` không thuộc danh sách của phase | `category = null` |
| `currency` khác VND | Giữ nguyên, cảnh báo `CURRENCY_NOT_VND` |

### 5.4 Cache, hạn mức, lỗi

- **Cache theo nội dung file:** SHA-256 của file → Redis `ai:receipt:{hash}` → `extractionId`, TTL `cache-ttl-hours`. Cùng một ảnh tải lại thì không gọi Gemini lần nữa.
- **Hạn mức:** Redis `ai:quota:{userId}:{yyyyMMdd}` tăng sau mỗi lần thực sự gọi Gemini. Vượt `max-calls-per-user-per-day` → `AiQuotaExceededException`.
- **Exception mới** trong `common/exception/`, đều kế thừa `BaseException` và được `GlobalExceptionHandler` bắt sẵn:
  - `AiUnavailableException` — 502, `AI_UNAVAILABLE` (không dùng 503 vì FE coi mọi 503 là "đang bảo trì") (tắt, không có key, Gemini lỗi hoặc timeout)
  - `AiQuotaExceededException` — 429, `AI_QUOTA_EXCEEDED`
  - `UnsupportedFileTypeException` — 400, `UNSUPPORTED_FILE_TYPE`
- **Thêm handler** `MaxUploadSizeExceededException` → 400. Hiện file quá lớn đang trả về 500.
- Gemini trả 429 (hết lượt free) → đổi thành `AiUnavailableException` với thông báo "Tính năng đọc chứng từ tạm thời quá tải, vui lòng nhập tay".

### 5.5 Cấu hình

```yaml
app:
  ai:
    enabled: ${AI_ENABLED:true}
    provider: ${AI_PROVIDER:gemini}          # gemini | mock
    timeout-ms: ${AI_TIMEOUT_MS:20000}
    max-calls-per-user-per-day: ${AI_MAX_CALLS_PER_DAY:30}
    cache-ttl-hours: ${AI_CACHE_TTL_HOURS:168}
    amount-mismatch-percent: 2
    max-invoice-age-days: 180
    gemini:
      base-url: https://generativelanguage.googleapis.com/v1beta
      api-key: ${GEMINI_API_KEY:}            # đặt trong .env (đã nằm trong .gitignore)
      model: ${GEMINI_MODEL:gemini-flash-latest}
```

### 5.6 Migration `V20__CREATE_DOCUMENT_EXTRACTIONS.sql`

```sql
CREATE TABLE IF NOT EXISTS document_extractions (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(id),
    file_hash         VARCHAR(64)  NOT NULL,
    mime_type         VARCHAR(100) NOT NULL,
    provider          VARCHAR(30)  NOT NULL,
    model             VARCHAR(100),
    total_amount      NUMERIC(19,2),
    invoice_date      DATE,
    vendor_name       VARCHAR(255),
    vendor_tax_code   VARCHAR(20),
    invoice_number    VARCHAR(100),
    vat_amount        NUMERIC(19,2),
    expense_summary   TEXT,
    raw_response      JSONB,
    warnings          JSONB,
    request_id        BIGINT REFERENCES requests(id),
    file_id           BIGINT REFERENCES file_storages(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP,
    created_by        BIGINT,
    updated_by        BIGINT
);
CREATE INDEX IF NOT EXISTS idx_doc_extract_hash    ON document_extractions(file_hash);
CREATE INDEX IF NOT EXISTS idx_doc_extract_request ON document_extractions(request_id);
CREATE INDEX IF NOT EXISTS idx_doc_extract_invoice ON document_extractions(vendor_tax_code, invoice_number);
```

`request_id` và `file_id` được gán khi tạo request có `extractionId`. Bảng này là dữ liệu để đối chiếu, để phát hiện hoá đơn dùng lại, và để đo độ chính xác cho báo cáo.

### 5.7 Khi tạo request

Trong `RequestServiceImpl.createRequest` (gọi qua `ReceiptExtractionService`, không inject repository của module `ai`):

1. Với mỗi attachment có `extractionId`: kiểm tra bản trích xuất thuộc đúng người tạo, rồi gán `request_id`, `file_id`.
2. **Đối chiếu số tiền:** tổng `total_amount` của các bản trích xuất lệch `amount` user nhập > `amount-mismatch-percent` → ghi `warnings` vào bản trích xuất. Người duyệt sẽ thấy cờ `RECEIPT_AMOUNT_MISMATCH` khi Task 1 xong.
3. **Hoá đơn dùng lại:** cùng `vendor_tax_code + invoice_number` đã gắn với một request khác chưa bị huỷ hoặc từ chối → ghi cờ `RECEIPT_REUSED`.
4. Không chặn việc tạo request ở cả hai bước 2 và 3. Đây chỉ là cờ cho người duyệt.

---

## 6. Frontend

| File | Thay đổi |
|---|---|
| `lib/api/ai.ts` (mới) | `getAiStatus()`, `extractReceipt(file, phaseId?)`, `suggestCategory(extractionId, phaseId)` |
| `types/ai.ts` (mới) | `ReceiptExtraction`, `AiStatus` |
| `components/request/receipt-scan-chip.tsx` (mới) | Chip trạng thái của từng file: đang đọc / đã đọc / không đọc được / bỏ qua |
| `components/request/ai-filled-badge.tsx` (mới) | Nhãn "✨ AI gợi ý" / "🔗 Theo tạm ứng" cạnh label |
| `app/(dashboard)/requests/new/page.tsx` | Đổi thứ tự trường (mục 3); gọi extract ngay khi chọn file; điền ô trống; theo dõi ô nào do AI điền (`aiFilled: Set<field>`), user sửa thì bỏ khỏi set; khi chọn phase sau khi upload thì gọi `suggestCategory`; gửi `extractionId` trong `attachments` |
| `types/request.ts` | Thêm `extractionId` vào attachment; thêm `projectId`, `phaseId`, `categoryId`… vào `AdvanceBalanceItem` |

Quy tắc điền:
- Chỉ điền khi ô đang trống, hoặc ô đang giữ giá trị do AI điền trước đó (lúc user thêm file thứ hai).
- **Số tiền** = tổng `totalAmount` của các file không bị bỏ qua. Ngày = ngày muộn nhất. Tiêu đề và mô tả lấy theo file đầu tiên, mô tả nối phần chứng từ của các file còn lại.
- **Hoàn ứng:** chọn khoản tạm ứng thì điền Dự án, Phase, Hạng mục từ `AdvanceBalanceItem`, khoá ba ô này và **không** gọi gợi ý hạng mục. Số tiền vượt `remainingAmount` thì hiện cảnh báo ngay dưới ô số tiền.

---

## 7. Kế hoạch triển khai

| ID | Việc | Size | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| **AI-0** | Lấy Gemini API key (AI Studio), thêm vào `.env`; chuẩn bị **15 ảnh hoá đơn mẫu**: Grab, nhà hàng, khách sạn, văn phòng phẩm, hoá đơn điện tử PDF; ghi sẵn kết quả đúng của từng ảnh | S · 0.5 | — | Có key và bộ ảnh có nhãn |
| **AI-1** | Khung module `ai`: cấu hình `app.ai.*`, `ReceiptExtractor` + `MockReceiptExtractor`, `GET /ai/status`, 3 exception + handler upload quá cỡ | S · 1 | — | `provider: mock` trả dữ liệu mẫu; không có key thì status `enabled: false`, app vẫn chạy |
| **AI-2** | `GeminiReceiptExtractor`: prompt, `responseSchema`, gọi API, parse, đổi lỗi 429 / timeout sang exception | M · 1.5 | AI-0, AI-1 | Đọc đúng số tiền và ngày trên ≥ 12/15 ảnh mẫu |
| **AI-3** | Migration V20 + entity, repo, mapper; `POST /ai/receipts/extract`: validate file, hash, cache Redis, hạn mức, kiểm tra sau AI (mục 5.3), lưu bản trích xuất | M · 1.5 | AI-2 | Cùng ảnh gửi 2 lần → lần 2 `cached: true`, không gọi Gemini |
| **AI-4** | Gợi ý hạng mục: method mới ở `ProjectQueryService`, enum id trong schema, kiểm tra id thuộc phase; `POST /ai/receipts/{id}/category-suggestion` | S · 1 | AI-3 | 15/15 lần trả về hoặc id thuộc phase, hoặc null; không bao giờ id ngoài danh sách |
| **AI-5** | Bổ sung `AdvanceBalanceItem` (dự án / phase / hạng mục) + `extractionId` trong `AttachmentRequest`; liên kết, đối chiếu số tiền, phát hiện hoá đơn dùng lại khi tạo request | S · 1 | AI-3 | Sửa số tiền khác hoá đơn > 2% → bản trích xuất có cờ; dùng lại hoá đơn → có cờ `RECEIPT_REUSED` |
| **AI-6** | FE: API client + type, đổi thứ tự form, chip từng file, điền ô trống, nhãn ✨, gộp nhiều file | M · 2 | AI-3 | Upload ảnh Grab → 5 ô tự điền trong ≤ 8 giây; sửa ô thì nhãn mất |
| **AI-7** | FE: gợi ý hạng mục khi chọn phase sau upload; Hoàn ứng tự điền theo tạm ứng + cảnh báo vượt số còn nợ; ẩn AI khi `enabled: false` | S · 1 | AI-4, AI-5, AI-6 | Đủ 3 kịch bản demo ở mục 8 |
| **AI-8** | Đo độ chính xác trên 15 ảnh: từng trường đúng / sai / trống; ghi kết quả vào báo cáo | S · 0.5 | AI-7 | Có bảng tỉ lệ đúng của số tiền, ngày, người bán, hạng mục |
| | **Tổng** | **~10** | | |

**Thứ tự:** `AI-0 → AI-1 → AI-2 → AI-3 → (AI-4, AI-5 song song) → AI-6 → AI-7 → AI-8`. AI-6 có thể bắt đầu ngay sau AI-1 bằng chế độ `mock`, không cần chờ Gemini.

Nếu thiếu thời gian, cắt theo thứ tự: phần hoá đơn dùng lại trong AI-5 → endpoint gợi ý hạng mục sau upload ở AI-4 (bắt buộc chọn phase trước khi upload) → AI-8.

---

## 8. Kịch bản demo

1. **Chi phí, một hoá đơn:** chọn dự án, phase → tải ảnh Grab → số tiền, ngày, tiêu đề, mô tả, **hạng mục *Travel & Accommodation*** tự điền → gõ mục đích → gửi.
2. **Chi phí, hai hoá đơn:** tải ảnh nhà hàng và ảnh taxi → số tiền là tổng hai file; bấm *Bỏ qua* một file → số tiền cập nhật.
3. **Hoàn ứng:** chọn khoản tạm ứng → dự án, phase, hạng mục tự điền và bị khoá → tải hoá đơn lớn hơn số còn nợ → hiện cảnh báo vượt.
4. **Dự phòng:** đổi `AI_PROVIDER=mock` để demo không cần mạng; tắt AI (`AI_ENABLED=false`) → form trở về nhập tay như cũ.

---

## 9. Rủi ro

| Rủi ro | Cách giảm |
|---|---|
| Hết lượt free hoặc bị 429 khi demo | Cache theo hash; dùng ảnh đã đọc trước; chế độ `mock` |
| Ảnh mờ hoặc nghiêng đọc sai số tiền | Kiểm tra mục 5.3; trường sai để trống; user luôn kiểm tra lại trước khi gửi |
| AI chọn sai hạng mục | Danh sách đóng + mô tả tiếng Việt; không chắc thì để trống; user sửa được |
| Google đổi tên model hoặc hạn mức free | Model và base URL nằm trong cấu hình; đổi bằng `.env`, không cần sửa code |

## 10. Ngoài phạm vi

- Đọc chứng từ cho Tạm ứng (báo giá, dự toán).
- Gọi bất đồng bộ qua RabbitMQ + SSE (thiết kế F1 gốc).
- Hiển thị cờ đối chiếu trên màn duyệt (thuộc Task 1, mục cờ cảnh báo).
- Chạy model local (Ollama).
