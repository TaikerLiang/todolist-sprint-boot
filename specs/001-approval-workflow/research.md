# Research & Design Decisions: Approval Workflow

**Feature**: Multi-Level Approval Workflow
**Date**: 2025-12-16
**Status**: Complete

This document resolves technical unknowns identified in the Technical Context section of plan.md.

## 1. Approval Rule Configuration

**Decision**: Store approval rules in database table (ApprovalRule entity)

**Rationale**:
- Enables runtime configuration without code deployment
- Supports extensibility requirement for new item types, priority levels, and roles
- Aligns with existing Spring Data JPA patterns in the codebase
- Allows for future UI-based rule management
- No additional dependencies required (uses existing PostgreSQL + JPA)

**Implementation Approach**:
- Create ApprovalRule entity with fields: itemType (TODO, INVOICE), priorityLevel (LOW, MEDIUM, HIGH), requiredRoles (JSON array or separate table)
- Service layer queries rules at runtime to determine required approvers
- Seed database with initial rules via Liquibase changeset with `<insert>` tags
- Future enhancement: Admin UI for rule management

**Alternatives Considered**:
- **Configuration file (YAML/properties)**: Rejected because requires application restart for changes; doesn't scale for multi-tenant scenarios
- **Hardcoded logic**: Rejected because violates extensibility requirement; every new rule requires code change
- **Hybrid (default rules in code + overrides in database)**: Rejected for added complexity; single source of truth is simpler

## 2. Notification Strategy

**Decision**: Spring Application Events (event-driven within same JVM)

**Rationale**:
- Decouples approval workflow from notification mechanism
- Leverages Spring's built-in `ApplicationEventPublisher` - no new dependencies
- Synchronous execution acceptable for notification use case (no complex async orchestration needed)
- Simple to test with `@EventListener` on test methods
- Aligns with Spring Boot best practices for in-application communication

**Implementation Approach**:
- Define event classes: `ApprovalRequestedEvent`, `ApprovalApprovedEvent`, `ApprovalRejectedEvent`
- ApprovalService publishes events via `ApplicationEventPublisher.publishEvent()`
- NotificationService listens with `@EventListener` and creates Notification entities
- Initial implementation: Store notifications in database (Notification entity)
- Future enhancement: Email/SMS integration via additional event listeners

**Alternatives Considered**:
- **Direct service method calls**: Rejected because tightly couples approval logic to notification logic; harder to add new notification channels
- **Message queue (RabbitMQ/Kafka)**: Rejected as over-engineered for current scale; adds external dependency and operational complexity for feature that doesn't require distributed messaging
- **Spring Integration**: Rejected as unnecessary abstraction layer for simple event handling

## 3. Status Transition State Machine

**Decision**: Explicit state validation in service layer with enum-based status

**Rationale**:
- Java enums provide compile-time safety for valid states
- State transition logic centralized in ApprovalService for easier testing and maintenance
- No additional state machine library needed (Spring Statemachine would be overkill)
- Clear and explicit - easy for developers to understand state flows

**Implementation Approach**:
- Create ApprovalStatus enum: `DRAFT`, `PENDING`, `APPROVED`, `REJECTED`, `WITHDRAWN`
- ApprovalService methods validate current status before transitions:
  ```java
  if (request.getStatus() != ApprovalStatus.PENDING) {
      throw new IllegalStateException("Cannot approve request in status: " + request.getStatus());
  }
  ```
- Document state machine as comment in ApprovalStatus enum or data-model.md
- State transitions:
  - `null/DRAFT → PENDING` (on submission)
  - `PENDING → APPROVED` (when all required approvals obtained)
  - `PENDING → REJECTED` (when any approver rejects)
  - `PENDING → WITHDRAWN` (when creator withdraws)
  - No transitions out of terminal states (APPROVED, REJECTED, WITHDRAWN)

