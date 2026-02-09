# Data Model: Audit Log

**Feature**: User Action Audit Log
**Date**: 2026-02-09
**Status**: Design Phase

## Entity Overview

The audit logging system introduces one new entity: **AuditLog**. This entity captures all create, update, and delete operations on Todo and Invoice entities with field-level change tracking.

## Entity: AuditLog

**Purpose**: Immutable record of a single action (CREATE/UPDATE/DELETE) performed on an audited entity, capturing who made the change, when it occurred, and exactly what changed (field-level before/after values).

**Table Name**: `audit_log` (partitioned by `created_at`)

### Fields

| Field Name | Java Type | Database Type | Constraints | Description |
|------------|-----------|---------------|-------------|-------------|
| id | UUID | UUID | PRIMARY KEY, NOT NULL | Unique identifier (UUID v4 random) |
| entityType | String | VARCHAR(50) | NOT NULL | Type of audited entity (e.g., "Todo", "Invoice", "User") |
| entityId | Long | BIGINT | NOT NULL | ID of the audited entity (no FK constraint to allow deleted entity auditing) |
| operation | AuditOperation (enum) | VARCHAR(10) | NOT NULL | Type of operation: INSERT, UPDATE, DELETE |
| createdBy | String | VARCHAR(100) | NULLABLE | Username of person who performed the action (matches User.username, no FK relation); NULL for system operations |
| createdAt | Instant (UTC) | TIMESTAMP WITH TIME ZONE | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Timestamp when audit entry was created (UTC, partition key) |
| changes | Map<String, FieldChange> | JSONB | NULLABLE | Field-level changes: {"fieldName": {"old": value, "new": value}}; NULL for INSERT (no "before" state) |
| requestId | UUID | UUID | NULLABLE | UUID of API request that triggered this audit entry (for correlation and tracing) |

### Enums

#### AuditOperation

```java
public enum AuditOperation {
    INSERT,  // Entity creation
    UPDATE,  // Entity modification
    DELETE   // Entity deletion
}
```

### FieldChange (Embedded Type)

**Purpose**: Represents before/after values for a single field that changed during an UPDATE operation.

**Structure** (stored in JSONB):
```json
{
  "old": <serialized value>,
  "new": <serialized value>
}
```

**Java Representation**:
```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldChange {
    private Object old;
    private Object newValue;
}
```

**Examples**:
- Simple field: `{"old": "LOW", "new": "HIGH"}`
- Numeric field: `{"old": 100.00, "new": 150.00}`
- Boolean field: `{"old": false, "new": true}`
- Null to value: `{"old": null, "new": "2026-02-10T10:00:00Z"}`
- Date field: `{"old": "2026-02-09T14:30:00Z", "new": "2026-02-10T10:00:00Z"}`

### Relationships

**No Foreign Key Relationships**:
- `entityId` references Todo, Invoice, or User entities **logically** but has no FK constraint
- `createdBy` matches `User.username` but has no FK constraint
- **Rationale**: Allows audit logs to persist even after the original entity or user is deleted (requirement FR-013)
- Entity type + ID combination provides context
- Username snapshot preserved for historical accuracy even if user account changes

### Indexes

| Index Name | Type | Columns | Purpose |
|------------|------|---------|---------|
| idx_audit_entity | BTREE | (entity_type, entity_id, created_at DESC) | Primary query pattern: "show audit history for Todo #42" |
| idx_audit_time | BTREE | (created_at) | Date range queries and partition pruning |
| idx_audit_user | BTREE | (created_by, created_at DESC) | User activity tracking: "show all actions by admin@example.com" |
| idx_audit_changes_gin | GIN | (changes) | JSONB containment queries: "find all status changes" |
| idx_audit_operation | BTREE | (operation, created_at DESC) | Filter by operation type: "show all deletions" |
| idx_audit_request | BTREE | (request_id) WHERE request_id IS NOT NULL | Correlate all changes from same API request |

**Optional Expression Indexes** (add if specific field queries are frequent):
```sql
CREATE INDEX idx_audit_status_change ON audit_log
    ((changes->'status')) WHERE changes ? 'status';
```

### Partitioning Strategy

**Partition Key**: `created_at` (timestamp)
**Partition Type**: RANGE partitioning by quarter

**Rationale**:
- Keeps query performance constant as table grows
- Enables efficient archival (drop old partitions)
- Aligns with 2-year retention requirement

