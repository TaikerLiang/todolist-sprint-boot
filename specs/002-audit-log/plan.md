# Implementation Plan: Admin Audit Log for Todo and Invoice Changes

**Branch**: `002-audit-log` | **Date**: 2026-02-11 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-audit-log/spec.md`

## Summary

Implement a comprehensive audit logging system that captures all create, update, and delete operations on Todo and Invoice entities. The system will provide REST API endpoints for administrators to view complete change history, search and filter audit logs, and investigate data integrity issues. Audit logs will be retained indefinitely and will track who made each change, when it occurred, and what specifically changed (before/after values for each field).

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.1.5, Spring Data JPA, Hibernate 6, Spring Security
**Storage**: PostgreSQL with Liquibase migrations
**Testing**: JUnit 5, Spring Boot Test, MockMvc
**Target Platform**: Linux/macOS server (JVM-based)
**Project Type**: Web backend (REST API)
**Performance Goals**: Audit log writes <50ms latency; query operations <2s for 100k log entries
**Constraints**: Transactional integrity (audit writes must not be silently lost); 100% capture rate for all CUD operations
**Scale/Scope**: Indefinite retention (requires storage planning); expected 1000-10000 audit entries per day initially

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| **I. Layered Architecture** | ✅ PASS | Will implement standard layers: AuditLog entity (model), AuditLogRepository (repository), AuditLogService (service), AuditLogController (controller). Audit capture logic will be in service layer using Spring AOP or JPA entity listeners. |
| **II. UTC-First Timestamps** | ✅ PASS | AuditLog.createdAt field will use `Instant` type (UTC). Follows existing pattern from Todo and Invoice entities. |
| **III. Database Migration Discipline** | ✅ PASS | All schema changes (new audit_logs table, indexes) will be via Liquibase migrations. Generated via `make makemigration NAME=create_audit_logs_table`. |
| **IV. RESTful API Standards** | ✅ PASS | API endpoints will follow REST conventions: `GET /api/audit-logs?entityType=Todo&entityId=123`, `GET /api/audit-logs?userId=...&startDate=...`. Returns JSON with camelCase fields. |
| **V. Comprehensive Testing** | ⚠️ DEFERRED | Tests not explicitly requested in spec. If implemented, will follow Test-First approach with unit tests for service layer, integration tests for repository, and API tests with MockMvc. |
| **VI. Build & Dependency Management** | ✅ PASS | No new Maven dependencies required (uses existing Spring Boot stack). Java 21 source/target already configured. |

**Additional Technology Decisions**:
- **Custom UserActionLogs Model**: User-specified design with JSONB field changes via hypersistence-utils
- **Hypersistence Utils**: Version 3.7.3 for `@Type(JsonBinaryType.class)` JSONB support
- **Manual Audit Capture**: Service-layer audit logging or JPA Entity Listeners (to be determined during implementation)
- **Indexes**: entity_type and request_id columns for efficient querying (user-specified requirement)

## Project Structure

### Documentation (this feature)

```text
specs/002-audit-log/
├── plan.md              # This file (/speckit.plan command output)
├── spec.md              # Feature specification (completed)
├── checklists/
│   └── requirements.md  # Spec quality checklist (completed)
├── research.md          # Phase 0 output (TO BE CREATED)
├── data-model.md        # Phase 1 output (TO BE CREATED)
├── quickstart.md        # Phase 1 output (TO BE CREATED)
├── contracts/           # Phase 1 output (TO BE CREATED)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/main/java/com/example/todolist/
├── model/
│   ├── AuditLog.java              # NEW: Audit log entry entity
│   ├── AuditOperation.java        # NEW: Enum (CREATE, UPDATE, DELETE)
│   ├── FieldChange.java           # NEW: Embeddable for before/after values (or JSONB)
│   ├── Todo.java                  # EXISTING
│   ├── Invoice.java               # EXISTING
│   └── User.java                  # EXISTING
├── repository/
│   ├── AuditLogRepository.java    # NEW: Base JPA repository
│   ├── AuditLogRepositoryCustom.java   # NEW: Custom query interface
│   ├── AuditLogRepositoryImpl.java     # NEW: Custom query implementation
│   ├── TodoRepository.java        # EXISTING
│   └── InvoiceRepository.java     # EXISTING (if exists)
├── service/
│   ├── AuditLogService.java       # NEW: Audit log business logic
│   ├── AuditCaptureService.java   # NEW: Captures entity changes (AOP or listeners)
│   ├── TodoService.java           # MODIFIED: Integrate audit capture
│   └── InvoiceService.java        # MODIFIED: Integrate audit capture (if exists)
├── controller/
│   └── AuditLogController.java    # NEW: REST API endpoints
├── dto/
│   ├── AuditLogResponse.java      # NEW: API response DTO
│   ├── AuditLogSearchRequest.java # NEW: Search/filter request DTO
│   └── FieldChangeDto.java        # NEW: Field change representation
├── config/
│   └── AuditConfig.java           # NEW: Configuration for audit system (if needed)
└── exception/
    └── AuditException.java        # NEW: Audit-specific exceptions

