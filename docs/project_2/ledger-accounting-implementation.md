# IFMS — triển khai sổ cái và theo dõi ngân sách/tạm ứng

**Cập nhật:** 10/10/2026
**Phạm vi:** Backend IFMS và các API được dùng bởi trang Kế toán ở frontend.
**Trạng thái:** Code đã compile; chưa chạy migration hoặc luồng end-to-end với database.

## Mô hình dữ liệu và nghiệp vụ

- Transaction ghi một nghiệp vụ chuyển tiền; LedgerEntry ghi biến động số dư của từng ví.
- AccountingJournal và AccountingJournalLine ghi tác động của nghiệp vụ lên các tài khoản nội bộ. Mỗi journal phải cân bằng và duy nhất theo event/source.
- AdvanceBalance theo dõi phần tạm ứng còn phải quyết toán, tách riêng chứng từ hợp lệ, hoàn tiền thật, khấu trừ lương và lịch sử cũ không xác định được nguồn.

| Nghiệp vụ | Hạch toán/ghi nhận | Tiền thực tế |
|---|---|---|
| ADVANCE | Tăng khoản phải quyết toán, giảm quỹ dự án; chưa ghi chi phí. | Dự án → nhân viên. |
| EXPENSE xác nhận | Tăng chi phí, tăng khoản phải hoàn nhân viên. | Chưa chuyển tiền ở bước xác nhận. |
| EXPENSE thanh toán | Giảm khoản phải hoàn; không ghi chi phí lần hai. | Dự án → nhân viên. |
| REIMBURSE | Tăng chi phí và giảm khoản tạm ứng đã liên kết. | Không có transfer mới. |
| ADVANCE_RETURN | Giảm khoản tạm ứng. | Nhân viên → đúng dự án gốc; cập nhật ví và AdvanceBalance trong một transaction. |
| Payroll advanceDeduct | Giảm khoản tạm ứng theo các allocation FIFO. | Không giả lập transfer hoàn tiền. |
| SYSTEM_TOPUP | Tăng quỹ công ty, giảm tài khoản ngân hàng/nguồn ngoài. | Nguồn ngoài → COMPANY_FUND. |
| DEPARTMENT_ALLOCATION | Tăng quỹ phòng ban, giảm COMPANY_FUND. | Chuyển nội bộ. |
| PROJECT_ALLOCATION | Tăng quỹ dự án, giảm quỹ phòng ban. | Chuyển nội bộ. |
| DEPOSIT/WITHDRAW cá nhân | Không tạo journal công ty theo quyết định đã xác nhận. | Theo dõi trong ví cá nhân. |

Các mã tài khoản cho các event mới là mapping quản trị nội bộ. Chúng chưa phải hệ thống tài khoản pháp định và cần GVHD/người phụ trách nghiệp vụ duyệt.

## API

| API | Chức năng |
|---|---|
| GET /api/v1/accountant/ledger/wallet-transactions | Giao dịch ví gộp theo transaction, lọc loại/trạng thái/nguồn/ngày |
| GET /api/v1/accountant/ledger/summary | Tổng hợp COMPANY_FUND theo bộ lọc; số dư là snapshot hiện tại |
| GET /api/v1/accountant/ledger/journals | Danh sách journal; lọc event, from/to, employeeId, projectId, requestId |
| GET /api/v1/accountant/ledger/journals/{journalId} | Chi tiết journal, dòng tài khoản, tổng hai phía và audit |
| GET /api/v1/accountant/ledger/advances/outstanding | Tạm ứng còn mở, nhóm theo nhân viên |
| GET /api/v1/accountant/ledger/budget-exposure | Chi phí, khoản khóa và tạm ứng còn mở theo dự án/giai đoạn/danh mục |
| GET /api/v1/accountant/ledger/{transactionId} | Chi tiết giao dịch ví và các journal liên quan |
| GET /api/v1/requests/my-advance-balances | Khoản tạm ứng chưa quyết toán của nhân viên đang đăng nhập |
| POST /api/v1/requests/my-advance-balances/{advanceBalanceId}/return | Hoàn tiền thật từ ví nhân viên về ví dự án gốc |

Các API ghi nhận nghiệp vụ chạy trong transaction nghiệp vụ tương ứng. Bảng journal có unique event/source để chống tạo trùng; service kiểm tra cân bằng trước khi lưu.

## Báo cáo ngân sách và quy tắc không cộng trùng

API budget-exposure tổng hợp riêng:

- Chi phí đã xác nhận từ bộ đếm ngân sách project/phase/category hiện hữu.
- reservedAmount của yêu cầu được duyệt hoặc đã xác nhận chứng từ, tức tiền đang khóa theo yêu cầu.
- remainingAmount từ AdvanceBalance, tức tạm ứng còn mở.
- availableBudget từ Project, tức số dư quỹ dự án trong dữ liệu hiện có; tiền khóa và advance còn mở hiển thị riêng.

Các trường này mô tả các trạng thái khác nhau, không cộng chung thành số “đã chi”. ADVANCE không ghi chi phí khi giải ngân. EXPENSE ghi chi phí lúc chứng từ hợp lệ được xác nhận; thanh toán sau đó không ghi chi phí lần nữa. REIMBURSE chỉ ghi chi phí theo chứng từ gắn vào advance.

## Audit, kỳ và dữ liệu lịch sử

- Mỗi journal mới lưu createdByUserId, createdByName và createdAt. Khi phát sinh từ tiến trình không có người dùng, tên tác vụ có thể là system.
- Journal lịch sử được để trống thông tin actor/time nếu không thể khôi phục đáng tin cậy.
- Migration V19 tạo journal/header-lines, số tiền giữ trên request và các trường settlement breakdown cho AdvanceBalance.
- Migration V20 thêm audit columns và trigger PostgreSQL chặn UPDATE/DELETE trên journal và journal lines. Dữ liệu chỉ thêm mới ở database.
- Posting date hiện lấy từ ngày xử lý backend; kỳ là tháng tương ứng. Chưa có period close hoặc API đảo/điều chỉnh; ngày/kỳ xử lý khi phát hiện lỗi sau kỳ đóng vẫn cần chốt.
- Chưa backfill giao dịch cũ. Chỉ làm khi có chứng từ, nguồn giao dịch và số liệu đối chiếu đủ căn cứ. Dữ liệu thiếu chứng từ phải được trình bày là legacy chưa có journal.

## Kiểm chứng

- mvnw.cmd -q -DskipTests compile thành công sau các thay đổi này.
- Chưa chạy test suite hoặc API integration test.
- Chưa áp dụng V19/V20: tại lượt kiểm tra DATABASE_URL chưa được cấu hình và Docker Engine chưa chạy. Chưa xác nhận schema, trigger, query mới hoặc số dư trên database thật.
