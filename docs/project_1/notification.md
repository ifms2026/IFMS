# Notification Module Guide

Tai lieu nay mo ta module `notification` de cac domain khac co the tich hop dung cach: publish event, luu lich su thong bao, push realtime qua SSE, va quan ly vong doi thong bao.

## 1) Kien truc tong quan

Module notification chay theo mo hinh async qua RabbitMQ:

1. Domain service goi `NotificationService.notify(...)`.
2. `NotificationServiceImpl` publish `NotificationEvent` vao RabbitMQ qua `NotificationPublisher`.
3. `NotificationConsumer` nhan event tu queue.
4. Consumer persist vao bang `notifications`.
5. Consumer push realtime qua SSE stream `/notifications/stream` (best-effort).

Nguyen tac quan trong:

- Persist truoc, push sau.
- Neu SSE push fail, thong bao van nam trong DB de UI doc lai qua API.

## 2) Cac thanh phan chinh trong module

### `config/NotificationRabbitMQConfig`

- Khai bao topology cho notification:
  - `notificationExchange`
  - `notificationQueue`
  - `notificationDLX`
  - `notificationDLQ`
- Bind queue voi routing key `spring.rabbitmq.notification.routing-key`.

### `publisher/NotificationPublisher`

- Dung `RabbitTemplate.convertAndSend(exchange, routingKey, event)`.
- Khong xu ly business logic tai day; chi publish event.

### `publisher/NotificationEvent`

Payload event duoc truyen qua RabbitMQ:

- `userId`
- `userEmail` (legacy field, khong con dung cho realtime SSE push)
- `type` (string tu `NotificationType.name()`)
- `title`
- `message`
- `refId`
- `refType`

### `consumer/NotificationConsumer`

- `@RabbitListener` queue chinh: consume event, parse `NotificationType`, save DB, push SSE.
- `@RabbitListener` DLQ: log warning cho monitoring.
- Neu `type` khong hop le: throw `InternalSystemException` -> retry -> co the vao DLQ.
- Khi push SSE, consumer goi `SseService.sendToUser(userId, SseEvent)` (service dung chung tai `common`).

### `common/sse/SseService`

- Service SSE dung chung cross-module, quan ly ket noi theo tung `userId`.
- `connect(userId)`: tao `SseEmitter`, dang ky cleanup (`onCompletion`, `onTimeout`, `onError`) va gui event `connected`.
- `sendToUser(userId, SseEvent)`: push event theo `SseEvent.event`, payload la `SseEvent.data`.

### `common/dto/SseEvent`

- DTO command cho luong SSE noi bo:
  - `event`: ten SSE event (vi du: `notification`)
  - `data`: payload gui cho client (vi du: `NotificationDto`)

### `service/NotificationService` + `NotificationServiceImpl`

Cung cap API cho domain va API cho frontend:

- `notify(...)`: publish async event.
- `getNotifications(...)`: lay danh sach thong bao phan trang.
- `getUnreadCount(...)`: dem thong bao chua doc.
- `markAsRead(...)`: danh dau da doc 1 thong bao (co check owner).
- `markAllAsRead(...)`: danh dau da doc tat ca thong bao cua user.
- `deleteReadNotifications()`: xoa tat ca thong bao da doc.

### `scheduler/NotificationScheduler`

- Job dinh ky xoa thong bao da doc.
- Cron: `app.notification.cleanup-cron` (default `0 0 0 * * *`).

## 3) NotificationType hien tai

Enum `NotificationType` dang co cac nhom:

- Request flow: `REQUEST_SUBMITTED`, `REQUEST_APPROVED_BY_TL`, `REQUEST_REJECTED`, `REQUEST_PAID`
- Top-up flow: `PROJECT_TOPUP_APPROVED`, `PROJECT_TOPUP_REJECTED`, `DEPT_TOPUP_APPROVED`, `DEPT_TOPUP_REJECTED`
- Payroll: `SALARY_PAID`
- System/Security: `SYSTEM`, `SECURITY_ALERT`

Luu y: field `type` trong `NotificationEvent` phai khop chinh xac `NotificationType.name()`.

## 4) API contract cho frontend

Controller: `NotificationController` (`/notifications`)

- `GET /notifications?isRead={true|false}&type={NOTIFICATION_TYPE}&page=1&limit=20`
  - tra `ApiResponse<NotificationListResponse>`
  - `isRead`, `type` la optional
  - `page` bat dau tu `1`
- `GET /notifications/unread-count`
  - tra `ApiResponse<Long>`
