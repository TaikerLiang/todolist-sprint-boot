# Tasks: User Action Audit Log

**Input**: Design documents from `/specs/001-audit-log/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/audit-log-api.yaml

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

**Note**: User Story 4 (Automatic Background Logging) is P1 infrastructure and implemented in Foundational phase, as it's required for User Story 1 to function.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

All paths are relative to repository root:
- Java source: `src/main/java/com/example/todolist/`
- Resources: `src/main/resources/`
- Database migrations: `src/main/resources/db/changelog/changes/`
- Tests: `src/test/java/com/example/todolist/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add required dependencies and configuration for audit logging feature

- [X] T001 Add Hypersistence Utils dependency to pom.xml (version 3.7.3 for JSONB support)
- [X] T002 Verify PostgreSQL JSONB support is enabled in application.yml

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core audit logging infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete. This phase implements User Story 4 (Automatic Background Logging - P1) which is infrastructure required for all other stories.

### Data Model & Migration

- [X] T003 [P] Create AuditOperation enum in src/main/java/com/example/todolist/model/AuditOperation.java
- [X] T004 [P] Create FieldChange class in src/main/java/com/example/todolist/model/FieldChange.java
- [X] T005 Create AuditLog entity in src/main/java/com/example/todolist/model/AuditLog.java (depends on T003, T004)
- [X] T006 Create Liquibase migration in src/main/resources/db/changelog/changes/0009_create_audit_log_table.yaml (partitioned table with indexes and immutability trigger)
- [X] T007 Review generated migration for correctness (partitions, indexes, trigger)
- [X] T008 Apply migration using `make migrate` and verify audit_log table exists

### Repository Layer

- [X] T009 Create AuditLogRepository in src/main/java/com/example/todolist/repository/AuditLogRepository.java with custom query methods

### Service Layer & Audit Capture (User Story 4 Implementation)

- [X] T010 Create AuditContext helper class in src/main/java/com/example/todolist/audit/AuditContext.java (ThreadLocal for entity snapshots)
- [X] T011 Create FieldComparator utility in src/main/java/com/example/todolist/audit/FieldComparator.java (reflection-based diff computation)
- [X] T012 Create AuditEntityListener in src/main/java/com/example/todolist/audit/AuditEntityListener.java (@PostLoad, @PreUpdate, @PreRemove hooks)
- [X] T013 Create AuditLogService in src/main/java/com/example/todolist/service/AuditLogService.java (captures audit entries, extracts username from SecurityContext)
- [X] T014 Add @EntityListeners(AuditEntityListener.class) to Todo entity in src/main/java/com/example/todolist/model/Todo.java
- [X] T015 Add @EntityListeners(AuditEntityListener.class) to Invoice entity in src/main/java/com/example/todolist/model/Invoice.java

### Request ID Tracking (for correlation)

- [X] T015a [P] Create RequestIdInterceptor in src/main/java/com/example/todolist/interceptor/RequestIdInterceptor.java (captures X-Request-ID header or generates UUID, stores in ThreadLocal)
- [X] T015b Create WebConfig in src/main/java/com/example/todolist/config/WebConfig.java and register RequestIdInterceptor
- [X] T015c Update AuditLogService to capture requestId using RequestIdInterceptor.getCurrentRequestId()
- [ ] T015d Manual test: Send request with X-Request-ID header, verify it's captured in audit_log.request_id column

### Verification of Automatic Logging

- [ ] T016 Manual test: Create a Todo via existing API and verify audit_log entry is created
- [ ] T017 Manual test: Update a Todo via existing API and verify field-level changes are captured in JSONB
- [ ] T018 Manual test: Delete a Todo via existing API and verify DELETE audit entry is created
- [ ] T019 Manual test: Verify audit entries are transactional (force an error and ensure no audit entry created)

**Checkpoint**: Automatic audit logging is now working for all Todo/Invoice CRUD operations (User Story 4 complete). User story implementation can now begin.

---

## Phase 3: User Story 1 - View Complete Audit Trail for an Item (Priority: P1) 🎯 MVP

**Goal**: Enable administrators to view the complete history of changes for any todo or invoice item, including who made each change and when. This is the core MVP - administrators can answer "who changed what and when".

**Independent Test**: Create/update/delete a todo item, then query GET /api/audit-logs/entity/Todo/{id} and verify all actions are listed with correct timestamps, users, and operations.

**Acceptance Criteria**:
- GET /api/audit-logs/entity/{entityType}/{entityId} returns chronological audit history
- Response includes operation type, createdBy, createdAt, requestId for each entry
- Works for deleted entities (query by entity ID even after deletion)
- Admin-only access enforced (403 for non-admin users)

