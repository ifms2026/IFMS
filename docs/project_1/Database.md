# IFMS Database Dictionary

## 1) Scope and source

This document is generated from:
- `src/main/resources/db/migration/Database.sql` (physical schema)
- `.claude/CLAUDE.md` (domain conventions)
- `docs/API_Spec.md` (API-level DB mapping notes)

It describes:
- each table and what it is used for
- each column and its role
- constraints/indexes that matter for behavior
- relationships between tables and why those relationships exist

## 2) Domain grouping

- Identity and organization: `users`, `roles`, `role_permissions`, `user_profiles`, `user_security_settings`, `departments`
- Project and budgeting: `projects`, `project_phases`, `project_members`, `phase_category_budgets`, `expense_categories`
- Request lifecycle: `requests`, `request_histories`, `request_attachments`, `advance_balances`
- Wallet and money movement: `wallets`, `transactions`, `ledger_entries`, `withdrawal_requests`
- Accounting and payroll: `company_funds`, `payroll_periods`, `payslips`
- Platform services: `file_storages`, `notifications`, `audit_logs`, `system_configs`

## 3) Relationship overview (high level)

- `roles (1) -> (N) users`: each user belongs to one role.
- `departments (1) -> (N) users`: user can belong to one department.
- `users (1) -> (1) user_profiles`: profile extension for HR/bank data.
- `users (1) -> (1) user_security_settings`: transaction PIN/retry lock state.
- `users (1) -> (N) projects` via `projects.manager_id`: project manager.
- `departments (1) -> (N) projects`: project ownership by department.
- `projects (1) -> (N) project_phases`: phase breakdown.
- `projects (N) <-> (N) users` via `project_members`: project staffing.
- `project_phases (N) <-> (N) expense_categories` via `phase_category_budgets`: budget cap per category per phase.
- `users (1) -> (N) requests`: requester ownership.
- `projects/project_phases/expense_categories (1) -> (N) requests`: request context.
- `requests (1) -> (N) request_histories`: approval/audit timeline.
- `requests (N) <-> (N) file_storages` via `request_attachments`: evidence attachments.
- `requests (1) -> (0..1) advance_balances` and `advance_balances (1) -> (1) requests` (advance origin): tracks unsettled advance debt and reimbursement/return progress.
- `wallets (1) -> (N) ledger_entries` and `transactions (1) -> (N) ledger_entries`: double-entry trail per transaction.
- `users (1) -> (N) withdrawal_requests`: withdrawal workflow.
- `transactions (0..1) -> (N) withdrawal_requests`: execution linkage.
- `payroll_periods (1) -> (N) payslips`, `users (1) -> (N) payslips`: payroll outputs.

## 4) Table dictionary

### 4.1 `users`

Purpose: system account, authentication identity, RBAC binding, lifecycle state.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | User identifier. |
| `created_at`, `updated_at` | TIMESTAMP | Audit timestamps from `BaseEntity`. |
| `created_by`, `updated_by` | BIGINT | Actor tracking for audit. |
| `email` | VARCHAR(255), UNIQUE | Login identity (business unique). |
| `password` | VARCHAR(255) | Encoded password hash. |
| `full_name` | VARCHAR(255) | Display/legal name. |
| `is_first_login` | BOOLEAN | Force onboarding/password setup flows. |
| `role_id` | BIGINT FK -> `roles.id` | RBAC role assignment. |
| `department_id` | BIGINT FK -> `departments.id`, nullable | Org placement. |
| `status` | VARCHAR(20) | Account state (ACTIVE/LOCKED/etc). |
| `token_version` | INTEGER | JWT invalidation version (single-session control). |

Indexes/constraints:
- `uc_users_email` unique email.

### 4.2 `roles`

Purpose: RBAC role catalog.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Role identifier. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `name` | VARCHAR(50), UNIQUE | Stable role key (ADMIN, MANAGER, ...). |
| `description` | VARCHAR(255) | Human description. |

### 4.3 `role_permissions`

