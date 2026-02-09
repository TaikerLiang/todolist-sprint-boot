# Implementation Plan: User Action Audit Log

**Branch**: `001-audit-log` | **Date**: 2026-02-09 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-audit-log/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Implement an audit logging system that automatically captures all create, update, and delete operations on Todo and Invoice entities. The system will record who performed each action, when it occurred, and what changed (field-level before/after values). Administrators can view complete audit trails for any item, filter logs by various criteria, and access historical data even for deleted items. The audit log must be immutable, transactional, and performant at scale (100,000+ entries).

**Technical Approach**: Leverage Spring Data JPA entity listeners (`@EntityListeners`) with AOP (Aspect-Oriented Programming) to automatically intercept CRUD operations. Use Hibernate Envers for built-in audit trail functionality, or implement custom `@PrePersist`, `@PreUpdate`, `@PreRemove` lifecycle hooks to capture changes. Store audit logs in a dedicated `audit_log` table with JSON fields for change tracking. Provide REST API endpoints for administrators to query audit logs with filtering capabilities.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.1.5, Spring Data JPA, Hibernate 6, Lombok 1.18.36, Hypersistence Utils 3.7.3 (for JSONB support)
**Storage**: PostgreSQL with Liquibase migrations (partitioned table for audit_log)
**Testing**: JUnit 5, Spring Boot Test, Mockito, H2 in-memory database for testing
**Target Platform**: Linux server (existing deployment)
**Project Type**: Single web application (RESTful API backend)
**Performance Goals**: Audit log queries return results within 3-5 seconds for datasets of 100,000+ entries; audit logging adds <100ms overhead to CRUD operations
**Constraints**: Audit logs must be immutable (no updates/deletes allowed); transactional integrity with main operations; support 2+ years data retention
**Scale/Scope**: Support ~100,000 audit entries per year; handle filtering/querying on large datasets; admin-only access

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Layered Architecture ✅
- **Compliance**:
  - AuditLog entity in model layer (data representation only)
  - AuditLogRepository in repository layer (extends JpaRepository)
  - AuditLogService in service layer (business logic, audit capture orchestration)
  - AuditLogController in controller layer (REST endpoints for admin queries)
  - Audit capture logic isolated in service/aspect layer
- **Status**: COMPLIANT - follows standard 4-layer architecture

### II. UTC-First Timestamps ✅
- **Compliance**:
  - AuditLog entity will use `Instant` (UTC) for timestamp field
  - Consistent with existing Todo/Invoice timestamp patterns
- **Status**: COMPLIANT - all timestamps use `java.time.Instant`

### III. Database Migration Discipline ✅
- **Compliance**:
  - New AuditLog entity requires Liquibase migration
  - Process: Create entity → `./mvnw liquibase:diff` → review → `make migrate`
  - Hibernate ddl-auto already set to `none`
- **Status**: COMPLIANT - standard migration workflow applies

### IV. RESTful API Standards ✅
- **Compliance**:
  - Audit log endpoints follow resource-based URLs: `/api/audit-logs`
  - Query parameters for filtering: `?userId=X&entityType=Todo&startDate=...`
  - GET for retrieval, proper HTTP status codes
  - JSON response with camelCase (API) mapped from snake_case (DB)
- **Status**: COMPLIANT - RESTful conventions for read-only audit API

### V. Comprehensive Testing ⚠️
- **Compliance**:
  - Unit tests: AuditLogService with mocked repository (verify audit capture logic)
  - Integration tests: AuditLogRepository with H2 (verify queries, filtering)
  - API tests: AuditLogController with MockMvc (verify endpoints, permissions)
  - Tests for audit capture: verify CREATE/UPDATE/DELETE operations generate logs
  - Tests for edge cases: concurrent modifications, deleted entities, bulk operations
- **Status**: COMPLIANT - comprehensive testing plan required for audit integrity
- **Note**: Test-first approach RECOMMENDED but not mandatory per constitution

### VI. Build & Dependency Management ✅
- **Compliance**:
  - Java 21 source/target maintained
  - Explicit versions for new dependencies (Hibernate Envers if used)
  - Lombok already configured
  - No new SNAPSHOT dependencies
- **Status**: COMPLIANT - no build changes needed beyond potential Envers dependency

### Technology Standards ✅
- **Compliance**: All existing standards apply (Spring Boot, PostgreSQL, JPA, Liquibase, Lombok, JSON logging)
- **Status**: COMPLIANT

### Development Workflow ✅
- **Compliance**:
  - Package structure: `com.example.todolist.model.AuditLog`, etc.
  - Transaction boundaries at service layer
  - Environment variables for any new config
  - DTOs for audit log responses
  - Input validation where applicable
- **Status**: COMPLIANT

**Gate Result**: ✅ PASSED - No violations of NON-NEGOTIABLE principles

## Project Structure

### Documentation (this feature)

```text
specs/001-audit-log/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output - audit logging patterns research
├── data-model.md        # Phase 1 output - AuditLog entity design
├── quickstart.md        # Phase 1 output - how to use audit logs
├── contracts/           # Phase 1 output - API contracts
│   └── audit-log-api.yaml  # OpenAPI spec for audit endpoints
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/main/java/com/example/todolist/
├── model/
│   └── AuditLog.java           # JPA entity for audit log records
├── repository/
│   └── AuditLogRepository.java # Spring Data repository with custom queries
├── service/
│   └── AuditLogService.java    # Audit capture and retrieval logic
├── controller/
│   └── AuditLogController.java # Admin REST endpoints for querying logs
├── dto/
│   ├── AuditLogResponse.java   # Response DTO for audit log entries
│   └── AuditLogFilter.java     # Request DTO for filtering criteria
└── aspect/ (if using AOP)
    └── AuditingAspect.java     # Intercepts CRUD operations

src/main/resources/db/changelog/changes/
└── 000X_create_audit_log_table.yaml  # Liquibase migration

tests/
├── unit/
│   └── AuditLogServiceTest.java
├── integration/
│   └── AuditLogRepositoryTest.java
└── contract/
    └── AuditLogControllerTest.java
```

