# Research: Admin Audit Log Technical Decisions

**Date**: 2026-02-11
**Feature**: 002-audit-log
**Plan**: [plan.md](plan.md)

## Executive Summary

This document captures architectural decisions and technical research for implementing the admin audit logging system. All decisions are based on the project's technology stack (Spring Boot 3.1.5, Hibernate 6, PostgreSQL) and align with the project constitution.

---

## Decision 1: Audit Capture Mechanism

### Question
What is the best approach for intercepting and capturing entity changes (CREATE, UPDATE, DELETE) for audit logging?

### Options Evaluated
1. **JPA Entity Listeners** (`@EntityListeners`, `@PreUpdate`, etc.)
2. **Spring AOP** with `@Transactional` aspects
3. **Hibernate Envers** (mature audit framework)
4. **Custom Audit Model** (UserActionLogs with manual capture)

### Decision: ✅ Custom Audit Model (UserActionLogs)

**Rationale** (User-specified requirement):
- **Explicit control**: User provided specific model design with `UserActionLogs` entity using JSONB for field changes
- **Flexibility**: Custom model allows for exact schema control (table name, column names, index strategy)
- **JSONB storage**: Uses PostgreSQL JSONB for flexible field change storage via `hypersistence-utils`
- **Custom requirements**: Matches user's repository pattern preferences (RepoCustom/RepoImpl)
- **Alignment with existing patterns**: Follows project's established entity design patterns

**Implementation Approach**: Custom `UserActionLogs` entity with JPA Entity Listeners or service-layer manual audit capture

**Alternatives Considered** (from research):
- **Hibernate Envers**: Would provide automatic auditing but less control over schema; research completed for reference
- **JPA Entity Listeners**: Complex for field-level tracking but viable with custom code
- **Spring AOP**: Transaction coordination challenges; not chosen

### Implementation Approach

**Maven Dependency**:
```xml
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-63</artifactId>
    <version>3.7.3</version>
</dependency>
```

**UserActionLogs Entity**:
```java
@Entity
@Table(name = "admin_portal_user_action_logs", indexes = {
    @Index(name = "idx_user_action_logs_entity_type", columnList = "entity_type"),
    @Index(name = "idx_user_action_logs_request_id", columnList = "request_id")
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

**FieldChange Class** (stored in JSONB):
```java
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

**Audit Capture Service** (manual approach):
```java
@Service
@RequiredArgsConstructor
public class AuditCaptureService {
    private final UserActionLogsRepository repository;

    public void captureAudit(String entityType, Long entityId, AuditOperation operation,
                              Map<String, FieldChange> changes) {
        UserActionLogs audit = UserActionLogs.builder()
            .entityType(entityType)
            .entityId(entityId)
            .operation(operation)
            .createdBy(RequestContext.getUserId())
            .requestId(UUID.fromString(RequestContext.getRequestId()))
            .changes(changes)
            .build();

        repository.save(audit);
    }
}
```

---

## Decision 2: JSONB Storage Strategy

### Question
How should we store field changes (before/after values) in PostgreSQL with Spring Data JPA and Hibernate 6?

### Decision: ✅ `@JdbcTypeCode(SqlTypes.JSON)` with PostgreSQL JSONB

**Rationale**:
- **Native Hibernate 6 support**: No additional dependencies required beyond Jackson (already included in `spring-boot-starter-web`)
- **Type-safe and flexible**: Works with `Map<String, Object>` for dynamic field changes
- **PostgreSQL JSONB**: Binary JSON format provides efficient storage and indexing
- **Automatic serialization**: Hibernate detects Jackson on classpath and handles serialization/deserialization automatically
- **Queryable**: PostgreSQL JSONB operators enable filtering by field change content

**Note**: Given that we're using Hibernate Envers, we may not need custom JSONB field change storage. Envers automatically stores complete entity state at each revision. However, this approach is documented for flexibility if we need additional custom metadata.

### Implementation Example

**Entity with JSONB Column**:
```java
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", columnDefinition = "jsonb")
    private Map<String, Object> changes = new HashMap<>();
}
```

**Liquibase Migration**:
```yaml
- addColumn:
    tableName: audit_logs
    columns:
      - column:
          name: metadata
          type: jsonb
          defaultValueComputed: "'{}'::jsonb"
```

**Querying JSONB Data**:
```java
@Query(value = "SELECT * FROM audit_logs WHERE metadata->>'fieldName' = :value",
       nativeQuery = true)
List<AuditLog> findByMetadataField(@Param("value") String value);
```

