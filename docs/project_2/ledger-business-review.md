# Rà soát nghiệp vụ sổ cái IFMS — bản trình bày

> Tài liệu tóm tắt chức năng tài chính đang có, vấn đề cần sửa và quy tắc nghiệp vụ nhóm đã chốt để trình bày với GVHD. Tên tiếng Việt được đặt trước; tên trong mã nguồn để trong ngoặc.

## 1. Chức năng hiện có

Màn hình hiện tên **Sổ cái** đang chủ yếu là lịch sử giao dịch và biến động ví. Hệ thống **chưa có sổ cái kế toán theo tài khoản**.

| Chức năng | Hiện trạng trong IFMS |
|---|---|
| Ví và số dư | Có ví quỹ công ty (COMPANY_FUND), ví phòng ban (DEPARTMENT), ví dự án (PROJECT), ví nhân viên (USER) và ví kiểm soát tổng (FLOAT_MAIN). Mỗi ví theo dõi số dư tổng, số dư bị khóa và số dư khả dụng. |
| Chuyển tiền nội bộ | Một giao dịch (Transaction) thường tạo hai dòng biến động ví (LedgerEntry): một dòng tiền ra khỏi ví nguồn, một dòng tiền vào ví đích. Ký hiệu tiền ra khỏi ví (DEBIT) và tiền vào ví (CREDIT) chỉ hướng biến động ví. |
| Nạp, rút tiền | Có nạp vào quỹ hệ thống (SYSTEM_TOPUP), nạp tiền vào ví (DEPOSIT) và rút tiền ra ngân hàng (WITHDRAW). |
| Cấp ngân sách | Có cấp ngân sách cho phòng ban (DEPARTMENT_TOPUP / DEPT_QUOTA_ALLOCATION) và cấp vốn cho dự án (PROJECT_TOPUP / PROJECT_QUOTA_ALLOCATION). |
| Tạm ứng (ADVANCE) | Trưởng nhóm (Team Leader) duyệt và giữ tiền; Kế toán (Accountant) giải ngân từ ví dự án sang ví nhân viên, đồng thời tạo số dư tạm ứng còn phải xử lý (AdvanceBalance). |
| Chi phí nhân viên tự ứng tiền (EXPENSE) | Mã nguồn dùng mã nghiệp vụ EXPENSE và chuyển tiền từ ví dự án sang ví nhân viên; một số chỉ tiêu “đã chi” cũng tăng khi giải ngân. |
| Quyết toán tạm ứng (REIMBURSE) | Dùng chứng từ để giảm số dư tạm ứng còn phải xử lý (AdvanceBalance); hiện không tạo chuyển tiền qua ví. |
| Trả lương (Payroll) | Bảng lương chuyển lương ròng vào ví nhân viên; khoản khấu trừ tạm ứng vào lương (advanceDeduct) làm giảm số dư tạm ứng. |
| Tra cứu và đảo giao dịch | Kế toán xem danh sách/chi tiết giao dịch. Có đảo giao dịch (REVERSAL), nhưng liên kết về giao dịch gốc và chống đảo trùng chưa đầy đủ. |

## 2. Lỗ hổng và sai lệch đang gặp

1. **Nhầm sổ biến động ví với sổ cái kế toán.** Dòng biến động ví (LedgerEntry) chỉ ghi ví nào tăng/giảm và số dư sau giao dịch. Nó chưa ghi tài khoản kế toán, ngày hạch toán hoặc hai bên ghi sổ Nợ/Có. Phần giao diện gọi “Bút toán kép” hiện vẫn là các dòng ví.
2. **Nghĩa của khoản chi nhân viên tự ứng tiền (EXPENSE) chưa đồng nhất.** Mã nguồn chuyển tiền vào ví nhân viên, trong khi một số mô tả cũ ghi trả trực tiếp nhà cung cấp hoặc xin cấp tiền. Chi phí đã xác nhận (currentSpent) còn tăng từ lúc giải ngân, trước khi Kế toán xác nhận chứng từ.
3. **Tạm ứng (ADVANCE) và quyết toán tạm ứng (REIMBURSE) có thể làm sai chi phí.** Tạm ứng đã được tính vào một số chỉ tiêu “đã chi”; khi quyết toán, danh mục chi phí lại có thể tăng tiếp. Một khoản có nguy cơ bị ghi sớm, ghi hai lần hoặc lệch giữa dự án/giai đoạn/danh mục chi.
4. **Khoản tiền giữ chưa theo dõi đủ theo từng yêu cầu.** Cần biết yêu cầu nào đang giữ bao nhiêu, đã giải ngân/nhả bao nhiêu và ngăn việc giải ngân vượt số đã giữ của chính yêu cầu đó.
5. **Hoàn tiền tạm ứng và khấu trừ qua lương chưa tách rõ.** Chưa thấy luồng hoàn tiền thật từ ví nhân viên về ví dự án (ADVANCE_RETURN). Khấu trừ tạm ứng qua lương (advanceDeduct) là bù trừ, không có tiền thật chuyển ngược; cần có dấu vết kế toán và liên kết với khoản tạm ứng.
6. **Đối soát và sửa sai còn thiếu quy tắc.** Ví kiểm soát tổng (FLOAT_MAIN) không tự đại diện cho tài khoản ngân hàng. Đảo giao dịch thiếu liên kết gốc/chống trùng; chưa có ngày và kỳ hạch toán.
7. **Giao diện có thể khiến người xem hiểu sai số liệu.** Một giao dịch chuyển nội bộ có thể hiện thành nhiều dòng vì mỗi dòng là biến động của một ví. Các thẻ tổng hợp có thể tính theo phạm vi lọc khác nhau; ảnh hiện tại còn có giá trị lỗi (NaN) và phần biến động ví mang tên “Bút toán kép”.

