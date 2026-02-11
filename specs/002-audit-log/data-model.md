# Data Model: Admin Audit Log (Custom Model)

**Feature**: 002-audit-log
**Date**: 2026-02-11
**Related**: [spec.md](spec.md) | [research.md](research.md) | [plan.md](plan.md)

## Overview

The audit logging system uses a **custom `UserActionLogs` entity** to track all changes to `Todo` and `Invoice` entities. Field-level changes are stored in PostgreSQL JSONB format using the `hypersistence-utils` library.

**Design Philosophy**: Explicit control over audit schema, following user-specified requirements with custom table name, JSONB field storage, and strategic indexing.

---

## Core Entities

### 1. UserActionLogs

**Purpose**: Main audit log table storing all change events with field-level detail in JSONB format.

**Table**: `admin_portal_user_action_logs`

**Fields**:

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | `UUID` | PRIMARY KEY, AUTO-GENERATED | Unique identifier for each audit log entry |
| `entity_type` | `VARCHAR(50)` | NOT NULL, INDEXED | Type of entity (e.g., "Todo", "Invoice") |
| `entity_id` | `BIGINT` | NOT NULL | ID of the affected entity |
| `operation` | `VARCHAR(10)` | NOT NULL | Operation type (CREATE, UPDATE, DELETE) |
| `created_by` | `VARCHAR(100)` | NULL, INDEXED | Username/email of user who made the change |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | NOT NULL, INDEXED | When the change occurred (UTC) |
| `changes` | `JSONB` | NULL | Field-level changes (before/after values) |
| `request_id` | `UUID` | NULL, INDEXED | Correlation ID for grouping related changes |

**Indexes**:
```sql
CREATE INDEX idx_user_action_logs_entity_type ON admin_portal_user_action_logs(entity_type);
CREATE INDEX idx_user_action_logs_request_id ON admin_portal_user_action_logs(request_id);
CREATE INDEX idx_user_action_logs_created_by ON admin_portal_user_action_logs(created_by);
CREATE INDEX idx_user_action_logs_created_at ON admin_portal_user_action_logs(created_at);
```

**Java Entity**:
```java
package com.example.todolist.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "admin_portal_user_action_logs", indexes = {
    @Index(name = "idx_user_action_logs_entity_type", columnList = "entity_type"),
    @Index(name = "idx_user_action_logs_request_id", columnList = "request_id"),
    @Index(name = "idx_user_action_logs_created_by", columnList = "created_by"),
    @Index(name = "idx_user_action_logs_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActionLogs {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditOperation operation;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Type(JsonBinaryType.class)
    @Column(name = "changes", columnDefinition = "JSONB")
    private Map<String, FieldChange> changes;

    @Column(name = "request_id")
    private UUID requestId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
```

---

### 2. FieldChange (JSONB Value Object)

**Purpose**: Represents a single field change, stored as part of the `changes` JSONB column.

**NOT a database table** - this is a Java class serialized to JSON.

**Fields**:

| Field | Type | Description |
|-------|------|-------------|
| `fieldName` | `String` | Name of the changed field (e.g., "title", "completed") |
| `oldValue` | `Object` | Previous value before the change (null for CREATE) |
| `newValue` | `Object` | New value after the change (null for DELETE or setting to null) |
| `fieldType` | `String` | Data type for display (e.g., "String", "Boolean", "Integer") |

**Java Class**:
```java
package com.example.todolist.model;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldChange implements Serializable {
    private String fieldName;
    private Object oldValue;
    private Object newValue;
    private String fieldType;
}
```

**JSONB Example**:
```json
{
  "title": {
    "fieldName": "title",
    "oldValue": "Original Todo Title",
    "newValue": "Updated Todo Title",
    "fieldType": "String"
  },
  "completed": {
    "fieldName": "completed",
    "oldValue": false,
    "newValue": true,
    "fieldType": "Boolean"
  }
}
```

---

## Enums

### AuditOperation

**Purpose**: Defines the type of operation performed on an audited entity.

```java
package com.example.todolist.model;

public enum AuditOperation {
    CREATE,   // Entity was created
    UPDATE,   // Entity was modified
    DELETE    // Entity was deleted
}
```

**Database Storage**: Stored as `VARCHAR(10)` using `@Enumerated(EnumType.STRING)`

---

## Data Flow

### 1. CREATE Operation

```
User creates Todo → TodoService.createTodo() →
  todoRepository.save(todo) → Todo saved to 'todos' table →
  AuditCaptureService.captureCreate() →
    UserActionLogs entry created:
      - entityType = "Todo"
      - entityId = 123
      - operation = CREATE
      - createdBy = "john@example.com" (from RequestContext)
      - requestId = "a7f3e4d2-..." (from RequestContext)
      - changes = {} (empty for CREATE - no before values)
    UserActionLogsRepository.save() → Saved to 'admin_portal_user_action_logs'
```