**Supported Data Types** (automatically serialized by Jackson):
- String, Integer, Long, Double, Boolean
- null values
- Nested Maps and Lists
- Custom POJOs (auto-serialized to JSON)

---

## Decision 3: Request ID Correlation

### Question
How should we generate, propagate, and access request IDs across the entire transaction lifecycle?

### Decision: ✅ MDC + ThreadLocal via RequestContext

**Rationale**:
- **MDC (Mapped Diagnostic Context)**: Industry-standard solution for request correlation in logging; automatically integrates with Slf4j (project already uses `@Slf4j`)
- **ThreadLocal**: Provides thread-safe storage for request-scoped data accessible anywhere in the call stack without method signature pollution
- **No coupling**: Business logic doesn't need to pass request IDs as parameters
- **Automatic log correlation**: Request ID appears in all log statements automatically
- **Spring-friendly**: Works seamlessly with Spring Web filters and transaction management

### Implementation Approach

**1. Request Correlation Filter**:
```java
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

        String requestId = extractOrGenerateRequestId(request);

        try {
            MDC.put(MDC_REQUEST_ID_KEY, requestId);
            RequestContext.setRequestId(requestId);
            response.setHeader(REQUEST_ID_HEADER, requestId);

            filterChain.doFilter(request, response);

        } finally {
            // CRITICAL: Always clear to prevent memory leaks
            MDC.remove(MDC_REQUEST_ID_KEY);
            RequestContext.clear();
        }
    }

    private String extractOrGenerateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        return (requestId == null || requestId.trim().isEmpty())
            ? UUID.randomUUID().toString()
            : requestId;
    }
}
```

**2. RequestContext Utility**:
```java
public final class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

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

**3. Integration with JWT Filter**:
Modify existing `JwtAuthenticationFilter.java` to populate user context:
```java
// After successful authentication
SecurityContextHolder.getContext().setAuthentication(authentication);
RequestContext.setUserId(userId.toString());  // NEW LINE
```

**4. Logback Configuration** (`logback-spring.xml`):
```xml
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{requestId}] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
</appender>
```

**5. Accessing in Services**:
```java
@Service
@RequiredArgsConstructor
public class AuditLogService {

    public void captureAudit(String entityType, Long entityId, String operation) {
        // Access without passing parameters!
        String requestId = RequestContext.getRequestId();
        String userId = RequestContext.getUserId();

        // ... save audit log
    }
}
```

**Important Considerations**:
- **Memory leak prevention**: Always clear ThreadLocal in `finally` block
- **Async processing**: ThreadLocal doesn't propagate to `@Async` methods; must capture and pass explicitly
- **Non-HTTP contexts**: Scheduled tasks can generate synthetic request IDs (`SCHEDULED-{UUID}`)

---

## Decision 4: Transaction Boundary Management

### Question
How do we ensure audit writes participate in the same transaction as business operations (FR-014 requirement)?

### Decision: ✅ Hibernate Envers handles this automatically

**Rationale**:
- Envers writes to audit tables (`*_aud`) within the same Hibernate transaction
- If business operation fails → transaction rolls back → no audit log written ✅
- If audit write fails → transaction rolls back → business operation reverted ✅
- No additional configuration needed beyond `@Transactional` on service methods

**Transaction Flow**:
```java
@Service
@RequiredArgsConstructor
public class TodoService {
    private final TodoRepository todoRepository;

    @Transactional  // Single transaction for both operations
    public Todo createTodo(Todo todo) {
        Todo saved = todoRepository.save(todo);
        // Envers automatically captures audit within same transaction
        return saved;
    }
}
```

**Verification**: If `todoRepository.save()` throws exception, neither the todo nor the audit log are persisted.

---

## Decision 5: User Context Extraction

### Question
How do we capture the current authenticated user for audit logs?

### Decision: ✅ Spring Security Context + RequestContext

**Rationale**:
- Project already has Spring Security with JWT authentication
- User information is available via `SecurityContextHolder`
- Combine with `RequestContext` for easy access throughout the application

**Implementation**:
```java
// In JwtAuthenticationFilter (already exists)
Authentication authentication = ... // from JWT
SecurityContextHolder.getContext().setAuthentication(authentication);
RequestContext.setUserId(userId.toString());  // Store in RequestContext