## 3. Biện pháp nghiệp vụ đã chốt

| Nghiệp vụ | Quy tắc dùng trong IFMS |
|---|---|
| **Chi phí nhân viên tự ứng tiền (EXPENSE)** | Nhân viên tự trả bằng tiền cá nhân rồi nộp chứng từ. Khi Kế toán xác nhận chứng từ hợp lệ, hệ thống ghi nhận chi phí và khoản phải hoàn cho nhân viên. Chuyển tiền vào ví nhân viên là thanh toán khoản hoàn chi. Đây là quyết định đã chốt; mô tả cũ về trả nhà cung cấp/xin cấp tiền phải được cập nhật theo nghĩa này. |
| **Tạm ứng (ADVANCE)** | Công ty đưa tiền trước. Khi giải ngân, ghi nhận khoản tạm ứng còn phải quyết toán; chưa ghi toàn bộ thành chi phí. |
| **Quyết toán tạm ứng (REIMBURSE)** | Nhân viên nộp chứng từ cho khoản tạm ứng (ADVANCE). Chứng từ hợp lệ làm giảm số dư tạm ứng còn phải xử lý (AdvanceBalance) và ghi nhận phần chi phí đúng một lần; không chuyển thêm tiền vào ví nhân viên. Giao diện nên dùng tên “Quyết toán tạm ứng”. |
| **Hoàn lại tiền tạm ứng thật (ADVANCE_RETURN)** | Nhân viên trả tiền về: ghi chuyển từ ví nhân viên sang ví dự án và giảm số dư tạm ứng trong cùng nghiệp vụ. |
| **Khấu trừ tạm ứng qua lương (advanceDeduct)** | Là bù trừ với khoản lương phải trả; giảm số dư tạm ứng nhưng không tạo giao dịch tiền giả từ ví nhân viên về ví dự án. |
| **Cấp ngân sách nội bộ (DEPARTMENT_TOPUP / PROJECT_TOPUP)** | Là phân bổ ngân sách giữa các cấp ví, không tự động là chi phí. Ví FLOAT_MAIN chỉ dùng kiểm soát tổng số dư, không dùng làm tài khoản đối ứng kế toán. |
| **Ngân sách và chi phí** | Tách khoản đã cam kết, đã giữ, đã giải ngân, tạm ứng còn treo, chi phí đã xác nhận, tiền hoàn thật và khoản khấu trừ qua lương. Chỉ tiêu chi phí đã xác nhận (currentSpent) chỉ mang nghĩa chi phí đã xác nhận; một khoản không được tính hai lần. |

### Bút toán kế toán

Bút toán kế toán (Journal) ghi từng tài khoản tăng hoặc giảm do một nghiệp vụ. Theo thuật ngữ kế toán, mỗi bút toán có hai bên ghi sổ là Nợ và Có, với tổng số tiền hai bên phải bằng nhau. Khi thuyết trình, có thể giải thích bằng việc tài khoản nào tăng/giảm; bảng dưới đây giữ thuật ngữ Nợ/Có trong cột ghi sổ để đối chiếu. Nợ/Có kế toán không đồng nghĩa với tiền ra/vào ví (DEBIT/CREDIT): một tài khoản tăng hay giảm ở bên nào còn tùy loại tài khoản.

| Sự kiện | Diễn giải dễ hiểu | Tài khoản tăng/giảm (cách ghi Nợ/Có) |
|---|---|---|
| Kế toán xác nhận khoản chi nhân viên tự ứng tiền (EXPENSE) | Ghi tăng chi phí và tăng khoản công ty còn phải hoàn cho nhân viên. | Chi phí tăng (Nợ); khoản phải hoàn nhân viên tăng (Có) |
| Thanh toán khoản hoàn vào ví nhân viên | Giảm khoản phải hoàn và giảm nguồn tiền dùng để thanh toán. | Khoản phải hoàn giảm (Nợ); nguồn tiền thanh toán giảm (Có) |
| Giải ngân khoản tạm ứng (ADVANCE) | Tăng khoản nhân viên còn phải quyết toán và giảm nguồn tiền giải ngân. | Khoản tạm ứng phải quyết toán tăng (Nợ); nguồn tiền giải ngân giảm (Có) |
| Chấp nhận chứng từ quyết toán tạm ứng (REIMBURSE) | Ghi tăng chi phí hợp lệ và giảm khoản tạm ứng còn phải quyết toán. | Chi phí hợp lệ tăng (Nợ); khoản tạm ứng phải quyết toán giảm (Có) |

