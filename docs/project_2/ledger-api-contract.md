# Hợp đồng API sổ cái — kỳ kế toán

**Cập nhật:** 06/11/2026  
**Phạm vi:** xem kỳ kế toán, xem trước điều kiện đóng và đóng kỳ.  
**Quyền:** `ACCOUNTANT`, `CFO`. API dùng envelope `ApiResponse` hiện hành.

## 1. Liệt kê kỳ kế toán

`GET /api/v1/accountant/ledger/periods`

Trả các kỳ xuất hiện trong journal hoặc bảng kỳ kế toán.

```json
{
  "success": true,
  "data": [
    {
      "period": "2026-10",
      "closed": false,
      "closedAt": null,
      "closedBy": null,
      "reason": null
    }
  ]
}
```

## 2. Kiểm tra trước khi đóng

`GET /api/v1/accountant/ledger/periods/{period}/close-preview`

`period` có định dạng `YYYY-MM`. Query parameters:

| Tên | Mặc định | Giới hạn | Ý nghĩa |
|---|---:|---:|---|
| `issuePage` | 1 | Từ 1 trở lên | Trang của danh sách ngoại lệ. |
| `issueSize` | 20 | 1–100 | Số ngoại lệ trong một trang. |

Preview trả tổng số bút toán, bộ đếm theo nhóm, tổng ngoại lệ, trang hiện tại,
danh sách ngoại lệ, lý do chặn và các số dư được chuyển tiếp. Tổng count không
bị giới hạn theo trang; chỉ `issues` được phân trang.

```json
{
  "success": true,
  "data": {
    "period": "2026-10",
    "eligible": false,
    "journalCount": 42,
    "invalidJournalCount": 1,
    "missingSourceCount": 0,
    "unmatchedTopupCount": 0,
    "invalidSourceLinkCount": 0,
    "sourceAmountMismatchCount": 0,
    "invalidAdvanceBalanceCount": 0,
    "issueTotal": 1,
    "issuePage": 1,
    "issueSize": 20,
    "issues": [
      {
        "code": "UNBALANCED_JOURNAL",
        "severity": "BLOCKER",
        "journalId": 81,
        "journalCode": "JRN-2026-0081",
        "eventType": "EXPENSE_VERIFIED",
        "sourceType": "REQUEST",
        "sourceId": "45",
        "amount": 1000000,
        "message": "Bút toán thiếu dòng hoặc tổng hai bên không bằng số tiền header.",
        "actionHint": "Mở chi tiết bút toán, xác minh các dòng và lập bút toán điều chỉnh có liên kết."
      }
    ],
    "blockingIssues": ["Có bút toán thiếu dòng hoặc không cân bằng · 1"],
    "carryForwardNotes": ["2 khoản tạm ứng còn dư tại ngày chốt được chuyển tiếp theo dõi, chưa tự ghi thành chi phí."]
  }
}
```

Các mã ngoại lệ hiện hành:

| Mã | Ý nghĩa |
|---|---|
| `UNBALANCED_JOURNAL` | Thiếu dòng hoặc tổng hai phía không khớp header. |
| `MISSING_SOURCE` | Thiếu hồ sơ nguồn hoặc source type/ID không hợp lệ. |
| `INVALID_SOURCE_LINK` | Sai loại, trạng thái hoặc liên kết giữa journal và nghiệp vụ. |
| `INVALID_REVERSAL_LINK` | Journal đảo không khớp nguồn gốc hoặc không đảo đúng từng dòng. |
| `SOURCE_AMOUNT_MISMATCH` | Số tiền journal không khớp request, transaction, payroll hoặc mapping dòng. |
| `MISSING_EXPENSE_EVIDENCE` | Tổng phân bổ chứng từ không bằng chi phí đã xác nhận/quyết toán. |
| `INVALID_ADVANCE_BALANCE` | Dư tạm ứng không khớp giải ngân, quyết toán, hoàn tiền, bù trừ và phần lịch sử chưa phân loại. |
| `UNMATCHED_SYSTEM_TOPUP` | Nạp quỹ thiếu sao kê IN đã xác nhận đúng tài khoản và số tiền. |

`eligible` chỉ là kết quả preview tại thời điểm đọc. Một preview đủ điều kiện
không đảm bảo đóng kỳ thành công nếu dữ liệu nguồn thay đổi sau đó.

## 3. Đóng kỳ

`POST /api/v1/accountant/ledger/periods/{period}/close`

Request:

```json
{
  "reason": "Đã rà soát journal, chứng từ và đối chiếu sao kê tháng 10/2026",
  "reviewAcknowledged": true
}
```

- `reason` bắt buộc, tối đa 1.000 ký tự.
- `reviewAcknowledged` phải là `true`.
- Chỉ đóng kỳ đã kết thúc theo múi giờ `Asia/Ho_Chi_Minh`.
- Backend lấy khóa theo kỳ, chạy lại toàn bộ điều kiện chặn trong transaction,
  rồi mới ghi trạng thái đóng. Kỳ đã đóng không được đóng lần nữa.
- Nếu preview còn blocker hoặc dữ liệu đổi trước khi ghi, API trả lỗi nghiệp vụ;
  client phải tải preview mới. Không lấy kết quả preview cũ làm căn cứ bỏ qua
  kiểm tra ở server.
- Sau khi kỳ đóng, nghiệp vụ sửa sai xử lý theo quy tắc correction hiện hành,
  không sửa journal gốc hoặc ghi lùi vào kỳ đã đóng.

## 4. Phân loại lỗi và số dư chuyển tiếp

`blockingIssues` là lỗi đang chặn đóng kỳ. `carryForwardNotes` giải thích các
khoản còn mở hợp lệ như tạm ứng còn dư, khoản hoàn chi đã xác nhận nhưng chưa
thanh toán và sao kê đang chờ. Tạm ứng còn dư không tự biến thành chi phí.
Sao kê chờ chỉ được tính trong phạm vi tài khoản ngân hàng công ty đang cấu hình;
dòng của tài khoản khác không được nhập vào ghi chú chuyển kỳ của IFMS.

## 5. Quy tắc tiêu thụ ở giao diện

- Giữ `period` và trang ngoại lệ đang xem khi gọi lại preview.
- Dùng `issueTotal` để tính số trang và hiển thị `issuePage/issueSize` từ response.
- Link journal/source chỉ điều hướng; quyền xem nguồn vẫn do API đích xác thực.
- API lỗi hoặc lỗi quyền phải hiển thị trạng thái lỗi, không chuyển thành danh sách
  rỗng hoặc kết luận đủ điều kiện đóng.
- Không lưu lý do, PIN hoặc thông tin xác nhận nhạy cảm vào URL. Hợp đồng đóng kỳ
  hiện tại chỉ nhận `reason` và `reviewAcknowledged`, không nhận PIN.

**Schema:** các thay đổi ở trên không thêm migration; database hiện hành dùng
Flyway V36.