### DTOs for User Story 1

- [X] T020 [P] [US1] Create AuditLogResponse DTO in src/main/java/com/example/todolist/dto/AuditLogResponse.java
- [X] T021 [P] [US1] Add mapper method in AuditLogService to convert AuditLog entity to AuditLogResponse DTO

### Controller Endpoints for User Story 1

- [X] T022 [US1] Create AuditLogController in src/main/java/com/example/todolist/controller/AuditLogController.java with @RestController and @RequestMapping("/api/audit-logs")
- [X] T023 [US1] Implement GET /api/audit-logs/entity/{entityType}/{entityId} endpoint in AuditLogController (calls AuditLogService, returns paginated AuditLogResponse)
- [X] T024 [US1] Add @PreAuthorize("hasRole('ADMIN')") annotation to all audit log endpoints for admin-only access
- [X] T025 [US1] Implement GET /api/audit-logs/{id} endpoint in AuditLogController (get single audit entry by ID)

### Testing User Story 1

- [ ] T026 [US1] Manual test: Create a Todo, view its audit history via GET /api/audit-logs/entity/Todo/{id}, verify creation is logged
- [ ] T027 [US1] Manual test: Update the Todo, refresh audit history, verify update is logged with correct timestamp
- [ ] T028 [US1] Manual test: Delete the Todo, query audit history by ID, verify deletion is logged and history still accessible
- [ ] T029 [US1] Manual test: Access audit endpoint as non-admin user, verify 403 Forbidden response
- [ ] T030 [US1] Verify response format matches AuditLogResponse DTO structure from OpenAPI spec

**Checkpoint**: User Story 1 is complete. Administrators can now view complete audit trails for any item. This is a functional MVP - deploy and demo if ready.

---

## Phase 4: User Story 2 - Track Detailed Change Information (Priority: P2)

**Goal**: Enhance audit log display to show exactly what changed in each update operation with before/after values. This adds the "what changed" detail that makes auditing meaningful.

**Independent Test**: Update specific fields on a todo item (e.g., title from "Old" to "New", level from LOW to HIGH), then query audit history and verify the `changes` field contains before/after values for each changed field.

**Acceptance Criteria**:
- AuditLogResponse.changes field contains field-level diffs for UPDATE operations
- Each change shows {"old": value, "new": value} structure
- Multiple simultaneous field changes all captured in single audit entry
- Human-readable format (not internal data structures)

**Note**: The automatic capture of field-level changes is already implemented in Foundational phase (T011-T012). This phase focuses on proper display and formatting.

### Service Enhancements for User Story 2

- [ ] T031 [US2] Add method to AuditLogService to format changes into human-readable descriptions (e.g., "title changed from 'Old Value' to 'New Value'")
- [ ] T032 [US2] Update AuditLogResponse mapper to include formatted change descriptions in addition to raw JSONB data

### Controller Enhancements for User Story 2

- [ ] T033 [US2] Update GET /api/audit-logs/entity/{entityType}/{entityId} endpoint to include formatted change descriptions in response
- [ ] T034 [US2] Update GET /api/audit-logs/{id} endpoint to include formatted change descriptions

### Testing User Story 2

- [ ] T035 [US2] Manual test: Update multiple fields on an Invoice (amount, status), verify audit log shows all changes with before/after values
- [ ] T036 [US2] Manual test: Verify UPDATE operations show changes field populated, INSERT operations show changes as null
- [ ] T037 [US2] Manual test: Verify DELETE operations show final state in changes field (old values, new values all null)
- [ ] T038 [US2] Verify human-readable change descriptions are present and formatted correctly

**Checkpoint**: User Story 2 is complete. Audit logs now show detailed field-level changes. This significantly enhances investigative capability.

---

## Phase 5: User Story 3 - Filter and Search Audit Logs (Priority: P3)

**Goal**: Enable administrators to filter audit logs by date range, user, action type, and entity type for efficient investigation and compliance reporting.

**Independent Test**: Create audit entries from different users performing various operations, then apply filters (e.g., GET /api/audit-logs?operation=DELETE&createdBy=admin&startDate=2026-02-01) and verify results match filter criteria.

**Acceptance Criteria**:
- GET /api/audit-logs supports filtering by entityType, entityId, createdBy, operation, startDate, endDate, requestId
- Pagination works correctly (page, size parameters)
- Results sorted by createdAt descending (most recent first)
- Filter queries return within 5 seconds for 10,000+ entries

### DTOs for User Story 3

