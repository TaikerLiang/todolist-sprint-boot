# Data Model: General Approval Workflow

**Feature**: 002-approval-workflow
**Date**: 2025-12-27
**Source**: Derived from spec.md functional requirements and research.md decisions

## Entity Diagram

```
┌─────────────────────┐
│       User          │
│ ─────────────────── │
│ id: Long (PK)       │
│ username: String    │
│ role: Role (enum)   │───────┐
│ createdAt: Instant  │       │
└─────────────────────┘       │
         │                    │
         │ 1:N                │ 1:N (requester)
         │                    │
         ▼                    ▼
┌──────────────────────────────────────┐
│        ApprovalRequest               │
│ ──────────────────────────────────── │
│ id: Long (PK)                        │
│ targetItemType: String               │  Examples: "TODO", "INVOICE"
│ targetItemId: Long (nullable)        │  Null for CREATE operations
│ operation: RequestOperation (enum)   │  CREATE, UPDATE, DELETE
│ requestedData: String (JSONB)        │  Null for DELETE operations
│ status: ApprovalStatus (enum)        │  PENDING, PARTIALLY_APPROVED, APPROVED, REJECTED, WITHDRAWN
│ requesterId: Long (FK → User)        │
│ createdAt: Instant                   │
│ updatedAt: Instant                   │
└──────────────────────────────────────┘
         │
         │ 1:N
         ▼
┌──────────────────────────────────────┐
│        ApprovalRecord                │
│ ──────────────────────────────────── │
│ id: Long (PK)                        │
│ approvalRequestId: Long (FK)         │
│ approverId: Long (FK → User)         │
│ decision: Boolean                    │  True = approve, False = reject
│ comment: String (nullable)           │
│ createdAt: Instant                   │
└──────────────────────────────────────┘


┌──────────────────────────────────────┐
│        ApprovalRule                  │
│ ──────────────────────────────────── │
│ id: Long (PK)                        │
│ targetItemType: String               │  "TODO", "INVOICE"
│ operation: RequestOperation (enum)   │  CREATE, UPDATE, DELETE
│ condition: String (nullable)         │  e.g., "level=HIGH", "amount>10000"
│ ruleType: RuleType (enum)            │  ALL_REQUIRED (AND), ANY_REQUIRED (OR)
│ requiredRoles: String (JSONB)        │  ["ADMIN", "MANAGER"]
│ priority: Integer                    │  For rule precedence (higher = more specific)
│ createdAt: Instant                   │
└──────────────────────────────────────┘


Existing Entities (Referenced):
┌─────────────────────┐       ┌─────────────────────┐
│       Todo          │       │      Invoice        │
│ ─────────────────── │       │ ─────────────────── │
│ id: Long (PK)       │       │ id: Long (PK)       │
│ title: String       │       │ invoiceId: UUID     │
│ description: String │       │ amount: BigDecimal  │
│ completed: Boolean  │       │ status: InvoiceStatus│
│ level: Level (enum) │       │ level: Level (enum) │
│ createdAt: Instant  │       │ userId: Long (FK)   │
│ userId: Long (FK)   │       │ createdAt: Instant  │
└─────────────────────┘       │ updatedAt: Instant  │
                              └─────────────────────┘
```

## Entity Details

### ApprovalRequest

**Purpose**: Represents a pending change to an approvable item (Todo, Invoice, etc.)

**Fields**:
- `id`: Primary key, auto-generated
- `targetItemType`: String discriminator identifying the type of item ("TODO", "INVOICE", etc.)
- `targetItemId`: ID of the target item; NULL for CREATE operations (no item exists yet)
- `operation`: Type of operation requested (CREATE, UPDATE, DELETE)
- `requestedData`: JSONB column containing the data for CREATE/UPDATE operations; NULL for DELETE
- `status`: Current status of the approval request
- `requesterId`: Foreign key to User who submitted the request
- `createdAt`: UTC timestamp when request was created
- `updatedAt`: UTC timestamp when request was last modified

**Validation Rules**:
- `targetItemType` must be non-empty
- For UPDATE and DELETE operations, `targetItemId` must be non-null
- For CREATE and UPDATE operations, `requestedData` must be non-null
- For DELETE operations, `requestedData` must be null
- `requesterId` must reference an existing User

**State Transitions**:
```
PENDING → PARTIALLY_APPROVED (when some but not all approvals received)
PENDING → APPROVED (when all required approvals received)
PENDING → REJECTED (when any approver rejects)
PENDING → WITHDRAWN (when requester withdraws)

PARTIALLY_APPROVED → APPROVED (when remaining approvals received)
PARTIALLY_APPROVED → REJECTED (when any approver rejects)
PARTIALLY_APPROVED → WITHDRAWN (when requester withdraws)

APPROVED → (terminal state, no transitions)
REJECTED → (terminal state, no transitions)
WITHDRAWN → (terminal state, no transitions)
```

**Indexes**:
- Primary key on `id`
- Composite index on `(targetItemType, targetItemId)` for finding requests by item
- Index on `status` for querying pending requests
- Unique partial index on `(targetItemType, targetItemId) WHERE status IN ('PENDING', 'PARTIALLY_APPROVED')` to enforce one active request per item

