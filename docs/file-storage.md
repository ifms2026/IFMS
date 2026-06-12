# File Storage Guide

Tài liệu này mô tả cách các domain khác sử dụng `FileStorageService` để lưu metadata file vào database, và cách lấy Cloudinary signature qua `FileStorageController` để client upload trực tiếp.

## 1) Mục tiêu

- Tách upload file ra khỏi backend business API (client upload trực tiếp lên Cloudinary).
- Chuẩn hóa cách lưu metadata file vào bảng `file_storages` qua `FileStorageService`.
- Cho phép các domain tái sử dụng chung logic đọc/xóa file đã lưu.

## 2) Thành phần chính

- `FileStorageController`:
  - `GET /uploads/signature`
  - Trả về chữ ký upload gồm: `signature`, `timestamp`, `apiKey`, `cloudName`, `folder`.
- `CloudinaryService`:
  - `generateSignature(UploadFolder folder)` để sinh chữ ký upload.
  - `deleteFile(String publicId)` để xóa file trên Cloudinary.
  - Không cung cấp hàm generate signed URL để trả cho client.
- `FileStorageService`:
  - `save(FileStorageRequest request)`
  - `saveAll(List<FileStorageRequest> requests)`
  - `findById(Long id)`
  - `findAllByIds(List<Long> ids)`
  - `deleteFile(Long id)` (xóa Cloudinary + xóa DB)
  - Không cung cấp hàm generate signed URL.

## 3) Luồng upload chuẩn (khuyến nghị cho mọi domain)

### Bước 1: Client lấy signature từ backend

Gọi endpoint:

```http
GET /api/v1/uploads/signature?folder=AVATAR
```

`folder` là enum `UploadFolder`: `POST | AVATAR | DOCUMENT | PRODUCT | REPORT`.

Response `data` ví dụ:

```json
{
  "signature": "abc123...",
  "timestamp": 1738900000,
  "apiKey": "123456789",
  "cloudName": "ifms-cloud",
  "folder": "avatars"
}
```

### Bước 2: Client upload trực tiếp lên Cloudinary

Client dùng dữ liệu signature để upload (multipart/form-data) trực tiếp đến Cloudinary.

Các field quan trọng cần lấy từ Cloudinary response:

- `public_id`
- `secure_url` (hoặc `url`)
- `original_filename`
- `bytes`
- `format` (hoặc MIME phù hợp từ client)

### Bước 3: Client gọi API business của domain

Sau khi upload thành công, client gửi metadata file vào API xử lý của domain (ví dụ: request, profile, product...).

Ví dụ payload file từ client gửi vào domain API:

```json
{
  "fileName": "invoice-04-2026.pdf",
  "cloudinaryPublicId": "documents/abc_xyz_123",
  "url": "https://res.cloudinary.com/.../file.pdf",
  "fileType": "application/pdf",
  "size": 245000
}
```

### Bước 4: Domain service lưu metadata bằng `FileStorageService`

Trong service của domain, map request file -> `FileStorageRequest` và gọi:

- `save(...)` khi chỉ có 1 file.
- `saveAll(...)` khi có nhiều file.

Kết quả trả về là entity `FileStorage` (hoặc list), domain lưu khóa ngoại `file_storages.id` vào bảng nghiệp vụ của mình.

## 4) Hướng dẫn inject và dùng trong domain service

Ví dụ constructor injection:

```java
@Service
@RequiredArgsConstructor
public class ExpenseRequestService {

    private final FileStorageService fileStorageService;

    public void createRequest(List<FileStorageRequest> attachments) {
        var savedFiles = fileStorageService.saveAll(attachments);
        // Gắn savedFiles.get(i).getId() vào bảng attachment/domain entity
    }
}
```

## 5) Chi tiết các hàm trong `FileStorageService`

### `save(FileStorageRequest request)`

- Mục đích: lưu 1 metadata file vào `file_storages`.
- Input bắt buộc (`FileStorageRequest`):
  - `fileName`
  - `cloudinaryPublicId`
  - `url`
- Input tùy chọn:
  - `fileType`
  - `size`
- Kết quả: trả về `FileStorage` đã persist (có `id`).

### `saveAll(List<FileStorageRequest> requests)`

- Mục đích: lưu nhiều metadata file trong một lần gọi.
- Nếu danh sách `null` hoặc rỗng -> trả về `Collections.emptyList()`.
- Kết quả: trả về danh sách `FileStorage` đã persist.

### `getFile(Long id)`

- Mục đích: lấy metadata 1 file theo `file_storages.id`.
- Kết quả: `FileStorage`.

### `getMutipleFiles(List<Long> ids)`

- Mục đích: lấy nhiều file theo danh sách `id`.
- Nếu danh sách `null` hoặc rỗng -> trả về `Collections.emptyList()`.
- Kết quả: `List<FileStorage>`.

### `deleteFile(Long id)`

- Mục đích: xóa file theo `file_storages.id`.
- Hành vi:
  1. Tìm record trong DB theo `id`.
  2. Gọi `CloudinaryService.deleteFile(cloudinaryPublicId)` để xóa file trên Cloudinary.
  3. Xóa record khỏi `file_storages`.
- Exception:
  - `ResourceNotFoundException("File not found")` khi không tìm thấy `id` trong DB.

## 6) Quy ước dùng chung cho các domain

- Domain service chỉ nên làm việc với `FileStorageService`, không gọi Cloudinary SDK trực tiếp.
- Endpoint business của domain nên nhận metadata file từ client sau khi upload Cloudinary thành công.
- Lưu `file_storages.id` làm khóa ngoại ở domain để quản lý lifecycle file rõ ràng.
- Khi xóa entity nghiệp vụ có file đính kèm, gọi `fileStorageService.deleteFile(fileId)` để dọn cả Cloudinary và DB.
- URL trả cho client dùng trực tiếp từ `FileStorage.url` (public/permanent), không ký lại và không có TTL.
- Không thêm lại các method kiểu `generateSignedUrl(...)` vào `CloudinaryService` hoặc `FileStorageService`.

## 7) Mapping nhanh từ Cloudinary response -> `FileStorageRequest`

- `FileStorageRequest.fileName` <- `original_filename` (hoặc tên file hiển thị do client quy ước)
- `FileStorageRequest.cloudinaryPublicId` <- `public_id`
- `FileStorageRequest.url` <- `secure_url`
- `FileStorageRequest.fileType` <- MIME/type hoặc `format`
- `FileStorageRequest.size` <- `bytes`

---

Nếu cần chuẩn hóa thêm, có thể tạo DTO chung cho metadata upload ở tầng API để các domain dùng cùng một contract request.
