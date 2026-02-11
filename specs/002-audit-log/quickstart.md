# Quickstart: Admin Audit Log (Custom Model)

**Feature**: 002-audit-log
**Audience**: Developers implementing or using the audit log feature
**Prerequisites**: Spring Boot 3.1.5, PostgreSQL, Liquibase configured

---

## Overview

This feature provides comprehensive audit logging for `Todo` and `Invoice` entities using a **custom `UserActionLogs` entity** with JSONB storage for field changes. It captures all CREATE, UPDATE, and DELETE operations with complete change history, including who made each change, when it occurred, and what specifically changed.

**Key Capabilities**:
- ✅ Custom audit model with full schema control
- ✅ JSONB storage for flexible field change tracking
- ✅ Request correlation (group related changes by request ID)
- ✅ User attribution (track who made each change)
- ✅ REST API for querying audit logs
- ✅ Strategic indexing for query performance

---

## Quick Setup (5 Steps)

### Step 1: Add Hypersistence Utils Dependency

The `UserActionLogs` entity uses `@Type(JsonBinaryType.class)` for JSONB support.

Add to `pom.xml`:

```xml
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-63</artifactId>
    <version>3.7.3</version>
</dependency>
```

### Step 2: Verify Model Classes Exist

These classes should already be created in `src/main/java/com/example/todolist/model/`:

- ✅ `UserActionLogs.java` - Main audit log entity
- ✅ `FieldChange.java` - JSONB value object for field changes
- ✅ `AuditOperation.java` - Enum (CREATE, UPDATE, DELETE)

**Verify**:
```bash
ls -l src/main/java/com/example/todolist/model/{UserActionLogs,FieldChange,AuditOperation}.java
```

### Step 3: Generate and Run Database Migration

```bash
# Generate migration for UserActionLogs table
make makemigration NAME=create_user_action_logs_table

# Review the generated migration
cat src/main/resources/db/changelog/changes/000X_create_user_action_logs_table.yaml

# Apply migration
make migrate
```

**Verify table created**:
```sql
\d admin_portal_user_action_logs;
```

### Step 4: Create Repository Layer

Create repository following the project's best practices pattern:

**`UserActionLogsRepository.java`**:
```java
package com.example.todolist.repository;

import com.example.todolist.model.UserActionLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserActionLogsRepository
    extends JpaRepository<UserActionLogs, UUID>, UserActionLogsRepositoryCustom {
    // IMPORTANT: DO NOT add derived query methods here.
    // JPA repository for basic CRUD operations only.
    // Custom queries go in UserActionLogsRepositoryCustom.
}
```