Purpose: role-to-permission expansion table.

| Column | Type | Meaning |
|---|---|---|
| `role_id` | BIGINT FK -> `roles.id` | Role owner. |
| `permission` | VARCHAR(50) | Authority string (`RESOURCE_ACTION`). |

Relationship purpose:
- keep permissions normalized and queryable without hardcoding all authorities in code.

### 4.4 `departments`

Purpose: organization unit and project budget envelope.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Department id. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `name` | VARCHAR(255) | Department display name. |
| `code` | VARCHAR(20), UNIQUE | Business code (`DEPT_*`). |
| `manager_id` | BIGINT FK -> `users.id`, nullable | Department manager user. |
| `total_project_quota` | DECIMAL(19,2) | Planned top-level budget quota. |
| `total_available_balance` | DECIMAL(19,2) | Remaining distributable budget. |

### 4.5 `user_profiles`

Purpose: user extension data (HR + banking + avatar), separated from auth core.

| Column | Type | Meaning |
|---|---|---|
| `user_id` | BIGINT PK/FK -> `users.id` | 1:1 extension key. |
| `employee_code` | VARCHAR(20), UNIQUE | Employee business id. |
| `job_title` | VARCHAR(100) | Position title. |
| `phone_number` | VARCHAR(15), UNIQUE | Contact number. |
| `date_of_birth` | DATE | HR detail. |
| `citizen_id` | VARCHAR(20) | National id/passport id. |
| `address` | VARCHAR(255) | Current address. |
| `avatar_file_id` | BIGINT FK -> `file_storages.id` | Avatar file reference. |
| `bank_name` | VARCHAR(100) | Default payout bank name. |
| `bank_account_num` | VARCHAR(30) | Default payout account number. |
| `bank_account_owner` | VARCHAR(100) | Account holder name. |

### 4.6 `user_security_settings`

Purpose: transaction security controls for each user.

| Column | Type | Meaning |
|---|---|---|
| `user_id` | BIGINT PK/FK -> `users.id` | 1:1 security settings key. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `transaction_pin` | VARCHAR(100) | Encoded transaction PIN hash. |
| `retry_count` | INTEGER | Failed PIN attempts counter. |
| `locked_until` | TIMESTAMP | Temporary lock expiration. |

### 4.7 `file_storages`

Purpose: metadata registry for uploaded files.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | File id used by other modules. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `file_name` | VARCHAR(255) | Original or display filename. |
| `cloudinary_public_id` | VARCHAR(255), UNIQUE | Cloudinary resource id. |
| `url` | TEXT | Public URL returned to clients. |
| `file_type` | VARCHAR(100) | MIME or logical type. |
| `size` | BIGINT | File size in bytes. |

### 4.8 `projects`

Purpose: project master record and budget container.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Project id. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `project_code` | VARCHAR(30), UNIQUE | Business code (`PRJ_*`). |
| `name` | VARCHAR(255) | Project name. |
| `description` | TEXT | Business context. |
| `department_id` | BIGINT FK -> `departments.id` | Owning department. |
| `manager_id` | BIGINT FK -> `users.id` | Responsible manager. |
| `total_budget` | DECIMAL(19,2) | Approved budget cap. |
| `available_budget` | DECIMAL(19,2) | Remaining budget. |
| `total_spent` | DECIMAL(19,2) | Aggregate spent amount. |
| `status` | VARCHAR(20) | Project state. |
| `current_phase_id` | BIGINT FK -> `project_phases.id`, UNIQUE, nullable | Current active phase pointer. |

### 4.9 `project_phases`

Purpose: phase decomposition under one project with own budget tracking.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Phase id. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `phase_code` | VARCHAR(30), UNIQUE | Business code (`PHASE_*`). |
| `project_id` | BIGINT FK -> `projects.id` | Parent project. |
| `name` | VARCHAR(255) | Phase title. |
| `budget_limit` | DECIMAL(19,2) | Phase budget cap. |
| `current_spent` | DECIMAL(19,2) | Running spent amount. |
| `status` | VARCHAR(20) | Phase status. |
| `start_date`, `end_date` | DATE | Planned timeline. |