src/main/resources/
└── db/changelog/changes/
    ├── 000X_create_audit_logs_table.yaml   # NEW: Migration for audit_logs table
    └── 000Y_add_audit_indexes.yaml         # NEW: Indexes on entity_type, request_id

src/test/java/com/example/todolist/
├── service/
│   ├── AuditLogServiceTest.java          # NEW: Unit tests
│   └── AuditCaptureServiceTest.java      # NEW: Unit tests
├── repository/
│   └── AuditLogRepositoryTest.java       # NEW: Integration tests
└── controller/
    └── AuditLogControllerTest.java       # NEW: API tests
```

**Structure Decision**: This is a single Spring Boot web application (backend only). The audit logging feature fits naturally into the existing layered architecture. We'll add new model entities, repositories following the existing custom repository pattern (RepoCustom/RepoImpl), services for business logic, and a REST controller for API endpoints. The structure aligns with the established codebase patterns.

## Complexity Tracking

No constitution violations requiring justification. The custom repository pattern (RepoCustom/RepoImpl) already exists in the codebase and is the established pattern for complex queries beyond JpaRepository's derived methods.

---

## Phase 0: Research & Decisions

*This section documents research tasks and architectural decisions made during planning.*

### Research Tasks

1. **Audit Capture Mechanism**: Investigate best approach for intercepting entity changes
   - Option A: JPA Entity Listeners (`@PreUpdate`, `@PreRemove`, `@PostPersist`)
   - Option B: Spring AOP with `@Transactional` aspect
   - Option C: Hibernate Envers (audit framework)
   - Decision: [TO BE RESEARCHED]

2. **JSONB Storage Strategy**: Determine how to store field changes in PostgreSQL JSONB
   - PostgreSQL JSONB column type support in Spring Data JPA
   - Hibernate `@Type` annotation for JSONB mapping
   - Serialization/deserialization strategy for field values
   - Decision: [TO BE RESEARCHED]

3. **Request ID Correlation**: How to capture and propagate request ID across transaction
   - Spring Web: Extract from HTTP headers or generate UUID per request
   - ThreadLocal or Spring's RequestContextHolder
   - Propagation to service layer without polluting business logic
   - Decision: [TO BE RESEARCHED]

4. **Transaction Boundary**: Ensure audit writes participate in same transaction as business operation
   - Service layer `@Transactional` propagation
   - Rollback behavior when audit write fails
   - Decision: [TO BE RESEARCHED - relates to FR-014]

5. **Security Context**: Extract current user information for audit log
   - Spring Security: `SecurityContextHolder.getContext().getAuthentication()`
   - JWT token claims (username/user ID)
   - Decision: [TO BE RESEARCHED]

### Architectural Decisions

*To be filled after research tasks complete*

---

## Phase 1: Design Artifacts

*Artifacts to be generated: data-model.md, contracts/ (OpenAPI spec), quickstart.md*

### Data Model Preview

Key entities (detailed in data-model.md):
- **AuditLog**: id (UUID), entityType, entityId, operation, createdBy, createdAt, changes (JSONB), requestId
- **FieldChange**: fieldName, previousValue, newValue (stored as JSONB map)

### API Contract Preview

Key endpoints (detailed in contracts/audit-api.yaml):
- `GET /api/audit-logs?entityType={type}&entityId={id}` - Get history for specific item
- `GET /api/audit-logs?userId={user}&startDate={date}&endDate={date}` - Search by user/date
- `GET /api/audit-logs?requestId={uuid}` - Correlate changes by request

### Implementation Sequence

1. **Phase 1a: Core Data Model**
   - Create AuditLog entity with JSONB field
   - Create AuditOperation enum
   - Generate Liquibase migration

2. **Phase 1b: Audit Capture**
   - Implement audit capture mechanism (based on research decision)
   - Integrate with Todo and Invoice services
   - Ensure transactional behavior

3. **Phase 1c: Query Layer**
   - Create AuditLogRepository with custom queries
   - Implement filtering by entity, user, date, request ID
   - Add pagination support

4. **Phase 1d: REST API**
   - Create AuditLogController with search endpoints
   - Implement DTOs for requests/responses
   - Add security (admin-only access)

---

## Implementation Status

- ✅ **Phase 0 Complete**: All research tasks completed, decisions documented in [research.md](research.md)
- ✅ **Phase 1 Complete**: Design artifacts generated:
  - [data-model.md](data-model.md) - Entity schemas and relationships
  - [contracts/audit-api.yaml](contracts/audit-api.yaml) - REST API specification
  - [quickstart.md](quickstart.md) - Developer guide
- ⏭️ **Next**: Run `/speckit.tasks` to generate implementation tasks (Phase 2)
