# Quickstart Guide: Approval Workflow Implementation

**Feature**: Multi-Level Approval Workflow
**Date**: 2025-12-16
**Audience**: Developers implementing this feature

## Overview

This guide provides step-by-step instructions for implementing the multi-level approval workflow feature. The implementation follows Spring Boot's layered architecture and uses existing patterns from the todolist application.

## Prerequisites

- Java 21 installed
- PostgreSQL database running
- Existing todolist application set up
- Maven 3.9.11+ installed
- Familiarity with Spring Boot, JPA, and Liquibase

## Implementation Steps

### Step 1: Create Model Entities

Create the following new entity classes in `src/main/java/com/example/todolist/model/`:

#### 1.1 ApprovalStatus.java (Enum)
```java
package com.example.todolist.model;

public enum ApprovalStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
```

#### 1.2 ItemType.java (Enum)
```java
package com.example.todolist.model;

public enum ItemType {
    TODO,
    INVOICE
}
```

#### 1.3 DecisionType.java (Enum)
```java
package com.example.todolist.model;

public enum DecisionType {
    APPROVED,
    REJECTED
}
```

#### 1.4 NotificationType.java (Enum)
```java
package com.example.todolist.model;

public enum NotificationType {
    APPROVAL_REQUESTED,
    APPROVED,
    REJECTED
}
```

#### 1.5 ApprovalRequest.java
```java
package com.example.todolist.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "approval_requests")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemType itemType;

    @Column(nullable = false)
    private Long itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'PENDING'")
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
```

#### 1.6 ApprovalRecord.java
```java
package com.example.todolist.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "approval_records")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id", nullable = false)
    private ApprovalRequest approvalRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionType decision;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    @Version
    private Long version;
}
```

#### 1.7 ApprovalRule.java
```java
package com.example.todolist.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "approval_rules")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemType itemType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Level priorityLevel;

    @Column(nullable = false, length = 255)
    private String requiredRoles; // Comma-separated: "MANAGER,ADMIN"

    @Column(nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();
}
```

#### 1.8 Notification.java
```java
package com.example.todolist.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id", nullable = false)
    private ApprovalRequest approvalRequest;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private Boolean readStatus = false;

    @Column(nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();
}
```

### Step 2: Modify Existing Entities

#### 2.1 Todo.java - Add status field
```java
// Add this field to the Todo entity
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'APPROVED'")
private ApprovalStatus status = ApprovalStatus.APPROVED;
```

#### 2.2 Invoice.java - Add status field
```java
// Add this field to the Invoice entity
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'APPROVED'")
private ApprovalStatus status = ApprovalStatus.APPROVED;
```

### Step 3: Create Repositories

Create repository interfaces in `src/main/java/com/example/todolist/repository/`:

#### 3.1 ApprovalRequestRepository.java
```java
package com.example.todolist.repository;

import com.example.todolist.model.ApprovalRequest;
import com.example.todolist.model.ApprovalStatus;
import com.example.todolist.model.ItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    Page<ApprovalRequest> findByStatus(ApprovalStatus status, Pageable pageable);

    Page<ApprovalRequest> findByCreatorId(Long creatorId, Pageable pageable);

    Page<ApprovalRequest> findByCreatorIdAndStatus(Long creatorId, ApprovalStatus status, Pageable pageable);

    Optional<ApprovalRequest> findByItemTypeAndItemIdAndStatus(ItemType itemType, Long itemId, ApprovalStatus status);
}
```

#### 3.2 ApprovalRecordRepository.java
```java
package com.example.todolist.repository;

import com.example.todolist.model.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {
    List<ApprovalRecord> findByApprovalRequestIdOrderByCreatedAtDesc(Long approvalRequestId);
}
```

#### 3.3 ApprovalRuleRepository.java
```java
package com.example.todolist.repository;

import com.example.todolist.model.ApprovalRule;
import com.example.todolist.model.ItemType;
import com.example.todolist.model.Level;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {
    Optional<ApprovalRule> findByItemTypeAndPriorityLevel(ItemType itemType, Level priorityLevel);
}
```