### 4.10 `project_members`

Purpose: project-team membership (many-to-many between users and projects).

| Column | Type | Meaning |
|---|---|---|
| `project_id` | BIGINT PK/FK -> `projects.id` | Project reference. |
| `user_id` | BIGINT PK/FK -> `users.id` | Member user. |
| `project_role` | VARCHAR(20) | Role in project (LEADER/MEMBER/etc). |
| `position` | VARCHAR(100) | Team position label. |
| `joined_at` | TIMESTAMP | Membership start time. |

Relationship purpose:
- enables one user joining many projects and each project having many members.

### 4.11 `expense_categories`

Purpose: expense classification used by requests and budget control.
Categories are either **system-wide** (`project_id IS NULL`, seeded, not deletable) or **project-specific** (`project_id IS NOT NULL`, created by Team Leader for a single project).

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Category id. |
| `name` | VARCHAR(255) | Category key/name. |
| `description` | TEXT | Category explanation. |
| `is_system_default` | BOOLEAN | True for seeded categories — cannot be deleted. |
| `project_id` | BIGINT FK -> `projects.id`, nullable | Null = system-wide; non-null = visible only within this project. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Metadata | Ownership/audit fields. |

Indexes/constraints:
- `uidx_expense_cat_name_system` — unique `name` where `project_id IS NULL` (system categories cannot share names).
- `uidx_expense_cat_name_project` — unique `(name, project_id)` where `project_id IS NOT NULL` (unique name within each project).
- The old global `UNIQUE (name)` constraint (`uc_expense_categories_name`) was dropped in V14.

### 4.12 `phase_category_budgets`

Purpose: per-phase per-category budget matrix.

| Column | Type | Meaning |
|---|---|---|
| `phase_id` | BIGINT PK/FK -> `project_phases.id` | Phase reference. |
| `category_id` | BIGINT PK/FK -> `expense_categories.id` | Category reference. |
| `budget_limit` | DECIMAL(19,2) | Spending ceiling for this pair. |
| `current_spent` | DECIMAL(19,2) | Current spend in this pair. |

Relationship purpose:
- enforces granular budget governance, not only project-level cap.

### 4.13 `requests`

Purpose: central business request (advance, reimbursement, payment, topup, etc.) with approval status.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Request id. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `request_code` | VARCHAR(30), UNIQUE | Business request code. |
| `requester_id` | BIGINT FK -> `users.id` | User who created request. |
| `project_id` | BIGINT FK -> `projects.id`, nullable | Related project. |
| `phase_id` | BIGINT FK -> `project_phases.id`, nullable | Related phase. |
| `category_id` | BIGINT FK -> `expense_categories.id`, nullable | Expense category. |
| `type` | VARCHAR(20) | Request type enum. |
| `advance_balance_id` | BIGINT FK -> `advance_balances.id`, nullable | Linked outstanding advance debt. |
| `amount` | DECIMAL(19,2) | Requested amount. |
| `approved_amount` | DECIMAL(19,2), nullable | Approved amount. |
| `status` | VARCHAR(25) | Workflow status. |
| `reject_reason` | TEXT | Reason when rejected. |
| `description` | TEXT | Request narrative. |
| `paid_at` | TIMESTAMP, nullable | Payment completion timestamp. |

Indexes:
- requester/status, project/status, status/type, request_code.

### 4.14 `request_histories`

Purpose: immutable timeline of request actions.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | History id. |
| `request_id` | BIGINT FK -> `requests.id` | Request reference. |
| `actor_id` | BIGINT FK -> `users.id` | User who performed action. |
| `action` | VARCHAR(20) | Action verb (SUBMIT/APPROVE/REJECT...). |
| `status_after_action` | VARCHAR(25) | Request status after this step. |
| `comment` | VARCHAR(500) | Optional note. |
| `created_at` | TIMESTAMP | Action time. |

### 4.15 `request_attachments`