- [X] T039 [P] [US3] Create AuditLogFilter DTO in src/main/java/com/example/todolist/dto/AuditLogFilter.java (encapsulates filter parameters)

### Service Layer for User Story 3

- [X] T040 [US3] Add method to AuditLogService to build dynamic query based on AuditLogFilter parameters
- [X] T041 [US3] Implement filtering logic in AuditLogService using JPA Specifications or QueryDSL for complex filtering
- [X] T042 [US3] Add pagination support (Pageable parameter) to filtering methods

### Controller Endpoints for User Story 3

- [X] T043 [US3] Implement GET /api/audit-logs endpoint in AuditLogController with query parameters (entityType, entityId, createdBy, operation, startDate, endDate, requestId, page, size, sort)
- [X] T044 [US3] Add input validation for date formats and parameter combinations using @Valid annotation

### Testing User Story 3

- [ ] T045 [US3] Manual test: Filter by specific user (createdBy=user@example.com), verify only that user's actions returned
- [ ] T046 [US3] Manual test: Filter by operation type (operation=DELETE), verify only deletion entries returned
- [ ] T047 [US3] Manual test: Filter by date range (startDate/endDate), verify only entries within range returned
- [ ] T048 [US3] Manual test: Combine multiple filters (entityType=Invoice, operation=UPDATE, date range), verify results match all criteria
- [ ] T049 [US3] Manual test: Test pagination (page=0&size=20, page=1&size=20), verify correct results and no duplicates
- [ ] T050 [US3] Manual test: Filter by requestId to find all audit entries from a single API request, verify correlation works
- [ ] T051 [US3] Performance test: Query with 10,000+ audit entries, verify response time under 5 seconds

**Checkpoint**: User Story 3 is complete. Administrators can now efficiently search and filter large audit datasets.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final improvements, optimization, and documentation

### Performance Optimization

- [ ] T052 Verify database indexes are being used with EXPLAIN ANALYZE on common queries
- [ ] T053 Add additional GIN expression indexes if specific JSONB field queries are frequent (per data-model.md)
- [ ] T054 Test partition pruning with date range queries to ensure partitions are working correctly

### Security Hardening

- [ ] T055 Verify immutability trigger prevents UPDATE/DELETE on audit_log table (manual SQL test)
- [ ] T056 Review AuditEntityListener to ensure sensitive fields (e.g., passwords) are excluded from auditing
- [ ] T057 Verify admin-only access is enforced on all audit endpoints (security test with different user roles)

### Documentation

- [ ] T058 [P] Update README.md with audit logging feature overview and quickstart link
- [ ] T059 [P] Verify quickstart.md examples work correctly (follow guide step-by-step)
- [ ] T060 [P] Add JavaDoc comments to AuditLog entity, AuditLogService, and AuditLogController

### Integration Validation

- [ ] T061 End-to-end test: Perform full CRUD lifecycle on Todo (create → update → update → delete) and verify complete audit trail
- [ ] T062 End-to-end test: Perform full CRUD lifecycle on Invoice and verify complete audit trail
- [ ] T063 Verify SnapAdmin automatically shows audit_log table at /admin (read-only access expected)
- [ ] T064 Test concurrent modifications by multiple users, verify each gets its own audit entry with correct timestamps
- [ ] T065 Test request correlation: Perform operation with X-Request-ID header, verify all audit entries share same requestId

### Partition Management Setup

