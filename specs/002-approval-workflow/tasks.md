# Tasks: General Approval Workflow

**Input**: Design documents from `/specs/002-approval-workflow/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are OPTIONAL per the feature specification. No test tasks are included unless explicitly requested.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Single Spring Boot project: `src/main/java/com/example/todolist/`
- Tests: `src/test/java/com/example/todolist/`
- Resources: `src/main/resources/`
- Database migrations: `src/main/resources/db/changelog/changes/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and verification of existing structure

- [ ] T001 Verify existing Spring Boot project structure and dependencies
- [ ] T002 Verify PostgreSQL database connection and Liquibase configuration
- [ ] T003 [P] Verify existing User, Todo, and Invoice entities are compatible with approval workflow

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core approval infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Enums

- [ ] T004 [P] Create ApprovalStatus enum in src/main/java/com/example/todolist/model/ApprovalStatus.java
- [ ] T005 [P] Create RequestOperation enum in src/main/java/com/example/todolist/model/RequestOperation.java
- [ ] T006 [P] Create RuleType enum in src/main/java/com/example/todolist/model/RuleType.java

### Core Entities

- [ ] T007 [P] Create ApprovalRequest entity in src/main/java/com/example/todolist/model/ApprovalRequest.java
- [ ] T008 [P] Create ApprovalRecord entity in src/main/java/com/example/todolist/model/ApprovalRecord.java
- [ ] T009 [P] Create ApprovalRule entity in src/main/java/com/example/todolist/model/ApprovalRule.java

### Database Migration

- [ ] T010 Generate Liquibase migration for approval tables using ./mvnw liquibase:diff
- [ ] T011 Review and enhance generated migration with unique partial index for item locking in src/main/resources/db/changelog/changes/
- [ ] T012 Apply database migration using ./mvnw liquibase:update or make migrate
- [ ] T013 Create SQL seed script for initial approval rules in src/main/resources/db/changelog/changes/seed-approval-rules.sql
- [ ] T014 Apply approval rules seed data

### Repositories

- [ ] T015 [P] Create ApprovalRequestRepository in src/main/java/com/example/todolist/repository/ApprovalRequestRepository.java
- [ ] T016 [P] Create ApprovalRecordRepository in src/main/java/com/example/todolist/repository/ApprovalRecordRepository.java
- [ ] T017 [P] Create ApprovalRuleRepository in src/main/java/com/example/todolist/repository/ApprovalRuleRepository.java

### Core Services

- [ ] T018 Create ApprovalRuleService with rule evaluation logic in src/main/java/com/example/todolist/service/ApprovalRuleService.java
- [ ] T019 Create NotificationService interface in src/main/java/com/example/todolist/service/NotificationService.java
- [ ] T020 Create PrintNotificationService implementation in src/main/java/com/example/todolist/service/PrintNotificationService.java

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Submit Approval Request (Priority: P1) 🎯 MVP

**Goal**: Enable users to submit approval requests for Todo and Invoice create/update/delete operations. Requests are created with correct status, routed to appropriate approvers based on rules, and properly handle item locking for update/delete operations.

**Independent Test**: A user can attempt to create/update/delete a Todo or Invoice item. The system correctly creates an approval request (or executes immediately for low-priority items), enforces locking for update/delete operations, and routes the request to the appropriate approvers based on the configured rules.

### Implementation for User Story 1

