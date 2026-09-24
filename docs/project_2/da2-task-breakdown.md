# IFMS — Chia task phát triển Đồ án 2

> Bám theo phạm vi đã chốt ở [da2-improvement-request.md](da2-improvement-request.md).
> Lập ngày 2026-09-23.

**Giả định khi ước lượng:** 1 người làm, "ngày" = một ngày công tập trung (~6h code thật). Nếu lịch của bạn khác, xem mục [Nén phạm vi](#nén-phạm-vi-khi-thiếu-thời-gian) ở cuối.

---

## Tổng quan

| Giai đoạn | Nội dung | Ngày công | Cắt được? |
|---|---|---:|---|
| **P0** | Dọn nợ & chuẩn bị | 3 | Không (P0-2 là bắt buộc) |
| **A1** | Chuẩn hoá sổ cái | 15 | Không — là nền của F4/F5 |
| **A2** | Xem chi tiết theo vai trò | 9 | Thu hẹp được |
| **A3** | Import Excel | 7 | Thu hẹp được (bỏ import nhân sự) |
| **F1** | Trích xuất & đối chiếu chứng từ | 12.5 | Không — feature demo chính |
| **F5** | Dự báo dòng tiền | 9 | Thu hẹp được |
| **F4** | Hỏi đáp ngôn ngữ tự nhiên | 11 | **Cắt được cả cụm** |
| **EV** | Đánh giá & thực nghiệm | 6 | Không — phần ăn điểm |
| | **Tổng** | **~72** | |

~72 ngày công ≈ 14–15 tuần nếu làm full-time, ~24 tuần nếu làm 3 buổi/tuần.

### Đường găng

```
P0-2 ──► A1-3 ──► A1-4 ──► A1-5 ──► A1-8 ──► F5-2 ──► F5-1 ──► F5-5
                     └────► A1-9 ──► F4-1 ──► F4-3 ──► F4-5
A2-1 ──► A2-2 ──► A2-3 ──► F1-6
F1-1 ──► F1-4 ──► F1-5 ──► F1-6 ──► F1-8
```

A2, A3, F1-1 **chạy song song được** với A1 vì không đụng vào sổ cái. Nếu bí thời gian, xen kẽ chúng vào những ngày đang chờ review/chốt thiết kế A1.

---

## P0 — Dọn nợ & chuẩn bị (3 ngày)

| ID | Task | Size | Xong khi |
|---|---|---|---|
| **P0-1** | Xoá dead code WebSocket: `config/WebSocketConfig.java`, `modules/auth/websocket/`, 2 exception `WebSocket*`, `/ws/**` khỏi `WHITE_LIST_URLS`, `spring-boot-starter-websocket` khỏi `pom.xml` | S · 0.5 | Build pass, app khởi động bình thường, grep `SockJS` không còn kết quả |
| **P0-2** | **Test harness cho sổ cái** — dựng được Spring context trong test, viết test khẳng định: transfer trừ đúng ví nguồn/cộng đúng ví đích, reversal đưa số dư về nguyên trạng, transfer vượt số dư bị từ chối | M · 2 | 3 test xanh, chạy được bằng `./mvnw test` |
| **P0-3** | Tách cấu hình demo khỏi cấu hình thật: `DataSeeder` + `app.demo.reset-*` chỉ chạy ở profile `dev` | S · 0.5 | Chạy với profile `prod` không seed, không reset |
| **P0-4** | **Bắt đầu thu chứng từ thật cho F1** — mục tiêu 150–200 ảnh hoá đơn, gán nhãn 4 trường (nhà cung cấp, ngày, tổng tiền, VAT) | — · chạy nền | Đủ 150 ảnh có nhãn **trước khi** tới F1-8 |

> **P0-2 không được bỏ qua.** Repo hiện có đúng 1 file test. A1 sẽ mổ thẳng vào sổ cái — làm việc đó mà không có lưới an toàn là cách chắc chắn nhất để hỏng dữ liệu tài chính mà không biết.

> **P0-4 bắt đầu từ hôm nay.** Đây là chỗ đồ án hay chết: tới lúc cần số liệu cho báo cáo mới đi thu thập thì không kịp.

---

## A1 — Chuẩn hoá sổ cái (15 ngày)

**Tin tốt:** việc ghi sổ đã tập trung sẵn — chỉ 3 chỗ gọi `buildTransaction`, tất cả nằm trong `WalletServiceImpl`; 5 module khác (`request`, `accounting` ×2, `user`, `dashboard`) đều đi qua API công khai của `WalletService`. Không phải đi gom bút toán rải rác, chỉ cần siết đúng một chỗ.

| ID | Task | Size | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| **A1-1** | Thiết kế danh mục tài khoản tối giản (~12–15 tài khoản: tiền, tạm ứng, chi phí theo nhóm, phải trả nhân viên) + bảng ánh xạ từ `expense_categories` | S · 1 | — | Có tài liệu 1 trang, **đã chốt với GVHD** |
| **A1-2** | Migration `V17` — bảng `accounts`, cột `account_id` **nullable** trên `ledger_entries` | S · 0.5 | A1-1 | Migration chạy sạch, app start OK |
| **A1-3** | Siết ràng buộc **Σ Nợ = Σ Có** trong `WalletServiceImpl` trước khi persist | S · 1 | P0-2 | Test: bút toán lệch bị từ chối, không ghi gì vào DB |
| **A1-4** | Thêm chiều tài khoản vào API ghi sổ (`transfer`, `settleAndTransfer`, `deposit`, `withdraw`, `systemTopup`, `reversal`) + cập nhật 5 module gọi | M · 3 | A1-2, A1-3 | Mọi bút toán mới đều có `account_id`; 5 module build & chạy |
| **A1-5** | **Backfill** `account_id` cho dữ liệu cũ (suy từ `reference_type` + `transaction_type`) + migration `V18` set `NOT NULL` | M · 1.5 | A1-4 | **Số dư mọi ví trước/sau backfill khớp tuyệt đối** — đây là điều kiện nghiệm thu bắt buộc |
| **A1-6** | Kỳ kế toán + trạng thái khoá sổ; ghi vào kỳ đã khoá bị từ chối | M · 2 | A1-4 | Test: ghi vào kỳ `CLOSED` ném lỗi đúng loại |
| **A1-7** | Tách ngày hạch toán khỏi `created_at` | S · 1 | A1-6 | Bút toán nhập tháng 9 cho nghiệp vụ tháng 8 rơi đúng kỳ tháng 8 |
| **A1-8** | Truy vấn **"đã cam kết"** — đề nghị `APPROVED_*` chưa `PAID`, gộp theo ví/phòng ban | S · 1 | A1-4 | Trả đúng tổng cam kết; **đầu vào của F5** |
| **A1-9** | Endpoint **bảng cân đối thử** theo tài khoản + đối soát | M · 2 | A1-5 | Bảng cân đối cân bằng trên toàn bộ dữ liệu thật; **đầu vào của F4** |
| **A1-10** | Bộ test cho A1: invariant, khoá kỳ, đối soát backfill | M · 2 | A1-9 | Test xanh, chạy lại được sau mỗi thay đổi |

**Rủi ro lớn nhất của cả dự án nằm ở A1-5.** Làm nó trên bản sao dữ liệu trước, so số dư, rồi mới chạy thật.

---

## A2 — Xem chi tiết theo vai trò (9 ngày) · *song song được với A1*

| ID | Task | Size | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| **A2-1** | Ma trận **vai trò × khối thông tin** (3 khối: đối tượng / ngữ cảnh tài chính / dấu vết) cho 5 vai trò | S · 1 | — | Bảng ma trận được duyệt, không còn ô "tuỳ" |
| **A2-2** | Lớp hình chiếu (projection) theo quyền — một chỗ quyết định thấy gì | M · 2 | A2-1 | Thêm 1 vai trò mới không phải sửa controller |
| **A2-3** | Áp cho **chi tiết đề nghị**: khối ngữ cảnh tài chính (ngân sách còn lại, tạm ứng của người đề nghị, tác động nếu duyệt) | M · 3 | A2-2 | 5 vai trò mở cùng 1 đề nghị thấy đúng lát cắt của mình |
| **A2-4** | Áp cho **chi tiết dự án / giai đoạn** | M · 2 | A2-2 | Như trên |
| **A2-5** | Test rò rỉ quyền — mỗi vai trò thử đọc dữ liệu ngoài phạm vi | S · 1 | A2-3, A2-4 | Không trường hợp nào lọt |

---

## A3 — Import Excel (7 ngày) · *song song được với A1*

| ID | Task | Size | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| **A3-1** | Pipeline dùng chung **tải file → đối soát & xem trước → xác nhận ghi**; bước giữa không ghi gì | M · 2 | — | Xem trước phân loại đúng hợp lệ / cảnh báo / lỗi |
| **A3-2** | Import **phòng ban** | S · 1 | A3-1 | Import 20 dòng có 3 dòng lỗi → báo đúng 3 dòng |
| **A3-3** | Import **nhân sự** — kèm sinh mã NV, tạo hồ sơ, tạo ví, gửi mail onboard | M · 3 | A3-2 | Người được tạo đăng nhập được bằng luồng first-login |
| **A3-4** | Ngữ nghĩa **toàn-phần-hoặc-không** + test lô lỗi | S · 1 | A3-3 | Lô lỗi không để lại dòng nào trong DB |

> A3-3 nặng vì chạm nhiều domain (`user`, `profile`, `wallet`, `mail`). Nếu thiếu thời gian, cắt A3-3 và chỉ giữ import phòng ban — vẫn đủ minh hoạ pipeline.

---

## F1 — Trích xuất & đối chiếu chứng từ (12.5 ngày)

| ID | Task | Size | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| **F1-1** | Dựng module `ai`: client gọi mô hình, cấu hình `app.ai.*`, ngắt mạch khi nhà cung cấp lỗi | M · 2 | — | Tắt API key → app vẫn chạy, chỉ tính năng AI báo không khả dụng |
| **F1-2** | Hàng đợi `ai` + consumer + đẩy kết quả qua SSE (**lưu trước, đẩy sau**) | S · 1 | F1-1 | Gửi việc → nhận kết quả qua `/users/stream` |
| **F1-3** | Migration `V19` — bảng lưu kết quả trích xuất + bảng đề xuất AI (kèm `model`, `confidence`, quyết định của người dùng) | S · 0.5 | — | Migration sạch |
| **F1-4** | Trích xuất từ ảnh/PDF → cấu trúc (nhà cung cấp, ngày, tổng tiền, VAT, dòng hàng, gợi ý khoản mục) | M · 3 | F1-1, F1-3 | Chạy đúng trên 10 hoá đơn mẫu |
| **F1-5** | **Đối chiếu** số trích xuất vs số người dùng nhập → sinh cờ lệch theo ngưỡng cấu hình | S · 1.5 | F1-4 | Lệch quá ngưỡng sinh đúng 1 cờ; bằng nhau thì không sinh |
| **F1-6** | Hiển thị cờ lệch + bản trích xuất ở khối "dấu vết" của màn duyệt | S · 1 | F1-5, **A2-3** | Người duyệt thấy được cờ mà không cần rời màn hình |
| **F1-7** | Đệm kết quả theo hash file, giới hạn lượt/người/ngày, hạn mức token tháng | S · 1.5 | F1-4 | Trích xuất lại cùng file không gọi API lần hai |
| **F1-8** | **Đo độ chính xác** từng trường trên tập đã gán nhãn | M · 2 | F1-4, **P0-4** | Có bảng số cho 4 trường + tỉ lệ phát hiện lệch |

---

## F5 — Dự báo dòng tiền (9 ngày)

| ID | Task | Size | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| **F5-1** | Bộ dự báo **tất định** (không AI) — chiếu số dư ví theo tốc độ chi lịch sử | M · 3 | A1-9 | Chạy 2 lần cùng đầu vào ra đúng cùng kết quả |
| **F5-2** | Gộp 3 lớp đầu vào: đã thực chi + **đã cam kết** + định kỳ dự kiến | S · 1.5 | **A1-8**, F5-1 | Bỏ lớp cam kết ra thì kết quả lệch rõ rệt — chứng minh lớp này có tác dụng |
| **F5-3** | AI **diễn giải** kết quả dự báo thành văn bản tiếng Việt | S · 1.5 | F5-2, F1-1 | Tắt AI → vẫn còn đủ con số, chỉ mất phần diễn giải |
| **F5-4** | Cảnh báo theo ngưỡng (cấu hình runtime) đẩy qua hạ tầng thông báo có sẵn | S · 1 | F5-3 | Hạ ngưỡng xuống → nhận được cảnh báo qua SSE |
| **F5-5** | **Backtest** trên dữ liệu lịch sử — đo sai số dự báo | M · 2 | F5-2 | Có bảng sai số theo mốc 7/14/30 ngày |

---

## F4 — Hỏi đáp ngôn ngữ tự nhiên (11 ngày) · *cụm dễ cắt nhất*

| ID | Task | Size | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| **F4-1** | Định nghĩa **bộ công cụ truy vấn** 6–8 hàm; mỗi hàm tự kiểm quyền | M · 2 | **A1-9** | Danh sách hàm + quyền tương ứng được duyệt |
| **F4-2** | Tầng thực thi công cụ + lược đồ tham số | M · 2 | F4-1 | Gọi trực tiếp từng hàm ra đúng số, so khớp với dashboard |
| **F4-3** | Vòng lặp chọn-công-cụ với mô hình | M · 3 | F4-2 | 10 câu hỏi mẫu chọn đúng hàm |
| **F4-4** | Trả lời dần qua SSE + **kèm nguồn** (đã gọi hàm nào, tham số gì) | M · 2 | F4-3 | Người dùng tự kiểm chứng được câu trả lời |
| **F4-5** | Bộ **50 câu hỏi vàng** + **kịch bản thử vượt quyền** | M · 2 | F4-4 | Đúng số ≥ mục tiêu; **0 trường hợp vượt quyền** |

> **F4-5 là tiêu chí sống còn của cụm này.** Một trường hợp nhân viên lấy được dữ liệu ngoài phạm vi là đủ để phủ nhận toàn bộ lập luận "tool-use an toàn hơn sinh truy vấn tự do".

---

## EV — Đánh giá & thực nghiệm (6 ngày)

| ID | Task | Size | Phụ thuộc | Xong khi |
|---|---|---|---|---|
| **EV-1** | Gom số liệu từ bảng đề xuất AI: độ trễ, chi phí/lượt, tỉ lệ người dùng chấp nhận | S · 1 | F1-8 | Có bảng tổng hợp xuất được |
| **EV-2** | **Thí nghiệm so sánh** — mô hình mạnh vs mô hình rẻ trên F1; có vs không đệm prompt | M · 2 | EV-1 | Có bảng đánh đổi chất lượng/chi phí |
| **EV-3** | Viết chương thực nghiệm của báo cáo | M · 3 | EV-2, F5-5, F4-5 | Chương hoàn chỉnh, mọi khẳng định đều có số dẫn chứng |

---

## Nén phạm vi khi thiếu thời gian

Cắt theo thứ tự này, dừng khi vừa lịch:

| Bước cắt | Tiết kiệm | Còn lại có bảo vệ được không? |
|---|---:|---|
| 1. Bỏ **F4** cả cụm | 11 ngày | **Được** — vẫn còn A1 + F1 + F5 + đánh giá, là một đồ án hoàn chỉnh |
| 2. Bỏ **A3-3** (import nhân sự), giữ import phòng ban | 3 ngày | Được — pipeline vẫn được minh hoạ |
| 3. Thu hẹp **A2-4** (bỏ chi tiết dự án/giai đoạn, giữ chi tiết đề nghị) | 2 ngày | Được — F1-6 chỉ cần A2-3 |
| 4. Bỏ **EV-2** (thí nghiệm so sánh) | 2 ngày | Yếu đi rõ — chương thực nghiệm mất phần thuyết phục nhất |

**Không bao giờ cắt:** P0-2 (lưới an toàn), A1-5 (đối soát backfill), P0-4 (thu chứng từ), F1-8 / F5-5 / F4-5 (các phép đo).

Cắt tới bước 3 thì tổng còn ~56 ngày ≈ 11 tuần full-time.

---

## Mốc kiểm tra

Sau mỗi mốc, dừng lại đối chiếu trước khi đi tiếp:

| Mốc | Sau task | Câu hỏi tự kiểm |
|---|---|---|
| **M1** | A1-5 | Số dư mọi ví có khớp tuyệt đối trước/sau backfill không? Không khớp thì **dừng**, đừng đi tiếp. |
| **M2** | A1-9 | Bảng cân đối thử có cân trên dữ liệu thật không? Đây là cửa vào của F4/F5. |
| **M3** | F1-6 | Người duyệt có thực sự thấy cờ lệch ở đúng chỗ họ ra quyết định không? |
| **M4** | F5-5 | Dự báo có tốt hơn "lấy trung bình quá khứ" không? Không thì phải nói thẳng trong báo cáo. |
| **M5** | F4-5 | Có trường hợp nào vượt quyền không? Một cái cũng là hỏng. |

---

## Tham chiếu

- Phạm vi & kiến trúc: [da2-improvement-request.md](da2-improvement-request.md)
- Khảo sát AI đầy đủ: [ai-features-proposal.md](ai-features-proposal.md)
- Sổ cái hiện tại: [financial-architecture.md](financial-architecture.md)
- Phân quyền (cho A2, F4): [rbac-model.md](rbac-model.md)
- Quy ước code: [../.claude/CLAUDE.md](../.claude/CLAUDE.md)