### 2. UPDATE Operation

```
User updates Todo.title → TodoService.updateTodo() →
  1. Load original Todo from database (before state)
  2. Apply updates to Todo entity
  3. todoRepository.save(todo) → Todo updated in 'todos' table
  4. AuditCaptureService.captureUpdate(originalTodo, updatedTodo) →
     Compare fields and build changes map:
       changes = {
         "title": FieldChange(fieldName="title", oldValue="Old", newValue="New", fieldType="String")
       }
  5. UserActionLogs entry created with changes JSONB
  6. UserActionLogsRepository.save()
```

### 3. DELETE Operation

```
User deletes Todo → TodoService.deleteTodo() →
  1. Load Todo (final state before deletion)
  2. todoRepository.delete(todo) → Todo removed from 'todos' table
  3. AuditCaptureService.captureDelete(deletedTodo) →
     UserActionLogs entry created:
       - operation = DELETE
       - changes = {} (or final field values for reference)
  4. UserActionLogsRepository.save()
```

**Key Point**: Unlike Envers, this approach requires manual integration in service layer to capture changes.

---

## Querying Audit Data

### Using Custom Repository Queries

Following the project's repository pattern (RepoCustom/RepoImpl):

```java
package com.example.todolist.repository;

import com.example.todolist.model.UserActionLogs;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserActionLogsRepository
    extends JpaRepository<UserActionLogs, UUID>, UserActionLogsRepositoryCustom {
    // Base CRUD operations only
}
```

```java
package com.example.todolist.repository;

import com.example.todolist.model.AuditOperation;
import com.example.todolist.model.UserActionLogs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserActionLogsRepositoryCustom {

    List<UserActionLogs> findByEntityTypeAndEntityId(String entityType, Long entityId);

    Page<UserActionLogs> searchAuditLogs(
        String entityType,
        Long entityId,
        String username,
        AuditOperation operation,
        UUID requestId,
        Instant startDate,
        Instant endDate,
        Pageable pageable
    );

    List<UserActionLogs> findByRequestId(UUID requestId);
}
```

```java
package com.example.todolist.repository;

import com.example.todolist.model.AuditOperation;
import com.example.todolist.model.UserActionLogs;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserActionLogsRepositoryImpl implements UserActionLogsRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<UserActionLogs> findByEntityTypeAndEntityId(String entityType, Long entityId) {
        String jpql = "SELECT u FROM UserActionLogs u " +
                      "WHERE u.entityType = :entityType AND u.entityId = :entityId " +
                      "ORDER BY u.createdAt DESC";
        TypedQuery<UserActionLogs> query = entityManager.createQuery(jpql, UserActionLogs.class);
        query.setParameter("entityType", entityType);
        query.setParameter("entityId", entityId);
        return query.getResultList();
    }

    @Override
    public Page<UserActionLogs> searchAuditLogs(
            String entityType, Long entityId, String username,
            AuditOperation operation, UUID requestId,
            Instant startDate, Instant endDate, Pageable pageable) {

        StringBuilder jpql = new StringBuilder("SELECT u FROM UserActionLogs u WHERE 1=1");

        if (entityType != null) jpql.append(" AND u.entityType = :entityType");
        if (entityId != null) jpql.append(" AND u.entityId = :entityId");
        if (username != null) jpql.append(" AND u.createdBy = :username");
        if (operation != null) jpql.append(" AND u.operation = :operation");
        if (requestId != null) jpql.append(" AND u.requestId = :requestId");
        if (startDate != null) jpql.append(" AND u.createdAt >= :startDate");
        if (endDate != null) jpql.append(" AND u.createdAt <= :endDate");

        jpql.append(" ORDER BY u.createdAt DESC");

        TypedQuery<UserActionLogs> query = entityManager.createQuery(jpql.toString(), UserActionLogs.class);

        if (entityType != null) query.setParameter("entityType", entityType);
        if (entityId != null) query.setParameter("entityId", entityId);
        if (username != null) query.setParameter("username", username);
        if (operation != null) query.setParameter("operation", operation);
        if (requestId != null) query.setParameter("requestId", requestId);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        // Apply pagination
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<UserActionLogs> results = query.getResultList();

        // Count total (for pagination)
        long total = countAuditLogs(entityType, entityId, username, operation, requestId, startDate, endDate);

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public List<UserActionLogs> findByRequestId(UUID requestId) {
        String jpql = "SELECT u FROM UserActionLogs u WHERE u.requestId = :requestId ORDER BY u.createdAt ASC";
        TypedQuery<UserActionLogs> query = entityManager.createQuery(jpql, UserActionLogs.class);
        query.setParameter("requestId", requestId);
        return query.getResultList();
    }

    private long countAuditLogs(String entityType, Long entityId, String username,
                                AuditOperation operation, UUID requestId,
                                Instant startDate, Instant endDate) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(u) FROM UserActionLogs u WHERE 1=1");

        if (entityType != null) jpql.append(" AND u.entityType = :entityType");
        if (entityId != null) jpql.append(" AND u.entityId = :entityId");
        if (username != null) jpql.append(" AND u.createdBy = :username");
        if (operation != null) jpql.append(" AND u.operation = :operation");
        if (requestId != null) jpql.append(" AND u.requestId = :requestId");
        if (startDate != null) jpql.append(" AND u.createdAt >= :startDate");
        if (endDate != null) jpql.append(" AND u.createdAt <= :endDate");

        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);

        if (entityType != null) query.setParameter("entityType", entityType);
        if (entityId != null) query.setParameter("entityId", entityId);
        if (username != null) query.setParameter("username", username);
        if (operation != null) query.setParameter("operation", operation);
        if (requestId != null) query.setParameter("requestId", requestId);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        return query.getSingleResult();
    }
}
```