- [ ] T021 [US1] Implement DiffService for generating field diffs in src/main/java/com/example/todolist/service/DiffService.java
- [ ] T022 [US1] Implement ApprovalService.submitApprovalRequest() method in src/main/java/com/example/todolist/service/ApprovalService.java
- [ ] T023 [US1] Implement ApprovalService.hasActiveApprovalRequest() method for locking checks in src/main/java/com/example/todolist/service/ApprovalService.java
- [ ] T024 [US1] Implement ApprovalService.applyChange() method for executing approved changes in src/main/java/com/example/todolist/service/ApprovalService.java
- [ ] T025 [US1] Create ApprovalRequestController with POST /api/approval-requests endpoint in src/main/java/com/example/todolist/controller/ApprovalRequestController.java
- [ ] T026 [US1] Add GET /api/approval-requests endpoint to list approval requests in src/main/java/com/example/todolist/controller/ApprovalRequestController.java
- [ ] T027 [US1] Add GET /api/approval-requests/{id} endpoint with diff view in src/main/java/com/example/todolist/controller/ApprovalRequestController.java
- [ ] T028 [US1] Modify TodoController to route create/update/delete through approval workflow in src/main/java/com/example/todolist/controller/TodoController.java
- [ ] T029 [US1] Modify InvoiceController to route create/update/delete through approval workflow in src/main/java/com/example/todolist/controller/InvoiceController.java
- [ ] T030 [US1] Add error handling for item locking conflicts (409 Conflict) in approval workflow integration

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently. Users can submit approval requests, items are locked during review, and low-priority items execute immediately.

---

## Phase 4: User Story 2 - Review and Approve/Reject Requests (Priority: P2)

**Goal**: Enable approvers (admin/manager) to review pending approval requests, see request details including diff for updates, and approve or reject requests with optional comments. System correctly evaluates approval rules (AND/OR logic), updates request status, and applies changes when all requirements are met.

**Independent Test**: Pre-create approval requests in pending/partially approved states. Verify approvers can view request details with diff, approve or reject with comments, system correctly evaluates AND/OR approval rules, status updates appropriately (pending → partially approved → approved), and changes are applied only when all approvals received.

### Implementation for User Story 2

- [ ] T031 [US2] Implement ApprovalService.approve() method with rule evaluation and state transitions in src/main/java/com/example/todolist/service/ApprovalService.java
- [ ] T032 [US2] Implement ApprovalService.reject() method in src/main/java/com/example/todolist/service/ApprovalService.java
- [ ] T033 [US2] Implement approval requirement checking logic (ALL_REQUIRED vs ANY_REQUIRED) in src/main/java/com/example/todolist/service/ApprovalService.java
- [ ] T034 [US2] Add POST /api/approval-requests/{id}/approve endpoint in src/main/java/com/example/todolist/controller/ApprovalRequestController.java
- [ ] T035 [US2] Add POST /api/approval-requests/{id}/reject endpoint in src/main/java/com/example/todolist/controller/ApprovalRequestController.java
- [ ] T036 [US2] Add GET /api/approval-requests/pending-for-me endpoint to show approver's queue in src/main/java/com/example/todolist/controller/ApprovalRequestController.java
- [ ] T037 [US2] Add role-based authorization checks for approval operations in src/main/java/com/example/todolist/controller/ApprovalRequestController.java
- [ ] T038 [US2] Add validation to prevent duplicate approvals from same user in src/main/java/com/example/todolist/service/ApprovalService.java
- [ ] T039 [US2] Integrate NotificationService to send approval/rejection notifications in src/main/java/com/example/todolist/service/ApprovalService.java

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently. Users can submit requests (US1) and approvers can review/approve/reject them (US2).

---

## Phase 5: User Story 3 - Withdraw Pending Approval Request (Priority: P3)

**Goal**: Enable users to withdraw their own pending or partially approved approval requests. Withdrawn requests change status to WITHDRAWN, are removed from approvers' queues, and unlock the target item. System prevents withdrawal of already approved/rejected requests and prevents users from withdrawing others' requests.

**Independent Test**: Pre-create approval requests in various states (pending, partially approved, approved, rejected). Verify requester can withdraw pending/partially approved requests, status changes to WITHDRAWN, item is unlocked, approvers no longer see the request. Verify approved/rejected requests cannot be withdrawn, and users cannot withdraw others' requests.

### Implementation for User Story 3

