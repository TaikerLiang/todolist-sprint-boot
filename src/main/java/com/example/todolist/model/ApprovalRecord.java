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

    @Column(nullable = false)
    private Boolean decision;  // true = approve, false = reject

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    public ApprovalRecord(ApprovalRequest approvalRequest, User approver, Boolean decision, String comment) {
        this.approvalRequest = approvalRequest;
        this.approver = approver;
        this.decision = decision;
        this.comment = comment;
    }
}