Purpose: many-to-many link between request and uploaded file evidence.

| Column | Type | Meaning |
|---|---|---|
| `request_id` | BIGINT PK/FK -> `requests.id` | Request owner. |
| `file_id` | BIGINT PK/FK -> `file_storages.id` | Attached file metadata. |

### 4.16 `advance_balances`

Purpose: tracks outstanding debt from advance requests until fully settled.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Advance balance id. |
| `user_id` | BIGINT FK -> `users.id` | Debt owner. |
| `advance_request_id` | BIGINT FK -> `requests.id`, UNIQUE | Origin advance request. |
| `original_amount` | DECIMAL(19,2) | Initial advanced money. |
| `reimbursed_amount` | DECIMAL(19,2) | Amount settled by reimbursement requests. |
| `returned_amount` | DECIMAL(19,2) | Amount directly returned. |
| `remaining_amount` | DECIMAL(19,2) | Current outstanding debt. |
| `status` | VARCHAR(20) | OPEN/SETTLED/etc. |
| `settled_at` | TIMESTAMP, nullable | Settlement completion time. |
| `created_at` | TIMESTAMP | Created time. |

Index:
- `idx_advance_balance_user_status` supports debt checks by user/status.

### 4.17 `wallets`

Purpose: balance holder for an owner (`USER`, possibly other owner types).

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Wallet id. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `owner_type` | VARCHAR(20) | Owner discriminator (USER/PROJECT/etc). |
| `owner_id` | BIGINT | Owner entity id. |
| `balance` | DECIMAL(19,2) | Available balance. |
| `locked_balance` | DECIMAL(19,2) | Reserved amount pending settlement. |
| `version` | BIGINT | Optimistic locking version. |

Constraints:
- `uk_wallet_owner (owner_type, owner_id)` ensures one wallet per owner.

### 4.18 `transactions`

Purpose: financial transaction header (business event + gateway info + references).

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Transaction id. |
| `transaction_code` | VARCHAR(30), UNIQUE | Business transaction code. |
| `amount` | DECIMAL(19,2) | Transaction amount. |
| `type` | VARCHAR(30) | Transaction category (DEPOSIT, WITHDRAW, PAYROLL...). |
| `status` | VARCHAR(20) | Processing status. |
| `payment_ref` | VARCHAR(100) | External payment reference. |
| `gateway_provider` | VARCHAR(20) | Gateway name/source. |
| `reference_type` | VARCHAR(30) | Polymorphic business source type. |
| `reference_id` | BIGINT | Polymorphic business source id. |
| `description` | TEXT | Transaction note. |
| `created_at` | TIMESTAMP | Creation time. |

Indexes:
- transaction code, created_at, and reference tuple for tracing.

### 4.19 `ledger_entries`

Purpose: append-only debit/credit lines linked to transaction and wallet (double-entry auditability).

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Ledger line id. |
| `transaction_id` | BIGINT FK -> `transactions.id` | Parent transaction. |
| `wallet_id` | BIGINT FK -> `wallets.id` | Affected wallet. |
| `direction` | VARCHAR(10) | DEBIT or CREDIT. |
| `amount` | DECIMAL(19,2) | Moved amount. |
| `balance_after` | DECIMAL(19,2) | Wallet balance after posting. |
| `created_at` | TIMESTAMP | Ledger posting timestamp. |

Indexes:
- wallet/time and transaction id for statement and reconciliation queries.

### 4.20 `withdrawal_requests`

