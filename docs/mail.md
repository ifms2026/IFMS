# Mail Module Guide

Tai lieu nay mo ta tong quan module `mail`, cach no van hanh theo Strategy Pattern, va checklist day du de them mot loai email moi.

## 1) Kien truc tong quan

Module mail duoc thiet ke theo mo hinh publish/consume bat dong bo:

1. Domain service goi `MailPublisher.publish(type, to, subject, content)`.
2. `MailPublisher` chon dung `MailStrategy` theo `MailType`.
3. Strategy publish `MailEvent` vao RabbitMQ exchange/routing-key tuong ung.
4. `MailConsumer` nhan message tu queue, goi `BrevoMailService` de gui mail.
5. Neu gui that bai, message se retry theo cau hinh listener; het retry thi route sang DLQ.

## 2) Cac thanh phan trong `modules/mail`

### `publisher/`

- `MailType`: enum loai mail (`ONBOARD`, `WARNING`, `FORGET_PASSWORD`).
- `MailStrategy`: interface chien luoc gui mail cho moi loai.
- `MailPublisher`: facade dispatcher theo Strategy Pattern.
- `publisher/strategy/*`: cac strategy cu the, moi class map 1 `MailType` -> 1 routing-key.

### `config/`

- `MailRabbitMQConfig`: khai bao exchange, DLX, queue, DLQ, binding cho tung loai mail.

### `consumers/`

- `MailEvent`: payload record (`to`, `subject`, `content`).
- `MailConsumer`: `@RabbitListener` cho tung queue va DLQ.

### `service/`

- `BrevoMailService`: contract gui email.
- `BrevoMailServiceImpl`: implementation goi Brevo API + render template Thymeleaf neu can.

## 3) Strategy Pattern dang duoc ap dung

`MailPublisher` inject danh sach `List<MailStrategy>`, sau do map ve `Map<MailType, MailStrategy>`.
Khi domain goi `publish(...)`, module se tu dong dispatch dung strategy ma khong can `if/else` hoac `switch` lon.

Loi ich:

- De mo rong mail type moi.
- Giam coupling giua domain va ha tang RabbitMQ.
- Mỗi loai mail co routing-key/queue rieng de monitor va scale.

## 4) Cach domain khac su dung

Trong domain service (vd: auth, notification, payroll), inject `MailPublisher`:

```java
@Service
@RequiredArgsConstructor
public class SomeDomainService {

    private final MailPublisher mailPublisher;

    public void sendSomething(String email, String htmlContent) {
        mailPublisher.publish(
                MailType.WARNING,
                email,
                "IFMS Notification",
                htmlContent
        );
    }
}
```

Domain chi can biet `MailType`; khong can biet queue/exchange.

## 5) Checklist them loai mail moi (quan trong)

Khi can them mot dang email moi, lam dung thu tu duoi day:

### B1. Them enum moi trong `MailType`

Vi du: `CUSTOM`.

### B2. Them RabbitMQ variables trong `application.yml`

Bo bien moi cho queue, routing-key, dlq, dlq-routing-key.

Hien tai file `application.yml` da co scaffold:

- `spring.rabbitmq.mail.custom.queue`
- `spring.rabbitmq.mail.custom.routing-key`
- `spring.rabbitmq.mail.custom.dlq`
- `spring.rabbitmq.mail.custom.dlq-routing-key`

Khi ap dung thuc te, doi `custom` thanh ten nghiep vu cu the neu can.

### B3. Cap nhat `MailRabbitMQConfig`

Them:

- `@Value` fields cho queue/routing-key/dlq cua loai moi.
- `Queue`, `Binding`, `DLQ`, `DLQ Binding` beans cho loai moi.

### B4. Tao strategy moi trong `publisher/strategy`

Tao class moi implement `MailStrategy`:

- `getType()` tra ve enum moi.
- `publish(...)` gui `MailEvent` vao exchange + routing-key moi.

### B5. Cap nhat `MailConsumer`

Them 2 listener:

- Listener queue chinh de goi `BrevoMailService`.
- Listener queue DLQ de log/monitor loi.

### B6. Cap nhat `BrevoMailService` + `BrevoMailServiceImpl`

- Them method send cho loai moi neu can render template rieng.
- Render template Thymeleaf + map variables.

### B7. Tao template mail moi trong `src/main/resources/templates/email`

File mau da tao san:

- `templates/email/custom-email.html`

Ban co the copy va doi ten theo nghiep vu, vi du: `expense-approved-email.html`.

### B8. Goi publish tu domain service

Sau khi hoan tat cac buoc tren, domain chi can goi:

```java
mailPublisher.publish(MailType.CUSTOM, to, subject, content);
```

## 6) Quy uoc template email

- Dat ten theo nghiep vu, vi du: `warning-email.html`, `salary-paid-email.html`.
- De placeholders Thymeleaf ro rang: `${fullName}`, `${message}`, `${requestCode}`...
- Tranh embed logic qua phuc tap trong template; xu ly data tai service.

## 7) RabbitMQ env vars can co

Cac bien chung:

- `RABBITMQ_MAIL_EXCHANGE`
- `RABBITMQ_MAIL_DLX`

Moi loai mail can toi thieu 4 bien:

- `<TYPE>_QUEUE`
- `<TYPE>_ROUTING_KEY`
- `<TYPE>_DLQ`
- `<TYPE>_DLQ_ROUTING_KEY`

Vi du voi loai `CUSTOM`:

- `RABBITMQ_MAIL_CUSTOM_QUEUE`
- `RABBITMQ_MAIL_CUSTOM_ROUTING_KEY`
- `RABBITMQ_MAIL_CUSTOM_DLQ`
- `RABBITMQ_MAIL_CUSTOM_DLQ_ROUTING_KEY`

## 8) Luu y van hanh

- Neu strategy khong duoc register, `MailPublisher` se throw `InternalSystemException`.
- Mỗi mail type nen co queue rieng de de trace va alert.
- DLQ bat buoc phai co listener log de khong mat dau vet su co.
- Neu loai mail moi can template, phai tao file template truoc khi release.

## 9) Danh sach file lien quan de update khi them mail type moi

- `src/main/java/com/mkwang/backend/modules/mail/publisher/MailType.java`
- `src/main/java/com/mkwang/backend/modules/mail/publisher/strategy/*`
- `src/main/java/com/mkwang/backend/modules/mail/config/MailRabbitMQConfig.java`
- `src/main/java/com/mkwang/backend/modules/mail/consumers/MailConsumer.java`
- `src/main/java/com/mkwang/backend/modules/mail/service/BrevoMailService.java`
- `src/main/java/com/mkwang/backend/modules/mail/service/BrevoMailServiceImpl.java`
- `src/main/resources/templates/email/*.html`
- `src/main/resources/application.yml`

---

Tai lieu nay dung nhu checklist chuan de dam bao them mail type moi dung voi Strategy Pattern hien tai va khong bo sot phan cau hinh RabbitMQ/template.
