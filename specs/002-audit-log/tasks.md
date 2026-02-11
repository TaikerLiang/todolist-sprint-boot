# Implementation Tasks: Admin Audit Log for Todo and Invoice Changes

**Feature**: 002-audit-log
**Branch**: `002-audit-log`
**Created**: 2026-02-11
**Related**: [spec.md](spec.md) | [plan.md](plan.md) | [data-model.md](data-model.md) | [contracts/audit-api.yaml](contracts/audit-api.yaml)

---

## Overview

This document provides an actionable, dependency-ordered task list for implementing the admin audit logging system. Tasks are organized by user story to enable independent implementation and testing of each feature increment.

**Implementation Strategy**: MVP-first, incremental delivery
- **MVP Scope**: User Story 1 (P1) - Complete change history for specific items
- **Incremental Additions**: User Stories 2-4 (P2-P3) build on MVP foundation

**Total Tasks**: 47
**Parallel Opportunities**: 23 tasks marked [P] can run in parallel within their phase

---

## Task Organization

### Legend

- `[T###]` - Sequential task ID
- `[P]` - Parallelizable (can run concurrently with other [P] tasks in same phase)
- `[US#]` - User Story association (US1, US2, US3, US4)
- File paths shown for each implementation task

### User Story Mapping

| User Story | Priority | Phase | Task Count | Independent Test Criteria |
|------------|----------|-------|------------|---------------------------|
| **US1**: View Complete Change History | P1 | 3 | 18 | Given a Todo/Invoice ID, retrieve complete audit history with all operations (CREATE/UPDATE/DELETE), user info, timestamps, and field changes |
| **US2**: Search and Filter Audit Logs | P2 | 4 | 5 | Given filter criteria (user/date/entity/operation), retrieve matching audit logs with pagination |
| **US3**: Track Deletion Events | P3 | 5 | 3 | Given a deleted Todo/Invoice, verify audit logs are preserved and queryable |
| **US4**: Correlate Related Changes | P3 | 6 | 3 | Given a request ID, retrieve all related audit entries from the same transaction |

---

## Phase 1: Setup (Project Initialization)

**Goal**: Verify project structure and dependencies for audit logging implementation.

### Tasks

- [x] T001 Verify hypersistence-utils dependency exists in pom.xml (line 114-118)
- [x] T002 Verify project follows layered architecture (model, repository, service, controller packages exist)
- [x] T003 Verify PostgreSQL JSONB support is available (PostgreSQL 9.4+ required)

**Completion Criteria**: All dependencies and project structure verified; ready to proceed with foundational tasks.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Goal**: Establish request context infrastructure required for all user stories (request ID tracking, user identification).

**Dependencies**: Phase 1 must complete first.

### Tasks

- [x] T004 [P] Create RequestContext utility class with ThreadLocal storage in src/main/java/com/example/todolist/util/RequestContext.java
- [x] T005 [P] Create RequestCorrelationFilter for request ID generation in src/main/java/com/example/todolist/filter/RequestCorrelationFilter.java
- [x] T006 Modify JwtAuthenticationFilter to populate RequestContext.setUserId() in src/main/java/com/example/todolist/security/JwtAuthenticationFilter.java
- [x] T007 [P] Update logback-spring.xml to include MDC requestId in log pattern in src/main/resources/logback-spring.xml

**Completion Criteria**: RequestContext accessible throughout application; request IDs generated and logged; user IDs captured from JWT tokens.

**Test Verification**:
- Start application and make authenticated request
- Verify logs contain `[requestId]` in output
- Verify RequestContext.getRequestId() and RequestContext.getUserId() return non-null values

---

## Phase 3: User Story 1 (P1) - View Complete Change History

**Goal**: Implement complete audit capture pipeline and REST API endpoints to retrieve change history for specific Todo/Invoice items.

**Dependencies**: Phase 2 must complete first.

**Test Criteria**: Given a Todo ID or Invoice UUID, API returns complete audit history showing all CREATE/UPDATE/DELETE operations with user info, timestamps, and field-level changes (before/after values).

### Model Layer

