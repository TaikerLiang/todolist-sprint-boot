# Schema Update Summary

**Date**: 2026-02-09
**Updated By**: Schema improvements based on user feedback

## Changes Applied

### 1. ✅ Updated data-model.md

**Field Changes**:
- **id**: BIGSERIAL → UUID (gen_random_uuid())
- **changed_by → created_by**: Renamed to reflect append-only semantics, matches User.username
- **changed_at → created_at**: Renamed to reflect append-only semantics
- **entity_description**: REMOVED (not needed, simplifies schema)
- **request_id**: ADDED (UUID) - Tracks API requests for correlation

**Updated Sections**:
- Entity fields table
- Indexes (all references updated)
- Partitioning strategy (partition by created_at)
- Constraints
- Sample data (JSON examples)
- Query examples (6 examples, including new request correlation query)
- Validation rules
- Storage estimates (reduced ~10% without entity_description)

**Migration Approach**:
- Changed to use `make makemigration` instead of hardcoded SQL
- Documents manual post-generation steps (partitioning, triggers, indexes)

### 2. ✅ Created example-AuditLog.java

**File**: `specs/001-audit-log/example-AuditLog.java`

**Features**:
- UUID primary key with @GeneratedValue(strategy = GenerationType.UUID)
- Field name changes: createdBy, createdAt
- New field: requestId (UUID)
- Removed: entityDescription
- Comprehensive JavaDoc documentation
- Custom constructor for audit log creation
- JPA indexes via @Index annotations

### 3. ✅ Updated contracts/audit-log-api.yaml

**OpenAPI Schema Changes**:
- **AuditLogResponse.id**: integer/int64 → string/uuid
- **changedBy → createdBy**: Query parameter and response field
- **changedAt → createdAt**: Response field
- **entityDescription**: REMOVED from schema
- **requestId**: ADDED to schema (string/uuid, nullable)

**Impact**: All API consumers will need to update:
- Field name changes in requests/responses
- ID type change from integer to UUID string

---

## Implementation Recommendations

### Request ID Implementation

Based on your requirements, I recommend **Option C** (hybrid approach):

```java
// 1. Create RequestIdInterceptor
@Component
public class RequestIdInterceptor implements HandlerInterceptor {
    private static final ThreadLocal<UUID> REQUEST_ID = new ThreadLocal<>();
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    public static UUID getCurrentRequestId() {
        return REQUEST_ID.get();
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // Use client-provided request ID if present, otherwise generate
        String requestIdHeader = request.getHeader(REQUEST_ID_HEADER);
        UUID requestId = requestIdHeader != null
            ? UUID.fromString(requestIdHeader)
            : UUID.randomUUID();

        REQUEST_ID.set(requestId);

        // Echo back in response for debugging
        response.setHeader(REQUEST_ID_HEADER, requestId.toString());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        REQUEST_ID.remove(); // Clean up ThreadLocal
    }
}

// 2. Register Interceptor
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private RequestIdInterceptor requestIdInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestIdInterceptor);
    }
}

// 3. Update AuditLogService to capture request ID
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {
    private final AuditLogRepository repository;

    public void logUpdate(Object entity, Map<String, FieldChange> changes) {
        AuditLog log = new AuditLog();
        log.setEntityType(entity.getClass().getSimpleName());
        log.setEntityId(getEntityId(entity));
        log.setOperation(AuditOperation.UPDATE);
        log.setCreatedBy(getCurrentUsername());
        log.setCreatedAt(Instant.now());
        log.setChanges(changes);
        log.setRequestId(RequestIdInterceptor.getCurrentRequestId()); // <-- ADD THIS

        repository.save(log);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
```

**Benefits**:
- Clients can provide their own request IDs for distributed tracing
- Auto-generates if not provided (no client changes required)
- Single request ID correlates all audit entries from bulk operations
- Easy debugging (request ID in response headers)

### UUID Version Recommendation

