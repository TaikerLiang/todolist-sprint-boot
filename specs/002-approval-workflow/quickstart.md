# Quickstart: General Approval Workflow

**Feature**: 002-approval-workflow
**Last Updated**: 2025-12-27

## Overview

This guide walks you through implementing and testing the approval workflow system. The workflow allows Todo and Invoice operations (create/update/delete) to require approval from designated users based on configurable rules.

## Prerequisites

- Spring Boot application running (v3.5.7)
- PostgreSQL database configured
- Maven installed (`./mvnw` available)
- Basic understanding of JPA, Spring Data, and REST APIs

## Implementation Phases

### Phase 1: Create Data Model (Entities)

**Goal**: Create JPA entities for approval workflow

**Files to Create**:
1. `src/main/java/com/example/todolist/model/ApprovalStatus.java` (enum)
2. `src/main/java/com/example/todolist/model/RequestOperation.java` (enum)
3. `src/main/java/com/example/todolist/model/RuleType.java` (enum)
4. `src/main/java/com/example/todolist/model/ApprovalRequest.java`
5. `src/main/java/com/example/todolist/model/ApprovalRecord.java`
6. `src/main/java/com/example/todolist/model/ApprovalRule.java`

**Key Points**:
- Use `@Entity`, `@Table`, `@Column` annotations
- All timestamps use `Instant` (UTC-safe)
- Use `@Enumerated(EnumType.STRING)` for enums
- ApprovalRequest uses JSONB for `requestedData` field
- Add Lombok `@Getter`, `@Setter`, `@NoArgsConstructor`

**Example** (ApprovalRequest entity):
```java
@Entity
@Table(name = "approval_requests")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String targetItemType;

    @Column
    private Long targetItemId;  // Nullable for CREATE operations

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RequestOperation operation;

    @Column(columnDefinition = "JSONB")
    private String requestedData;  // Nullable for DELETE operations

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
```

**Verification**:
- Code compiles without errors
- All entities have proper JPA annotations

---

### Phase 2: Generate Database Migration

**Goal**: Create Liquibase changelog for new tables

**Steps**:
1. Ensure `.env` file has database credentials
2. Run: `./mvnw liquibase:diff`
3. Review generated changelog in `src/main/resources/db/changelog/changes/`
4. Add indexes and constraints if not auto-generated:
   ```sql
   CREATE UNIQUE INDEX idx_one_active_request_per_item
       ON approval_requests (target_item_type, target_item_id)
       WHERE status IN ('PENDING', 'PARTIALLY_APPROVED');

   ALTER TABLE approval_records
       ADD CONSTRAINT uk_approval_record UNIQUE (approval_request_id, approver_id);
   ```
5. Apply migration: `./mvnw liquibase:update` or `make migrate`

**Verification**:
- Run: `make showmigrations` to see applied migrations
- Connect to PostgreSQL and verify tables exist:
  ```sql
  \dt approval*
  \d approval_requests
  ```

---

### Phase 3: Create Repositories

**Goal**: Create Spring Data JPA repositories for data access

**Files to Create**:
1. `src/main/java/com/example/todolist/repository/ApprovalRequestRepository.java`
2. `src/main/java/com/example/todolist/repository/ApprovalRecordRepository.java`
3. `src/main/java/com/example/todolist/repository/ApprovalRuleRepository.java`

**Example** (ApprovalRequestRepository):
```java
@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    Optional<ApprovalRequest> findByTargetItemTypeAndTargetItemIdAndStatusIn(
        String targetItemType, Long targetItemId, List<ApprovalStatus> statuses);

    boolean existsByTargetItemTypeAndTargetItemIdAndStatusIn(
        String targetItemType, Long targetItemId, List<ApprovalStatus> statuses);

    List<ApprovalRequest> findByStatusIn(List<ApprovalStatus> statuses);

    List<ApprovalRequest> findByRequester(User requester);
}
```

**Verification**:
- Repositories extend `JpaRepository<T, ID>`
- Method names follow Spring Data naming conventions
- No compilation errors

---

### Phase 4: Seed Approval Rules

**Goal**: Insert initial approval rules for Todo and Invoice