**Alternatives Considered**:
- **Spring Statemachine**: Rejected as over-engineered; adds complexity and learning curve for simple linear state transitions
- **Status as String**: Rejected due to lack of compile-time safety and potential for typos/invalid states
- **Separate state machine library (Stateless4j)**: Rejected to avoid external dependency for simple state validation

## 4. Concurrent Approval Handling

**Decision**: Optimistic locking with JPA `@Version` annotation

**Rationale**:
- JPA built-in feature - no additional dependencies
- Prevents lost updates when multiple approvers act simultaneously
- Appropriate for low-contention scenarios (approval actions are relatively infrequent compared to reads)
- Simple to implement and understand
- Throws `OptimisticLockException` which can be caught and retried with user-friendly message

**Implementation Approach**:
- Add `@Version` field to ApprovalRequest entity: `private Long version;`
- JPA automatically increments version on every update
- If two approvers try to update simultaneously, second one gets `OptimisticLockException`
- Service layer catches exception and returns appropriate error response (409 Conflict)
- For same-role approvers: First one wins (only one manager approval needed)
- For multi-role approvals: Each role approval recorded in separate ApprovalRecord entry

**Alternatives Considered**:
- **Pessimistic locking (SELECT FOR UPDATE)**: Rejected because it can cause blocking and deadlocks; overkill for this use case; degrades read performance
- **Database unique constraints**: Rejected because doesn't handle partial approval state elegantly; would need complex constraint logic
- **Redis distributed lock**: Rejected as over-engineered; adds external dependency and complexity for feature that doesn't need distributed coordination

## 5. Test Strategy

**Decision**: Comprehensive tests required (following Constitution Section V)

**Test Coverage Scope**:
1. **Unit Tests** (service layer):
   - ApprovalService: approval rule evaluation, status transitions, approver determination
   - ApprovalRuleService: rule matching logic for different item types and levels
   - Mock repositories and event publisher
   - Test coverage: happy path + edge cases (invalid status, missing rules, concurrent updates)

2. **Integration Tests** (repository layer):
   - ApprovalRequestRepository: query methods for pending approvals by role, item type filtering
   - ApprovalRecordRepository: audit trail queries
   - Use H2 in-memory database
   - Test coverage: complex queries, relationship loading (eager/lazy), constraint validation

3. **API Tests** (controller layer):
   - ApprovalController: REST endpoint behavior, authorization checks, error responses
   - Use MockMvc with @WebMvcTest
   - Test coverage: all endpoints, status codes, request/response JSON format, security (role-based access)

**Test-First Approach**:
- Write tests before implementation
- Verify tests fail before writing production code
- Implement until tests pass
- Red-Green-Refactor cycle

**Rationale**:
- Tests required per Constitution when tests are included in feature
- Approval workflow is critical business logic that must work correctly
- Complex state transitions and role-based logic are error-prone without tests
- Tests serve as documentation for expected behavior
- Enables confident refactoring and future enhancements

## Summary of Design Decisions

| Area | Decision | Key Benefit |
|------|----------|-------------|
| Rule Storage | Database table (ApprovalRule) | Runtime configurability, extensibility |
| Notifications | Spring Application Events | Decoupling, no new dependencies |
| State Management | Explicit validation with enums | Simplicity, compile-time safety |
| Concurrency | Optimistic locking (@Version) | Prevents lost updates, simple |
| Testing | Comprehensive with Test-First | Quality assurance, living documentation |

## Dependencies Summary

**No new external dependencies required**. All design decisions use existing Spring Boot, JPA, and PostgreSQL capabilities already in the project.

## Phase 0 Completion Checklist

- [x] Approval rule configuration strategy defined
- [x] Notification pattern selected and justified
- [x] State machine approach documented
- [x] Concurrent approval handling designed
- [x] Test strategy clarified and scoped
- [x] All alternatives considered and documented
- [x] No new external dependencies introduced

**Status**: Research complete. Ready for Phase 1 (Data Model & Contracts).
