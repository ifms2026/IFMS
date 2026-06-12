# Internal Financial Management System (IFMS) Backend

Spring Boot backend for managing internal company finance operations: budget allocation, project funding, employee requests, payroll, wallet ledger, and real-time notifications.

## Table of Contents

- [1. Overview](#1-overview)
- [2. Core Capabilities](#2-core-capabilities)
- [3. Technology Stack](#3-technology-stack)
- [4. High-Level Architecture](#4-high-level-architecture)
- [5. Domain Modules](#5-domain-modules)
- [6. Prerequisites](#6-prerequisites)
- [7. Local Setup](#7-local-setup)
- [8. Configuration](#8-configuration)
- [9. API and Realtime](#9-api-and-realtime)
- [10. Data and Migrations](#10-data-and-migrations)
- [11. Development Conventions](#11-development-conventions)
- [12. Testing and Build](#12-testing-and-build)
- [13. Troubleshooting](#13-troubleshooting)
- [14. Project Documentation](#14-project-documentation)

## 1. Overview

**IFMS (Internal Financial Management System)** is a backend platform for controlling cash flow and financial operations across departments and projects.

Primary goals:
- Centralize financial workflows (requests, approvals, disbursement, payroll).
- Preserve traceability through audit and wallet ledger.
- Enforce role-based access control and secure authentication.
- Support asynchronous and real-time communication for a responsive user experience.

Base runtime configuration:
- Base URL: `http://localhost:8080/api/v1`
- Main package: `com.mkwang.backend`
- Default app name: `IFMS-Backend`

## 2. Core Capabilities

- JWT authentication with refresh token flow and single-session enforcement via token version.
- Dynamic RBAC (roles + permissions) with method-level authorization.
- Internal wallet system with multi-owner model and double-entry ledger concepts.
- Request and approval flows for financial operations (department, project, employee).
- Async processing with RabbitMQ for notification, audit, and mail workflows.
- Real-time notifications via Server-Sent Events (SSE) with per-user stream handling.
- File metadata and upload flow integrated with Cloudinary.
- Configuration and cache support with Redis.

## 3. Technology Stack

- Java 21
- Spring Boot 3.4.x
- Spring Security + JJWT
- Spring Data JPA + Hibernate 6
- PostgreSQL
- Flyway
- Redis (Lettuce)
- RabbitMQ (AMQP)
- Cloudinary SDK
- Brevo API (mail)
- SpringDoc OpenAPI (Swagger UI)
- Maven

## 4. High-Level Architecture

### Request/Command Flow

1. Client calls REST API.
2. JWT filter authenticates and attaches user context.
3. Service layer applies authorization (`@PreAuthorize`) and business rules.
4. Data persistence runs through JPA repositories.
5. Domain events are published asynchronously (RabbitMQ) when needed.
6. Consumers persist side effects (audit/notification/mail) and perform realtime push (SSE).

### Async and Realtime

- **RabbitMQ** handles decoupled processing for:
  - Audit logs
  - Mail delivery
  - Notifications
- **SSE** provides user-targeted realtime updates through `/notifications/stream`.

## 5. Domain Modules

Project source root: `src/main/java/com/mkwang/backend/modules`

- `auth`: login, refresh token, password-related flows
- `audit`: async audit pipeline and persistence
- `mail`: queue-driven transactional email
- `file`: file storage metadata and upload-signature flow
- `user`: user, role, permission management
- `profile`: profile and security settings (PIN, etc.)
- `organization`: department domain
- `project`: project, phase, member, budgeting
- `expense`: expense category domain
- `request`: request lifecycle and related entities
- `wallet`: wallet, transaction, ledger entries, withdraw/deposit-related workflows
- `accounting`: payroll and accounting flows
- `notification`: notification persistence + delivery
- `config`: system configuration management

## 6. Prerequisites

Install and verify:
- JDK 21+
- Maven 3.8+ (or use included Maven Wrapper)
- Docker Desktop (for PostgreSQL, Redis, RabbitMQ)

Optional but recommended:
- IntelliJ IDEA
- Postman

## 7. Local Setup

### 7.1 Start infrastructure services

```powershell
Set-Location "D:\InternalFinancalManagementSystem"
docker compose up -d
```

Default service ports:
- PostgreSQL: `5433`
- Redis: `6379`
- RabbitMQ AMQP: `5672`
- RabbitMQ Management UI: `15672` (`guest/guest`)

### 7.2 Run the backend

Using Maven Wrapper (Windows):

```powershell
Set-Location "D:\InternalFinancalManagementSystem"
.\mvnw.cmd spring-boot:run
```

Or build first, then run:

```powershell
Set-Location "D:\InternalFinancalManagementSystem"
.\mvnw.cmd clean package -DskipTests
java -jar .\target\SpringProjectTemplate-0.0.1-SNAPSHOT.jar
```

### 7.3 Verify startup

- API root with context path should be reachable at `http://localhost:8080/api/v1`.
- Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`

## 8. Configuration

Main configuration file: `src/main/resources/application.yml`

The app supports environment-variable overrides for all major dependencies.

### Common environment variables

- Database:
  - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- Redis:
  - `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- RabbitMQ:
  - `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`
- JWT:
  - `JWT_SECRET_KEY`, `JWT_EXPIRATION`, `JWT_REFRESH_EXPIRATION`
- Mail / External:
  - `BREVO_API_KEY`, `MAIL_FROM_EMAIL`, `MAIL_FROM_NAME`
  - `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`
- Application:
  - `SERVER_PORT`, `LOG_LEVEL`, `OTP_TTL_MS`, `OTP_LENGTH`

## 9. API and Realtime

### REST API

- Context path: `/api/v1`
- OpenAPI docs via SpringDoc
- API contract reference: `docs/API_Spec.md`

### Notification SSE

- Endpoint: `GET /notifications/stream`
- Content type: `text/event-stream`
- Uses shared SSE service in `common` to manage per-user emitters.

## 10. Data and Migrations

- Schema is managed by Flyway scripts in `src/main/resources/db/migration`.
- JPA schema mode uses validation (`ddl-auto: validate`), not auto-create.
- PostgreSQL is the primary relational database.

## 11. Development Conventions

Project conventions are defined in:
- `.claude/CLAUDE.md`

Important standards used by this backend:
- Every endpoint returns `ResponseEntity<ApiResponse<T>>`.
- Paginated data must use `PageResponse<T>` as `ApiResponse.data`.
- Cross-domain access should go through service interfaces, not foreign repositories.
- Schema changes must be delivered via new Flyway migration files.
- Exceptions must use project-specific `BaseException` hierarchy.

## 12. Testing and Build

Run tests:

```powershell
Set-Location "D:\InternalFinancalManagementSystem"
.\mvnw.cmd test
```

Compile only:

```powershell
Set-Location "D:\InternalFinancalManagementSystem"
.\mvnw.cmd -DskipTests compile
```

Package artifact:

```powershell
Set-Location "D:\InternalFinancalManagementSystem"
.\mvnw.cmd clean package
```

## 13. Troubleshooting

- **Cannot connect to PostgreSQL (`localhost:5433`)**
  - Ensure Docker services are running: `docker compose ps`
- **RabbitMQ not available**
  - Check `http://localhost:15672` and container health
- **Flyway validation/migration issues**
  - Confirm DB user has schema permissions
  - Verify migration order and naming `V{N}__DESCRIPTION.sql`
- **401 / auth problems**
  - Check JWT secret and expiration env vars
  - Validate token version/session logic if forced logout occurs

## 14. Project Documentation

Detailed guides in `docs/`:

- `docs/API_Spec.md` — endpoint contracts and examples
- `docs/financial-architecture.md` — wallet tiers, ledger, reconciliation
- `docs/security-architecture.md` — JWT, session policy, audit, mail security
- `docs/rbac-model.md` — role/permission model
- `docs/entity-conventions.md` — entity and persistence guidelines
- `docs/business-codes.md` — business code generation rules
- `docs/deposit-withdraw.md` — deposit/withdraw implementation notes
- `docs/file-storage.md` — file storage strategy and lifecycle
- `docs/mail.md` — mail queue architecture
- `docs/notification.md` — notification flow and realtime delivery
- `docs/system-config.md` — runtime system config + cache
- `docs/implementation-status.md` — completed vs pending features

---

For contributors: start with `.claude/CLAUDE.md` and `docs/API_Spec.md` before implementing new endpoints.
