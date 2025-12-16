# Data Model: Approval Workflow

**Feature**: Multi-Level Approval Workflow
**Date**: 2025-12-16
**Status**: Complete

This document defines the entities and relationships for the approval workflow feature.

## Entity Relationship Diagram

```
┌─────────────────┐
│      User       │
│ (EXISTING)      │
│─────────────────│
│ id (PK)         │
│ username        │
│ role            │◄────────────┐
│ created_at      │             │
└─────────────────┘             │
         │                      │
         │ creates              │ approver
         ▼                      │
┌─────────────────┐     ┌──────┴───────────┐
│      Todo       │     │ ApprovalRecord   │
│ (EXISTING+MOD)  │     │     (NEW)        │
│─────────────────│     │──────────────────│
│ id (PK)         │     │ id (PK)          │
│ title           │     │ approval_request │ FK
│ description     │     │ approver_id      │ FK ──┘
│ level           │     │ decision         │ ENUM
│ completed       │     │ comments         │
│ user_id (FK)    │     │ created_at       │
│ created_at      │     │ version          │ (optimistic lock)
│ status          │ NEW └──────────────────┘
└─────────────────┘             ▲
         │                      │
         │ references           │
         ▼                      │ records
┌─────────────────┐     ┌──────┴───────────┐
│ ApprovalRequest │────►│                  │
│     (NEW)       │ 1:N │                  │
│─────────────────│     └──────────────────┘
│ id (PK)         │
│ item_type       │ ENUM (TODO, INVOICE)
│ item_id         │ (polymorphic reference)
│ creator_id      │ FK → User
│ status          │ ENUM (PENDING, APPROVED, REJECTED, WITHDRAWN)
│ created_at      │
│ updated_at      │
│ version         │ (optimistic lock)
└─────────────────┘
         │
         │ governed by
         ▼
┌─────────────────┐
│  ApprovalRule   │
│     (NEW)       │
│─────────────────│
│ id (PK)         │
│ item_type       │ ENUM (TODO, INVOICE)
│ priority_level  │ ENUM (LOW, MEDIUM, HIGH)
│ required_roles  │ VARCHAR (comma-separated: MANAGER,ADMIN)
│ created_at      │
└─────────────────┘

┌─────────────────┐
│   Notification  │
│     (NEW)       │
│─────────────────│
│ id (PK)         │
│ recipient_id    │ FK → User
│ type            │ ENUM (APPROVAL_REQUESTED, APPROVED, REJECTED)
│ approval_req_id │ FK → ApprovalRequest
│ message         │
│ read_status     │ BOOLEAN
│ created_at      │
└─────────────────┘
```

## Entity Definitions

### 1. ApprovalRequest (NEW)

Represents a pending approval for a todo or invoice item.

**Table**: `approval_requests`

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGINT | PK, AUTO INCREMENT | Primary key |
| item_type | VARCHAR(20) | NOT NULL | Type of item (TODO, INVOICE) |
| item_id | BIGINT | NOT NULL | Foreign key to todo or invoice table |
| creator_id | BIGINT | NOT NULL, FK → users.id | User who created the item |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | Current status (PENDING, APPROVED, REJECTED, WITHDRAWN) |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL, DEFAULT CURRENT_TIMESTAMP | When approval was requested |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Last status update |
| version | BIGINT | NOT NULL, DEFAULT 0 | Optimistic locking version |

**Indexes**:
- `idx_approval_request_status` on (status) - for filtering pending approvals
- `idx_approval_request_item` on (item_type, item_id) - for looking up approval by item
- `idx_approval_request_creator` on (creator_id) - for user's approval history

**Validation Rules**:
- `status` must be one of: PENDING, APPROVED, REJECTED, WITHDRAWN
- `item_type` must be one of: TODO, INVOICE
- `updated_at` must be >= `created_at`

**State Transitions**:
```
null/DRAFT → PENDING (on submission)
PENDING → APPROVED (when all required approvals obtained)
PENDING → REJECTED (when any approver rejects)
PENDING → WITHDRAWN (when creator withdraws)
[No transitions out of APPROVED, REJECTED, WITHDRAWN - terminal states]
```

### 2. ApprovalRecord (NEW)

Represents a single approval or rejection action by an approver.

**Table**: `approval_records`

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGINT | PK, AUTO INCREMENT | Primary key |
| approval_request_id | BIGINT | NOT NULL, FK → approval_requests.id | Related approval request |
| approver_id | BIGINT | NOT NULL, FK → users.id | User who approved/rejected |
| decision | VARCHAR(20) | NOT NULL | APPROVED or REJECTED |
| comments | TEXT | NULL | Optional reason/comments |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL, DEFAULT CURRENT_TIMESTAMP | When action occurred |
| version | BIGINT | NOT NULL, DEFAULT 0 | Optimistic locking version |