---

## Storage Considerations

### Size Estimates

Assuming:
- Average audit log entry: ~300 bytes (base fields)
- Average JSONB changes field: ~200 bytes (2-3 field changes)
- Total per entry: ~500 bytes
- 1500 CUD operations per day (1000 Todos + 500 Invoices)

**Daily Growth**:
- 1500 entries * 500 bytes = ~750 KB/day
- **Annual**: ~274 MB/year
- **10-Year**: ~2.7 GB

**Mitigation Strategies** (future):
- Table partitioning by `created_at` (monthly or yearly partitions)
- Archival of old audit logs (>3 years) to cold storage
- JSONB compression (PostgreSQL handles this automatically)

---

## Database Indexes

### Performance Optimization

**Primary Access Patterns**:
1. Get history for specific entity: `entity_type + entity_id`
2. Search by user: `created_by`
3. Search by date range: `created_at`
4. Correlate by request: `request_id`

**Recommended Indexes**:
```sql
-- Already defined in @Table annotation
CREATE INDEX idx_user_action_logs_entity_type ON admin_portal_user_action_logs(entity_type);
CREATE INDEX idx_user_action_logs_request_id ON admin_portal_user_action_logs(request_id);
CREATE INDEX idx_user_action_logs_created_by ON admin_portal_user_action_logs(created_by);
CREATE INDEX idx_user_action_logs_created_at ON admin_portal_user_action_logs(created_at);

-- Composite index for common query pattern
CREATE INDEX idx_user_action_logs_entity_lookup
    ON admin_portal_user_action_logs(entity_type, entity_id, created_at DESC);

-- GIN index for JSONB queries (if needed for searching within changes)
CREATE INDEX idx_user_action_logs_changes_gin
    ON admin_portal_user_action_logs USING GIN (changes);
```

---

## Validation Rules

**UserActionLogs Constraints**:
- `entity_type`: NOT NULL, max 50 characters
- `entity_id`: NOT NULL (foreign key logic enforced in application, not database)
- `operation`: NOT NULL, ENUM (CREATE, UPDATE, DELETE)
- `created_at`: NOT NULL, auto-set on insert
- `changes`: NULL allowed (empty for CREATE/DELETE operations)

**Data Integrity**:
- Audit logs are **append-only** (no updates or deletes allowed)
- Enforced via `updatable = false` on `created_at` field
- Repository should not expose `delete()` or `update()` methods

---

## Comparison with Envers Approach

| Aspect | Custom UserActionLogs | Hibernate Envers |
|--------|----------------------|------------------|
| **Setup Complexity** | Manual audit capture required | Automatic with `@Audited` |
| **Schema Control** | Full control (custom table name, columns) | Fixed schema (`*_aud` tables) |
| **Field Changes** | JSONB map (flexible) | Separate columns with `*_mod` flags |
| **Storage Overhead** | ~500 bytes/entry | ~600-800 bytes/entry (multiple tables) |
| **Query Flexibility** | Custom JPQL queries | AuditReader API |
| **Transaction Safety** | Manual integration required | Automatic |
| **Deletion Tracking** | Manual capture | Automatic |
| **Maintenance** | Custom code to maintain | Framework-maintained |

**Trade-offs**: Custom model provides explicit control and follows user's design patterns, but requires more manual implementation work for audit capture logic.

---

## Next Steps

1. **Liquibase Migration**: Generate migration for `admin_portal_user_action_logs` table with indexes
2. **Repository Implementation**: Create `UserActionLogsRepository` with custom query methods
3. **Audit Capture Service**: Implement field comparison and audit log creation logic
4. **Service Integration**: Add audit capture to `TodoService` and `InvoiceService`
5. **API Layer**: Implement REST endpoints to expose audit data

---

**Last Updated**: 2026-02-11 (Updated to reflect custom UserActionLogs model)
**Status**: Data model design complete with custom approach
