# Rà soát nghiệp vụ sổ cái IFMS — bản trình bày

> Tài liệu tóm tắt chức năng tài chính đang có, vấn đề cần sửa và quy tắc nghiệp vụ

## 1. Chức năng hiện có

Màn hình hiện tên **Sổ cái** chủ yếu là lịch sử giao dịch và biến động ví.
Hệ thống **chưa có sổ cái kế toán theo tài khoản**.

- **Ví và số dư:** gồm ví quỹ công ty (COMPANY_FUND), ví phòng ban (DEPARTMENT),
  ví dự án (PROJECT), ví nhân viên (USER) và ví kiểm soát tổng (FLOAT_MAIN).
  Mỗi ví theo dõi số dư tổng, số dư bị khóa và số dư khả dụng.

- **Chuyển tiền nội bộ:** một giao dịch (Transaction) thường tạo hai dòng biến động
  ví (LedgerEntry): tiền ra khỏi ví nguồn và tiền vào ví đích. Ký hiệu tiền ra
  (DEBIT) và tiền vào (CREDIT) chỉ chiều biến động của ví.

- **Nạp, rút tiền:** có nạp vào quỹ hệ thống (SYSTEM_TOPUP), nạp tiền vào ví
  (DEPOSIT) và rút tiền ra ngân hàng (WITHDRAW).

- **Cấp ngân sách:** có cấp ngân sách cho phòng ban
  (DEPARTMENT_TOPUP / DEPT_QUOTA_ALLOCATION) và cấp vốn cho dự án
  (PROJECT_TOPUP / PROJECT_QUOTA_ALLOCATION).

- **Tạm ứng (ADVANCE):** Trưởng nhóm (Team Leader) duyệt và giữ tiền; Kế toán
  (Accountant) giải ngân từ ví dự án sang ví nhân viên, đồng thời tạo số dư tạm ứng
  còn phải xử lý (AdvanceBalance).

- **Chi phí nhân viên tự ứng tiền (EXPENSE):** mã nguồn chuyển tiền từ ví dự án
  sang ví nhân viên; một số chỉ tiêu “đã chi” cũng tăng khi giải ngân.

- **Quyết toán tạm ứng (REIMBURSE):** dùng chứng từ để giảm số dư tạm ứng còn phải
  xử lý (AdvanceBalance), hiện không tạo chuyển tiền qua ví.

- **Trả lương (Payroll):** chuyển lương ròng vào ví nhân viên; khoản khấu trừ tạm
  ứng vào lương (advanceDeduct) làm giảm số dư tạm ứng.
  
- **Tra cứu và đảo giao dịch:** Kế toán xem danh sách/chi tiết giao dịch. Có nghiệp
  vụ đảo giao dịch (REVERSAL), nhưng liên kết về giao dịch gốc và chống đảo trùng
  chưa đầy đủ.

## 2. Lỗ hổng và sai lệch

1. **Sổ biến động ví đang bị gọi là sổ cái kế toán.** Dòng biến động ví
   (LedgerEntry) chỉ ghi ví nào tăng/giảm và số dư sau giao dịch. Nó chưa ghi tài
   khoản kế toán, ngày hạch toán hoặc hai bên ghi sổ. Phần giao diện gọi “Bút toán
   kép” hiện vẫn là các dòng ví.

2. **Nghĩa của khoản chi nhân viên tự ứng tiền (EXPENSE) chưa đồng nhất.** Mã nguồn
   chuyển tiền vào ví nhân viên, trong khi một số mô tả cũ ghi trả nhà cung cấp. Chi phí đã xác nhận (currentSpent) còn tăng từ lúc giải ngân,
   trước khi Kế toán xác nhận chứng từ.

3. **Tạm ứng (ADVANCE) và quyết toán tạm ứng (REIMBURSE) có thể làm sai chi phí.**
   Tạm ứng đã được tính vào một số chỉ tiêu “đã chi”; khi quyết toán, danh mục chi
   phí lại tăng tiếp. Một khoản bị ghi sớm, ghi hai lần hoặc lệch
   giữa dự án, giai đoạn và danh mục chi.

4. **Khoản tiền giữ chưa truy được đủ theo từng yêu cầu.** Cần biết yêu cầu nào
   đang giữ bao nhiêu, đã giải ngân hoặc nhả bao nhiêu, đồng thời ngăn giải ngân
   vượt số đã giữ của chính yêu cầu đó.

5. **Hoàn tiền tạm ứng và khấu trừ qua lương chưa tách rõ.** Chưa thấy luồng hoàn
   tiền thật từ ví nhân viên về ví dự án (ADVANCE_RETURN). Khấu trừ tạm ứng qua
   lương (advanceDeduct) là bù trừ, không có tiền thật chuyển ngược; cần ghi nhận
   kế toán và liên kết với khoản tạm ứng.



## 3. Biện pháp nghiệp vụ đã chốt

### Chi phí nhân viên tự ứng tiền (EXPENSE)