Purpose: user withdrawal workflow from request to accountant execution.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Withdrawal id. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `withdraw_code` | VARCHAR(30), UNIQUE | Withdrawal business code. |
| `user_id` | BIGINT FK -> `users.id` | Request owner. |
| `amount` | DECIMAL(19,2) | Withdrawal amount. |
| `credit_account` | VARCHAR(30) | Destination account number. |
| `credit_account_name` | VARCHAR(100) | Destination account owner. |
| `credit_bank_code` | VARCHAR(20) | Destination bank code. |
| `credit_bank_name` | VARCHAR(100) | Destination bank name. |
| `user_note` | VARCHAR(500) | User-provided note. |
| `status` | VARCHAR(20) | Workflow status. |
| `bank_transaction_id` | VARCHAR(50) | Bank/external transfer id. |
| `accountant_note` | VARCHAR(500) | Ops/accounting note. |
| `executed_by` | BIGINT | Operator user id (not FK constrained). |
| `executed_at` | TIMESTAMP | Execution time. |
| `transaction_id` | BIGINT | Linked transaction id (logical link, no FK constraint in this file). |
| `failure_reason` | VARCHAR(500) | Failure explanation. |

Indexes:
- by `user_id`, `status`, `created_at` for list/filter screens.

### 4.21 `payroll_periods`

Purpose: payroll cycle definition.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Period id. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `period_code` | VARCHAR(30), UNIQUE | Payroll period code. |
| `name` | VARCHAR(255) | Display name. |
| `month`, `year` | INTEGER | Accounting period values. |
| `start_date`, `end_date` | DATE | Period boundary dates. |
| `status` | VARCHAR(20) | OPEN/CLOSED/etc. |

### 4.22 `payslips`

Purpose: salary calculation result per employee per period.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Payslip id. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `payslip_code` | VARCHAR(30), UNIQUE | Payslip business code. |
| `period_id` | BIGINT FK -> `payroll_periods.id` | Payroll period. |
| `user_id` | BIGINT FK -> `users.id` | Employee user. |
| `base_salary` | DECIMAL(19,2) | Base salary amount. |
| `bonus` | DECIMAL(19,2) | Bonus amount. |
| `allowance` | DECIMAL(19,2) | Allowance amount. |
| `deduction` | DECIMAL(19,2) | Deduction amount. |
| `advance_deduct` | DECIMAL(19,2) | Deducted outstanding advance debt. |
| `final_net_salary` | DECIMAL(19,2) | Net payable salary. |
| `status` | VARCHAR(20) | Draft/approved/paid state. |
| `payment_date` | TIMESTAMP | Actual payment timestamp. |

### 4.23 `company_funds`

Purpose: company-level treasury/bank snapshot metadata.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Fund record id. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `bank_account` | VARCHAR(30) | Main company bank account. |
| `bank_name` | VARCHAR(100) | Bank provider name. |
| `external_bank_balance` | DECIMAL(19,2) | Last known external balance. |
| `last_statement_date` | DATE | Statement date. |
| `last_statement_updated_by` | BIGINT | User id who updated statement. |

### 4.24 `notifications`

Purpose: persisted in-app notification inbox.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Notification id. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `user_id` | BIGINT FK -> `users.id` | Recipient user. |
| `title` | VARCHAR(255) | Notification title. |
| `message` | TEXT | Notification content. |
| `type` | VARCHAR(30) | Notification type/category. |
| `ref_id` | BIGINT | Related entity id (optional). |
| `ref_type` | VARCHAR(50) | Related entity type (optional). |
| `is_read` | BOOLEAN | Read/unread state. |

### 4.25 `audit_logs`

Purpose: append-only audit trail for entity changes and actions.

| Column | Type | Meaning |
|---|---|---|
| `id` | BIGINT PK | Audit id. |
| `trace_id` | VARCHAR(36) | Request/correlation id across services. |
| `actor_id` | BIGINT FK -> `users.id`, nullable | User who triggered event. |
| `action` | VARCHAR(50) | Action name. |
| `entity_name` | VARCHAR(100) | Target entity/table name. |
| `entity_id` | VARCHAR(100) | Target row id (string for flexibility). |
| `old_values` | JSONB | Previous snapshot payload. |
| `new_values` | JSONB | New snapshot payload. |
| `created_at` | TIMESTAMP | Event time. |

Indexes:
- `idx_audit_trace`, `idx_audit_entity` for forensic query speed.

### 4.26 `system_configs`

Purpose: key-value configuration store managed in DB.