- [ ] T040 [US3] Implement ApprovalService.withdraw() method with status validation in src/main/java/com/example/todolist/service/ApprovalService.java
- [ ] T041 [US3] Add authorization check to ensure only requester can withdraw in src/main/java/com/example/todolist/service/ApprovalService.java
- [ ] T042 [US3] Add POST /api/approval-requests/{id}/withdraw endpoint in src/main/java/com/example/todolist/controller/ApprovalRequestController.java
- [ ] T043 [US3] Add validation to prevent withdrawal of terminal states (APPROVED, REJECTED) in src/main/java/com/example/todolist/service/ApprovalService.java

**Checkpoint**: All three core user stories should now be independently functional. Users can submit requests (US1), approvers can review (US2), and requesters can withdraw (US3).

---

## Phase 6: User Story 4 - View Diff for Update Requests (Priority: P4)

**Goal**: When approvers review update requests, they see a clear field-by-field diff showing old vs new values for changed fields. Create requests show only new values, delete requests show current values to be deleted. Diff is computed on-demand using current item state.

**Independent Test**: Create update requests for Todo and Invoice items with various field changes. Verify approvers see accurate diff view with old and new values for each changed field. Verify create requests show only new values, delete requests show current values. Verify unchanged fields are not shown or clearly marked as unchanged.

### Implementation for User Story 4

- [ ] T044 [US4] Implement DiffService.generateDiff() method for Todo items in src/main/java/com/example/todolist/service/DiffService.java
- [ ] T045 [US4] Implement DiffService.generateDiff() method for Invoice items in src/main/java/com/example/todolist/service/DiffService.java
- [ ] T046 [US4] Create FieldDiff DTO class in src/main/java/com/example/todolist/service/DiffService.java
- [ ] T047 [US4] Integrate diff generation into GET /api/approval-requests/{id} response in src/main/java/com/example/todolist/controller/ApprovalRequestController.java
- [ ] T048 [US4] Handle diff display for CREATE operations (new values only) in src/main/java/com/example/todolist/service/DiffService.java
- [ ] T049 [US4] Handle diff display for DELETE operations (current values only) in src/main/java/com/example/todolist/service/DiffService.java

**Checkpoint**: Approvers can now see detailed diffs when reviewing update requests, improving review efficiency and decision quality.

---

## Phase 7: User Story 5 - Receive Notification on Approval Completion (Priority: P5)

**Goal**: After approval request reaches terminal state (approved or rejected), requester receives email notification (printed to console for now) with request details and outcome. Notifications are sent automatically when changes are applied or rejections are recorded.

**Independent Test**: Complete approval requests by getting all required approvals. Verify console output shows email notification to requester with approval confirmation and change details. Reject an approval request and verify console shows rejection notification with reason. Verify notification content includes all relevant request information.

### Implementation for User Story 5

- [ ] T050 [US5] Implement NotificationService.sendApprovalCompletedNotification() in src/main/java/com/example/todolist/service/PrintNotificationService.java
- [ ] T051 [US5] Implement NotificationService.sendRejectionNotification() in src/main/java/com/example/todolist/service/PrintNotificationService.java
- [ ] T052 [US5] Integrate notification calls into ApprovalService.approve() for completed approvals in src/main/java/com/example/todolist/service/ApprovalService.java
- [ ] T053 [US5] Integrate notification calls into ApprovalService.reject() in src/main/java/com/example/todolist/service/ApprovalService.java
- [ ] T054 [US5] Add structured logging for notification events in src/main/java/com/example/todolist/service/PrintNotificationService.java

**Checkpoint**: All five user stories should now be complete and functional. Users receive notifications when their requests are approved or rejected.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories and final validation