---

### ApprovalRecord

**Purpose**: Represents a single approval or rejection decision by an approver

**Fields**:
- `id`: Primary key, auto-generated
- `approvalRequestId`: Foreign key to the ApprovalRequest being approved/rejected
- `approverId`: Foreign key to User making the decision
- `decision`: Boolean (true = approve, false = reject)
- `comment`: Optional comment explaining the decision
- `createdAt`: UTC timestamp when decision was made

**Validation Rules**:
- `approvalRequestId` must reference an existing ApprovalRequest
- `approverId` must reference an existing User
- `decision` must be non-null
- One approver can only have one record per request (unique constraint on approvalRequestId + approverId)

**Indexes**:
- Primary key on `id`
- Composite index on `(approvalRequestId, decision)` for counting approvals
- Index on `approverId` for finding all approvals by a user

---

### ApprovalRule

**Purpose**: Defines approval requirements for specific item types and operations

**Fields**:
- `id`: Primary key, auto-generated
- `targetItemType`: String identifying the item type this rule applies to
- `operation`: Type of operation (CREATE, UPDATE, DELETE)
- `condition`: Optional condition string for conditional rules (e.g., "level=HIGH")
- `ruleType`: Whether all required roles must approve (ALL_REQUIRED) or any one (ANY_REQUIRED)
- `requiredRoles`: JSONB array of role names (e.g., ["ADMIN", "MANAGER"])
- `priority`: Integer for rule precedence; higher values = more specific rules
- `createdAt`: UTC timestamp when rule was created

**Validation Rules**:
- `targetItemType` must be non-empty
- `operation` must be non-null
- `ruleType` must be non-null
- `requiredRoles` must be a valid JSON array containing at least one role
- `priority` must be non-negative

**Indexes**:
- Primary key on `id`
- Composite index on `(targetItemType, operation, priority DESC)` for rule lookup

**Rule Matching Logic**:
1. Find all rules matching targetItemType and operation
2. Evaluate condition field (if present) against item data
3. Select rule with highest priority among matches
4. If no conditional rules match, fall back to rule with null condition

**Example Rules**:
```java
// Todo high-level items require ADMIN and MANAGER
{
  targetItemType: "TODO",
  operation: UPDATE,
  condition: "level=HIGH",
  ruleType: ALL_REQUIRED,
  requiredRoles: ["ADMIN", "MANAGER"],
  priority: 100
}

// Todo medium-level items require MANAGER only
{
  targetItemType: "TODO",
  operation: UPDATE,
  condition: "level=MEDIUM",
  ruleType: ALL_REQUIRED,
  requiredRoles: ["MANAGER"],
  priority: 50
}

// Todo low-level items have no approval rule (immediate execution)
// (No rule created for level=LOW)

// Invoice create/update require MANAGER
{
  targetItemType: "INVOICE",
  operation: CREATE,
  condition: null,
  ruleType: ALL_REQUIRED,
  requiredRoles: ["MANAGER"],
  priority: 0
}

// Invoice delete requires ADMIN or MANAGER
{
  targetItemType: "INVOICE",
  operation: DELETE,
  condition: null,
  ruleType: ANY_REQUIRED,
  requiredRoles: ["ADMIN", "MANAGER"],
  priority: 0
}
```

---

### Enums

**ApprovalStatus**:
- `PENDING`: Request submitted, awaiting approvals
- `PARTIALLY_APPROVED`: Some but not all required approvals received
- `APPROVED`: All required approvals received, change applied
- `REJECTED`: At least one approver rejected the request
- `WITHDRAWN`: Requester withdrew the request before completion

**RequestOperation**:
- `CREATE`: Create a new item
- `UPDATE`: Modify an existing item
- `DELETE`: Remove an existing item

**RuleType**:
- `ALL_REQUIRED`: All specified roles must approve (AND logic)
- `ANY_REQUIRED`: Any one of the specified roles must approve (OR logic)

---

## Integration with Existing Entities

### Todo and Invoice

**No schema changes required** for basic approval workflow. The approval system references items by type and ID.

**Optional enhancement**: Add a computed property or query method to check if an item has an active approval request:
```java
// In TodoRepository or InvoiceRepository
@Query("SELECT CASE WHEN COUNT(ar) > 0 THEN true ELSE false END " +
       "FROM ApprovalRequest ar " +
       "WHERE ar.targetItemType = :itemType AND ar.targetItemId = :itemId " +
       "AND ar.status IN ('PENDING', 'PARTIALLY_APPROVED')")
boolean hasActiveApprovalRequest(String itemType, Long itemId);
```

This allows checking for locks without adding columns to Todo/Invoice tables.

---

## Database Schema (Liquibase Migration)

Generated via `./mvnw liquibase:diff` after creating JPA entities. Expected structure:

```sql
CREATE TABLE approval_requests (
    id BIGSERIAL PRIMARY KEY,
    target_item_type VARCHAR(255) NOT NULL,
    target_item_id BIGINT,
    operation VARCHAR(16) NOT NULL,
    requested_data JSONB,
    status VARCHAR(32) NOT NULL,
    requester_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_approval_requests_item ON approval_requests(target_item_type, target_item_id);
CREATE INDEX idx_approval_requests_status ON approval_requests(status);
CREATE UNIQUE INDEX idx_one_active_request_per_item
    ON approval_requests (target_item_type, target_item_id)
    WHERE status IN ('PENDING', 'PARTIALLY_APPROVED');

CREATE TABLE approval_records (
    id BIGSERIAL PRIMARY KEY,
    approval_request_id BIGINT NOT NULL REFERENCES approval_requests(id) ON DELETE CASCADE,
    approver_id BIGINT NOT NULL REFERENCES users(id),
    decision BOOLEAN NOT NULL,
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(approval_request_id, approver_id)
);

CREATE INDEX idx_approval_records_request ON approval_records(approval_request_id, decision);
CREATE INDEX idx_approval_records_approver ON approval_records(approver_id);

CREATE TABLE approval_rules (
    id BIGSERIAL PRIMARY KEY,
    target_item_type VARCHAR(255) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    condition VARCHAR(255),
    rule_type VARCHAR(16) NOT NULL,
    required_roles JSONB NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_approval_rules_lookup ON approval_rules(target_item_type, operation, priority DESC);
```

---

## Data Access Patterns

### Common Queries

**Find active approval request for an item**:
```java
Optional<ApprovalRequest> findByTargetItemTypeAndTargetItemIdAndStatusIn(
    String itemType, Long itemId, List<ApprovalStatus> statuses);
```

**Find pending requests for an approver**:
```java
// In service layer: find requests where approver's role is required
@Query("SELECT ar FROM ApprovalRequest ar " +
       "WHERE ar.status IN ('PENDING', 'PARTIALLY_APPROVED') " +
       "AND NOT EXISTS (" +
       "  SELECT 1 FROM ApprovalRecord rec " +
       "  WHERE rec.approvalRequest = ar AND rec.approver.id = :approverId" +
       ")")
List<ApprovalRequest> findPendingRequestsForApprover(Long approverId);
```

**Find applicable rule for a request**:
```java
// In ApprovalRuleService
List<ApprovalRule> findByTargetItemTypeAndOperationOrderByPriorityDesc(
    String itemType, RequestOperation operation);
// Then evaluate condition field in service layer
```

**Count approvals by role for a request**:
```java
@Query("SELECT u.role, COUNT(ar) FROM ApprovalRecord ar " +
       "JOIN ar.approver u " +
       "WHERE ar.approvalRequest.id = :requestId AND ar.decision = true " +
       "GROUP BY u.role")
List<Object[]> countApprovalsByRole(Long requestId);
```

---

## Migration Strategy

1. Create JPA entities (ApprovalRequest, ApprovalRecord, ApprovalRule, enums)
2. Run `./mvnw liquibase:diff` to generate migration changelog
3. Review and adjust generated migration (add indexes, constraints)
4. Run `./mvnw liquibase:update` or `make migrate` to apply
5. Seed initial approval rules via data migration or ApplicationRunner

**Initial Rule Seeding** (via SQL or Java code):
```sql
-- Todo rules
INSERT INTO approval_rules (target_item_type, operation, condition, rule_type, required_roles, priority) VALUES
('TODO', 'CREATE', 'level=HIGH', 'ALL_REQUIRED', '["ADMIN", "MANAGER"]', 100),
('TODO', 'CREATE', 'level=MEDIUM', 'ALL_REQUIRED', '["MANAGER"]', 50),
('TODO', 'UPDATE', 'level=HIGH', 'ALL_REQUIRED', '["ADMIN", "MANAGER"]', 100),
('TODO', 'UPDATE', 'level=MEDIUM', 'ALL_REQUIRED', '["MANAGER"]', 50),
('TODO', 'DELETE', 'level=HIGH', 'ALL_REQUIRED', '["ADMIN", "MANAGER"]', 100),
('TODO', 'DELETE', 'level=MEDIUM', 'ALL_REQUIRED', '["MANAGER"]', 50);

-- Invoice rules
INSERT INTO approval_rules (target_item_type, operation, condition, rule_type, required_roles, priority) VALUES
('INVOICE', 'CREATE', NULL, 'ALL_REQUIRED', '["MANAGER"]', 0),
('INVOICE', 'UPDATE', NULL, 'ALL_REQUIRED', '["MANAGER"]', 0),
('INVOICE', 'DELETE', NULL, 'ANY_REQUIRED', '["ADMIN", "MANAGER"]', 0);
```

---

## Summary

Data model supports:
- ✅ Polymorphic item references (string type + ID)
- ✅ Flexible storage of requested data (JSONB)
- ✅ Item locking via unique constraint
- ✅ Configurable approval rules (database-backed)
- ✅ Role-based approval tracking
- ✅ State machine for approval lifecycle
- ✅ Extensibility to new item types without schema changes

Ready for Phase 1 contracts generation.