**Structure Decision**: Single project structure maintained. Audit logging is a cross-cutting concern that integrates into existing layered architecture. New packages added under `com.example.todolist` following established conventions. Aspect package optional depending on implementation approach (Envers vs custom listeners vs AOP).

## Complexity Tracking

No violations requiring justification. All implementation approaches align with constitution principles.

## Post-Design Constitution Re-Check

*Re-evaluated after Phase 1 design completion*

### I. Layered Architecture ✅
**Re-evaluation**: Design artifacts (data-model.md, contracts/) confirm proper layering:
- AuditLog entity (model layer) - pure JPA entity, no business logic
- AuditLogRepository (repository layer) - extends JpaRepository with custom query methods
- AuditLogService (service layer) - handles audit capture logic and user extraction from SecurityContext
- AuditLogController (controller layer) - REST endpoints with @PreAuthorize for admin access
- AuditEntityListener - entity lifecycle hooks isolated in dedicated component

**Status**: ✅ COMPLIANT - design maintains clean separation of concerns

### II. UTC-First Timestamps ✅
**Re-evaluation**: data-model.md confirms:
- `createdAt` field uses `Instant` type
- Database column: `TIMESTAMP WITH TIME ZONE`
- All timestamps in UTC per existing standards

**Status**: ✅ COMPLIANT

### III. Database Migration Discipline ✅
**Re-evaluation**: Liquibase migration defined in data-model.md:
- Migration file: `db/changelog/changes/000X_create_audit_log_table.yaml`
- Includes partitioned table creation, indexes, and triggers
- No ddl-auto changes
- Standard workflow: create entity → liquibase:diff → review → migrate

**Status**: ✅ COMPLIANT

### IV. RESTful API Standards ✅
**Re-evaluation**: OpenAPI contract (contracts/audit-log-api.yaml) confirms:
- Resource-based URLs: `/api/audit-logs`, `/api/audit-logs/{id}`, `/api/audit-logs/entity/{type}/{id}`
- Proper HTTP methods: GET only (read-only API)
- Standard status codes: 200, 400, 401, 403, 404, 500
- Query parameters for filtering (entityType, entityId, createdBy, operation, startDate, endDate, requestId)
- JSON responses with camelCase field names
- UUID-based primary keys

**Status**: ✅ COMPLIANT

### V. Comprehensive Testing ✅
**Re-evaluation**: quickstart.md includes test examples:
- Unit tests: AuditLogService with mocked repository
- Integration tests: AuditLogRepository with verify audit capture on CRUD operations
- API tests: AuditLogController with MockMvc (verify endpoints and admin permissions)
- Edge cases: concurrent modifications, deleted entities, field-level diff accuracy

**Status**: ✅ COMPLIANT - comprehensive test strategy documented

### VI. Build & Dependency Management ✅
**Re-evaluation**: New dependency identified in research.md:
```xml
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-63</artifactId>
    <version>3.7.3</version>
</dependency>
```
- Explicit version (not SNAPSHOT)
- Required for PostgreSQL JSONB support in Hibernate 6
- Java 21 maintained

**Status**: ✅ COMPLIANT

### Technology Standards ✅
**Re-evaluation**: All existing standards maintained:
- Spring Boot 3.1.5, PostgreSQL, JPA, Liquibase, Lombok
- New: JSONB for change storage (native PostgreSQL feature)
- New: Table partitioning (native PostgreSQL feature)

**Status**: ✅ COMPLIANT

### Development Workflow ✅
**Re-evaluation**: Design follows all workflow standards:
- Package structure: `com.example.todolist.{model,repository,service,controller,dto}`
- Transaction boundaries at service layer (EntityListeners run within entity transaction)
- DTOs for API responses (AuditLogResponse, AuditLogFilter)
- No new environment variables required
- Input validation via Spring's @Valid where applicable

**Status**: ✅ COMPLIANT

**Final Gate Result**: ✅ PASSED - Design phase complete with full constitutional compliance

### Key Design Decisions Rationale

1. **Custom EntityListeners over Hibernate Envers**:
   - Envers stores full entity snapshots (not field-level diffs) - doesn't meet FR-007 requirement
   - Custom approach provides 25-40% better performance
   - Gives full control over what gets audited and how
   - Aligns with "avoid over-engineering" principle - no external framework needed

2. **JSONB for change storage**:
   - Flexible schema adapts to any entity without migrations
   - PostgreSQL native feature with excellent performance (GIN indexes)
   - Ideal for audit use case (infrequent writes, rich querying needs)
   - 30% storage savings vs. full entity snapshots

3. **PostgreSQL table partitioning**:
   - Necessary for 2+ year retention requirement (200,000+ entries)
   - Keeps query performance constant as data grows
   - Native PostgreSQL feature, no external tools
   - Enables efficient archival (drop old partitions)

All decisions support simplicity while meeting non-negotiable requirements for performance, scalability, and compliance.