- `GET /notifications/stream`
  - mo kenh SSE (`text/event-stream`) cho user hien tai
  - event realtime: `notification` (data la `NotificationDto`)
- `PATCH /notifications/{id}/read`
  - danh dau da doc 1 thong bao
- `PATCH /notifications/read-all`
  - danh dau da doc tat ca

Quyen:

- Service methods REST duoc bao ve boi `@PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")`.

## 5) SSE realtime flow

Thong so lien quan:

- SSE endpoint: `/notifications/stream`
- Content-Type: `text/event-stream`
- Event name: `notification`

Consumer push:

- `sseService.sendToUser(event.userId(), SseEvent.builder().event("notification").data(NotificationDto).build())`

Luu y:

- `SseEvent` la DTO noi bo de quy dinh event name + payload cho `SseService`.
- Frontend van nhan SSE event name `notification` voi data la `NotificationDto` (khong doi contract client).

Frontend thuong subscribe:

- `GET /notifications/stream` (EventSource)

## 6) Cach domain khac publish notification

Trong domain service, inject `NotificationService` va goi `notify(...)`.

```java
@Service
@RequiredArgsConstructor
public class SomeDomainService {

    private final NotificationService notificationService;

    public void afterBusinessAction(Long userId, String userEmail, Long refId) {
        notificationService.notify(
                userId,
                userEmail,
                NotificationType.REQUEST_SUBMITTED,
                "Request moi can duyet",
                "Ban co mot request moi can xu ly.",
                refId,
                "REQUEST"
        );
    }
}
```

Best practice:

- Goi `notify(...)` sau khi business state da commit hop le.
- `title` ngan gon, `message` ro rang cho user cuoi.
- `refType` dung format on dinh theo domain (`REQUEST`, `PAYSLIP`, `PROJECT`, ...).

## 7) Quy trinh them notification event moi

Khi can them loai thong bao moi, lam theo thu tu:

1. Them enum moi trong `NotificationType`.
2. Tai domain service, goi `notificationService.notify(...)` voi enum moi.
3. Dam bao payload `refId/refType` map dung entity de frontend dieu huong.
4. Neu frontend can icon/mau rieng theo type, update map UI theo enum moi.
5. Test ca 2 kenh:
   - API pull (`GET /notifications`)
   - SSE push (`GET /notifications/stream`)

Thong thuong KHONG can sua `NotificationRabbitMQConfig` khi chi them type moi, vi notification module dang dung 1 queue chung cho tat ca type.

## 8) Cac bien cau hinh lien quan

Trong `application.yml`:

- RabbitMQ notification:
  - `spring.rabbitmq.notification.exchange`
  - `spring.rabbitmq.notification.dlx`
  - `spring.rabbitmq.notification.queue`
  - `spring.rabbitmq.notification.routing-key`
  - `spring.rabbitmq.notification.dlq`
  - `spring.rabbitmq.notification.dlq-routing-key`
- Scheduler cleanup:
  - `app.notification.cleanup-cron`

Env vars tuong ung:

- `RABBITMQ_NOTIFICATION_EXCHANGE`
- `RABBITMQ_NOTIFICATION_DLX`
- `RABBITMQ_NOTIFICATION_QUEUE`
- `RABBITMQ_NOTIFICATION_ROUTING_KEY`
- `RABBITMQ_NOTIFICATION_DLQ`
- `RABBITMQ_NOTIFICATION_DLQ_ROUTING_KEY`
- `NOTIFICATION_CLEANUP_CRON`

## 9) Danh sach file thuong phai su dung khi tich hop

- `src/main/java/com/mkwang/backend/modules/notification/service/NotificationService.java`
- `src/main/java/com/mkwang/backend/modules/notification/service/NotificationServiceImpl.java`
- `src/main/java/com/mkwang/backend/modules/notification/publisher/NotificationEvent.java`
- `src/main/java/com/mkwang/backend/modules/notification/publisher/NotificationPublisher.java`
- `src/main/java/com/mkwang/backend/modules/notification/consumer/NotificationConsumer.java`
- `src/main/java/com/mkwang/backend/modules/notification/entity/NotificationType.java`
- `src/main/java/com/mkwang/backend/modules/notification/controller/NotificationController.java`
- `src/main/java/com/mkwang/backend/common/sse/SseService.java`
- `src/main/java/com/mkwang/backend/common/dto/SseEvent.java`
- `src/main/resources/application.yml`

---

Tai lieu nay la checklist de domain team tich hop notification dung flow async + realtime, khong bo sot DLQ va cleanup van hanh.
