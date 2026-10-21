# IFMS — triển khai sổ cái và theo dõi ngân sách/tạm ứng

> **Rà soát ngày 18/10/2026:** [Review và kế hoạch hoàn thiện sổ cái/Excel](<../../../../DA1/financial-wallet-frontend/docs/ledger-completion-and-excel-plan.md>) ghi các lỗi mới đã xác định, gồm trùng V20, startup reset dữ liệu, khóa nghiệp vụ và sai nguồn chỉ tiêu. Tài liệu này mô tả phần code đã có ngày 10/10; ràng buộc migration chỉ có hiệu lực khi đã áp dụng và kiểm chứng trên database.

**Cập nhật:** 10/10/2026
**Phạm vi:** Backend IFMS và các API được dùng bởi trang Kế toán ở frontend.
**Trạng thái ngày 21/10/2026:** Các mục bên dưới mô tả thiết kế triển khai ban đầu. Backend đã có migration V21–V33, API kỳ/đảo/đối soát/mở sổ/Excel và integration test trên PostgreSQL cô lập. Xem mục “Cập nhật triển khai” ở cuối; database chính chưa được migrate hoặc nghiệm thu.

> **Review bổ sung 21/10:** [Các phát hiện F01–F11](<../../../../DA1/financial-wallet-frontend/docs/ledger-implementation-review.md>) ghi lỗi hồi quy cập nhật dự án, số liệu Manager/Team Leader, hạn mức phase, chứng cứ nạp quỹ mới, đóng kỳ/ngày đảo, transaction PIN, bootstrap và phạm vi Excel/UI. Đây là các việc còn phải sửa; chưa đủ điều kiện gọi là hoàn tất toàn bộ kế hoạch.

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

## Cập nhật triển khai — 21/10/2026

Các mục trên ghi lại phạm vi ngày 10/10 và không còn mô tả đầy đủ code hiện tại. Các định nghĩa được dùng từ bản triển khai mới:

- `Wallet.balance` là tiền ví; `Project.availableBudget` là hạn mức ngân sách còn khả dụng, không gọi là số dư ví. Tạm ứng giải ngân tạo khoản phải quyết toán; chứng từ EXPENSE/REIMBURSE hợp lệ mới tạo chi phí. Khấu trừ payroll giảm khoản phải quyết toán bằng bù trừ, không sinh chuyển ví USER → PROJECT.
- Journal là bảng riêng, có hai phía ghi sổ cân bằng, nguồn nghiệp vụ, người ghi, ngày nghiệp vụ/ngày hạch toán và chiều phòng ban/dự án/giai đoạn/danh mục/tạm ứng. V22 chặn sửa/xóa/thêm dòng sau ghi, thiếu dòng, lệch tổng và ghi vào kỳ đóng. V24–V30 bổ sung chứng từ phân bổ, đảo, sao kê, thay thế lương và biên nhận nghiệp vụ.
- Trang đọc sổ theo tài khoản lấy số đầu kỳ, phát sinh và cuối kỳ từ journal. Kỳ kế toán có xem điều kiện và đóng kỳ; sai sót xử lý bằng đảo toàn phần có liên kết nguồn hoặc quy trình sửa nguồn, không sửa journal gốc.
- Ghi bù lịch sử có tìm **mã nguồn** trực tiếp trong DB, xem trước cả lô, đối chiếu tiền/chứng từ và khóa chống ghi trùng. Nguồn không đủ chứng cứ không được tự chuyển thành journal. Mở sổ chỉ ghi chênh lệch giữa số dư nguồn đã xác nhận với journal đã có, lưu căn cứ, PIN và mốc chuyển đổi; sau đó chặn ghi bù lịch sử tự động.
- Đối chiếu chi phí dự án kiểm tra từng nguồn EXPENSE/REIMBURSE với chứng từ phân bổ và journal theo đúng phạm vi, so với tổng tài khoản chi phí. Chỉ khi khớp mới cho sửa bộ đếm dự án/giai đoạn/danh mục, có PIN và audit ảnh chụp trước/sau ở V33.
- Sao kê ngân hàng nhập từ file có tài khoản, mốc và trạng thái BOOKED; đối chiếu với biến động tiền thuộc công ty tại cùng thời điểm. Ghi bù nạp quỹ lịch sử kiểm BOOKED; luồng nạp quỹ mới hiện chỉ kiểm paymentRef, còn thiếu ràng buộc chứng cứ như yêu cầu F05. Ví cá nhân nạp/rút không là khoản tiền công ty giữ hộ. MockBank của luồng rút ví cá nhân vẫn là hệ thống mô phỏng, chưa được gọi là tích hợp ngân hàng thật.
- API `/api/v1/spreadsheets` cấp danh sách dataset theo role; template `.xlsx` lấy tham chiếu hiện tại, parser kiểm schema/kiểu ô/công thức/trùng, preview không ghi nghiệp vụ, commit toàn lô có khóa và chống lặp. Export thực hiện trên server theo scope và ngày nếu dataset hỗ trợ. Payroll dùng nhân sự hiện có, không gắn mức lương mẫu. Các bộ dữ liệu đang có cho sáu vai trò gồm phòng ban, nhân sự, dự án, giai đoạn, thành viên, hạn mức, bảng lương, yêu cầu, sao kê, journal, tài khoản, ví, tạm ứng, phiếu lương, audit và báo cáo dự án.
- Startup không nạp/reset người dùng, payroll hay số dư mẫu ngoài profile `demo`. Bản cài mới có thể cấp ADMIN đầu tiên bằng `BOOTSTRAP_ADMIN_EMAIL` và `BOOTSTRAP_ADMIN_PASSWORD` (tối thiểu 12 ký tự). Tài khoản được tạo với PIN chưa thiết lập, nhưng đường thiết lập PIN ban đầu còn thiếu F08; chưa coi onboarding đã hoàn chỉnh. Không đặt mật khẩu mặc định cho môi trường thật.

**Kiểm chứng:** backend package thành công; PostgreSQL thử nghiệm mới và bản sao lịch sử đều lên V33; 12 integration test qua (7 nghiệp vụ/Excel, 5 ràng buộc journal). Frontend TypeScript và production build thành công. API smoke nối backend với PostgreSQL clone qua cho journal, sổ tài khoản, tạm ứng còn mở, mức sử dụng ngân sách, kỳ kế toán và danh mục Excel. Database triển khai chính chưa thay đổi. Chưa có browser provider trong phiên để nghiệm thu trực quan; cần kiểm tra UI đủ sáu role, từng loại Excel, chứng từ nguồn, ngân hàng ngoài và số dư mở sổ trên bản sao dữ liệu thực trước khi đưa vào sử dụng.
