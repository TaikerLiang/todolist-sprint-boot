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

    @Column(name = "target_item_type", nullable = false)
    private String targetItemType;  // "TODO", "INVOICE", etc.

    @Column(name = "target_item_id")
    private Long targetItemId;  // Nullable for CREATE operations

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RequestOperation operation;

    @Column(name = "requested_data", columnDefinition = "JSONB")
    private String requestedData;  // Nullable for DELETE operations

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // Helper methods
    public boolean isForTodo() {
        return "TODO".equals(targetItemType);
    }

    public boolean isForInvoice() {
        return "INVOICE".equals(targetItemType);
    }

    public boolean isPending() {
        return status == ApprovalStatus.PENDING || status == ApprovalStatus.PARTIALLY_APPROVED;
    }

    public boolean isTerminal() {
        return status == ApprovalStatus.APPROVED ||
               status == ApprovalStatus.REJECTED ||
               status == ApprovalStatus.WITHDRAWN;
    }
}
