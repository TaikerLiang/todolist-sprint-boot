# Research: General Approval Workflow

**Feature**: 002-approval-workflow
**Date**: 2025-12-27
**Purpose**: Resolve technical unknowns and establish design patterns for approval workflow implementation

## Research Areas

### 1. Polymorphic References for Approvable Items

**Question**: How to reference different item types (Todo, Invoice, future items) in ApprovalRequest without using JPA inheritance or reflection?

**Decision**: Use string-based item type discriminator with Long item ID

**Rationale**:
- Avoids JPA inheritance complexity (SINGLE_TABLE, JOINED, TABLE_PER_CLASS all have trade-offs)
- Simple to extend to new item types (just add new string constant)
- Allows querying approval requests by item type efficiently
- Pattern commonly used in polymorphic scenarios (e.g., Hibernate's @Any annotation concept, but simpler)

**Implementation**:
```java
@Entity
public class ApprovalRequest {
    @Column(nullable = false)
    private String targetItemType;  // "TODO", "INVOICE", "DAY_OFF_REQUEST", etc.

    @Column(nullable = false)
    private Long targetItemId;      // ID of the target item (nullable for CREATE operations)

    // Transient helper methods
    public boolean isForTodo() { return "TODO".equals(targetItemType); }
    public boolean isForInvoice() { return "INVOICE".equals(targetItemType); }
}
```

**Alternatives Considered**:
- JPA @Inheritance: Complex, harder to extend, requires schema changes for new types
- @Any annotation: Hibernate-specific, overly complex for our needs
- Separate tables per item type: Duplicates approval logic, not DRY

---

### 2. Storing Requested Data for CREATE and UPDATE Operations

**Question**: How to store the requested data (JSON) for create/update operations in a type-safe yet flexible way?

**Decision**: Use PostgreSQL JSONB column with String field in Java

**Rationale**:
- JSONB provides efficient storage and querying of structured data
- Flexible enough to handle different item types without schema changes
- Can index on JSONB fields if needed for performance
- Java String field keeps entity simple; serialization/deserialization handled in service layer

**Implementation**:
```java
@Entity
public class ApprovalRequest {
    @Column(columnDefinition = "JSONB")
    private String requestedData;  // JSON string of the data to be created/updated

    // Service layer handles ObjectMapper serialization/deserialization
}
```

**Alternatives Considered**:
- Separate columns for each possible field: Inflexible, breaks for new item types
- @ElementCollection with Map: JPA complexity, harder to query
- Separate request detail tables: Over-engineered for this use case

---

### 3. Item Locking Mechanism to Prevent Concurrent Update/Delete Requests

**Question**: How to ensure only one update/delete approval request exists for an item at a time?

**Decision**: Database unique constraint + application-level check

**Rationale**:
- Database constraint provides fail-safe against race conditions
- Application-level check provides better error messages for users
- Avoids distributed locking complexity (Redis, etc.)
- Leverages PostgreSQL's ACID guarantees

**Implementation**:
```sql
-- Unique partial index: allows multiple non-pending requests, but only one pending/partial
CREATE UNIQUE INDEX idx_one_active_request_per_item
ON approval_requests (target_item_type, target_item_id)
WHERE status IN ('PENDING', 'PARTIALLY_APPROVED');
```

```java
// Application-level check in ApprovalService
public ApprovalRequest submitRequest(...) {
    boolean hasActiveRequest = approvalRequestRepository
        .existsByTargetItemTypeAndTargetItemIdAndStatusIn(
            itemType, itemId, List.of(PENDING, PARTIALLY_APPROVED));

    if (hasActiveRequest) {
        throw new ItemLockedException("Item is locked for review");
    }
    // Proceed with request creation
}
```

**Alternatives Considered**:
- Explicit lock column on Todo/Invoice: Requires modifying every approvable entity
- Distributed locks (Redis): Over-engineered for current scale
- Optimistic locking with @Version: Doesn't prevent creating duplicate requests

---

### 4. Approval Rule Storage and Evaluation

**Question**: How to store and evaluate approval rules (AND vs OR, role requirements) in a configurable way?

**Decision**: Database-backed approval rules with enum-based rule types

**Rationale**:
- Rules can be changed without code deployment
- Simple enum-based rule types cover current requirements (ALL_REQUIRED, ANY_REQUIRED)
- JSON array stores required roles for maximum flexibility
- Query-friendly structure for finding applicable rules

**Implementation**:
```java
@Entity
public class ApprovalRule {
    @Column(nullable = false)
    private String targetItemType;  // "TODO", "INVOICE"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestOperation operation;  // CREATE, UPDATE, DELETE

    @Column(columnDefinition = "VARCHAR(255)")
    private String condition;  // e.g., "level=HIGH" for Todo, null for Invoice

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleType ruleType;  // ALL_REQUIRED (AND), ANY_REQUIRED (OR)

    @Column(columnDefinition = "JSONB", nullable = false)
    private String requiredRoles;  // JSON array: ["ADMIN", "MANAGER"]
}
```

**Alternatives Considered**:
- Hard-coded rules in service: Not configurable, requires deployment for changes
- Complex rule engine (Drools): Over-engineered for simple role-based rules
- Separate rule tables per item type: Violates DRY, harder to maintain

---

### 5. Diff Generation for Update Requests

**Question**: How to generate and store diffs showing old vs new values for update requests?

**Decision**: Compute diff on-demand using current item state + requested data

**Rationale**:
- Avoids storing redundant data (old values already in database)
- Always shows current state vs requested state (even if item changed after request)
- Simple Map-based comparison in service layer
- JSON diff format easy to display in UI

**Implementation**:
```java
public class DiffService {
    public Map<String, FieldDiff> generateDiff(String itemType, Long itemId, String requestedDataJson) {
        // 1. Fetch current item from database
        // 2. Deserialize requestedDataJson
        // 3. Compare field-by-field
        // 4. Return Map<fieldName, FieldDiff(oldValue, newValue)>
    }
}

public class FieldDiff {
    private Object oldValue;
    private Object newValue;
    // Getters, equals, hashCode
}
```

**Alternatives Considered**:
- Store diff at request creation time: Becomes stale if item changes, wastes storage
- Use JSON Patch (RFC 6902): Overly complex for simple field comparisons
- No diff, just show new values: Poor reviewer experience

---

### 6. Email Notification Mocking Strategy

**Question**: How to mock email sending in a way that's easy to replace with real email service later?

**Decision**: NotificationService interface with PrintNotificationService implementation

**Rationale**:
- Clean separation via interface allows swapping implementations
- Print implementation useful for testing and development
- Easy to add JavaMailSender implementation later
- Can log notifications for audit trail

**Implementation**:
```java
public interface NotificationService {
    void sendApprovalCompletedNotification(ApprovalRequest request);
    void sendRejectionNotification(ApprovalRequest request, String reason);
}

@Service
public class PrintNotificationService implements NotificationService {
    private static final Logger log = LoggerFactory.getLogger(PrintNotificationService.class);

    @Override
    public void sendApprovalCompletedNotification(ApprovalRequest request) {
        String message = String.format(
            "EMAIL: To=%s, Subject=Approval Request #%d Approved, Body=Your request has been approved...",
            request.getRequester().getUsername(), request.getId()
        );
        System.out.println(message);
        log.info("Notification sent: {}", message);
    }
}
```

**Alternatives Considered**:
- Direct System.out.println in service: Hard to test, no audit trail
- No abstraction, implement email directly: Violates open/closed principle
- Event-based notifications (Spring Events): Over-engineered for current needs

---

### 7. Handling Approval Logic for AND vs OR Rules

**Question**: How to efficiently evaluate approval requirements (all approvers vs any approver)?

**Decision**: Count-based evaluation with role tracking in ApprovalRecord

**Rationale**:
- Simple counting logic: compare counts vs required counts
- Efficient database queries using GROUP BY and COUNT
- Works for both ALL_REQUIRED and ANY_REQUIRED rule types
- Easy to extend to more complex rules (e.g., "2 out of 3 roles")

**Implementation**:
```java
public class ApprovalService {
    public ApprovalRequest approve(Long requestId, User approver, String comment) {
        ApprovalRequest request = findRequest(requestId);
        ApprovalRule rule = findApplicableRule(request);

        // Record this approval
        ApprovalRecord record = new ApprovalRecord(request, approver, true, comment);
        approvalRecordRepository.save(record);

        // Check if all requirements met
        Set<Role> requiredRoles = parseRequiredRoles(rule.getRequiredRoles());
        Set<Role> approvedRoles = approvalRecordRepository
            .findDistinctApproverRolesByRequestAndDecision(requestId, true);

        if (rule.getRuleType() == RuleType.ALL_REQUIRED) {
            if (approvedRoles.containsAll(requiredRoles)) {
                applyChange(request);
                request.setStatus(APPROVED);
            } else {
                request.setStatus(PARTIALLY_APPROVED);
            }
        } else if (rule.getRuleType() == RuleType.ANY_REQUIRED) {
            boolean hasAnyRequired = requiredRoles.stream()
                .anyMatch(approvedRoles::contains);
            if (hasAnyRequired) {
                applyChange(request);
                request.setStatus(APPROVED);
            }
        }

        return approvalRequestRepository.save(request);
    }
}
```

**Alternatives Considered**:
- State machine library: Over-engineered for simple status transitions
- Pre-calculate required approval count: Doesn't handle role-based requirements
- Workflow engine (Activiti, Camunda): Massive overkill for this use case

---

## Summary

All technical unknowns resolved. Implementation approach:
- Polymorphic references via string type discriminator
- JSONB for flexible requested data storage
- Database unique constraint for item locking
- Database-backed approval rules with simple enum-based evaluation
- On-demand diff generation
- Interface-based notification service with print implementation
- Count-based approval logic with role tracking

No external dependencies required beyond existing Spring Boot, JPA, and PostgreSQL stack. Ready to proceed to Phase 1 (Data Model and Contracts).