#### 3.4 NotificationRepository.java
```java
package com.example.todolist.repository;

import com.example.todolist.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadStatusOrderByCreatedAtDesc(Long recipientId, Boolean readStatus, Pageable pageable);
}
```

### Step 4: Generate Liquibase Migration

```bash
# Generate the migration
make makemigration NAME=approval_workflow

# Review the generated migration file
cat src/main/resources/db/changelog/changes/0007_approval_workflow.yaml

# Apply the migration
make migrate

# Verify migration succeeded
make showmigrations
```

### Step 5: Implement Service Layer

Implementation order:
1. **ApprovalRuleService** - Rule evaluation logic
2. **NotificationService** - Notification creation (stub for now)
3. **ApprovalService** - Core workflow logic
4. **Modify TodoService and InvoiceService** - Integration

See `data-model.md` for detailed entity specifications and `research.md` for design patterns to follow.

### Step 6: Implement Controller Layer

Create `ApprovalController.java` implementing the API contract defined in `contracts/approval-api.yaml`.

Key endpoints:
- `GET /api/approvals` - List pending approvals
- `GET /api/approvals/{id}` - Get approval details
- `POST /api/approvals/{id}/approve` - Approve request
- `POST /api/approvals/{id}/reject` - Reject request
- `POST /api/approvals/{id}/withdraw` - Withdraw request

### Step 7: Testing

Follow Test-First approach (per Constitution Section V):

1. Write service layer unit tests first
2. Verify tests fail
3. Implement service methods
4. Verify tests pass
5. Write controller tests
6. Implement controller methods
7. Write integration tests

## Development Workflow

### Creating a Test Todo/Invoice

```bash
# Low-level item (no approval needed)
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title":"Test Todo","description":"Test","level":"LOW","userId":1}'

# Medium-level item (requires manager approval)
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title":"Test Todo","description":"Test","level":"MEDIUM","userId":1}'
```

### Testing Approval Flow

```bash
# 1. Manager lists pending approvals
curl -X GET http://localhost:8080/api/approvals \
  -H "Authorization: Bearer {manager-token}"

# 2. Manager approves request
curl -X POST http://localhost:8080/api/approvals/1/approve \
  -H "Authorization: Bearer {manager-token}" \
  -H "Content-Type: application/json" \
  -d '{"comments":"Looks good"}'

# 3. Creator checks their requests
curl -X GET http://localhost:8080/api/approvals/my-requests \
  -H "Authorization: Bearer {user-token}"
```

## Troubleshooting

### Migration Issues

```bash
# Check current migration status
make showmigrations

# Rollback if needed
make rollback COUNT=1

# Reapply
make migrate
```

### Database Inspection

```bash
# Connect to PostgreSQL
psql -U default_user -d todolist

# Inspect tables
\dt

# Check approval rules
SELECT * FROM approval_rules;

# Check pending approvals
SELECT * FROM approval_requests WHERE status = 'PENDING';
```

## Key Design Patterns

1. **Optimistic Locking**: Use `@Version` on ApprovalRequest to handle concurrent approvals
2. **Event-Driven Notifications**: Use Spring Application Events for decoupled notification system
3. **State Machine**: Validate status transitions in service layer
4. **Configurable Rules**: Store approval rules in database for runtime configuration

## Next Steps

After implementation:
1. Run `/speckit.tasks` to generate detailed implementation tasks
2. Follow task priorities (P1 → P2 → P3) from spec.md
3. Test each user story independently
4. Create API documentation from OpenAPI contract
5. Add monitoring/metrics for approval turnaround time

## References

- **Feature Spec**: [spec.md](./spec.md)
- **Implementation Plan**: [plan.md](./plan.md)
- **Research Decisions**: [research.md](./research.md)
- **Data Model**: [data-model.md](./data-model.md)
- **API Contract**: [contracts/approval-api.yaml](./contracts/approval-api.yaml)
- **Constitution**: [/.specify/memory/constitution.md](/.specify/memory/constitution.md)