**Partition Naming**:
- `audit_log_2026_q1`: 2026-01-01 to 2026-04-01
- `audit_log_2026_q2`: 2026-04-01 to 2026-07-01
- `audit_log_2026_q3`: 2026-07-01 to 2026-10-01
- `audit_log_2026_q4`: 2026-10-01 to 2027-01-01

**Partition Management**:
- Create partitions 1 quarter in advance (automated via cron or pg_partman)
- Drop or archive partitions older than 2 years
- Monitor partition sizes (~25,000 rows per quarter estimated)

### Constraints

1. **Primary Key**: (id, created_at) - composite key required for partitioning
2. **NOT NULL**: entity_type, entity_id, operation, created_at
3. **Immutability Trigger**: BEFORE UPDATE OR DELETE trigger raises exception
4. **Check Constraint**: operation IN ('INSERT', 'UPDATE', 'DELETE')
5. **Default Values**: id DEFAULT gen_random_uuid(), created_at DEFAULT CURRENT_TIMESTAMP

### Sample Data

**INSERT Operation (Todo creation)**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "entityType": "Todo",
  "entityId": 42,
  "operation": "INSERT",
  "createdBy": "user@example.com",
  "createdAt": "2026-02-09T15:30:00Z",
  "changes": null,
  "requestId": "650e8400-e29b-41d4-a716-446655440010"
}
```

**UPDATE Operation (Todo modification)**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440002",
  "entityType": "Todo",
  "entityId": 42,
  "operation": "UPDATE",
  "createdBy": "user@example.com",
  "createdAt": "2026-02-09T16:45:00Z",
  "changes": {
    "title": {
      "old": "Buy groceries",
      "new": "Buy groceries and cook dinner"
    },
    "level": {
      "old": "LOW",
      "new": "HIGH"
    }
  },
  "requestId": "650e8400-e29b-41d4-a716-446655440011"
}
```

**DELETE Operation (Todo deletion)**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440003",
  "entityType": "Todo",
  "entityId": 42,
  "operation": "DELETE",
  "createdBy": "admin@example.com",
  "createdAt": "2026-02-10T09:00:00Z",
  "changes": {
    "title": {"old": "Buy groceries and cook dinner", "new": null},
    "completed": {"old": false, "new": null},
    "level": {"old": "HIGH", "new": null}
  },
  "requestId": "650e8400-e29b-41d4-a716-446655440012"
}
```

## Entity Modifications (Existing Entities)

### Todo, Invoice, User Entities

**Changes Required**:

1. **Add @EntityListeners annotation**:
```java
@Entity
@Table(name = "todos")
@EntityListeners(AuditEntityListener.class)  // ADD THIS
@Getter
@Setter
@NoArgsConstructor
public class Todo {
    // Existing fields unchanged
}
```

2. **Add transient field for snapshot** (optional, depending on implementation):
```java
@Transient
private transient Map<String, Object> auditSnapshot;
```

**Impact**: Minimal - only metadata change, no schema migration needed for existing entities.

## Database Schema (Liquibase)

### Migration Generation

**Migration will be generated using**: `make makemigration NAME=create_audit_log_table`

This will auto-generate the migration based on the AuditLog JPA entity. Additional manual steps after generation:

1. **Add partitioning** (Liquibase diff doesn't auto-detect partitioning):
```sql
-- Modify generated CREATE TABLE to add partitioning
ALTER TABLE audit_log PARTITION BY RANGE (created_at);

-- Create initial quarterly partitions
CREATE TABLE audit_log_2026_q1 PARTITION OF audit_log
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
CREATE TABLE audit_log_2026_q2 PARTITION OF audit_log
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE audit_log_2026_q3 PARTITION OF audit_log
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
```

2. **Add immutability trigger** (business rule, not JPA-generated):
```sql
CREATE OR REPLACE FUNCTION prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit records are immutable and cannot be modified or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_modification();
```

3. **Add additional indexes** (beyond JPA @Index annotations):
```sql
CREATE INDEX idx_audit_request ON audit_log (request_id)
    WHERE request_id IS NOT NULL;