**Option A: SQL Script** (quickest for testing):
Create `src/main/resources/db/changelog/changes/seed-approval-rules.sql`:
```sql
INSERT INTO approval_rules (target_item_type, operation, condition, rule_type, required_roles, priority, created_at) VALUES
-- Todo rules
('TODO', 'CREATE', 'level=HIGH', 'ALL_REQUIRED', '["ADMIN", "MANAGER"]', 100, CURRENT_TIMESTAMP),
('TODO', 'CREATE', 'level=MEDIUM', 'ALL_REQUIRED', '["MANAGER"]', 50, CURRENT_TIMESTAMP),
('TODO', 'UPDATE', 'level=HIGH', 'ALL_REQUIRED', '["ADMIN", "MANAGER"]', 100, CURRENT_TIMESTAMP),
('TODO', 'UPDATE', 'level=MEDIUM', 'ALL_REQUIRED', '["MANAGER"]', 50, CURRENT_TIMESTAMP),
('TODO', 'DELETE', 'level=HIGH', 'ALL_REQUIRED', '["ADMIN", "MANAGER"]', 100, CURRENT_TIMESTAMP),
('TODO', 'DELETE', 'level=MEDIUM', 'ALL_REQUIRED', '["MANAGER"]', 50, CURRENT_TIMESTAMP),

-- Invoice rules
('INVOICE', 'CREATE', NULL, 'ALL_REQUIRED', '["MANAGER"]', 0, CURRENT_TIMESTAMP),
('INVOICE', 'UPDATE', NULL, 'ALL_REQUIRED', '["MANAGER"]', 0, CURRENT_TIMESTAMP),
('INVOICE', 'DELETE', NULL, 'ANY_REQUIRED', '["ADMIN", "MANAGER"]', 0, CURRENT_TIMESTAMP);
```

Reference in main changelog file, then run `make migrate`.

**Option B: ApplicationRunner** (better for production):
```java
@Component
public class ApprovalRuleSeeder implements ApplicationRunner {
    @Autowired
    private ApprovalRuleRepository ruleRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (ruleRepository.count() == 0) {
            // Seed rules as shown above
        }
    }
}
```

**Verification**:
```sql
SELECT * FROM approval_rules ORDER BY target_item_type, operation, priority DESC;
```

---

### Phase 5: Implement Services

**Goal**: Create business logic for approval workflow

**Files to Create**:
1. `src/main/java/com/example/todolist/service/ApprovalRuleService.java`
2. `src/main/java/com/example/todolist/service/ApprovalService.java`
3. `src/main/java/com/example/todolist/service/DiffService.java`
4. `src/main/java/com/example/todolist/service/NotificationService.java` (interface)
5. `src/main/java/com/example/todolist/service/PrintNotificationService.java`

**Implementation Order**:
1. **ApprovalRuleService** (simplest, no dependencies):
   - `findApplicableRule(String itemType, RequestOperation op, Map<String, Object> itemData)`
   - Evaluates condition string against item data

2. **DiffService** (simple utility):
   - `generateDiff(String itemType, Long itemId, String requestedDataJson)`
   - Returns `Map<String, FieldDiff>`

3. **NotificationService + PrintNotificationService**:
   - Interface with methods for approved/rejected notifications
   - Print implementation logs to console

4. **ApprovalService** (most complex):
   - `submitApprovalRequest(...)`
   - `approve(Long requestId, User approver, String comment)`
   - `reject(Long requestId, User approver, String comment)`
   - `withdraw(Long requestId, User requester)`
   - `applyChange(ApprovalRequest request)` (creates/updates/deletes the actual item)

**Key Logic** (ApprovalService.approve):
```java
@Transactional
public ApprovalRequest approve(Long requestId, User approver, String comment) {
    ApprovalRequest request = findRequest(requestId);

    // Validate approver hasn't already approved
    if (hasAlreadyApproved(requestId, approver.getId())) {
        throw new IllegalStateException("Already approved");
    }

    // Record approval
    ApprovalRecord record = new ApprovalRecord(request, approver, true, comment);
    approvalRecordRepository.save(record);

    // Check if all requirements met
    ApprovalRule rule = approvalRuleService.findApplicableRule(request);
    if (allRequirementsMet(request, rule)) {
        applyChange(request);
        request.setStatus(ApprovalStatus.APPROVED);
        notificationService.sendApprovalCompletedNotification(request);
    } else {
        request.setStatus(ApprovalStatus.PARTIALLY_APPROVED);
    }

    return approvalRequestRepository.save(request);
}
```

**Verification**:
- Write unit tests for ApprovalRuleService (condition evaluation)
- Write unit tests for ApprovalService (state transitions)
- Tests run with `./mvnw test`