- [x] T008 [P] [US1] Create AuditOperation enum (CREATE, UPDATE, DELETE) in src/main/java/com/example/todolist/model/AuditOperation.java
- [x] T009 [P] [US1] Create FieldChange class with fieldName, oldValue, newValue, fieldType in src/main/java/com/example/todolist/model/FieldChange.java
- [x] T010 [US1] Create UserActionLogs entity with JSONB changes field in src/main/java/com/example/todolist/model/UserActionLogs.java

### Database Migration

- [x] T011 [US1] Generate Liquibase migration for admin_portal_user_action_logs table with 4 indexes using `make makemigration NAME=create_admin_portal_user_action_logs`
- [x] T012 [US1] Review generated migration file in src/main/resources/db/changelog/changes/ and verify schema matches data-model.md
- [x] T013 [US1] Apply migration using `make migrate` and verify table creation

### Repository Layer

- [x] T014 [P] [US1] Create UserActionLogsRepository interface extending JpaRepository in src/main/java/com/example/todolist/repository/UserActionLogsRepository.java
- [x] T015 [P] [US1] Create UserActionLogsRepositoryCustom interface with findByEntityTypeAndEntityId signature in src/main/java/com/example/todolist/repository/UserActionLogsRepositoryCustom.java
- [x] T016 [US1] Create UserActionLogsRepositoryImpl with EntityManager and implement findByEntityTypeAndEntityId using JPQL in src/main/java/com/example/todolist/repository/UserActionLogsRepositoryImpl.java

### Service Layer - Audit Capture

- [x] T017 [P] [US1] Create EntityComparator utility with compareEntities method for field change detection in src/main/java/com/example/todolist/util/EntityComparator.java
- [x] T018 [US1] Create AuditCaptureService with captureCreate, captureUpdate, captureDelete methods in src/main/java/com/example/todolist/service/AuditCaptureService.java
- [x] T019 [US1] Integrate audit capture into TodoService.createTodo() - call AuditCaptureService.captureCreate() in src/main/java/com/example/todolist/service/TodoService.java
- [x] T020 [US1] Integrate audit capture into TodoService.updateTodo() - call AuditCaptureService.captureUpdate() with before/after comparison in src/main/java/com/example/todolist/service/TodoService.java
- [x] T021 [US1] Integrate audit capture into TodoService.deleteTodo() - call AuditCaptureService.captureDelete() in src/main/java/com/example/todolist/service/TodoService.java
- [x] T022 [US1] Integrate audit capture into InvoiceService.createInvoice() in src/main/java/com/example/todolist/service/InvoiceService.java
- [x] T023 [US1] Integrate audit capture into InvoiceService.updateInvoice() in src/main/java/com/example/todolist/service/InvoiceService.java
- [x] T024 [US1] Integrate audit capture into InvoiceService.deleteInvoice() in src/main/java/com/example/todolist/service/InvoiceService.java

### Controller Layer - REST API

- [x] T025 [P] [US1] Create AuditLogController with @RestController and base mapping /api/audit in src/main/java/com/example/todolist/controller/AuditLogController.java
- [x] T026 [P] [US1] Create AuditLogResponse DTO in src/main/java/com/example/todolist/dto/AuditLogResponse.java
- [x] T027 [P] [US1] Create AuditEntryDto DTO in src/main/java/com/example/todolist/dto/AuditEntryDto.java
- [x] T028 [P] [US1] Create FieldChangeDto DTO in src/main/java/com/example/todolist/dto/FieldChangeDto.java
- [x] T029 [US1] Implement GET /audit/todos/{id}/history endpoint in AuditLogController with pagination support
- [x] T030 [US1] Implement GET /audit/invoices/{id}/history endpoint in AuditLogController with pagination support
- [x] T031 [US1] Add @PreAuthorize("hasRole('ADMIN')") to audit endpoints for admin-only access

**Phase 3 Completion Criteria**:
- Create a Todo, update its title, delete it → verify 3 audit log entries exist
- Call GET /api/audit/todos/{id}/history → receive JSON with CREATE, UPDATE, DELETE operations
- Verify each audit entry contains: operation, timestamp, username, field changes (for UPDATE)
- Verify audit logs preserved even after Todo deletion

