# Quickstart Guide: Audit Log

**Feature**: User Action Audit Log
**Date**: 2026-02-09
**For**: Administrators and Developers

## Overview

The audit log system automatically tracks all create, update, and delete operations on Todo and Invoice entities. Every change is recorded with:
- **Who** made the change (username)
- **When** it occurred (UTC timestamp)
- **What** changed (field-level before/after values)
- **Which** entity was affected (type and ID)

Audit logs are **immutable** and persist even after entities are deleted, providing complete accountability and compliance.

## Quick Start

### For Administrators: Viewing Audit Logs

#### 1. Access the API

**Prerequisites**:
- ADMIN role required
- Valid JWT authentication token

**Base URL**: `http://localhost:8080/api/audit-logs` (development)

#### 2. View History for a Specific Entity

**Example: Get audit history for Todo #42**

```bash
curl -X GET "http://localhost:8080/api/audit-logs/entity/Todo/42" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Response**:
```json
{
  "content": [
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
    },
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
    },
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
  ],
  "totalElements": 3,
  "totalPages": 1,
  "size": 50,
  "number": 0,
  "first": true,
  "last": true
}
```

**Understanding the Response**:
- Most recent change appears first (DELETE on 2026-02-10)
- Each entry has a unique UUID identifier
- UPDATE shows exactly what changed (title and level fields)
- INSERT shows initial creation (no "before" state, so changes is null)
- requestId correlates all changes from the same API request
- Even though Todo #42 is deleted, complete history is preserved

#### 3. Filter Audit Logs

**Example: Find all deletions by admin in February 2026**

```bash
curl -X GET "http://localhost:8080/api/audit-logs?operation=DELETE&createdBy=admin@example.com&startDate=2026-02-01T00:00:00Z&endDate=2026-03-01T00:00:00Z" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Example: View all Invoice changes**

```bash
curl -X GET "http://localhost:8080/api/audit-logs?entityType=Invoice&page=0&size=20" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Example: Track a specific user's activity**

```bash
curl -X GET "http://localhost:8080/api/audit-logs?createdBy=user@example.com&startDate=2026-02-09T00:00:00Z" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Example: Correlate all changes from a single API request**

```bash
curl -X GET "http://localhost:8080/api/audit-logs?requestId=650e8400-e29b-41d4-a716-446655440010" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

#### 4. Using SnapAdmin (Web UI)

The audit_log table is automatically available in SnapAdmin at `http://localhost:8080/admin`:

1. Navigate to **SnapAdmin**: `http://localhost:8080/admin`
2. Select **audit_log** from the entity list
3. Browse, search, and filter audit entries
4. View JSONB changes in formatted view

**Note**: SnapAdmin provides basic CRUD interface but cannot modify audit logs due to immutability trigger.

### For Developers: Implementation

#### 1. Enable Audit Logging on an Entity

**Add @EntityListeners annotation** to your JPA entity:

```java
@Entity
@Table(name = "todos")
@EntityListeners(AuditEntityListener.class)  // Enable audit logging
@Getter
@Setter
@NoArgsConstructor
public class Todo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean completed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Level level = Level.LOW;

    // Other fields...
}
```

**That's it!** No other code changes needed. Audit logging is automatic.

#### 2. Verify Audit Capture

**Test Case Example**:

```java
@SpringBootTest
@Transactional
class AuditLogIntegrationTest {

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void shouldCaptureAuditOnTodoCreation() {
        // Create a todo
        Todo todo = new Todo();
        todo.setTitle("Test Todo");
        todo.setLevel(Level.HIGH);
        todo = todoRepository.save(todo);

        // Verify audit log was created
        List<AuditLog> logs = auditLogRepository
            .findByEntityTypeAndEntityId("Todo", todo.getId());

        assertThat(logs).hasSize(1);
        AuditLog log = logs.get(0);
        assertThat(log.getOperation()).isEqualTo(AuditOperation.INSERT);
        assertThat(log.getCreatedBy()).isNotNull();
        assertThat(log.getChanges()).isNull();  // INSERT has no "before" state
    }

    @Test
    void shouldCaptureFieldLevelChangesOnUpdate() {
        // Create and save a todo
        Todo todo = new Todo();
        todo.setTitle("Original Title");
        todo.setLevel(Level.LOW);
        todo = todoRepository.save(todo);

        // Update the todo
        todo.setTitle("Updated Title");
        todo.setLevel(Level.HIGH);
        todoRepository.save(todo);

        // Verify audit log captured changes
        List<AuditLog> logs = auditLogRepository
            .findByEntityTypeAndEntityIdAndOperation(
                "Todo", todo.getId(), AuditOperation.UPDATE);

        assertThat(logs).hasSize(1);
        AuditLog log = logs.get(0);
        assertThat(log.getChanges()).isNotNull();
        assertThat(log.getChanges()).containsKeys("title", "level");

        FieldChange titleChange = log.getChanges().get("title");
        assertThat(titleChange.getOld()).isEqualTo("Original Title");
        assertThat(titleChange.getNew()).isEqualTo("Updated Title");

        FieldChange levelChange = log.getChanges().get("level");
        assertThat(levelChange.getOld()).isEqualTo("LOW");
        assertThat(levelChange.getNew()).isEqualTo("HIGH");
    }
}
```