// In Envers RevisionListener
@Component
public class CustomRevisionListener implements RevisionListener {
    @Override
    public void newRevision(Object revisionEntity) {
        CustomRevisionEntity revision = (CustomRevisionEntity) revisionEntity;

        // Option 1: From SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null) ? auth.getName() : "system";

        // Option 2: From RequestContext (easier)
        String userId = RequestContext.getUserId();

        revision.setUsername(username);
        revision.setRequestId(RequestContext.getRequestId());
    }
}
```

---

## Technology Stack Confirmation

| Component | Technology | Version | Status |
|-----------|-----------|---------|--------|
| Audit Model | Custom UserActionLogs entity | Custom | ✅ Implemented |
| JSONB Support | Hypersistence Utils | 3.7.3 | ✅ Added to pom.xml |
| Request Correlation | MDC + ThreadLocal | Slf4j (existing) | ✅ No dependencies |
| JSON Serialization | Jackson | Spring Boot 3.1.5 (existing) | ✅ No changes needed |
| Database | PostgreSQL with JSONB | Existing | ✅ No changes needed |
| Migrations | Liquibase | 4.27.0 (existing) | ✅ No changes needed |

---

## Implementation Roadmap

### Phase 1: Core Model Setup
1. ✅ Create `UserActionLogs` entity with JSONB support
2. ✅ Create `FieldChange` class for JSONB field changes
3. ✅ Create `AuditOperation` enum (CREATE/UPDATE/DELETE)
4. ✅ Add `hypersistence-utils` dependency to `pom.xml`
5. Generate Liquibase migration for `admin_portal_user_action_logs` table
6. Create `UserActionLogsRepository` with custom query interface

### Phase 2: Audit Capture Mechanism
1. Create `AuditCaptureService` for manual audit logging
2. Implement field comparison utility (detect before/after changes)
3. Integrate audit capture into `TodoService` and `InvoiceService`
4. OR: Create JPA Entity Listeners for automatic capture (alternative approach)
5. Ensure transactional behavior (audit writes fail with business operation)

### Phase 3: Request Context Infrastructure
1. Create `RequestContext` utility class (ThreadLocal wrapper)
2. Create `RequestCorrelationFilter` (generates/extracts request IDs)
3. Modify `JwtAuthenticationFilter` to populate user ID in RequestContext
4. Update `logback-spring.xml` to include MDC in log pattern
5. Test request ID propagation in logs

### Phase 4: REST API Layer
1. Create `AuditLogController` with endpoints:
   - `GET /api/audit/todos/{id}/history` - Audit history for specific todo
   - `GET /api/audit/invoices/{id}/history` - Audit history for specific invoice
   - `GET /api/audit/search` - Search/filter audit logs
2. Implement DTOs for API requests/responses
3. Implement custom repository queries for filtering/searching
4. Add pagination and sorting support
5. Secure endpoints (admin-only access via Spring Security)

---

## References

### Audit Capture Mechanism
- [Auditing with JPA, Hibernate, and Spring Data JPA | Baeldung](https://www.baeldung.com/database-auditing-jpa)
- [Auditing with Hibernate Envers in Spring Boot - GeeksforGeeks](https://www.geeksforgeeks.org/advance-java/auditing-with-hibernate-envers-in-spring-boot/)
- [How to Track Data Changes with Hibernate Envers in Spring (2026)](https://oneuptime.com/blog/post/2026-01-25-track-data-changes-hibernate-envers-spring/view)
- [Hibernate Envers – Extending Revision Info | Baeldung](https://www.baeldung.com/java-hibernate-envers-extending-revision-custom-fields)

### JSONB Storage
- [Storing PostgreSQL JSONB Using Spring Boot and JPA | Baeldung](https://www.baeldung.com/spring-boot-jpa-storing-postgresql-jsonb)
- [Postgres JSON Functions With Hibernate 6 - DZone](https://dzone.com/articles/postgres-json-functions-with-hibernate-6)
- [Querying JSONB Columns Using Spring Data JPA | Baeldung](https://www.baeldung.com/spring-data-jpa-querying-jsonb-columns)

### Request Correlation
- [Enhancing Logging in Spring Boot with MDC | Medium](https://medium.com/@sudacgb/enhancing-logging-in-spring-boot-with-mapped-diagnostic-context-mdc-a-step-by-step-tutorial-0a57b0304dd3)
- [Request Correlation IDs in Spring Boot for Distributed Tracing | Medium](https://medium.com/@AlexanderObregon/request-correlation-ids-in-spring-boot-for-distributed-tracing-da86fbe5e47b)
- [Java Logging with MDC | Baeldung](https://www.baeldung.com/mdc-in-log4j-2-logback)
- [Using correlation IDs in spring boot for distributed tracing](https://fati.dev/blog/using-correlation-ids-in-spring-boot-for-distributed-tracing/)

---

**Last Updated**: 2026-02-11
**Status**: Research complete, ready for Phase 1 implementation
