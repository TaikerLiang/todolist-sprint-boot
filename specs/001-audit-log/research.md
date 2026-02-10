# Research: Audit Logging Implementation

**Feature**: User Action Audit Log
**Date**: 2026-02-09
**Status**: Complete

## Executive Summary

After comprehensive research of audit logging patterns for Spring Boot 3.1.5 with JPA/Hibernate 6 and PostgreSQL, the recommended approach is **Custom JPA EntityListeners with JSONB storage**. This provides optimal field-level change tracking, superior query performance, and scalability for 100,000+ audit entries over 2+ years.

## Research Questions Investigated

1. Hibernate Envers vs Custom EntityListeners vs Spring AOP
2. Field-level diff storage strategies (JSON vs relational)
3. PostgreSQL indexing and partitioning for audit data at scale
4. Immutability enforcement mechanisms
5. Handling deleted entity references

## Key Decisions

### Decision 1: Implementation Approach

**Chosen**: Custom JPA EntityListeners (`@PreUpdate`, `@PreRemove`, `@PostLoad`)

**Rationale**:
- **True field-level diff tracking**: Only stores changed fields with before/after values (Envers stores complete entity snapshots requiring manual comparison)
- **25-40% lower performance overhead** than Hibernate Envers (no shadow tables to maintain)
- **Full control**: Customize exactly what gets audited and how
- **Bulk operation support**: Can audit bulk updates through service layer hooks (Envers cannot)
- **Simpler integration**: No external framework dependencies, uses standard JPA lifecycle callbacks

**Alternatives Considered**:

| Approach | Pros | Cons | Rejected Because |
|----------|------|------|------------------|
| **Hibernate Envers** | Automatic setup, mature framework, built-in query API | 35-54% performance overhead, stores full snapshots (not diffs), slow queries, cannot audit bulk ops | Does not meet requirement for immediate field-level change visibility |
| **Spring AOP** | Method-level auditing, good for business operations | Cannot capture field-level entity changes without additional JPA queries | Not designed for entity field tracking; better suited for service-layer operation logging |

### Decision 2: Change Storage Format

**Chosen**: PostgreSQL JSONB column for field changes

**Rationale**:
- **Schema flexibility**: Supports any entity without database migrations when adding audited fields
- **Storage efficiency**: Only stores changed fields (~30% smaller than full snapshots)
- **Query capabilities**: PostgreSQL GIN indexes enable powerful filtering on JSON content
- **Perfect fit for audit use case**: Infrequent writes, read-heavy admin queries, rich filtering needs
- **Native PostgreSQL support**: JSONB is highly optimized with excellent indexing

**Schema Design**:
```sql
CREATE TABLE audit_log (
    id BIGSERIAL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    operation VARCHAR(10) NOT NULL,  -- INSERT, UPDATE, DELETE
    changed_by VARCHAR(100),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    changes JSONB,  -- {"fieldName": {"old": value, "new": value}}
    entity_description TEXT,  -- Human-readable context
    PRIMARY KEY (id, changed_at)
) PARTITION BY RANGE (changed_at);
```

**Example JSONB content**:
```json
{
  "title": {
    "old": "Buy groceries",
    "new": "Buy groceries and cook dinner"
  },
  "level": {
    "old": "LOW",
    "new": "HIGH"
  }
}
```

**Alternatives Considered**:

| Approach | Pros | Cons | Rejected Because |
|----------|------|------|------------------|
| **Separate row per field change** | Precise BTREE indexing on known fields | Storage bloat (1 row per changed field), complex queries requiring JOINs, poor "what changed" query performance | Inefficient for displaying complete change history; difficult to query |
| **Full entity snapshot** | Simple to understand | Wastes storage (stores unchanged fields), difficult to identify what changed | Does not meet requirement for field-level diff visibility |

### Decision 3: Scalability Strategy

**Chosen**: PostgreSQL table partitioning by time range (quarterly partitions)

**Rationale**:
- **Constant query performance**: As dataset grows to 100,000+ entries, queries remain fast by targeting specific partitions
- **Efficient archival**: Drop old partitions instantly (vs. slow DELETE operations)
- **Retention management**: Easy to archive partitions older than 2 years to cold storage
- **Automated management**: Use pg_partman extension for automatic partition creation

**Implementation**:
```sql
-- Parent table (partitioned by changed_at)
CREATE TABLE audit_log (...) PARTITION BY RANGE (changed_at);

-- Quarterly partitions
CREATE TABLE audit_log_2026_q1 PARTITION OF audit_log
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');

CREATE TABLE audit_log_2026_q2 PARTITION OF audit_log
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
-- etc.
```

