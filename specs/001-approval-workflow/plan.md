# Implementation Plan: Multi-Level Approval Workflow

**Branch**: `001-approval-workflow` | **Date**: 2025-12-16 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-approval-workflow/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Implement a flexible, multi-level approval workflow system for todo and invoice items based on priority levels. Low-priority items require no approval, medium-priority items require manager approval, and high-priority items require both manager and admin approval. The system must be extensible to support future item types, priority levels, and user roles. All approval actions are tracked in an audit trail for accountability.

**Technical Approach**: Extend existing Todo and Invoice models with approval status tracking. Create new ApprovalRequest and ApprovalRecord entities to manage the approval workflow. Implement service layer logic to determine required approvers based on configurable rules. Use event-driven notifications for approval requests and decisions. Provide REST API endpoints for approval dashboard and actions.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.1.5, Spring Data JPA, Hibernate 6, Liquibase 4.27.0, PostgreSQL driver
**Storage**: PostgreSQL database (existing)
**Testing**: JUnit 5, MockMvc, H2 (in-memory test database)
**Target Platform**: Linux server (existing Spring Boot application)
**Project Type**: Single web application (extending existing todolist API)
**Performance Goals**: Process 95% of approval actions within 3 seconds; support 100 concurrent approval requests; dashboard load time under 2 seconds for 1000 pending items
**Constraints**: Must maintain UTC-first timestamp handling; approval rules must be configurable without code changes; extensible to support new item types
**Scale/Scope**: Support 3 user roles initially (user, manager, admin); 3 priority levels (low, medium, high); 2 item types (todo, invoice) with ability to extend

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Layered Architecture ✅ COMPLIANT
- **Model Layer**: New entities (ApprovalRequest, ApprovalRecord, ApprovalRule, Notification) will be JPA entities with no business logic
- **Repository Layer**: New repositories (ApprovalRequestRepository, ApprovalRecordRepository, etc.) will extend JpaRepository
- **Service Layer**: New ApprovalService will contain all approval workflow logic; existing TodoService and InvoiceService will interact with ApprovalService
- **Controller Layer**: New ApprovalController will provide REST endpoints for approval dashboard and actions
- **Assessment**: Follows standard layered architecture with clear separation

### II. UTC-First Timestamps ✅ COMPLIANT
- All new entities (ApprovalRequest, ApprovalRecord, Notification) will use `java.time.Instant` for timestamps
- Approval workflow timestamps (created_at, approved_at, rejected_at) will use Instant
- No LocalDateTime usage
- **Assessment**: Maintains UTC-first principle

### III. Database Migration Discipline ✅ COMPLIANT
- Hibernate `ddl-auto=none` already configured
- New tables and schema changes will go through Liquibase: `make makemigration NAME=approval_workflow`
- Manual review of generated migrations before applying
- Rollback tested with `make rollback`
- **Assessment**: Follows migration discipline

### IV. RESTful API Standards ✅ COMPLIANT
- REST endpoints will follow resource-based URLs: `/api/approvals`, `/api/approvals/{id}/approve`, `/api/approvals/{id}/reject`
- Proper HTTP methods: GET (list/view), POST (approve/reject/create), DELETE (withdraw)
- Appropriate status codes: 200 (success), 201 (created), 400 (bad request), 404 (not found), 403 (forbidden)
- JSON request/response with camelCase in API
- `@RestController` and `@RequestMapping` usage
- **Assessment**: Follows RESTful standards

### V. Comprehensive Testing ✅ COMPLIANT (RESOLVED)
- Unit tests for ApprovalService logic with mocked repositories
- Integration tests for approval workflow with test database
- API tests for ApprovalController with MockMvc
- **Decision**: Comprehensive tests required following Constitution Section V
- Test-First approach: Write tests → Verify fail → Implement → Verify pass
- **Assessment**: Test strategy defined in research.md; will follow constitution standards

### VI. Build & Dependency Management ✅ COMPLIANT
- Java 21 source/target already configured
- All dependencies already in pom.xml (Spring Boot, JPA, Liquibase)
- Lombok already configured
- **Assessment**: No new dependencies required; existing setup compliant

### Constitution Violations: NONE

All core principles are compliant. All clarifications resolved during research phase.

**Post-Design Re-evaluation** (after Phase 1): ✅ All design decisions validated against constitution. No violations introduced during design phase.

## Project Structure

### Documentation (this feature)