Các tên trên giải thích bản chất nghiệp vụ; tài khoản cụ thể cần khớp với cách IFMS xác định tiền trong từng ví thuộc quyền quản lý nào. Không thể lấy hướng tiền ra/vào ví để tự suy ra Nợ/Có kế toán.

## 4. Hướng sửa giao diện sổ cái

### Danh sách

Giữ mục **Sổ cái**, chia thành hai phần:

- **Giao dịch ví:** mỗi dòng là một giao dịch hoàn chỉnh, gồm mã, loại nghiệp vụ, ví nguồn → ví đích, số tiền, trạng thái, yêu cầu tham chiếu và thời gian. Các dòng biến động của từng ví nằm trong trang chi tiết.
- **Sổ cái kế toán:** mỗi dòng là một bút toán kế toán, gồm mã bút toán, ngày/kỳ hạch toán, nghiệp vụ nguồn, tổng Nợ/Có và trạng thái cân bằng/đã ghi sổ.

Bộ lọc giao dịch ví gồm loại, trạng thái, ví/chủ ví, yêu cầu nguồn và thời gian. Bộ lọc sổ kế toán bổ sung tài khoản, kỳ, dự án và danh mục chi phí. Khi xem một ví, thẻ tổng quan hiển thị số dư đầu kỳ, tiền vào, tiền ra và số dư cuối kỳ của chính ví đó. Khi xem toàn hệ thống, không cộng các lần chuyển nội bộ thành tổng tiền thu/chi bên ngoài. Tất cả thẻ số liệu phải cùng phạm vi lọc.

### Chi tiết giao dịch

Tách thành các phần **Nguồn nghiệp vụ**, **Biến động ví**, **Bút toán kế toán** và **Lịch sử xử lý**. Đổi tên mục “Bút toán kép” hiện tại thành “Biến động ví”. Kế toán cần mở được yêu cầu/chứng từ, xem người đề nghị/duyệt, dự án/giai đoạn/danh mục chi, khoản tạm ứng và các lần thanh toán/hoàn liên quan.

Với khoản chi nhân viên tự ứng tiền (EXPENSE), hiển thị riêng:

- **Chứng từ và ghi nhận chi phí:** ngày chi, số tiền Kế toán xác nhận, người xác nhận và bút toán ghi nhận chi phí.
- **Thanh toán hoàn chi:** số đã chuyển vào ví nhân viên, ngày trả và số còn phải hoàn.

Trang lịch sử và chi tiết tiếp tục ở chế độ chỉ đọc. Kế toán xác nhận/từ chối chứng từ tại màn hình xử lý yêu cầu; sau đó chi phí và khoản phải hoàn được ghi nhận. Khi chuyển tiền, trạng thái thanh toán được cập nhật riêng. Giao dịch đã ghi không sửa trực tiếp; sai sót được xử lý bằng nghiệp vụ đảo/điều chỉnh có lý do và liên kết bản gốc.

## 5. Phạm vi ảnh hưởng

Việc chuẩn hóa tác động đến xử lý yêu cầu, ví, tạm ứng, bảng lương, ngân sách, dữ liệu bút toán và màn hình Kế toán/báo cáo. Không cần viết lại toàn bộ IFMS hay tăng số lượng sáu vai trò hiện có; cần rà lại quyền xem, xác nhận chứng từ, ghi sổ và điều chỉnh.

**Trạng thái:** Sổ cái kế toán theo tài khoản và các thay đổi nêu trên mới là yêu cầu nghiệp vụ; chưa được triển khai trong mã nguồn.









































### Mã nguồn đã đối chiếu

- [Dịch vụ xử lý ví](../../src/main/java/com/mkwang/backend/modules/wallet/service/WalletServiceImpl.java), [Dòng biến động ví](../../src/main/java/com/mkwang/backend/modules/wallet/entity/LedgerEntry.java), [Giao dịch](../../src/main/java/com/mkwang/backend/modules/wallet/entity/Transaction.java)
- [Dịch vụ xử lý yêu cầu](../../src/main/java/com/mkwang/backend/modules/request/service/RequestServiceImpl.java), [Số dư tạm ứng](../../src/main/java/com/mkwang/backend/modules/request/entity/AdvanceBalance.java), [Dịch vụ bảng lương](../../src/main/java/com/mkwang/backend/modules/accounting/service/PayrollManagementServiceImpl.java)
- [Danh sách sổ Kế toán](<../../../../DA1/financial-wallet-frontend/app/(dashboard)/accountant/ledger/page.tsx>), [Chi tiết giao dịch](<../../../../DA1/financial-wallet-frontend/app/(dashboard)/accountant/ledger/[id]/page.tsx>)