Use **PostgreSQL's gen_random_uuid()** (UUID v4):
- Available in PostgreSQL 13+ (you're using modern PostgreSQL)
- Random UUIDs prevent ID guessing (security)
- Good enough performance with proper indexing

**Alternative**: If you want time-ordered UUIDs for better index locality, consider UUID v7:
```sql
-- Requires pgcrypto extension or custom function
-- UUID v7 embeds timestamp for better B-tree index performance
```

For most cases, standard v4 (gen_random_uuid()) is recommended.

---

## Migration Checklist

When implementing these changes:

### Phase 1: Update Code
- [ ] Create AuditLog entity with new schema (use example-AuditLog.java)
- [ ] Create AuditOperation enum
- [ ] Create FieldChange class
- [ ] Create RequestIdInterceptor and register in WebConfig
- [ ] Update AuditLogService to capture requestId

### Phase 2: Generate Migration
- [ ] Run `make makemigration NAME=create_audit_log_table`
- [ ] Review generated migration
- [ ] Manually add partitioning SQL (not auto-detected by Liquibase)
- [ ] Manually add immutability trigger
- [ ] Manually add request_id index: `CREATE INDEX idx_audit_request ON audit_log (request_id) WHERE request_id IS NOT NULL;`

### Phase 3: Update API Layer
- [ ] Update AuditLogResponse DTO (createdBy, createdAt, requestId)
- [ ] Update AuditLogController query parameters (createdBy, startDate, endDate)
- [ ] Update repository query methods (findByCreatedBy, etc.)

### Phase 4: Update Documentation
- [x] data-model.md - DONE ✅
- [x] example-AuditLog.java - DONE ✅
- [x] audit-log-api.yaml - DONE ✅
- [ ] quickstart.md - Update field names in examples
- [ ] plan.md - Update references to changed_by/changed_at
- [ ] tasks.md - Update task descriptions with new field names

---

## Breaking Changes

**API Consumers Must Update**:
1. **Field renames**:
   - `changedBy` → `createdBy` (query param and response)
   - `changedAt` → `createdAt` (response)
2. **ID type change**:
   - Before: `"id": 1002` (integer)
   - After: `"id": "550e8400-e29b-41d4-a716-446655440002"` (UUID string)
3. **Removed field**:
   - `entityDescription` no longer exists in response
4. **New field** (non-breaking):
   - `requestId` (UUID, nullable) added to response

**Database Impact**:
- UUIDs are 16 bytes vs 8 bytes for BIGINT (2x storage for primary key)
- Overall impact: ~2% storage increase (only affects PK, not JSONB)

---

## Testing Plan

### 1. Request ID Correlation
```bash
# Test 1: Client provides request ID
curl -X POST http://localhost:8080/api/todos \
  -H "X-Request-ID: 650e8400-e29b-41d4-a716-446655440010" \
  -H "Content-Type: application/json" \
  -d '{"title": "Test Todo"}'

# Verify response includes same X-Request-ID header
# Query audit_log to confirm request_id matches

# Test 2: No request ID provided (auto-generated)
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title": "Test Todo 2"}'

# Verify response includes generated X-Request-ID header
# Query audit_log to confirm request_id is populated
```

### 2. Bulk Operations
```bash
# Create multiple todos in same request (if supported)
# OR perform multiple operations in sequence with same request ID
# Verify all audit entries share same request_id
SELECT * FROM audit_log
WHERE request_id = '650e8400-e29b-41d4-a716-446655440010';
```

### 3. UUID Performance
```bash
# Insert 10,000 audit entries
# Measure query performance on UUID primary key
EXPLAIN ANALYZE
SELECT * FROM audit_log
WHERE id = '550e8400-e29b-41d4-a716-446655440002';
```

---

## Rollout Strategy

**Recommended**: Blue-Green Deployment

1. **Blue** (Old Schema): Keep running with BIGSERIAL, changed_by, changed_at
2. **Green** (New Schema): Deploy with UUID, created_by, created_at, request_id
3. **Cutover**: Switch traffic from Blue to Green
4. **Backfill** (if needed): Migrate old audit logs to new schema (optional)

**Note**: Since audit_log is a new table (not yet in production), no migration needed!

---

## Questions Answered

> **Q1**: How do you want to populate `request_id`?

**A**: **Option C - Hybrid** (recommended above):
- Use `X-Request-ID` header if client provides it
- Auto-generate UUID if not provided
- Echo in response header for debugging

> **Q2**: Should we update API query parameter names?

**A**: **Yes**, updated:
- `changedBy` → `createdBy` (matches schema)
- Keep `startDate`/`endDate` (user-friendly, clear purpose)

> **Q3**: UUID v4 or v7?

**A**: **UUID v4** (gen_random_uuid()) recommended:
- Simpler (native PostgreSQL support)
- Good security (random, unpredictable)
- Acceptable performance with proper indexing
- Use v7 only if you need time-ordered UUIDs for index locality

---

## Next Steps

1. Review this summary
2. Implement RequestIdInterceptor (see code above)
3. Update remaining documentation files (quickstart.md, plan.md, tasks.md)
4. Create AuditLog JPA entity (use example-AuditLog.java as template)
5. Generate migration with `make makemigration`
6. Test request_id correlation in development

**Ready to proceed with implementation!** 🚀