**`UserActionLogsRepositoryCustom.java`** and **`UserActionLogsRepositoryImpl.java`** - see [data-model.md](data-model.md#querying-audit-data) for complete implementation.

### Step 5: Implement Audit Capture Service

Create `AuditCaptureService` to manually capture audit logs:

```java
package com.example.todolist.service;

import com.example.todolist.context.RequestContext;
import com.example.todolist.model.AuditOperation;
import com.example.todolist.model.FieldChange;
import com.example.todolist.model.UserActionLogs;
import com.example.todolist.repository.UserActionLogsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditCaptureService {

    private final UserActionLogsRepository repository;

    @Transactional
    public void captureCreate(String entityType, Long entityId) {
        UserActionLogs audit = UserActionLogs.builder()
            .entityType(entityType)
            .entityId(entityId)
            .operation(AuditOperation.CREATE)
            .createdBy(RequestContext.getUserId())
            .requestId(parseRequestId(RequestContext.getRequestId()))
            .changes(new HashMap<>()) // Empty for CREATE
            .build();

        repository.save(audit);
        log.info("Audit log captured: {} {} [{}]",
                 AuditOperation.CREATE, entityType, entityId);
    }

    @Transactional
    public void captureUpdate(String entityType, Long entityId,
                               Map<String, FieldChange> changes) {
        UserActionLogs audit = UserActionLogs.builder()
            .entityType(entityType)
            .entityId(entityId)
            .operation(AuditOperation.UPDATE)
            .createdBy(RequestContext.getUserId())
            .requestId(parseRequestId(RequestContext.getRequestId()))
            .changes(changes)
            .build();

        repository.save(audit);
        log.info("Audit log captured: {} {} [{}] - {} fields changed",
                 AuditOperation.UPDATE, entityType, entityId, changes.size());
    }

    @Transactional
    public void captureDelete(String entityType, Long entityId) {
        UserActionLogs audit = UserActionLogs.builder()
            .entityType(entityType)
            .entityId(entityId)
            .operation(AuditOperation.DELETE)
            .createdBy(RequestContext.getUserId())
            .requestId(parseRequestId(RequestContext.getRequestId()))
            .changes(new HashMap<>()) // Empty for DELETE
            .build();

        repository.save(audit);
        log.info("Audit log captured: {} {} [{}]",
                 AuditOperation.DELETE, entityType, entityId);
    }

    private UUID parseRequestId(String requestIdStr) {
        if (requestIdStr == null) {
            return null;
        }
        try {
            return UUID.fromString(requestIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request ID format: {}", requestIdStr);
            return null;
        }
    }
}
```

That's it! The basic audit logging infrastructure is now ready.

---

## Integrating Audit Logging into Services

### Manual Approach (Recommended for Control)

Update your service methods to call `AuditCaptureService`:

**Example: TodoService with Audit Logging**

```java
package com.example.todolist.service;

import com.example.todolist.model.FieldChange;
import com.example.todolist.model.Todo;
import com.example.todolist.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;
    private final AuditCaptureService auditCaptureService;

    @Transactional
    public Todo createTodo(Todo todo) {
        Todo saved = todoRepository.save(todo);

        // Capture CREATE audit log
        auditCaptureService.captureCreate("Todo", saved.getId());

        return saved;
    }

    @Transactional
    public Todo updateTodo(Long id, Todo updates) {
        Todo existing = todoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Todo not found"));

        // Capture field changes
        Map<String, FieldChange> changes = new HashMap<>();

        if (!existing.getTitle().equals(updates.getTitle())) {
            changes.put("title", FieldChange.builder()
                .fieldName("title")
                .oldValue(existing.getTitle())
                .newValue(updates.getTitle())
                .fieldType("String")
                .build());
        }

        if (existing.getCompleted() != updates.getCompleted()) {
            changes.put("completed", FieldChange.builder()
                .fieldName("completed")
                .oldValue(existing.getCompleted())
                .newValue(updates.getCompleted())
                .fieldType("Boolean")
                .build());
        }

        // Apply updates
        existing.setTitle(updates.getTitle());
        existing.setCompleted(updates.getCompleted());

        Todo saved = todoRepository.save(existing);

        // Capture UPDATE audit log (only if changes exist)
        if (!changes.isEmpty()) {
            auditCaptureService.captureUpdate("Todo", saved.getId(), changes);
        }

        return saved;
    }

    @Transactional
    public void deleteTodo(Long id) {
        Todo todo = todoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Todo not found"));

        todoRepository.delete(todo);

        // Capture DELETE audit log
        auditCaptureService.captureDelete("Todo", id);
    }
}
```

### Field Comparison Utility (Optional)

For cleaner code, create a utility to compare entities:

```java
package com.example.todolist.util;

import com.example.todolist.model.FieldChange;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class EntityComparator {

    public static <T> Map<String, FieldChange> compareFields(T original, T updated) {
        Map<String, FieldChange> changes = new HashMap<>();

        Field[] fields = original.getClass().getDeclaredFields();
        for (Field field : fields) {
            // Skip id and audit fields
            if (field.getName().equals("id") ||
                field.getName().equals("createdAt") ||
                field.getName().equals("updatedAt")) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object oldValue = field.get(original);
                Object newValue = field.get(updated);

                // Check if values differ
                if (!java.util.Objects.equals(oldValue, newValue)) {
                    changes.put(field.getName(), FieldChange.builder()
                        .fieldName(field.getName())
                        .oldValue(oldValue)
                        .newValue(newValue)
                        .fieldType(field.getType().getSimpleName())
                        .build());
                }
            } catch (IllegalAccessException e) {
                // Skip fields we can't access
            }
        }

        return changes;
    }
}
```

Then simplify your service:

```java
@Transactional
public Todo updateTodo(Long id, Todo updates) {
    Todo existing = todoRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Todo not found"));

    // Compare before and after
    Map<String, FieldChange> changes = EntityComparator.compareFields(existing, updates);

    // Apply updates
    existing.setTitle(updates.getTitle());
    existing.setCompleted(updates.getCompleted());

    Todo saved = todoRepository.save(existing);

    // Capture audit log
    if (!changes.isEmpty()) {
        auditCaptureService.captureUpdate("Todo", saved.getId(), changes);
    }

    return saved;
}
```

---

## Using the Audit Log

### Querying Audit History (Java API)

```java
package com.example.todolist.service;

import com.example.todolist.model.UserActionLogs;
import com.example.todolist.repository.UserActionLogsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final UserActionLogsRepository repository;

    @Transactional(readOnly = true)
    public List<UserActionLogs> getTodoHistory(Long todoId) {
        return repository.findByEntityTypeAndEntityId("Todo", todoId);
    }

    @Transactional(readOnly = true)
    public List<UserActionLogs> getInvoiceHistory(Long invoiceId) {
        return repository.findByEntityTypeAndEntityId("Invoice", invoiceId);
    }
}
```

### Querying Audit History (REST API)

Once the API layer is implemented, you can query via HTTP:

**Get audit history for a specific Todo**:
```bash
curl -H "Authorization: Bearer <admin-jwt-token>" \
  http://localhost:8080/api/audit/todos/123/history
```

**Search audit logs by user**:
```bash
curl -H "Authorization: Bearer <admin-jwt-token>" \
  "http://localhost:8080/api/audit/search?username=john.doe@example.com&page=0&size=20"
```

**Search audit logs by date range**:
```bash
curl -H "Authorization: Bearer <admin-jwt-token>" \
  "http://localhost:8080/api/audit/search?startDate=2026-02-01T00:00:00Z&endDate=2026-02-11T23:59:59Z"
```

**Get all changes in a single request** (correlated by request ID):
```bash
curl -H "Authorization: Bearer <admin-jwt-token>" \
  "http://localhost:8080/api/audit/search?requestId=a7f3e4d2-1b9c-4e5a-8f2d-1c3b4a5e6f7a"
```

---

## Request Context Setup (Required)

For request ID and user tracking to work, you need to set up the request context infrastructure:

### 1. Create RequestContext Utility

```java
package com.example.todolist.context;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private RequestContext() {}

    public static String getRequestId() {
        return REQUEST_ID.get();
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    public static void clear() {
        REQUEST_ID.remove();
        USER_ID.remove();
    }

    public static boolean hasRequestContext() {
        return REQUEST_ID.get() != null;
    }
}
```

### 2. Create RequestCorrelationFilter

```java
package com.example.todolist.filter;

import com.example.todolist.context.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
@Order(1)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(MDC_REQUEST_ID_KEY, requestId);
            RequestContext.setRequestId(requestId);
            response.setHeader(REQUEST_ID_HEADER, requestId);

            filterChain.doFilter(request, response);

        } finally {
            MDC.remove(MDC_REQUEST_ID_KEY);
            RequestContext.clear();
        }
    }
}
```

### 3. Update JwtAuthenticationFilter

Add this line after successful authentication:

```java
SecurityContextHolder.getContext().setAuthentication(authentication);
RequestContext.setUserId(userId.toString());  // ADD THIS LINE
```

---

## Database Schema

After running the migration, your table will look like:

```sql
CREATE TABLE admin_portal_user_action_logs (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    operation VARCHAR(10) NOT NULL,
    created_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    changes JSONB,
    request_id UUID
);

-- Indexes
CREATE INDEX idx_user_action_logs_entity_type ON admin_portal_user_action_logs(entity_type);
CREATE INDEX idx_user_action_logs_request_id ON admin_portal_user_action_logs(request_id);
CREATE INDEX idx_user_action_logs_created_by ON admin_portal_user_action_logs(created_by);
CREATE INDEX idx_user_action_logs_created_at ON admin_portal_user_action_logs(created_at);
```

**Sample Data**:

```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "entity_type": "Todo",
  "entity_id": 42,
  "operation": "UPDATE",
  "created_by": "john.doe@example.com",
  "created_at": "2026-02-11T10:30:45.123Z",
  "request_id": "a7f3e4d2-1b9c-4e5a-8f2d-1c3b4a5e6f7a",
  "changes": {
    "title": {
      "fieldName": "title",
      "oldValue": "Original Todo",
      "newValue": "Updated Todo",
      "fieldType": "String"
    },
    "completed": {
      "fieldName": "completed",
      "oldValue": false,
      "newValue": true,
      "fieldType": "Boolean"
    }
  }
}
```

---

## Testing

### Unit Test Example

```java
package com.example.todolist.service;

import com.example.todolist.context.RequestContext;
import com.example.todolist.model.AuditOperation;
import com.example.todolist.model.UserActionLogs;
import com.example.todolist.repository.UserActionLogsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AuditCaptureServiceTest {

    @Autowired
    private AuditCaptureService auditCaptureService;

    @Autowired
    private UserActionLogsRepository repository;

    @BeforeEach
    void setUp() {
        RequestContext.setRequestId(UUID.randomUUID().toString());
        RequestContext.setUserId("test-user@example.com");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void testCaptureCreate() {
        auditCaptureService.captureCreate("Todo", 123L);

        List<UserActionLogs> logs = repository.findByEntityTypeAndEntityId("Todo", 123L);

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getOperation()).isEqualTo(AuditOperation.CREATE);
        assertThat(logs.get(0).getCreatedBy()).isEqualTo("test-user@example.com");
    }

    @Test
    void testCaptureUpdate() {
        Map<String, FieldChange> changes = new HashMap<>();
        changes.put("title", FieldChange.builder()
            .fieldName("title")
            .oldValue("Old")
            .newValue("New")
            .fieldType("String")
            .build());

        auditCaptureService.captureUpdate("Todo", 123L, changes);

        List<UserActionLogs> logs = repository.findByEntityTypeAndEntityId("Todo", 123L);

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getOperation()).isEqualTo(AuditOperation.UPDATE);
        assertThat(logs.get(0).getChanges()).hasSize(1);
        assertThat(logs.get(0).getChanges().get("title").getOldValue()).isEqualTo("Old");
    }
}
```

---

## Troubleshooting

### Issue: `JsonBinaryType` class not found

**Symptom**: Compilation error: `cannot find symbol class JsonBinaryType`

**Solution**:
1. Verify `hypersistence-utils-hibernate-63` dependency is in `pom.xml`
2. Run `./mvnw clean install` to download dependencies
3. Refresh your IDE's Maven project

### Issue: Audit logs have null `created_by` field

**Symptom**: `created_by` is always `null` in database.

**Solution**:
1. Verify `RequestContext.setUserId()` is called in `JwtAuthenticationFilter`
2. Check that authentication succeeds before audit capture
3. Verify ThreadLocal is not cleared before audit service executes

### Issue: Audit logs have null `request_id` field

**Symptom**: `request_id` is always `null`.

**Solution**:
1. Verify `RequestCorrelationFilter` is registered as `@Component`
2. Check that filter `@Order(1)` executes before business logic
3. Ensure `RequestContext.setRequestId()` is called in filter

### Issue: JSONB column type not recognized

**Symptom**: PostgreSQL error: `type "jsonb" does not exist`

**Solution**:
1. Verify you're using PostgreSQL 9.4+
2. Check Liquibase migration has `columnDefinition: "JSONB"`
3. Run migration manually: `make migrate`

### Issue: Changes field is empty even though fields changed

**Symptom**: `changes` JSONB is `{}` for UPDATE operations.

**Solution**:
1. Verify field comparison logic is comparing old vs new values
2. Check that `EntityComparator` is comparing the right fields
3. Ensure field values are actually different (e.g., "Old" != "New")

---

## Performance Considerations

### Write Performance

**Impact**: Each entity save triggers an additional write to `admin_portal_user_action_logs` (~10-15% overhead).

**Mitigation**:
- Audit writes happen in same transaction (efficient)
- Use Hibernate batch inserts: `spring.jpa.properties.hibernate.jdbc.batch_size=20`

### Query Performance

**Impact**: Audit table grows indefinitely; queries may slow over time.

**Mitigation**:
- Indexes on `entity_type`, `request_id`, `created_by`, `created_at` already defined
- Use pagination (limit results to 20-100 items)
- Consider partitioning for datasets >1M rows

### Storage Growth

**Estimate**: ~274 MB/year for 1500 daily operations (see [data-model.md](data-model.md#storage-considerations))

**Mitigation** (future):
- Table partitioning by `created_at` (monthly/yearly)
- Archival to cold storage for old logs (>3 years)
- PostgreSQL JSONB compression (automatic)

---

## Next Steps

1. **Generate Tasks**: Run `/speckit.tasks` to create implementation task list
2. **Implement REST API**: Create `AuditLogController` with search endpoints
3. **Add Security**: Restrict audit endpoints to `ROLE_ADMIN` users
4. **Monitor Performance**: Track audit table growth and query performance

---

## Additional Resources

- **Feature Specification**: [spec.md](spec.md)
- **Data Model**: [data-model.md](data-model.md)
- **API Contracts**: [contracts/audit-api.yaml](contracts/audit-api.yaml)
- **Research Notes**: [research.md](research.md)
- **Implementation Plan**: [plan.md](plan.md)

---

**Last Updated**: 2026-02-11 (Updated for custom UserActionLogs model)
**Status**: Ready for implementation with custom audit model
