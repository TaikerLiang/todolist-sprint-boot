# Implementation Plan: General Approval Workflow

**Branch**: `002-approval-workflow` | **Date**: 2025-12-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-approval-workflow/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Implement a general approval workflow system that applies to Todo and Invoice create/update/delete operations. The system uses role-based approval rules (admin, manager, user) where approval requirements vary by item type and operation. Todo items use level-based rules (high/medium/low), while Invoice items have operation-specific rules. The workflow supports request withdrawal, diff viewing for updates, item locking during review, and email notifications (mocked with console output). The design is extensible to support future item types like day-off requests.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.5.7, Spring Data JPA, Hibernate 6, Liquibase, Lombok, PostgreSQL JDBC Driver
**Storage**: PostgreSQL with Liquibase migrations
**Testing**: JUnit 5, Spring Boot Test, MockMvc (API tests optional unless requested)
**Target Platform**: JVM server application (Linux/macOS/Windows)
**Project Type**: Single Spring Boot application
**Performance Goals**: Support <100 concurrent approval requests with <500ms response time for approval operations
**Constraints**: Approval decision logic must complete in <200ms; item locking must prevent race conditions with database-level constraints
**Scale/Scope**: Initial support for Todo and Invoice items; designed to scale to 5+ approvable item types with configurable approval rules

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Layered Architecture ✅ PASS
- **Model Layer**: New JPA entities (ApprovalRequest, ApprovalRecord, ApprovalRule) with no business logic
- **Repository Layer**: Spring Data JPA repositories extending JpaRepository for approval entities
- **Service Layer**: ApprovalService contains approval decision logic, rule evaluation, and state transitions
- **Controller Layer**: ApprovalController provides REST endpoints, delegates to ApprovalService

### II. UTC-First Timestamps ✅ PASS
- All timestamp fields (createdAt, approvedAt, etc.) use `java.time.Instant`
- Consistent with existing Todo and Invoice entities
- No LocalDateTime usage

### III. Database Migration Discipline ✅ PASS
- Hibernate ddl-auto remains `none`
- All schema changes via Liquibase migrations
- Generate migrations with `./mvnw liquibase:diff` after entity changes

### IV. RESTful API Standards ✅ PASS
- Resource-based URLs: `/api/approval-requests`, `/api/approval-requests/{id}/approve`
- Proper HTTP methods: POST (submit/approve/reject/withdraw), GET (list/view), PUT (update request)
- Standard status codes: 200 (success), 201 (created), 400 (bad request), 404 (not found), 409 (conflict for locked items)
- JSON camelCase in API, snake_case in database

### V. Comprehensive Testing ✅ PASS (Optional)
- Unit tests for ApprovalService logic (rule evaluation, state transitions)
- Integration tests for ApprovalRequestRepository (locking behavior, queries)
- API tests for ApprovalController (MockMvc) if requested
- Tests run via `./mvnw test`

### VI. Build & Dependency Management ✅ PASS
- Java 21 source/target maintained
- No new external dependencies required (Spring Boot, JPA, Lombok already present)
- Reproducible builds with Maven

**Result**: All constitution checks PASS. No violations. No complexity justifications needed.

## Project Structure

### Documentation (this feature)

```text
specs/002-approval-workflow/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── approval-api.yaml  # OpenAPI specification
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/main/java/com/example/todolist/
├── model/
│   ├── ApprovalRequest.java      # NEW: Approval request entity
│   ├── ApprovalRecord.java       # NEW: Individual approval/rejection record
│   ├── ApprovalRule.java         # NEW: Configurable approval rules
│   ├── ApprovalStatus.java       # NEW: Enum (PENDING, PARTIALLY_APPROVED, APPROVED, REJECTED, WITHDRAWN)
│   ├── RequestOperation.java     # NEW: Enum (CREATE, UPDATE, DELETE)
│   ├── Todo.java                 # EXISTING: May need locking status field
│   ├── Invoice.java              # EXISTING: May need locking status field
│   ├── User.java                 # EXISTING: Already has Role enum
│   ├── Role.java                 # EXISTING: ADMIN, MANAGER, USER
│   └── Level.java                # EXISTING: LOW, MEDIUM, HIGH
│
├── repository/
│   ├── ApprovalRequestRepository.java   # NEW: Spring Data JPA repository
│   ├── ApprovalRecordRepository.java    # NEW: Spring Data JPA repository
│   ├── ApprovalRuleRepository.java      # NEW: Spring Data JPA repository
│   ├── TodoRepository.java              # EXISTING
│   ├── InvoiceRepository.java           # EXISTING
│   └── UserRepository.java              # EXISTING
│
├── service/
│   ├── ApprovalService.java         # NEW: Core approval workflow logic
│   ├── ApprovalRuleService.java     # NEW: Rule evaluation and management
│   ├── NotificationService.java     # NEW: Email notification (mocked with print)
│   ├── DiffService.java             # NEW: Generate diffs for update requests
│   ├── TodoService.java             # EXISTING: May need updates for approval integration
│   └── InvoiceService.java          # EXISTING: May need updates for approval integration
│
├── controller/
│   ├── ApprovalRequestController.java   # NEW: REST endpoints for approval requests
│   ├── TodoController.java              # EXISTING: May need updates
│   └── InvoiceController.java           # EXISTING: May need updates
│
└── config/
    └── [existing Spring Boot configuration]

src/main/resources/
└── db/changelog/
    └── changes/
        └── [NEW migrations for approval tables]

src/test/java/com/example/todolist/
├── service/
│   ├── ApprovalServiceTest.java         # NEW: Unit tests for approval logic
│   └── ApprovalRuleServiceTest.java     # NEW: Unit tests for rule evaluation
│
├── repository/
│   └── ApprovalRequestRepositoryTest.java  # NEW: Integration tests for locking
│
└── controller/
    └── ApprovalRequestControllerTest.java  # NEW: API tests (optional)
```

