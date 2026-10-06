# IFMS — triển khai sổ cái và theo dõi tạm ứng

**Cập nhật:** 06/10/2026  
**Phạm vi:** backend IFMS hiện tại, các API được gọi bởi trang Kế toán ở frontend.

## Màn hình và dữ liệu

Trang Sổ cái có ba phần tra cứu riêng:

1. **Giao dịch ví:** mỗi dòng là một Transaction; chi tiết liệt kê các ví tăng/giảm. Các dòng LedgerEntry là biến động ví, không phải journal theo tài khoản.
2. **Sổ cái kế toán:** mỗi dòng là một AccountingJournal, có ngày/kỳ ghi sổ, nghiệp vụ nguồn và các dòng tài khoản. Journal được lưu cùng nghiệp vụ tạo ra, có tổng hai bên bằng nhau và có mã nguồn duy nhất để tránh ghi trùng.
3. **Tạm ứng còn mở:** nhóm theo nhân viên, mở rộng tới từng AdvanceBalance, gồm khoản ban đầu, chứng từ đã quyết toán, tiền mặt đã hoàn, khấu trừ lương, số lịch sử chưa phân loại và số còn phải quyết toán.

## Quy tắc đang được thực hiện

| Nghiệp vụ | Khi nào ghi nhận | Tác động ví | Tác động số dư tạm ứng |
|---|---|---|---|
| Tạm ứng (ADVANCE) | Khi Kế toán giải ngân yêu cầu đã duyệt | Ví dự án → ví nhân viên | Tạo khoản phải quyết toán; chưa ghi chi phí |
| Nhân viên tự chi (EXPENSE) | Kế toán xác nhận chứng từ hợp lệ | Chưa chuyển tiền; tiền đã được nhân viên tự chi | Không tác động |
| Thanh toán hoàn chi (EXPENSE paid) | Khi Kế toán thanh toán khoản đã xác nhận | Ví dự án → ví nhân viên | Không ghi chi phí lần hai |
| Quyết toán tạm ứng (REIMBURSE) | Khi Kế toán chấp nhận chứng từ gắn với tạm ứng | Không chuyển thêm tiền | Giảm khoản tạm ứng và ghi chi phí đúng một lần |
| Hoàn tiền tạm ứng (ADVANCE_RETURN) | Khi nhân viên hoàn tiền thật qua API của mình | Ví nhân viên → đúng ví dự án gốc | Giảm khoản phải quyết toán cùng giao dịch |
| Khấu trừ qua lương (advanceDeduct) | Khi chạy bảng lương | Không tạo giao dịch hoàn tiền giả | Phân bổ FIFO vào từng khoản tạm ứng; ghi journal lương có tham chiếu từng khoản |

EXPENSE có trạng thái trung gian ACCOUNTANT_VERIFIED. Việc xác nhận chứng từ và thanh toán hoàn chi là hai thao tác khác nhau. Số đã ghi chi phí không tăng lại khi thanh toán.

Khoản tiền giữ được gắn theo số tiền đã duyệt của từng yêu cầu. ADVANCE và EXPENSE chỉ được chi trong giới hạn số tiền đã giữ cho chính yêu cầu đó. REIMBURSE không giữ thêm tiền trong ví dự án vì không có lần chuyển tiền mới.

## Các API được trang Sổ cái sử dụng