---

### Phase 6: Create REST Controllers

**Goal**: Expose approval workflow via REST API

**File to Create**:
`src/main/java/com/example/todolist/controller/ApprovalRequestController.java`

**Endpoints** (see contracts/approval-api.yaml for full spec):
- `POST /api/approval-requests` - Submit request
- `GET /api/approval-requests` - List requests
- `GET /api/approval-requests/{id}` - Get request details with diff
- `POST /api/approval-requests/{id}/approve` - Approve request
- `POST /api/approval-requests/{id}/reject` - Reject request
- `POST /api/approval-requests/{id}/withdraw` - Withdraw request
- `GET /api/approval-requests/pending-for-me` - Get my pending approvals

**Example**:
```java
@RestController
@RequestMapping("/api/approval-requests")
public class ApprovalRequestController {
    @Autowired
    private ApprovalService approvalService;

    @PostMapping
    public ResponseEntity<ApprovalRequestResponse> submitRequest(
            @RequestBody SubmitApprovalRequest request,
            @AuthenticationPrincipal User currentUser) {

        ApprovalRequest created = approvalService.submitApprovalRequest(
            request.getTargetItemType(),
            request.getTargetItemId(),
            request.getOperation(),
            request.getRequestedData(),
            currentUser
        );

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(toResponse(created));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApprovalRequestResponse> approve(
            @PathVariable Long id,
            @RequestBody(required = false) ApprovalDecisionRequest decision,
            @AuthenticationPrincipal User currentUser) {

        String comment = decision != null ? decision.getComment() : null;
        ApprovalRequest approved = approvalService.approve(id, currentUser, comment);
        return ResponseEntity.ok(toResponse(approved));
    }

    // ... other endpoints
}
```

**Verification**:
- API tests with MockMvc (optional)
- Manual testing with curl/Postman

---

### Phase 7: Integration with Todo/Invoice Controllers

**Goal**: Route create/update/delete operations through approval workflow

**Files to Modify**:
1. `src/main/java/com/example/todolist/controller/TodoController.java`
2. `src/main/java/com/example/todolist/controller/InvoiceController.java`

**Pattern**:
```java
@PostMapping("/todos")
public ResponseEntity<?> createTodo(@RequestBody TodoCreateRequest request, @AuthenticationPrincipal User user) {

    // Check if approval required
    ApprovalRule rule = approvalRuleService.findApplicableRule("TODO", RequestOperation.CREATE, request.toMap());

    if (rule == null) {
        // No approval required (e.g., level=LOW), execute immediately
        Todo created = todoService.create(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    } else {
        // Approval required, create approval request instead
        ApprovalRequest approvalRequest = approvalService.submitApprovalRequest(
            "TODO", null, RequestOperation.CREATE, toJson(request), user
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(approvalRequest);
    }
}
```

**Verification**:
- Attempting to create high-level Todo returns approval request (202 Accepted)
- Attempting to create low-level Todo creates immediately (201 Created)

---

## Testing Workflow

### Manual Test Scenario 1: Submit and Approve Todo Update Request

**Setup**:
1. Create users: admin (ADMIN), manager (MANAGER), user (USER)
2. Create a Todo with level=MEDIUM owned by user

**Steps**:
```bash
# 1. User submits update request (medium level -> high level)
curl -X POST http://localhost:8080/api/approval-requests \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <user-token>" \
  -d '{
    "targetItemType": "TODO",
    "targetItemId": 1,
    "operation": "UPDATE",
    "requestedData": {
      "level": "HIGH",
      "title": "Updated title"
    }
  }'

# Response: 201 Created
# {
#   "id": 1,
#   "status": "PENDING",
#   "targetItemType": "TODO",
#   ...
# }

# 2. Manager approves (HIGH requires both ADMIN and MANAGER, so this is partial)
curl -X POST http://localhost:8080/api/approval-requests/1/approve \
  -H "Authorization: Bearer <manager-token>" \
  -d '{"comment": "Looks good"}'

# Response: 200 OK, status: "PARTIALLY_APPROVED"

# 3. Admin approves (now all requirements met)
curl -X POST http://localhost:8080/api/approval-requests/1/approve \
  -H "Authorization: Bearer <admin-token>" \
  -d '{"comment": "Approved"}'

# Response: 200 OK, status: "APPROVED"
# Console output: "EMAIL: To=user, Subject=Approval Request #1 Approved..."

# 4. Verify Todo was updated
curl http://localhost:8080/api/todos/1 \
  -H "Authorization: Bearer <user-token>"

# Response shows level=HIGH, title="Updated title"
```