- [ ] T055 [P] Add comprehensive error handling and validation across all approval endpoints
- [ ] T056 [P] Add logging for all approval workflow operations (submit, approve, reject, withdraw, apply change)
- [ ] T057 Verify all database indexes are created correctly (item locking partial index, approval record unique constraint)
- [ ] T058 Test manual scenarios from quickstart.md (submit and approve, item locking, withdrawal)
- [ ] T059 [P] Document API endpoints in OpenAPI format (already done in contracts/approval-api.yaml)
- [ ] T060 Verify approval rules are seeded correctly for Todo (HIGH/MEDIUM/LOW) and Invoice operations
- [ ] T061 Performance test: Verify item locking prevents race conditions under concurrent requests
- [ ] T062 End-to-end validation: Test complete approval workflow for all item types and priority levels

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phases 3-7)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3 → P4 → P5)
- **Polish (Phase 8)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - Integrates with US1 but independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - Uses US1 infrastructure but independently testable
- **User Story 4 (P4)**: Can start after Foundational (Phase 2) - Enhances US2 review experience but independently testable
- **User Story 5 (P5)**: Can start after Foundational (Phase 2) - Enhances US2 with notifications but independently testable

### Within Each User Story

- Services before controllers
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational enums (T004-T006) can run in parallel
- All Foundational entities (T007-T009) can run in parallel
- All Foundational repositories (T015-T017) can run in parallel
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- Within US4: Todo diff (T044) and Invoice diff (T045) can run in parallel
- All Polish tasks marked [P] can run in parallel

---

## Parallel Example: Foundational Phase

```bash
# Launch all enums together:
Task: "Create ApprovalStatus enum in src/main/java/com/example/todolist/model/ApprovalStatus.java"
Task: "Create RequestOperation enum in src/main/java/com/example/todolist/model/RequestOperation.java"
Task: "Create RuleType enum in src/main/java/com/example/todolist/model/RuleType.java"

# Launch all entities together:
Task: "Create ApprovalRequest entity in src/main/java/com/example/todolist/model/ApprovalRequest.java"
Task: "Create ApprovalRecord entity in src/main/java/com/example/todolist/model/ApprovalRecord.java"
Task: "Create ApprovalRule entity in src/main/java/com/example/todolist/model/ApprovalRule.java"

# Launch all repositories together:
Task: "Create ApprovalRequestRepository in src/main/java/com/example/todolist/repository/ApprovalRequestRepository.java"
Task: "Create ApprovalRecordRepository in src/main/java/com/example/todolist/repository/ApprovalRecordRepository.java"
Task: "Create ApprovalRuleRepository in src/main/java/com/example/todolist/repository/ApprovalRuleRepository.java"
```

---

## Parallel Example: User Story 4

```bash
# Launch both diff implementations together:
Task: "Implement DiffService.generateDiff() method for Todo items in src/main/java/com/example/todolist/service/DiffService.java"
Task: "Implement DiffService.generateDiff() method for Invoice items in src/main/java/com/example/todolist/service/DiffService.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

**MVP Delivers**:
- Users can submit approval requests for Todo/Invoice operations
- Requests are routed to correct approvers based on rules
- Low-priority items execute immediately
- Items are locked during review (no duplicate requests)
- Integration with existing Todo/Invoice CRUD APIs

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo (Core workflow complete)
4. Add User Story 3 → Test independently → Deploy/Demo (User control added)
5. Add User Story 4 → Test independently → Deploy/Demo (Enhanced review experience)
6. Add User Story 5 → Test independently → Deploy/Demo (Full feature complete)
7. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (Submit requests)
   - Developer B: User Story 2 (Approve/Reject)
   - Developer C: User Story 4 (Diff view)
3. Stories complete and integrate independently
4. User Story 3 (Withdraw) and 5 (Notifications) can be added by any developer

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Tests are NOT included per the feature specification (optional unless requested)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
- All timestamps use Instant (UTC) per constitution
- All entities use Lombok annotations (@Getter, @Setter, @NoArgsConstructor)
- Database migrations generated with ./mvnw liquibase:diff
- Follow Spring Boot layered architecture (Model → Repository → Service → Controller)