| Column | Type | Meaning |
|---|---|---|
| `config_key` | VARCHAR(100) PK | Configuration key name. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Base fields | Metadata. |
| `config_value` | VARCHAR(255) | Value content. |
| `description` | TEXT | Meaning and usage note. |

## 5) Detailed relationship catalog and purpose

- `users.role_id -> roles.id`
  - Purpose: enforce one authoritative RBAC role per user.
- `users.department_id -> departments.id`
  - Purpose: drive department-scoped filtering in manager workflows.
- `departments.manager_id -> users.id`
  - Purpose: assign accountable manager for department-level approvals.
- `user_profiles.user_id -> users.id`
  - Purpose: strict 1:1 split between auth identity and profile data.
- `user_profiles.avatar_file_id -> file_storages.id`
  - Purpose: reuse generic file storage metadata for profile avatar.
- `user_security_settings.user_id -> users.id`
  - Purpose: strict 1:1 PIN/lock controls per user.
- `projects.department_id -> departments.id`
  - Purpose: budget ownership and access scope by department.
- `projects.manager_id -> users.id`
  - Purpose: project accountability and manager-specific queues.
- `projects.current_phase_id -> project_phases.id` (unique)
  - Purpose: points to active phase while keeping full phase history.
- `project_phases.project_id -> projects.id`
  - Purpose: phase lifecycle attached to one project.
- `project_members.project_id -> projects.id`, `project_members.user_id -> users.id`
  - Purpose: many-to-many staffing model.
- `expense_categories.project_id -> projects.id` (nullable)
  - Purpose: scope a custom category to one project. Null = system-wide (all projects can use it).
- `phase_category_budgets.phase_id -> project_phases.id`, `phase_category_budgets.category_id -> expense_categories.id`
  - Purpose: spending limit per category inside a phase.
- `requests.requester_id -> users.id`
  - Purpose: ownership and self-service filtering (`my requests`).
- `requests.project_id -> projects.id`, `requests.phase_id -> project_phases.id`, `requests.category_id -> expense_categories.id`
  - Purpose: tie request to budget context and reporting dimensions.
- `requests.advance_balance_id -> advance_balances.id`
  - Purpose: link reimbursement/settlement request to outstanding advance debt.
- `advance_balances.user_id -> users.id`
  - Purpose: debt tracked per employee.
- `advance_balances.advance_request_id -> requests.id` (unique)
  - Purpose: one advance request creates at most one debt tracker.
- `request_histories.request_id -> requests.id`, `request_histories.actor_id -> users.id`
  - Purpose: immutable audit timeline with actor attribution.
- `request_attachments.request_id -> requests.id`, `request_attachments.file_id -> file_storages.id`
  - Purpose: normalized evidence attachment model.
- `notifications.user_id -> users.id`
  - Purpose: per-user notification inbox.
- `audit_logs.actor_id -> users.id`
  - Purpose: who performed audited action.
- `payslips.period_id -> payroll_periods.id`, `payslips.user_id -> users.id`
  - Purpose: payroll output by employee and period.
- `ledger_entries.transaction_id -> transactions.id`, `ledger_entries.wallet_id -> wallets.id`
  - Purpose: trace each transaction impact on each wallet (double-entry).
- `withdrawal_requests.user_id -> users.id`
  - Purpose: owner linkage for withdrawal workflow.
- `role_permissions.role_id -> roles.id`
  - Purpose: role authority expansion.

## 6) Notes and caveats

- `withdrawal_requests.transaction_id` appears as a logical reference but has no explicit FK in this schema file.
- `withdrawal_requests.executed_by` also has no explicit FK to `users.id` in this schema file.
- `requests` and `advance_balances` form a bidirectional relationship by FK columns (`requests.advance_balance_id` and `advance_balances.advance_request_id`), used to model advance origin and settlement lifecycle.
- Money fields consistently use `DECIMAL(19,2)` for financial precision.
- Append-only intent is clear for `audit_logs`, `ledger_entries`, and `request_histories`.