Nhân viên tự trả bằng tiền cá nhân rồi nộp chứng từ. Khi Kế toán xác nhận chứng từ
hợp lệ, hệ thống ghi nhận chi phí và khoản phải hoàn cho nhân viên. Tiền chuyển vào
ví nhân viên là thanh toán khoản hoàn chi.

Đây là quyết định đã chốt. Mô tả cũ về trả nhà cung cấp hoặc xin cấp tiền cần được
cập nhật theo nghĩa này.

### Tạm ứng (ADVANCE) và quyết toán tạm ứng (REIMBURSE)

- Công ty đưa tiền trước qua nghiệp vụ tạm ứng (ADVANCE). Khi giải ngân, ghi nhận
  khoản tạm ứng còn phải quyết toán; chưa ghi toàn bộ thành chi phí.
- Nhân viên nộp chứng từ để quyết toán tạm ứng (REIMBURSE). Chứng từ hợp lệ làm
  giảm số dư tạm ứng còn phải xử lý (AdvanceBalance) và ghi nhận phần chi phí đúng
  một lần. Nghiệp vụ này không chuyển thêm tiền vào ví nhân viên.
- Giao diện nên gọi REIMBURSE là **“Quyết toán tạm ứng”** để phân biệt với khoản
  hoàn tiền cho nhân viên đã tự chi.

### Hoàn tiền và khấu trừ tạm ứng

- **Hoàn lại tiền thật (ADVANCE_RETURN):** nhân viên trả tiền về; ghi chuyển từ ví
  nhân viên sang ví dự án và giảm số dư tạm ứng trong cùng nghiệp vụ.
- **Khấu trừ tạm ứng qua lương (advanceDeduct):** bù trừ với khoản lương phải trả.
  Số dư tạm ứng giảm nhưng không tạo giao dịch tiền giả từ ví nhân viên về ví dự án.

### Ngân sách và cấp tiền nội bộ

- Cấp ngân sách nội bộ (DEPARTMENT_TOPUP / PROJECT_TOPUP) là phân bổ giữa các cấp
  ví, không tự động là chi phí.
- Ví kiểm soát tổng (FLOAT_MAIN) chỉ dùng kiểm soát số dư; không dùng làm tài khoản
  đối ứng kế toán.
- Tách khoản đã cam kết, đã giữ, đã giải ngân, tạm ứng còn treo, chi phí đã xác
  nhận, tiền hoàn thật và khoản khấu trừ qua lương.
- Chỉ tiêu chi phí đã xác nhận (currentSpent) chỉ mang một nghĩa; không tính cùng
  một khoản chi nhiều lần.

### Bút toán kế toán

Bút toán kế toán (Journal) ghi từng tài khoản tăng hoặc giảm do một nghiệp vụ.
Theo thuật ngữ kế toán, mỗi bút toán có hai bên ghi sổ là Nợ và Có; tổng số tiền
hai bên phải bằng nhau. Khi thuyết trình, có thể nói tài khoản nào tăng/giảm.
Nợ/Có kế toán không đồng nghĩa với tiền ra/vào ví (DEBIT/CREDIT); việc ghi tăng hay
giảm ở mỗi bên còn tùy loại tài khoản.

**Kế toán xác nhận khoản chi nhân viên tự ứng (EXPENSE)**

- Tác động: chi phí tăng; khoản công ty phải hoàn cho nhân viên tăng.
- Cách ghi: chi phí tăng (Nợ); khoản phải hoàn tăng (Có).

**Thanh toán khoản hoàn chi**

- Tác động: khoản phải hoàn giảm; nguồn tiền dùng để thanh toán giảm.
- Cách ghi: khoản phải hoàn giảm (Nợ); nguồn tiền giảm (Có).

**Giải ngân khoản tạm ứng (ADVANCE)**

- Tác động: khoản nhân viên còn phải quyết toán tăng; nguồn tiền giải ngân giảm.
- Cách ghi: khoản tạm ứng tăng (Nợ); nguồn tiền giảm (Có).

**Chấp nhận chứng từ quyết toán tạm ứng (REIMBURSE)**

- Tác động: chi phí hợp lệ tăng; khoản tạm ứng còn phải quyết toán giảm.
- Cách ghi: chi phí tăng (Nợ); khoản tạm ứng giảm (Có).

Tên tài khoản cụ thể cần khớp với cách IFMS xác định tiền trong từng ví thuộc quyền
quản lý nào. Không thể lấy hướng tiền ra/vào ví để tự suy ra cách ghi sổ kế toán.

## 4. Sửa giao diện sổ cái

### Danh sách

Giữ mục **Sổ cái**, chia thành hai phần:

- **Giao dịch ví:** mỗi dòng là một giao dịch hoàn chỉnh, gồm mã, loại nghiệp vụ,
  ví nguồn → ví đích, số tiền, trạng thái, yêu cầu tham chiếu và thời gian. Các
  dòng biến động của từng ví nằm trong trang chi tiết.