**Indexes**:
- `idx_approval_record_request` on (approval_request_id) - for fetching approval history
- `idx_approval_record_approver` on (approver_id) - for approver activity tracking

**Validation Rules**:
- `decision` must be one of: APPROVED, REJECTED
- `approver_id` must reference a valid user with MANAGER or ADMIN role
- Cannot have duplicate (approval_request_id, approver_id) - one action per approver per request

**Audit Trail**:
- All fields are immutable after creation (INSERT only, no UPDATE or DELETE)
- Preserves complete history of approval actions

### 3. ApprovalRule (NEW)

Defines which roles are required to approve items based on type and priority level.

**Table**: `approval_rules`

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGINT | PK, AUTO INCREMENT | Primary key |
| item_type | VARCHAR(20) | NOT NULL | Type of item (TODO, INVOICE) |
| priority_level | VARCHAR(20) | NOT NULL | Priority level (LOW, MEDIUM, HIGH) |
| required_roles | VARCHAR(255) | NOT NULL | Comma-separated roles (e.g., "MANAGER,ADMIN") |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL, DEFAULT CURRENT_TIMESTAMP | When rule was created |

**Indexes**:
- `idx_approval_rule_lookup` on (item_type, priority_level) - for rule matching (UNIQUE)

**Validation Rules**:
- Unique constraint on (item_type, priority_level) - only one rule per combination
- `required_roles` format validated by application layer: comma-separated, valid role names

**Initial Data** (seeded via Liquibase):
```sql
INSERT INTO approval_rules (item_type, priority_level, required_roles) VALUES
  ('TODO', 'LOW', ''),           -- No approval required
  ('TODO', 'MEDIUM', 'MANAGER'), -- Manager approval only
  ('TODO', 'HIGH', 'MANAGER,ADMIN'), -- Both manager and admin
  ('INVOICE', 'LOW', ''),
  ('INVOICE', 'MEDIUM', 'MANAGER'),
  ('INVOICE', 'HIGH', 'MANAGER,ADMIN');
```

### 4. Notification (NEW)

Represents approval-related notifications sent to users.

**Table**: `notifications`

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGINT | PK, AUTO INCREMENT | Primary key |
| recipient_id | BIGINT | NOT NULL, FK → users.id | User receiving notification |
| type | VARCHAR(30) | NOT NULL | APPROVAL_REQUESTED, APPROVED, REJECTED |
| approval_request_id | BIGINT | NOT NULL, FK → approval_requests.id | Related approval request |
| message | TEXT | NOT NULL | Notification message |
| read_status | BOOLEAN | NOT NULL, DEFAULT FALSE | Whether user has read it |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL, DEFAULT CURRENT_TIMESTAMP | When notification was created |

**Indexes**:
- `idx_notification_recipient` on (recipient_id, read_status) - for fetching unread notifications
- `idx_notification_created` on (created_at DESC) - for sorting by recency

**Validation Rules**:
- `type` must be one of: APPROVAL_REQUESTED, APPROVED, REJECTED

### 5. Todo (EXISTING - MODIFICATIONS)

Add approval status tracking to existing Todo entity.

**New Field**:
| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| status | VARCHAR(20) | NOT NULL, DEFAULT 'APPROVED' | Approval status for backward compatibility |

**Migration Strategy**:
- Add status column with default 'APPROVED' so existing todos remain active
- Low-level todos will have status 'APPROVED' (no approval needed)
- Medium/high-level todos will have status 'PENDING' until approved

### 6. Invoice (EXISTING - MODIFICATIONS)

Add approval status tracking to existing Invoice entity.

**New Field**:
| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| status | VARCHAR(20) | NOT NULL, DEFAULT 'APPROVED' | Approval status for backward compatibility |

**Migration Strategy**:
- Same as Todo: add status column with default 'APPROVED'

## Relationships

1. **ApprovalRequest → User (creator)**
   - Many-to-One: Each approval request has one creator
   - Foreign key: `creator_id → users.id`

2. **ApprovalRequest → Todo/Invoice (polymorphic)**
   - Polymorphic reference via `item_type` + `item_id`
   - Not enforced by database FK (application-level integrity)
   - Alternative: Separate tables (TodoApprovalRequest, InvoiceApprovalRequest) - rejected for simplicity

3. **ApprovalRecord → ApprovalRequest**
   - Many-to-One: Each record belongs to one approval request
   - Foreign key: `approval_request_id → approval_requests.id`
   - Cascade DELETE: When approval request is deleted, records are deleted