#### 3. Query Audit Logs Programmatically

**Using AuditLogRepository**:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {
    private final AuditLogRepository auditLogRepository;

    public List<AuditLog> getEntityHistory(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityId(
            entityType,
            entityId,
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    public List<AuditLog> getUserActivity(String username, Instant startDate, Instant endDate) {
        return auditLogRepository.findByCreatedByAndCreatedAtBetween(
            username,
            startDate,
            endDate,
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    public Page<AuditLog> getDeletedEntities(Pageable pageable) {
        return auditLogRepository.findByOperation(
            AuditOperation.DELETE,
            pageable
        );
    }
}
```

#### 4. Exclude Fields from Auditing

**Use @Transient annotation** for fields that should not be audited:

```java
@Entity
@EntityListeners(AuditEntityListener.class)
public class User {
    @Id
    private Long id;

    private String username;

    @Transient  // Not persisted, not audited
    private String temporaryToken;

    // Password is persisted but should not be audited
    // (AuditEntityListener should exclude password field)
    private String password;
}
```

**Configure AuditEntityListener** to skip sensitive fields:

```java
public class AuditEntityListener {
    private static final Set<String> EXCLUDED_FIELDS = Set.of("password", "secretKey");

    private boolean shouldAuditField(Field field) {
        return !field.isAnnotationPresent(Transient.class) &&
               !EXCLUDED_FIELDS.contains(field.getName());
    }
}
```

## Common Use Cases

### Use Case 1: Investigate Data Inconsistency

**Scenario**: A user reports that their invoice amount changed unexpectedly.

**Solution**:
1. Get the invoice ID (e.g., #789)
2. Query audit history: `GET /api/audit-logs/entity/Invoice/789`
3. Review the `changes` field to see:
   - When the amount changed
   - Who made the change
   - What the old and new values were
4. Verify if change was legitimate or a bug

### Use Case 2: Compliance Audit

**Scenario**: Regulatory audit requires proof of data retention and change tracking.

**Solution**:
1. Export audit logs for date range: `GET /api/audit-logs?startDate=2025-01-01T00:00:00Z&endDate=2025-12-31T23:59:59Z`
2. Generate CSV report showing:
   - All entity changes in the period
   - User accountability (who made each change)
   - Field-level details (what changed)
3. Demonstrate immutability with database trigger

### Use Case 3: User Activity Monitoring

**Scenario**: Track what actions a specific user performed in the system.

**Solution**:
1. Query by user: `GET /api/audit-logs?changedBy=user@example.com&startDate=2026-02-01T00:00:00Z`
2. Group by operation type to see:
   - How many creates, updates, deletes
   - Which entities were affected
   - Time distribution of activity

### Use Case 4: Rollback Analysis

**Scenario**: Need to restore a deleted todo item.

**Solution**:
1. Find the deletion: `GET /api/audit-logs?operation=DELETE&entityType=Todo&entityId=42`
2. Review the `changes` field which contains final state before deletion
3. Extract field values from `changes.{field}.old`
4. Recreate the entity with those values

**Example from audit log**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440005",
  "operation": "DELETE",
  "createdBy": "admin@example.com",
  "createdAt": "2026-02-10T14:30:00Z",
  "changes": {
    "title": {"old": "Important Meeting", "new": null},
    "description": {"old": "Quarterly review at 2pm", "new": null},
    "completed": {"old": false, "new": null},
    "level": {"old": "HIGH", "new": null}
  },
  "requestId": "650e8400-e29b-41d4-a716-446655440020"
}
```

Recreate with: `{"title": "Important Meeting", "description": "Quarterly review at 2pm", "completed": false, "level": "HIGH"}`

## API Reference Summary

| Endpoint | Method | Description | Admin Only |
|----------|--------|-------------|------------|
| `/api/audit-logs` | GET | List audit logs with filters | ✅ |
| `/api/audit-logs/{id}` | GET | Get specific audit entry | ✅ |
| `/api/audit-logs/entity/{type}/{id}` | GET | Get history for entity | ✅ |

**Common Query Parameters**:
- `entityType`: Filter by entity type (Todo, Invoice, User)
- `entityId`: Filter by specific entity ID
- `createdBy`: Filter by username
- `operation`: Filter by operation (INSERT, UPDATE, DELETE)
- `startDate`: Filter by start date (ISO 8601)
- `endDate`: Filter by end date (ISO 8601)
- `requestId`: Filter by request ID (UUID) to correlate related changes
- `page`: Page number (0-indexed)
- `size`: Page size (default 20, max 100)
- `sort`: Sort field and direction (default "createdAt,desc")

## Performance Tips

### For Querying Large Datasets

1. **Use specific filters**: Always filter by entity type or date range to leverage indexes
   ```bash
   # Good: Uses idx_audit_entity index
   GET /api/audit-logs?entityType=Todo&entityId=42

   # Less efficient: Full table scan
   GET /api/audit-logs
   ```

2. **Limit result size**: Use pagination (page/size parameters)
   ```bash
   # Get first 50 results
   GET /api/audit-logs?entityType=Invoice&page=0&size=50
   ```

3. **Use date ranges**: Leverage partitioning for time-based queries
   ```bash
   # Targets specific partitions
   GET /api/audit-logs?startDate=2026-02-01T00:00:00Z&endDate=2026-03-01T00:00:00Z
   ```

4. **Query JSON fields efficiently**: Use contains queries when searching for specific changes
   ```sql
   -- Find all status changes to PAID
   SELECT * FROM audit_log
   WHERE changes @> '{"status": {"new": "PAID"}}';
   ```

### For Write Operations

- Audit logging adds ~5-10ms overhead per CRUD operation
- This is transparent and automatic - no optimization needed
- Transaction ensures consistency (audit logged only if operation succeeds)

## Troubleshooting

### Problem: No audit logs created

**Check**:
1. Is `@EntityListeners(AuditEntityListener.class)` present on the entity?
2. Is AuditEntityListener properly configured as a Spring component?
3. Are you using repository.save()? (Direct SQL bypasses entity listeners)
4. Check logs for exceptions during audit capture

### Problem: Cannot update/delete audit logs

**This is expected!** Audit logs are immutable by design.

Database trigger prevents any modifications:
```
ERROR: Audit records are immutable and cannot be modified or deleted
```

**Solution**: If you need to correct an error, insert a compensating audit entry documenting the correction.

### Problem: Performance degradation with large audit log table

**Solutions**:
1. Verify indexes are being used: `EXPLAIN ANALYZE SELECT ...`
2. Check if partitions are properly configured
3. Archive old partitions (detach, export, drop)
4. Add expression indexes for frequently queried JSON fields

### Problem: JSONB changes field showing unexpected format

**Expected format**:
```json
{
  "fieldName": {
    "old": "previous value",
    "new": "new value"
  }
}
```

**Check**:
1. Verify FieldChange DTO is properly serialized to JSON
2. Check Hibernate JSONB type mapping configuration
3. Ensure hypersistence-utils dependency is included

## Security Considerations

### Access Control

- **Admin-only**: Only users with ADMIN role can query audit logs
- **Spring Security**: Enforced via `@PreAuthorize("hasRole('ADMIN')")` on controller methods
- **JWT Authentication**: Requires valid authentication token

### Sensitive Data

- **Exclude sensitive fields**: Configure AuditEntityListener to skip password, apiKey, etc.
- **Data masking**: Consider masking PII in entityDescription
- **Encryption at rest**: Use PostgreSQL TDE for audit_log table if required by compliance

### Immutability Guarantee

- **Database trigger**: Prevents tampering at database level
- **No UPDATE/DELETE methods**: Application code cannot modify audit logs
- **Separate backups**: Backup audit_log independently for compliance

## Data Retention

**Default**: 2 years

**Retention Process**:
1. Quarterly partitions created automatically
2. After 2 years, partition is detached from parent table
3. Archived to cold storage (S3, tape, etc.)
4. Partition is dropped from database

**Manual Archival** (for compliance):
```bash
# Export partition to archive
pg_dump -h localhost -U postgres -t audit_log_2024_q1 -F c -f audit_log_2024_q1.dump todolist

# Detach partition
psql -c "ALTER TABLE audit_log DETACH PARTITION audit_log_2024_q1;"

# Drop partition (after verifying archive)
psql -c "DROP TABLE audit_log_2024_q1;"
```

## Next Steps

1. **For Administrators**: Integrate audit log queries into operational dashboards
2. **For Developers**: Enable auditing on new entities by adding `@EntityListeners`
3. **For Compliance**: Set up automated export of audit logs for regulatory reporting
4. **For Operations**: Configure partition management automation (pg_partman)

## Additional Resources

- [OpenAPI Specification](./contracts/audit-log-api.yaml) - Complete API documentation
- [Data Model Documentation](./data-model.md) - Entity and database schema details
- [Implementation Plan](./plan.md) - Technical architecture and design decisions
- [Research Document](./research.md) - Technology choices and rationale