**Expected Results**:
- ✅ Request created with PENDING status
- ✅ Manager approval changes status to PARTIALLY_APPROVED
- ✅ Admin approval changes status to APPROVED
- ✅ Todo level and title updated
- ✅ Console shows email notification

---

### Manual Test Scenario 2: Item Locking

**Steps**:
```bash
# 1. User submits update request for Todo #1
curl -X POST http://localhost:8080/api/approval-requests \
  -H "Authorization: Bearer <user-token>" \
  -d '{
    "targetItemType": "TODO",
    "targetItemId": 1,
    "operation": "UPDATE",
    "requestedData": {"title": "First update"}
  }'

# 2. User tries to submit another update request for same Todo
curl -X POST http://localhost:8080/api/approval-requests \
  -H "Authorization: Bearer <user-token>" \
  -d '{
    "targetItemType": "TODO",
    "targetItemId": 1,
    "operation": "DELETE"
  }'

# Response: 409 Conflict
# {
#   "message": "Item is locked for review. An active approval request exists."
# }
```

**Expected Results**:
- ✅ First request succeeds
- ✅ Second request fails with 409 Conflict
- ✅ Error message indicates item is locked

---

### Manual Test Scenario 3: Withdrawal

**Steps**:
```bash
# 1. User submits request
curl -X POST http://localhost:8080/api/approval-requests \
  -H "Authorization: Bearer <user-token>" \
  -d '{...}'

# Response: id=2, status=PENDING

# 2. User withdraws request
curl -X POST http://localhost:8080/api/approval-requests/2/withdraw \
  -H "Authorization: Bearer <user-token>"

# Response: 200 OK, status="WITHDRAWN"

# 3. Verify item is unlocked - can now submit new request
curl -X POST http://localhost:8080/api/approval-requests \
  -H "Authorization: Bearer <user-token>" \
  -d '{
    "targetItemType": "TODO",
    "targetItemId": 1,
    "operation": "UPDATE",
    "requestedData": {"title": "New update"}
  }'

# Response: 201 Created (succeeds because previous request was withdrawn)
```

**Expected Results**:
- ✅ Withdrawal succeeds for PENDING request
- ✅ Status changes to WITHDRAWN
- ✅ Item unlocked (new request can be created)

---

## Common Issues and Solutions

### Issue: Liquibase migration fails with "relation already exists"

**Solution**:
- Check if tables already exist: `\dt approval*` in psql
- If testing, drop tables and re-run migration
- Or use `make rollback` to undo last migration

### Issue: JSONB column errors in PostgreSQL

**Solution**:
- Verify PostgreSQL version >= 9.4 (JSONB support)
- Check column definition in migration: `columnDefinition = "JSONB"`

### Issue: ApprovalRequest.requestedData is null when it shouldn't be

**Solution**:
- Ensure JSON serialization in controller uses ObjectMapper
- Check Content-Type header is `application/json`
- Verify Jackson is on classpath (included with Spring Boot Web)

### Issue: Unique constraint violation on approval_records

**Solution**:
- User trying to approve twice
- Add check in service: `if (hasAlreadyApproved(...)) throw exception`

### Issue: Approval rules not matching correctly

**Solution**:
- Check condition string parsing in ApprovalRuleService
- Verify priority ordering (higher priority = more specific)
- Ensure item data includes all fields needed for condition evaluation

---

## Next Steps

After implementing the approval workflow:

1. **Add UI Integration**: Build frontend components for approval request management
2. **Real Email Service**: Replace PrintNotificationService with JavaMailSender implementation
3. **Advanced Rules**: Extend ApprovalRule to support more complex conditions (ranges, regex)
4. **Audit Trail**: Add logging/events for all approval actions
5. **Metrics**: Track approval times, rejection rates, bottlenecks
6. **Extend to New Item Types**: Add day-off requests, purchase orders, etc.

---

## Reference Documentation

- [Spec](spec.md) - Feature requirements
- [Data Model](data-model.md) - Detailed entity design
- [API Contract](contracts/approval-api.yaml) - OpenAPI specification
- [Research](research.md) - Technical decisions and rationale

---

**Last Updated**: 2025-12-27
**Status**: Ready for implementation