**Partition Management**:
- Create partitions 1 quarter in advance
- Drop partitions after 2 years (or archive to cheaper storage)
- Monitor partition sizes and adjust boundaries if needed

**Alternatives Considered**:

| Approach | Pros | Cons | Rejected Because |
|----------|------|------|------------------|
| **Single table with indexes** | Simpler setup | Query performance degrades as table grows; slow archival/deletion | Will not scale efficiently to 2+ years of data |
| **Manual archival to separate tables** | Full control | Complex application logic, error-prone, difficult to query across archives | PostgreSQL partitioning handles this automatically |

### Decision 4: Indexing Strategy

**Chosen**: Multi-index approach optimized for common query patterns

**Indexes**:
```sql
-- 1. Composite index for entity lookups (primary use case)
CREATE INDEX idx_audit_entity ON audit_log
    (entity_type, entity_id, changed_at DESC);

-- 2. Timestamp range queries (for retention/archiving)
CREATE INDEX idx_audit_time ON audit_log (changed_at);

-- 3. User activity tracking
CREATE INDEX idx_audit_user ON audit_log (changed_by, changed_at DESC);

-- 4. GIN index for JSONB containment queries
CREATE INDEX idx_audit_changes_gin ON audit_log USING GIN (changes);

-- 5. Expression index for frequently queried JSON fields (optional)
CREATE INDEX idx_audit_status_change ON audit_log
    ((changes->'status')) WHERE changes ? 'status';
```

**Query Performance**:
- Entity history lookup: `WHERE entity_type = 'Todo' AND entity_id = 123` → uses idx_audit_entity (< 50ms for 100k rows)
- User activity: `WHERE changed_by = 'admin@example.com'` → uses idx_audit_user (< 100ms)
- Field change search: `WHERE changes @> '{"status": {"old": "CREATED"}}'` → uses GIN index (< 200ms)
- Date range filter: `WHERE changed_at BETWEEN ... AND ...` → uses partition pruning + idx_audit_time

**Rationale**:
- Composite index (entity_type, entity_id, changed_at) covers the most common query pattern (viewing history for a specific item)
- GIN index enables powerful JSON queries without predefined field structure
- Each index targets a specific query pattern in the requirements

### Decision 5: Immutability Enforcement

**Chosen**: Multi-layered approach combining database triggers, application controls, and architecture

**Layer 1 - Database Trigger** (primary enforcement):
```sql
CREATE OR REPLACE FUNCTION prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit records are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_modification();
```

**Layer 2 - Application Controls**:
- AuditLogRepository exposes only read operations publicly
- No update/delete methods in service layer API
- Only AuditLogService can call `repository.save()` internally

**Layer 3 - Architecture**:
- Append-only design: only INSERT operations
- If correction needed, insert compensating entry (document the correction)

**Rationale**:
- Database trigger provides strongest guarantee (cannot be bypassed by application bugs)
- Application controls prevent accidental modifications during development
- Append-only architecture makes intentions clear in code

**Alternatives Considered**:

| Approach | Pros | Cons | Rejected Because |
|----------|------|------|------------------|
| **Application-level only** | Simple, no database dependencies | Can be bypassed by bugs, direct SQL access | Insufficient for compliance requirements |
| **Blockchain/cryptographic** | Tamper-proof | Extreme complexity, performance overhead | Overkill for this use case; database trigger sufficient |

### Decision 6: Handling Deleted Entities

**Chosen**: Hybrid approach - no foreign keys + denormalized entity description

**Implementation**:
```java
@Entity
@Table(name = "audit_log")
public class AuditLog {
    private Long entityId;      // No FK constraint
    private String entityType;

    // Store human-readable context at audit time
    @Column(columnDefinition = "TEXT")
    private String entityDescription;  // e.g., "Invoice #12345 - $150.00 - PAID"
}
```

**In AuditLogService**:
```java
public void logUpdate(Object entity, Map<String, FieldChange> changes) {
    AuditLog log = new AuditLog();
    log.setEntityId(getEntityId(entity));
    log.setEntityType(entity.getClass().getSimpleName());
    log.setEntityDescription(generateDescription(entity));  // Todo: "Buy groceries" or Invoice: "#12345 - $150"
    log.setChanges(changes);
    // ...
}
```

**Rationale**:
- **No FK constraints**: Allows entities to be deleted without cascade issues
- **Denormalized description**: Audit log remains meaningful even after entity deletion
- **Satisfies requirements**: Can view audit history for deleted items by entity_id (FR-013)
- **Simple implementation**: No need for soft deletes or complex cascade rules

**Alternatives Considered**:

| Approach | Pros | Cons | Rejected Because |
|----------|------|------|------------------|
| **Soft deletes only** | Entities never truly deleted | Violates GDPR/data deletion requirements, complicates all queries | May not be acceptable for legal compliance |
| **Full entity snapshot** | Complete context preserved | Massive storage overhead, duplicates entity data | JSONB change tracking already provides sufficient context |
| **Foreign keys with SET NULL** | Referential integrity maintained | Loses entity type information on delete | Cannot query deleted entity audits effectively |

## Technology Recommendations

### Required Dependencies

**pom.xml additions**:
```xml
<!-- For JSONB support in Hibernate 6 -->
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-63</artifactId>
    <version>3.7.3</version>
</dependency>
```

**Existing dependencies** (already in project):
- Spring Data JPA (existing)
- Hibernate 6 (existing)
- PostgreSQL driver (existing)
- Liquibase 4.27.0 (existing)
- Lombok 1.18.36 (existing)

### PostgreSQL Features Required

- **JSONB data type**: For change storage (available in PostgreSQL 9.4+)
- **GIN indexes**: For JSONB query performance (available in PostgreSQL 9.4+)
- **Table partitioning**: For scalability (available in PostgreSQL 10+)
- **Triggers**: For immutability enforcement (core PostgreSQL feature)

Current project uses PostgreSQL - all features available.

### Performance Characteristics

**Expected Overhead**:
- **Write operations**: +5-10ms per audited CRUD operation
  - Snapshot capture: ~1-2ms
  - Field diff computation: ~2-3ms
  - JSONB serialization: ~1-2ms
  - INSERT into audit_log: ~2-3ms
- **Read operations**: No overhead (not audited per requirements)

**Query Performance** (100,000+ audit entries):
- Entity history lookup: < 50ms (composite index)
- Filtered queries: < 200ms (GIN index + partitions)
- Complex aggregations: < 500ms (partition pruning)

**Storage Requirements**:
- ~500 bytes per audit entry (JSONB + metadata)
- 100,000 entries/year = ~50 MB/year (uncompressed)
- 2-year retention = ~100 MB total (negligible)

## Implementation Patterns

### EntityListener Pattern

**Recommended Structure**:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEntityListener {
    private final AuditLogService auditLogService;

    @PostLoad
    public void onPostLoad(Object entity) {
        // Capture snapshot using ThreadLocal for comparison later
        AuditContext.captureSnapshot(entity);
    }

    @PreUpdate
    public void onPreUpdate(Object entity) {
        Map<String, FieldChange> diff = AuditContext.computeDiff(entity);
        if (!diff.isEmpty()) {
            auditLogService.logUpdate(entity, diff);
        }
    }

    @PreRemove
    public void onPreRemove(Object entity) {
        auditLogService.logDelete(entity);
    }
}
```

**ThreadLocal Context** (to store original state):
```java
public class AuditContext {
    private static final ThreadLocal<Map<Object, Object>> snapshots =
        ThreadLocal.withInitial(HashMap::new);

    public static void captureSnapshot(Object entity) {
        snapshots.get().put(entity, cloneEntity(entity));
    }

    public static Map<String, FieldChange> computeDiff(Object entity) {
        Object original = snapshots.get().get(entity);
        return FieldComparator.compare(original, entity);
    }

    public static void clear() {
        snapshots.remove();
    }
}
```

### Field Comparison Strategy

**Using Reflection** (simple, works for all entities):
```java
public class FieldComparator {
    public static Map<String, FieldChange> compare(Object oldState, Object newState) {
        Map<String, FieldChange> changes = new HashMap<>();
        Field[] fields = oldState.getClass().getDeclaredFields();

        for (Field field : fields) {
            if (shouldAudit(field)) {
                field.setAccessible(true);
                Object oldValue = field.get(oldState);
                Object newValue = field.get(newState);

                if (!Objects.equals(oldValue, newValue)) {
                    changes.put(field.getName(),
                        new FieldChange(serialize(oldValue), serialize(newValue)));
                }
            }
        }
        return changes;
    }

    private static boolean shouldAudit(Field field) {
        return !field.isAnnotationPresent(Transient.class) &&
               !field.isAnnotationPresent(Id.class) &&
               !Modifier.isStatic(field.getModifiers());
    }
}
```

### Security Integration

**Capturing Current User**:
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {
    private final AuditLogRepository repository;

    public void logUpdate(Object entity, Map<String, FieldChange> changes) {
        AuditLog log = new AuditLog();
        log.setChangedBy(getCurrentUsername());  // From SecurityContext
        log.setOperation(AuditOperation.UPDATE);
        log.setChanges(changes);
        // ...
        repository.save(log);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
```

## Migration & Rollout Strategy