**Parallel Execution Example for Phase 3**:
```bash
# Can run in parallel (independent files):
T008 (AuditOperation.java) || T009 (FieldChange.java) || T014 (UserActionLogsRepository.java) || T015 (UserActionLogsRepositoryCustom.java) || T017 (EntityComparator.java) || T025-T028 (DTOs and Controller skeleton)

# Must run sequentially (dependencies):
T010 (UserActionLogs entity) → T011 (generate migration) → T012 (review) → T013 (apply migration)
T016 (RepositoryImpl) depends on T014 + T015
T018 (AuditCaptureService) depends on T017 (EntityComparator)
T019-T024 (service integration) depend on T018 (AuditCaptureService)
T029-T031 (controller endpoints) depend on T025-T028 (DTOs)
```

---

## Phase 4: User Story 2 (P2) - Search and Filter Audit Logs

**Goal**: Enable administrators to search audit logs across all entities with flexible filtering.

**Dependencies**: Phase 3 must complete first (needs audit data to exist and query).

**Test Criteria**: Given filter criteria (username, date range, entity type, operation type), API returns matching audit logs with pagination.

### Tasks

- [x] T032 [P] [US2] Create AuditLogSearchRequest DTO with filter fields in src/main/java/com/example/todolist/dto/AuditLogSearchRequest.java
- [x] T033 [US2] Add searchAuditLogs method to UserActionLogsRepositoryCustom interface in src/main/java/com/example/todolist/repository/UserActionLogsRepositoryCustom.java
- [x] T034 [US2] Implement searchAuditLogs in UserActionLogsRepositoryImpl with dynamic JPQL query building in src/main/java/com/example/todolist/repository/UserActionLogsRepositoryImpl.java
- [x] T035 [US2] Implement countAuditLogs helper method for pagination in UserActionLogsRepositoryImpl in src/main/java/com/example/todolist/repository/UserActionLogsRepositoryImpl.java
- [x] T036 [US2] Implement GET /audit/search endpoint in AuditLogController with query parameters (entityType, username, operation, startDate, endDate, page, size)

**Phase 4 Completion Criteria**:
- Create audit logs for multiple users and dates
- Call GET /api/audit/search?username=john@example.com → receive only John's changes
- Call GET /api/audit/search?startDate=2026-02-10&endDate=2026-02-11 → receive changes in date range
- Call GET /api/audit/search?entityType=Todo&operation=DELETE → receive only Todo deletions
- Verify pagination works (page, size, totalElements, totalPages in response)

**Parallel Execution Example for Phase 4**:
```bash
# Can run in parallel:
T032 (AuditLogSearchRequest DTO) || T033 (interface method signature)

# Must run sequentially:
T034 + T035 (repository implementation) → T036 (controller endpoint)
```

---

## Phase 5: User Story 3 (P3) - Track Deletion Events

**Goal**: Ensure audit logs are preserved when entities are deleted.

**Dependencies**: Phase 3 must complete first (needs audit capture in delete operations).

**Test Criteria**: Given a deleted Todo/Invoice, verify audit logs are still queryable and include the final DELETE operation.

### Tasks

- [x] T037 [P] [US3] Verify TodoService.deleteTodo() captures audit BEFORE calling repository.delete() in src/main/java/com/example/todolist/service/TodoService.java
- [x] T038 [P] [US3] Verify InvoiceService.deleteInvoice() captures audit BEFORE calling repository.delete() in src/main/java/com/example/todolist/service/InvoiceService.java
- [ ] T039 [US3] Add integration test verifying audit logs persist after entity deletion (SKIPPED - tests optional)

**Phase 5 Completion Criteria**:
- Create a Todo with ID 999, delete it
- Call GET /api/audit/todos/999/history → receive audit history including DELETE operation
- Verify entityId=999 is queryable even though Todo no longer exists in todos table

**Parallel Execution Example for Phase 5**:
```bash
# Can run in parallel:
T037 (TodoService verification) || T038 (InvoiceService verification)

# Must run after parallel tasks:
T039 (integration test)
```

---

## Phase 6: User Story 4 (P3) - Correlate Related Changes

**Goal**: Enable administrators to see all changes that occurred in a single transaction using request ID.

**Dependencies**: Phase 3 must complete first (needs request ID infrastructure from Phase 2).

**Test Criteria**: Given a request ID, API returns all audit entries from the same transaction grouped together.

### Tasks