```

**Expected Migration File**: `src/main/resources/db/changelog/changes/0007_create_audit_log_table.yaml`

## Storage Estimates

**Per Audit Entry**:
- UUID id: 16 bytes
- Fixed fields (entity_type, operation, created_by): ~150 bytes
- Timestamp (created_at): 8 bytes
- UUID request_id: 16 bytes (when present)
- JSONB changes (average 2-3 fields): ~200-300 bytes
- **Total**: ~390-490 bytes per entry

**Annual Estimates** (for 10,000 active items, 10 actions each):
- Entries per year: 100,000
- Storage: 100,000 × 440 bytes = ~44 MB/year (uncompressed)
- With PostgreSQL TOAST compression: ~26-30 MB/year

**2-Year Retention**:
- Total entries: 200,000
- Total storage: ~60-70 MB (negligible for modern PostgreSQL)

**Partition Sizes**:
- Per quarter: ~25,000 entries = ~12-15 MB per partition

## Query Examples

### 1. Get Complete Audit History for an Entity
```sql
SELECT
    id,
    operation,
    created_by,
    created_at,
    changes,
    request_id
FROM audit_log
WHERE entity_type = 'Todo' AND entity_id = 42
ORDER BY created_at DESC;
```

### 2. Find All Changes to a Specific Field
```sql
SELECT
    entity_type,
    entity_id,
    created_by,
    created_at,
    changes->'status' AS status_change
FROM audit_log
WHERE changes ? 'status'  -- Has 'status' key
ORDER BY created_at DESC;
```

### 3. User Activity Report
```sql
SELECT
    entity_type,
    operation,
    COUNT(*) AS action_count
FROM audit_log
WHERE created_by = 'admin@example.com'
  AND created_at >= '2026-02-01'
  AND created_at < '2026-03-01'
GROUP BY entity_type, operation;
```

### 4. Find Deletions in Date Range
```sql
SELECT
    entity_type,
    entity_id,
    created_by,
    created_at,
    request_id
FROM audit_log
WHERE operation = 'DELETE'
  AND created_at >= '2026-02-01'
  AND created_at < '2026-03-01'
ORDER BY created_at DESC;
```

### 5. Audit Trail for Deleted Entity
```sql
-- Even though Todo #42 is deleted, we can still see its history
SELECT
    operation,
    created_by,
    created_at,
    changes,
    request_id
FROM audit_log
WHERE entity_type = 'Todo' AND entity_id = 42
ORDER BY created_at DESC;
```

### 6. Correlate All Changes from Single API Request
```sql
-- Find all audit entries created by a single API request
SELECT
    entity_type,
    entity_id,
    operation,
    created_by,
    created_at,
    changes
FROM audit_log
WHERE request_id = '650e8400-e29b-41d4-a716-446655440010'
ORDER BY created_at ASC;
```

## Data Lifecycle

**Creation**: Automatically created by EntityListeners on entity CRUD operations
**Read**: Admin users via REST API endpoints; reporting tools via SQL
**Update**: FORBIDDEN - trigger prevents any updates
**Delete**: FORBIDDEN - trigger prevents deletes; retention via partition archival only

**Archival Process** (after 2 years):
1. Detach old partition: `ALTER TABLE audit_log DETACH PARTITION audit_log_2024_q1;`
2. Export to archive storage: `pg_dump ... audit_log_2024_q1`
3. Drop partition: `DROP TABLE audit_log_2024_q1;`

## Validation Rules

1. **entity_type** must be non-empty and match known entity names
2. **entity_id** must be positive integer
3. **operation** must be one of: INSERT, UPDATE, DELETE
4. **created_at** must be UTC timestamp, cannot be future date
5. **changes** must be valid JSONB when not null
6. **request_id** must be valid UUID when not null
7. **For UPDATE operations**: changes should not be null/empty (at least one field changed)
8. **For INSERT operations**: changes should be null (no "before" state)
9. **For DELETE operations**: changes should contain final state of all fields

## Security Considerations

**Access Control**:
- Only ADMIN role can query audit logs (enforced in AuditLogController)
- Regular users cannot view audit history
- No public write access to audit_log table (only internal service)

**Sensitive Data**:
- Audit logs may contain sensitive information (email addresses, financial data)
- Apply same security level as audited entities
- Consider data masking for highly sensitive fields (e.g., passwords should never be audited)

**Immutability**:
- Database trigger prevents tampering
- No application code should attempt updates/deletes
- Backup audit_log table separately for compliance

## Next Steps

1. Generate Liquibase migration from this data model
2. Create JPA entity classes (AuditLog, AuditOperation enum, FieldChange class)
3. Implement AuditLogRepository with custom query methods
4. Create API contracts (OpenAPI spec) in `contracts/` directory
5. Proceed to implementation tasks