- **Sổ cái kế toán:** mỗi dòng là một bút toán kế toán, gồm mã bút toán, ngày/kỳ
  hạch toán, nghiệp vụ nguồn, tổng hai bên ghi sổ và trạng thái cân bằng/đã ghi sổ.

Bộ lọc giao dịch ví gồm loại, trạng thái, ví/chủ ví, yêu cầu nguồn và thời gian.
Bộ lọc sổ kế toán bổ sung tài khoản, kỳ, dự án và danh mục chi phí.

Khi xem một ví, thẻ tổng quan hiển thị số dư đầu kỳ, tiền vào, tiền ra và số dư
cuối kỳ của chính ví đó. Khi xem toàn hệ thống, không cộng các lần chuyển nội bộ
thành tổng tiền thu/chi bên ngoài. Tất cả thẻ số liệu phải cùng phạm vi lọc.

### Chi tiết giao dịch

Tách chi tiết thành các phần:

- **Nguồn nghiệp vụ:** yêu cầu/chứng từ, người đề nghị/duyệt, dự án/giai đoạn/danh
  mục chi và khoản tạm ứng liên quan.
- **Biến động ví:** ví nào tăng/giảm, hướng tiền, số tiền và số dư sau giao dịch.
  Đổi tên mục “Bút toán kép” hiện tại thành “Biến động ví”.
- **Bút toán kế toán:** tài khoản bị tác động, giải thích tài khoản tăng/giảm, ngày
  hạch toán và kỳ kế toán.
- **Lịch sử xử lý:** người thực hiện, thời điểm, quyết định và giao dịch hoàn/điều
  chỉnh liên quan.

Với khoản chi nhân viên tự ứng tiền (EXPENSE), hiển thị riêng:

- **Chứng từ và ghi nhận chi phí:** ngày chi, số tiền Kế toán xác nhận và người
  xác nhận.
- **Thanh toán hoàn chi:** số đã chuyển vào ví nhân viên, ngày trả và số còn phải
  hoàn.

### Vị trí thao tác

Trang lịch sử và chi tiết tiếp tục ở chế độ chỉ đọc. Kế toán xác nhận/từ chối chứng
từ tại màn hình xử lý yêu cầu. Khi xác nhận, hệ thống ghi nhận chi phí và khoản
phải hoàn; khi chuyển tiền, trạng thái thanh toán được cập nhật riêng.

Giao dịch đã ghi không sửa trực tiếp. Sai sót được xử lý bằng nghiệp vụ
đảo/điều chỉnh có lý do và liên kết với giao dịch gốc.

## 5. Phạm vi ảnh hưởng

Việc chuẩn hóa tác động đến xử lý yêu cầu, ví, tạm ứng, bảng lương, ngân sách, dữ
liệu bút toán và màn hình Kế toán/báo cáo. Không cần viết lại toàn bộ IFMS hay tăng
số lượng sáu vai trò hiện có; cần rà lại quyền xem, xác nhận chứng từ, ghi sổ và
điều chỉnh.

**Trạng thái:** Sổ cái kế toán theo tài khoản và các thay đổi nêu trên mới là yêu
cầu nghiệp vụ; chưa được triển khai trong mã nguồn.

### Mã nguồn đã đối chiếu

- [Dịch vụ xử lý ví](../../src/main/java/com/mkwang/backend/modules/wallet/service/WalletServiceImpl.java)
- [Dòng biến động ví](../../src/main/java/com/mkwang/backend/modules/wallet/entity/LedgerEntry.java)
- [Giao dịch](../../src/main/java/com/mkwang/backend/modules/wallet/entity/Transaction.java)
- [Dịch vụ xử lý yêu cầu](../../src/main/java/com/mkwang/backend/modules/request/service/RequestServiceImpl.java)
- [Số dư tạm ứng](../../src/main/java/com/mkwang/backend/modules/request/entity/AdvanceBalance.java)
- [Dịch vụ bảng lương](../../src/main/java/com/mkwang/backend/modules/accounting/service/PayrollManagementServiceImpl.java)
- [Danh sách sổ Kế toán](<../../../../DA1/financial-wallet-frontend/app/(dashboard)/accountant/ledger/page.tsx>)
- [Chi tiết giao dịch](<../../../../DA1/financial-wallet-frontend/app/(dashboard)/accountant/ledger/[id]/page.tsx>)




6. **Đối soát và sửa sai còn thiếu quy tắc.** Ví kiểm soát tổng (FLOAT_MAIN) không
   tự đại diện cho tài khoản ngân hàng. Giao dịch đảo thiếu liên kết gốc/chống
   trùng; chưa có ngày và kỳ hạch toán.

7. **Giao diện có thể gây hiểu sai số liệu.** Một lần chuyển nội bộ có thể hiện
   thành nhiều dòng vì mỗi dòng là biến động của một ví. Các thẻ tổng hợp có thể
   dùng phạm vi lọc khác nhau; ảnh hiện tại còn có giá trị lỗi (NaN) và phần biến
   động ví mang tên “Bút toán kép”.