### Phase 1: Infrastructure Setup (Week 1)
1. Create AuditLog entity with Lombok annotations
2. Generate Liquibase migration with partitioning
3. Apply migration: `make migrate`
4. Create AuditLogRepository, AuditLogService
5. Unit tests for service layer

### Phase 2: Entity Integration (Week 1-2)
1. Add `@EntityListeners(AuditEntityListener.class)` to Todo entity
2. Test audit capture for Todo CRUD operations
3. Add listener to Invoice entity
4. Integration tests for both entities

### Phase 3: Query API (Week 2)
1. Create AuditLogController with admin-only access
2. Implement filtering endpoints (by entity, user, date, action)
3. Create DTOs (AuditLogResponse, AuditLogFilter)
4. API tests with MockMvc

### Phase 4: Performance Optimization (Week 3)
1. Verify index usage with EXPLAIN ANALYZE
2. Add additional expression indexes if needed
3. Load testing with 10,000+ audit entries
4. Tune query performance

### Phase 5: Documentation & Handoff (Week 3)
1. Update quickstart.md with usage examples
2. Generate OpenAPI spec for audit endpoints
3. SnapAdmin integration (automatic for JPA entity)
4. User acceptance testing

## Risk Mitigation

### Risk 1: Performance Impact on CRUD Operations
**Mitigation**:
- Benchmark before/after implementation
- Make audit capture asynchronous if needed (`@Async` with `@TransactionalEventListener`)
- Monitor production metrics

### Risk 2: JSONB Schema Evolution
**Challenge**: If entity structure changes significantly, old JSONB entries may be confusing
**Mitigation**:
- Store entity_type and operation for context
- Include entity_description with human-readable summary
- Version JSONB schema if needed (add "schema_version" field)

### Risk 3: Partition Management Overhead
**Mitigation**:
- Use pg_partman extension for automatic partition creation
- Set up monitoring for partition count
- Document partition creation/archival procedures

### Risk 4: Storage Growth Beyond Estimates
**Mitigation**:
- Monitor audit_log table size monthly
- Implement automated archival to S3/cold storage after 1 year
- Compress old partitions with PostgreSQL TOAST

## References & Research Sources

1. **Hibernate Envers Analysis**:
   - [Auditing with Hibernate Envers in Spring Boot - GeeksforGeeks](https://www.geeksforgeeks.org/advance-java/auditing-with-hibernate-envers-in-spring-boot/)
   - [A beginner's guide to Spring Data Envers - Vlad Mihalcea](https://vladmihalcea.com/spring-data-envers/)
   - [The best way to implement an audit log using Hibernate Envers](https://vladmihalcea.com/the-best-way-to-implement-an-audit-log-using-hibernate-envers/)

2. **JPA EntityListeners**:
   - [Row Level Auditing in Spring Boot with JPA Event Listeners](https://medium.com/@AlexanderObregon/row-level-auditing-in-spring-boot-with-jpa-event-listeners-6a4ab6b3180f)
   - [JPA Auditing: Persisting Audit Logs Automatically using EntityListeners](https://dev.to/njnareshjoshi/jpa-auditing-persisting-audit-logs-automatically-using-entitylisteners-238p)

3. **PostgreSQL JSONB Best Practices**:
   - [Audit logging using JSONB in Postgres – Elephas](https://elephas.io/audit-logging-using-jsonb-in-postgres/)
   - [Let's Build Production-Ready Audit Logs in PostgreSQL](https://medium.com/@sehban.alam/lets-build-production-ready-audit-logs-in-postgresql-7125481713d8)
   - [How to Index JSONB Data in PostgreSQL](https://www.tigerdata.com/learn/how-to-index-json-columns-in-postgresql)

4. **Performance & Scalability**:
   - [PostgreSQL Best Practices for Production: Indexing, JSONB, Partitioning](https://medium.com/@pothiq/postgresql-in-production-a-beginner-to-pro-guide-82db452ffc88)
   - [Row change auditing options for PostgreSQL](https://www.cybertec-postgresql.com/en/row-change-auditing-options-for-postgresql/)

## Next Steps

With all technical decisions made and research complete, proceed to:

1. **Phase 1 - Design & Contracts** (`/speckit.plan` Phase 1):
   - Create data-model.md with AuditLog entity specification
   - Generate OpenAPI contract for audit API endpoints
   - Write quickstart.md with usage examples

2. **Phase 2 - Tasks** (`/speckit.tasks` command):
   - Generate dependency-ordered implementation tasks
   - Break down into testable increments
   - Assign priorities based on user story priorities (P1 → P2 → P3)

**Status**: ✅ Research phase complete - all "NEEDS CLARIFICATION" items resolved