- [x] T040 [P] [US4] Add findByRequestId method to UserActionLogsRepositoryCustom interface in src/main/java/com/example/todolist/repository/UserActionLogsRepositoryCustom.java
- [x] T041 [US4] Implement findByRequestId in UserActionLogsRepositoryImpl using JPQL in src/main/java/com/example/todolist/repository/UserActionLogsRepositoryImpl.java
- [x] T042 [US4] Implement GET /audit/revisions/{requestId} endpoint in AuditLogController to retrieve correlated changes

**Phase 6 Completion Criteria**:
- Create multiple Todos in a single request (bulk operation or multiple saves with same requestId)
- Extract requestId from one audit log entry
- Call GET /api/audit/revisions/{requestId} → receive all audit entries with matching requestId
- Verify response includes changes across multiple entities if applicable

**Parallel Execution Example for Phase 6**:
```bash
# Can run in parallel:
T040 (interface method signature)

# Must run sequentially:
T041 (repository implementation) → T042 (controller endpoint)
```

---

## Final Phase: Polish & Cross-Cutting Concerns

**Goal**: Production readiness - logging, documentation, security hardening, performance optimization.

**Dependencies**: All user story phases (3-6) should complete first.

### Tasks

- [x] T043 [P] Add comprehensive @Slf4j logging to AuditCaptureService (log each capture operation)
- [x] T044 [P] Add error handling and validation to AuditLogController (validate query parameters, handle not found cases)
- [ ] T045 [P] Add composite index for entity_type + entity_id + created_at in Liquibase migration for optimized queries (SKIPPED - individual indexes sufficient)
- [ ] T046 [P] Add GIN index for JSONB changes column if JSONB querying is needed (SKIPPED - not needed for current queries)
- [x] T047 Update README.md or CLAUDE.md with audit logging usage examples and API documentation links

**Completion Criteria**:
- All services log important operations (audit captures, queries)
- API returns proper error responses (400 for invalid params, 404 for not found, 403 for non-admins)
- Query performance verified for 10,000+ audit log entries (<2 seconds per query)
- Documentation updated with audit log examples

**Parallel Execution Example for Final Phase**:
```bash
# All tasks can run in parallel (independent concerns):
T043 (logging) || T044 (error handling) || T045 (composite index) || T046 (GIN index) || T047 (documentation)
```

---

## Dependency Graph

### User Story Completion Order

```
Phase 1 (Setup)
    ↓
Phase 2 (Foundational - Request Context)
    ↓
Phase 3 (US1 - View Complete Change History) ← MVP DELIVERY
    ↓
    ├─→ Phase 4 (US2 - Search and Filter)
    ├─→ Phase 5 (US3 - Track Deletion Events)
    └─→ Phase 6 (US4 - Correlate Related Changes)
    ↓
Final Phase (Polish & Cross-Cutting)
```

**Critical Path**: Phase 1 → Phase 2 → Phase 3 (US1 is blocking for all other user stories)

**Independent Paths**: After Phase 3 completes, Phases 4, 5, 6 can run in parallel or any order.

---

## Implementation Strategy

### MVP First (Minimum Viable Product)

**Recommended MVP Scope**: User Story 1 (P1) - Phases 1-3

**Why**: US1 provides complete end-to-end audit capture and query capability. Administrators can immediately view change history for specific items. This validates the entire technical approach (custom model, JSONB storage, request context, manual capture).

**MVP Deliverables**:
- Complete audit capture for CREATE/UPDATE/DELETE on Todo and Invoice
- REST API endpoints to retrieve audit history by entity ID
- Field-level change tracking with before/after values
- Request ID correlation infrastructure
- Admin-only security

**MVP Validation**:
```bash
# Test scenario:
1. Create Todo: POST /api/todos {"title": "Test Todo"}
2. Update Todo: PUT /api/todos/1 {"title": "Updated Todo"}
3. Delete Todo: DELETE /api/todos/1
4. Query audit: GET /api/audit/todos/1/history

# Expected result:
[
  {"operation": "DELETE", "timestamp": "2026-02-11T15:30:00Z", "username": "admin@example.com"},
  {"operation": "UPDATE", "changedFields": [{"fieldName": "title", "oldValue": "Test Todo", "newValue": "Updated Todo"}]},
  {"operation": "CREATE", "timestamp": "2026-02-11T15:00:00Z"}
]
```

### Incremental Delivery

After MVP (US1), deliver additional user stories incrementally:

**Increment 2**: User Story 2 (Phase 4) - Search and Filter
- Adds powerful admin search capabilities
- Enables compliance investigations (e.g., "show me all changes by user X")

**Increment 3**: User Story 3 (Phase 5) - Track Deletion Events
- Validates deletion preservation (should already work from US1, but adds explicit test)

**Increment 4**: User Story 4 (Phase 6) - Correlate Related Changes
- Adds transaction-level correlation for bulk operations

---

## Testing Strategy

### Unit Tests (Optional - not in spec requirements)

If implementing tests:
- EntityComparator field change detection
- AuditCaptureService create/update/delete methods
- UserActionLogsRepositoryImpl query methods

### Integration Tests (Recommended)

- TodoService + AuditCaptureService integration (verify audit logs created)
- InvoiceService + AuditCaptureService integration
- Repository query methods with actual database
- End-to-end API tests with MockMvc

### API Tests (Critical for Validation)

- GET /audit/todos/{id}/history with pagination
- GET /audit/invoices/{id}/history
- GET /audit/search with various filter combinations
- GET /audit/revisions/{requestId}
- Security tests (verify 403 for non-admin users)

---

## Task Summary by Phase

| Phase | Task Range | Count | Parallelizable | User Story | Blocking? |
|-------|------------|-------|----------------|------------|-----------|
| 1: Setup | T001-T003 | 3 | 0 | - | Yes (all phases) |
| 2: Foundational | T004-T007 | 4 | 3 | - | Yes (all user stories) |
| 3: US1 (P1) | T008-T031 | 24 | 12 | US1 | Yes (US2, US3, US4) |
| 4: US2 (P2) | T032-T036 | 5 | 2 | US2 | No |
| 5: US3 (P3) | T037-T039 | 3 | 2 | US3 | No |
| 6: US4 (P3) | T040-T042 | 3 | 1 | US4 | No |
| Final: Polish | T043-T047 | 5 | 5 | - | No |
| **TOTAL** | T001-T047 | **47** | **25** | 4 stories | - |

---

## Parallel Execution Opportunities

### Maximum Parallelism Points

**Phase 2 (Foundational)**: 3 tasks can run in parallel
```
T004 (RequestContext) || T005 (RequestCorrelationFilter) || T007 (logback-spring.xml)
→ T006 (JwtAuthenticationFilter) depends on T004
```

**Phase 3 (US1 - Model Layer)**: 2 tasks can run in parallel
```
T008 (AuditOperation enum) || T009 (FieldChange class)
→ T010 (UserActionLogs entity) depends on both
```

**Phase 3 (US1 - Repository Layer)**: 2 tasks can run in parallel
```
T014 (UserActionLogsRepository) || T015 (UserActionLogsRepositoryCustom)
→ T016 (UserActionLogsRepositoryImpl) depends on both
```

**Phase 3 (US1 - DTOs and Controller)**: 4 tasks can run in parallel
```
T025 (Controller skeleton) || T026 (AuditLogResponse) || T027 (AuditEntryDto) || T028 (FieldChangeDto)
```

**Final Phase (Polish)**: 5 tasks can run in parallel
```
T043 (logging) || T044 (error handling) || T045 (composite index) || T046 (GIN index) || T047 (docs)
```

---

## Progress Tracking

**Status Legend**: ❌ Not Started | 🔄 In Progress | ✅ Complete

| Phase | Status | Tasks Complete | Notes |
|-------|--------|----------------|-------|
| Phase 1: Setup | ✅ | 3/3 | Complete |
| Phase 2: Foundational | ✅ | 4/4 | Complete |
| Phase 3: US1 (P1) | ✅ | 24/24 | MVP DELIVERED |
| Phase 4: US2 (P2) | ✅ | 5/5 | Complete |
| Phase 5: US3 (P3) | ✅ | 2/3 | Complete (1 test skipped) |
| Phase 6: US4 (P3) | ✅ | 3/3 | Complete |
| Final: Polish | ✅ | 3/5 | Complete (2 optional indexes skipped) |
| **OVERALL** | **✅** | **44/47** | **COMPLETE** (3 optional tasks skipped) |

---

**Last Updated**: 2026-02-11
**Status**: Ready for implementation
**Next Command**: `/speckit.implement` to execute tasks or manually process tasks in dependency order