4. **ApprovalRecord → User (approver)**
   - Many-to-One: Each record has one approver
   - Foreign key: `approver_id → users.id`

5. **Notification → User (recipient)**
   - Many-to-One: Each notification has one recipient
   - Foreign key: `recipient_id → users.id`

6. **Notification → ApprovalRequest**
   - Many-to-One: Each notification relates to one approval request
   - Foreign key: `approval_request_id → approval_requests.id`

## Query Patterns

### 1. Get Pending Approvals for Manager
```sql
SELECT ar.*
FROM approval_requests ar
WHERE ar.status = 'PENDING'
  AND EXISTS (
    SELECT 1
    FROM approval_rules rl
    WHERE rl.item_type = ar.item_type
      AND rl.priority_level = (
        SELECT level FROM todos WHERE id = ar.item_id -- or invoices
      )
      AND rl.required_roles LIKE '%MANAGER%'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM approval_records rec
    WHERE rec.approval_request_id = ar.id
      AND rec.approver_id = :managerId
  );
```

### 2. Get Approval History for Item
```sql
SELECT rec.*, u.username as approver_name
FROM approval_records rec
JOIN approval_requests ar ON rec.approval_request_id = ar.id
JOIN users u ON rec.approver_id = u.id
WHERE ar.item_type = :itemType
  AND ar.item_id = :itemId
ORDER BY rec.created_at DESC;
```

### 3. Get Required Approvers for Item
```sql
SELECT required_roles
FROM approval_rules
WHERE item_type = :itemType
  AND priority_level = :priorityLevel;
```

### 4. Check if All Approvals Obtained
```sql
-- Application logic:
-- 1. Get required_roles from approval_rules
-- 2. Parse comma-separated roles (e.g., "MANAGER,ADMIN")
-- 3. For each role, check if at least one approval_record exists with approver in that role
SELECT COUNT(DISTINCT u.role)
FROM approval_records rec
JOIN users u ON rec.approver_id = u.id
WHERE rec.approval_request_id = :requestId
  AND rec.decision = 'APPROVED'
  AND u.role IN (:requiredRoles);
-- If count == number of required roles → all approvals obtained
```

## Enums

### ApprovalStatus
```java
public enum ApprovalStatus {
    DRAFT,      // Item being created (not yet submitted)
    PENDING,    // Awaiting approval
    APPROVED,   // All required approvals obtained
    REJECTED,   // At least one approver rejected
    WITHDRAWN   // Creator withdrew the request
}
```

### ItemType
```java
public enum ItemType {
    TODO,
    INVOICE
    // Future: EMAIL, DOCUMENT, etc.
}
```

### NotificationType
```java
public enum NotificationType {
    APPROVAL_REQUESTED,  // Sent to approvers when item needs approval
    APPROVED,            // Sent to creator when item approved
    REJECTED             // Sent to creator when item rejected
}
```

### DecisionType
```java
public enum DecisionType {
    APPROVED,
    REJECTED
}
```

## Validation & Constraints Summary

| Entity | Constraint | Enforcement |
|--------|------------|-------------|
| ApprovalRequest | Status enum values | Database CHECK constraint |
| ApprovalRequest | Optimistic locking | JPA @Version |
| ApprovalRequest | Unique item approval | Unique index on (item_type, item_id, status='PENDING') |
| ApprovalRecord | Decision enum values | Database CHECK constraint |
| ApprovalRecord | One action per approver | Unique constraint on (approval_request_id, approver_id) |
| ApprovalRule | Unique rule per type+level | Unique index on (item_type, priority_level) |
| Todo/Invoice | Status enum values | Database CHECK constraint (existing for other fields) |

## Migration Plan

1. **Create new tables**: approval_requests, approval_records, approval_rules, notifications
2. **Alter existing tables**: Add status column to todos and invoices with default 'APPROVED'
3. **Seed data**: Insert initial approval rules for LOW/MEDIUM/HIGH levels
4. **Create indexes**: All specified indexes for query performance
5. **Add foreign keys**: All FK relationships with proper cascade rules

**Rollback Strategy**: Drop new tables, remove status column from todos/invoices, remove seed data.

## Phase 1 Data Model Completion

- [x] Entity definitions complete with all fields and types
- [x] Relationships mapped with cardinality and foreign keys
- [x] Validation rules and constraints documented
- [x] Query patterns for common operations defined
- [x] Enum definitions provided
- [x] Migration plan outlined
- [x] All entities use Instant for UTC-first timestamps

**Status**: Data model complete. Ready to generate API contracts.