```text
specs/001-approval-workflow/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command) - PENDING
├── data-model.md        # Phase 1 output (/speckit.plan command) - PENDING
├── quickstart.md        # Phase 1 output (/speckit.plan command) - PENDING
├── contracts/           # Phase 1 output (/speckit.plan command) - PENDING
│   └── approval-api.yaml
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/main/java/com/example/todolist/
├── model/
│   ├── Todo.java                    # [EXISTING] - Add status field for approval state
│   ├── Invoice.java                 # [EXISTING] - Add status field for approval state
│   ├── User.java                    # [EXISTING] - No changes needed
│   ├── Level.java                   # [EXISTING] - Reused for priority levels
│   ├── ApprovalRequest.java         # [NEW] - Pending approval for an item
│   ├── ApprovalRecord.java          # [NEW] - Single approval/rejection action
│   ├── ApprovalRule.java            # [NEW] - Configurable approval rules
│   ├── ApprovalStatus.java          # [NEW] - Enum (PENDING, APPROVED, REJECTED)
│   └── Notification.java            # [NEW] - Approval notifications
├── repository/
│   ├── TodoRepository.java          # [EXISTING] - No changes needed
│   ├── InvoiceRepository.java       # [EXISTING] - No changes needed
│   ├── UserRepository.java          # [EXISTING] - No changes needed
│   ├── ApprovalRequestRepository.java   # [NEW]
│   ├── ApprovalRecordRepository.java    # [NEW]
│   ├── ApprovalRuleRepository.java      # [NEW]
│   └── NotificationRepository.java      # [NEW]
├── service/
│   ├── TodoService.java             # [MODIFY] - Integrate with ApprovalService
│   ├── InvoiceService.java          # [MODIFY] - Integrate with ApprovalService
│   ├── UserService.java             # [EXISTING] - No changes needed
│   ├── ApprovalService.java         # [NEW] - Core approval workflow logic
│   ├── ApprovalRuleService.java     # [NEW] - Rule evaluation logic
│   └── NotificationService.java     # [NEW] - Send approval notifications
└── controller/
    ├── TodoController.java          # [EXISTING] - No changes to existing endpoints
    ├── InvoiceController.java       # [EXISTING] - No changes to existing endpoints
    └── ApprovalController.java      # [NEW] - Approval dashboard and actions

src/main/resources/db/changelog/changes/
└── 0007_approval_workflow.yaml      # [NEW] - Liquibase migration

tests/
├── service/
│   ├── ApprovalServiceTest.java     # [NEW] - If tests required
│   └── ApprovalRuleServiceTest.java # [NEW] - If tests required
├── repository/
│   └── ApprovalRequestRepositoryTest.java  # [NEW] - If tests required
└── controller/
    └── ApprovalControllerTest.java  # [NEW] - If tests required
```

**Structure Decision**: Single project structure (Option 1) extending existing Spring Boot todolist application. All new code follows the established layered architecture: model entities, repositories, services, and controllers. Existing Todo and Invoice services will be modified to integrate with the new ApprovalService.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No complexity violations. All design decisions comply with constitution principles.

## Phase 0: Research & Design Decisions

**Status**: ✅ COMPLETE

Research tasks resolved:
1. ✅ **Approval Rule Configuration**: Database table (ApprovalRule entity) for runtime configurability
2. ✅ **Notification Strategy**: Spring Application Events (event-driven within same JVM)
3. ✅ **Status Transition State Machine**: Explicit state validation in service layer with enum-based status
4. ✅ **Concurrent Approval Handling**: Optimistic locking with JPA @Version annotation
5. ✅ **Test Strategy**: Comprehensive tests required following Constitution Section V

**Research documented in**: [`research.md`](./research.md) ✅

## Phase 1: Data Model & Contracts

**Status**: ✅ COMPLETE

Artifacts generated:
- ✅ [`data-model.md`](./data-model.md): Entity definitions and relationships for approval workflow (5 new entities + 2 modifications)
- ✅ [`contracts/approval-api.yaml`](./contracts/approval-api.yaml): OpenAPI 3.0 specification for approval REST endpoints (8 endpoints)
- ✅ [`quickstart.md`](./quickstart.md): Developer guide for implementing and using the approval workflow
- ✅ Agent context updated: CLAUDE.md updated with Java 21, Spring Boot 3.1.5, and PostgreSQL

**Phase 1 completion**: All design artifacts complete and validated

## Phase 2: Task Generation

**Status**: NOT STARTED

Task generation will occur via `/speckit.tasks` command after Phase 1 is complete. Tasks will be prioritized based on user story priorities from spec.md (P1 → P2 → P3).