**Structure Decision**: Single Spring Boot application following standard layered architecture. New approval workflow components integrate with existing Todo and Invoice models through polymorphic references (storing target item type and ID). This maintains separation of concerns while allowing the approval system to be extended to new item types without modifying core approval code.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

N/A - No constitutional violations. All complexity justified by requirements.

---

## Phase 0: Research (COMPLETED)

All technical unknowns resolved. See [research.md](research.md) for details.

**Key Decisions**:
- Polymorphic item references via string discriminator + ID
- JSONB for flexible requested data storage
- Database unique partial index for item locking
- Database-backed approval rules with enum-based evaluation
- On-demand diff generation
- Interface-based notification service (print implementation)
- Count-based approval logic with role tracking

---

## Phase 1: Design & Contracts (COMPLETED)

### Data Model

See [data-model.md](data-model.md) for complete entity design.

**Entities Created**:
- ApprovalRequest (approval request lifecycle)
- ApprovalRecord (individual approval/rejection decisions)
- ApprovalRule (configurable approval requirements)
- Enums: ApprovalStatus, RequestOperation, RuleType

**Database Design**:
- 3 new tables with proper indexes
- Unique partial index for item locking
- JSONB columns for flexible data storage
- UTC timestamps throughout

### API Contracts

See [contracts/approval-api.yaml](contracts/approval-api.yaml) for OpenAPI specification.

**Endpoints**:
- `POST /api/approval-requests` - Submit approval request
- `GET /api/approval-requests` - List requests
- `GET /api/approval-requests/{id}` - Get request details with diff
- `POST /api/approval-requests/{id}/approve` - Approve
- `POST /api/approval-requests/{id}/reject` - Reject
- `POST /api/approval-requests/{id}/withdraw` - Withdraw
- `GET /api/approval-requests/pending-for-me` - Get my pending approvals

### Quickstart Guide

See [quickstart.md](quickstart.md) for implementation guide and test scenarios.

**Implementation Phases**:
1. Create JPA entities
2. Generate Liquibase migration
3. Create repositories
4. Seed approval rules
5. Implement services
6. Create REST controllers
7. Integrate with Todo/Invoice controllers

---

## Phase 2: Task Breakdown

**Not included in /speckit.plan output**. Run `/speckit.tasks` to generate [tasks.md](tasks.md) with dependency-ordered implementation tasks.

---

## Implementation Notes

### Critical Path
1. Data model entities (ApprovalRequest, ApprovalRecord, ApprovalRule)
2. Database migration with unique constraint for locking
3. ApprovalRuleService for rule evaluation
4. ApprovalService for core workflow logic
5. REST API endpoints
6. Integration with existing Todo/Invoice controllers

### Testing Strategy
- **Unit Tests**: ApprovalService state transitions, rule evaluation logic
- **Integration Tests**: Item locking behavior, concurrent request handling
- **API Tests** (optional): End-to-end approval workflow via REST

### Extensibility Points
- New item types: Add to targetItemType, create approval rules
- New rule types: Add enum values to RuleType
- Complex conditions: Extend condition string parsing
- Real email: Implement NotificationService interface

### Performance Considerations
- Index on (target_item_type, target_item_id) for fast locking checks
- Partial unique index only applies to PENDING/PARTIALLY_APPROVED (efficient)
- JSONB indexed if needed for complex queries on requestedData
- Lazy loading on ManyToOne relationships

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Race condition on item locking | High | Database unique partial index enforces constraint |
| JSONB query performance | Medium | Add GIN index if complex queries needed |
| Approval rule complexity growth | Medium | Keep rules simple; document condition syntax |
| Notification service unavailable | Low | Interface allows swapping implementations |

---

## Post-Implementation Checklist

- [ ] All entities created with proper JPA annotations
- [ ] Database migration applied successfully
- [ ] Repositories follow Spring Data conventions
- [ ] Services implement all required approval logic
- [ ] REST endpoints match OpenAPI specification
- [ ] Approval rules seeded for Todo and Invoice
- [ ] Unit tests pass for ApprovalService
- [ ] Integration tests pass for locking behavior
- [ ] Manual testing completed (see quickstart.md scenarios)
- [ ] CLAUDE.md updated with new technologies (done by script)
- [ ] Constitution check re-verified post-design (all PASS)

---

## Summary

The approval workflow system is designed to be:
- ✅ **General**: Works with Todo, Invoice, and future item types
- ✅ **Configurable**: Rules stored in database, no code changes needed
- ✅ **Performant**: Database-level locking, efficient queries
- ✅ **Extensible**: Clean interfaces for notifications, rules, and item types
- ✅ **Testable**: Clear service boundaries, mockable dependencies
- ✅ **Constitution-compliant**: Follows all Spring Boot best practices

All design artifacts completed. Ready for task breakdown with `/speckit.tasks`.