| API | Chức năng |
|---|---|
| GET /api/v1/accountant/ledger/wallet-transactions | Giao dịch ví gộp theo transaction, lọc theo loại/trạng thái/nguồn/ngày |
| GET /api/v1/accountant/ledger/summary | Tổng hợp biến động của ví COMPANY_FUND; cùng bộ lọc với danh sách |
| GET /api/v1/accountant/ledger/journals | Danh sách journal, lọc theo nghiệp vụ và ngày |
| GET /api/v1/accountant/ledger/journals/{journalId} | Chi tiết dòng tài khoản và kiểm tra cân bằng |
| GET /api/v1/accountant/ledger/advances/outstanding | Tạm ứng còn mở, nhóm theo nhân viên |
| GET /api/v1/accountant/ledger/{transactionId} | Chi tiết giao dịch ví, kèm các journal liên quan nếu có |
| POST /api/v1/accountant/disbursements/{id}/disburse | Giải ngân ADVANCE; xác nhận chứng từ EXPENSE; ghi nhận quyết toán REIMBURSE |
| POST /api/v1/accountant/disbursements/{id}/pay | Thanh toán khoản hoàn chi EXPENSE đã xác nhận |
| POST /api/v1/requests/my-advance-balances/{advanceBalanceId}/return | Nhân viên hoàn tiền thật về ví dự án gốc |

Các thao tác Kế toán tiếp tục yêu cầu quyền PAYROLL_MANAGE hoặc REQUEST_PAYOUT theo endpoint; hoàn tiền tạm ứng yêu cầu chính nhân viên sở hữu khoản đó.

## Journal nội bộ

Mã tài khoản dưới đây là tên tài khoản quản trị nội bộ của IFMS để thể hiện đúng chiều tăng/giảm trong phạm vi đồ án; chúng chưa phải bảng hệ thống tài khoản pháp định:

- ADVANCE: tăng khoản tạm ứng phải quyết toán, giảm tiền ví dự án.
- EXPENSE xác nhận: tăng chi phí dự án, tăng khoản phải hoàn nhân viên.
- EXPENSE thanh toán: giảm khoản phải hoàn, giảm tiền ví dự án.
- REIMBURSE: tăng chi phí dự án, giảm khoản tạm ứng phải quyết toán.
- ADVANCE_RETURN: tăng tiền ví dự án, giảm khoản tạm ứng phải quyết toán.
- Payroll: ghi chi phí lương theo các trường hiện có; tách tiền lương thực chuyển, khoản khấu trừ đang chưa phân loại và phần bù trừ tạm ứng theo từng AdvanceBalance.

## Dữ liệu và giới hạn

- Migration V19 tạo bảng journal/header-lines, lưu khoản đã giữ theo yêu cầu, và tách tiền hoàn thật, khấu trừ lương, lịch sử chưa phân loại.
- Giá trị returned_amount cũ được chuyển nguyên vẹn vào legacy_unclassified_amount, không tự đoán đó là tiền mặt hay khấu trừ lương; số dư còn lại hiện có được giữ nguyên.
- Sổ journal hiện bao phủ ADVANCE, EXPENSE, REIMBURSE, ADVANCE_RETURN và payroll. SYSTEM_TOPUP, nạp/rút cá nhân và phân bổ nội bộ vẫn tra cứu ở giao dịch ví, chưa tạo journal.
- currentSpent hiện chỉ phản ánh chi phí đã xác nhận; hệ thống chưa có chỉ tiêu ngân sách riêng cho tiền đã giữ, tạm ứng đang mở và cam kết chưa giải ngân. Request.reservedAmount bảo vệ khoản giữ ở ví theo từng yêu cầu, không thay thế các chỉ tiêu ngân sách này.
- Chưa có giao diện khóa kỳ, đảo journal hoặc thao tác hoàn tiền tạm ứng từ trang Kế toán. Giao dịch gốc không bị sửa; các trường hợp sửa sai sau kỳ cần thiết kế nghiệp vụ đảo/điều chỉnh riêng.
- Tên tài khoản là mapping nội bộ; cần GVHD xác nhận cách trình bày phù hợp trước khi xem đây là hệ thống tài khoản hoàn chỉnh.

## Kiểm chứng

Lệnh mvnw.cmd -DskipTests compile chạy thành công trên repo backend IFMS sau các thay đổi. Chưa chạy bộ kiểm thử hoặc kết nối migration với database trong phiên này.