- [ ] T066 Document quarterly partition creation procedure in operational runbook
- [ ] T067 Create sample SQL script for creating new partitions (for future automation)
- [ ] T068 Document archival procedure for old partitions (detach, export, drop)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational completion - MVP
- **User Story 2 (Phase 4)**: Depends on Foundational completion - Can run in parallel with US1 if team capacity allows
- **User Story 3 (Phase 5)**: Depends on Foundational completion - Can run in parallel with US1/US2 if team capacity allows
- **Polish (Phase 6)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: No dependencies on other user stories - can start immediately after Foundational
- **User Story 2 (P2)**: No dependencies on US1 - independently testable (enhances US1 output but doesn't require it)
- **User Story 3 (P3)**: No dependencies on US1/US2 - independently testable (provides alternative access pattern)
- **User Story 4 (P1)**: Implemented in Foundational phase - required for all other stories

### Within Each User Story

**User Story 1**:
- DTOs (T020-T021) can run in parallel
- Controller (T022) depends on DTOs being complete
- Endpoints (T023-T025) depend on controller creation
- Testing (T026-T030) depends on endpoints being complete

**User Story 2**:
- Service enhancements (T031-T032) can run first
- Controller updates (T033-T034) depend on service changes
- Testing (T035-T038) depends on all enhancements

**User Story 3**:
- DTO (T039) can run first
- Service layer (T040-T042) depends on DTO
- Controller (T043-T044) depends on service layer
- Testing (T045-T050) depends on endpoints

### Parallel Opportunities

**Within Foundational Phase**:
- T003 (AuditOperation enum) and T004 (FieldChange class) can run in parallel
- T009 (Repository) can run in parallel with T010-T012 (audit utilities)
- T014 (Todo entity listener) and T015 (Invoice entity listener) can run in parallel

**Within User Story 1**:
- T020 (AuditLogResponse) and T021 (mapper) can run in parallel
- Testing tasks T026-T030 can run in sequence but don't block other phases

**Across User Stories** (if team has capacity):
- After Foundational completes, all three user stories (US1, US2, US3) can be worked on in parallel by different developers

---

## Parallel Example: Foundational Phase

```bash
# Launch these tasks together (different files, no dependencies):
Task T003: "Create AuditOperation enum"
Task T004: "Create FieldChange class"

# After T005 (AuditLog entity) is done, launch these in parallel:
Task T009: "Create AuditLogRepository"
Task T010: "Create AuditContext helper"
Task T011: "Create FieldComparator utility"

# After audit infrastructure is ready, add entity listeners in parallel:
Task T014: "Add @EntityListeners to Todo entity"
Task T015: "Add @EntityListeners to Invoice entity"
```

## Parallel Example: User Story 1

```bash
# Launch DTOs in parallel:
Task T020: "Create AuditLogResponse DTO"
Task T021: "Add mapper method in AuditLogService"

# After controller is created, add endpoints in sequence (same file):
Task T023: "Implement GET /api/audit-logs/entity/{entityType}/{entityId}"
Task T025: "Implement GET /api/audit-logs/{id}"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T002)
2. Complete Phase 2: Foundational (T003-T019) - CRITICAL, includes User Story 4
3. Complete Phase 3: User Story 1 (T020-T030)
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready - you now have a functional audit logging MVP

**MVP Value**: Administrators can view complete audit history for any item, with automatic background logging capturing all CRUD operations.

### Incremental Delivery

1. **Foundation** (Phases 1-2): Automatic audit logging working → Can verify in database
2. **+ User Story 1** (Phase 3): Admin API for viewing audit trails → Test independently → Deploy/Demo (MVP!)
3. **+ User Story 2** (Phase 4): Enhanced with field-level change details → Test independently → Deploy/Demo
4. **+ User Story 3** (Phase 5): Advanced filtering and search → Test independently → Deploy/Demo
5. **+ Polish** (Phase 6): Production-ready with optimizations and documentation

Each increment adds value without breaking previous functionality.

### Parallel Team Strategy

With multiple developers:

1. **Everyone**: Complete Setup + Foundational together (T001-T019)
2. **Once Foundational is done**:
   - Developer A: User Story 1 (T020-T030)
   - Developer B: User Story 2 (T031-T038) - works independently
   - Developer C: User Story 3 (T039-T050) - works independently
3. **Integration**: Minimal - each story operates on different endpoints/features
4. **Polish**: Team collaborates on final phase (T051-T066)

---

## Estimated Timeline

**Per-Phase Estimates** (1 developer, standard pace):

- **Phase 1 (Setup)**: 1-2 hours
- **Phase 2 (Foundational)**: 2-3 days
  - Data model & migration: 1 day
  - Audit capture infrastructure: 1-2 days
  - Verification: 0.5 day
- **Phase 3 (User Story 1)**: 1-2 days
  - DTOs & controller: 0.5 day
  - Endpoints: 0.5 day
  - Testing: 0.5-1 day
- **Phase 4 (User Story 2)**: 1 day
  - Service enhancements: 0.5 day
  - Testing: 0.5 day
- **Phase 5 (User Story 3)**: 2-3 days
  - Filtering logic: 1-1.5 days
  - Endpoints: 0.5 day
  - Testing: 0.5-1 day
- **Phase 6 (Polish)**: 1-2 days

**Total**: ~7-12 days (1 developer) or ~4-6 days (3 developers in parallel after Foundational)

**MVP Only** (Phases 1-3): ~4-6 days

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- User Story 4 (Automatic Background Logging) is implemented in Foundational phase as it's required infrastructure
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Test endpoints using curl, Postman, or automated tests
- Use `make migrate` for Liquibase migrations, `make rollback` if needed
- Monitor audit_log table size during testing (should grow with CRUD operations)
- Verify indexes with `EXPLAIN ANALYZE` on common queries
