package com.example.todolist.repository;

import com.example.todolist.model.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

    /**
     * Find all approval records for a specific approval request
     */
    List<ApprovalRecord> findByApprovalRequestId(Long approvalRequestId);

    /**
     * Find approval record by request ID and approver ID
     * Due to unique constraint, there can be at most one record per (request, approver) pair
     */
    Optional<ApprovalRecord> findByApprovalRequestIdAndApproverId(Long approvalRequestId, Long approverId);

    /**
     * Find all approval records by approver
     */
    List<ApprovalRecord> findByApproverId(Long approverId);

    /**
     * Count approvals (decision = true) for a specific approval request
     */
    @Query("SELECT COUNT(ar) FROM ApprovalRecord ar WHERE ar.approvalRequest.id = :requestId AND ar.decision = true")
    long countApprovalsByRequestId(@Param("requestId") Long requestId);

    /**
     * Count rejections (decision = false) for a specific approval request
     */
    @Query("SELECT COUNT(ar) FROM ApprovalRecord ar WHERE ar.approvalRequest.id = :requestId AND ar.decision = false")
    long countRejectionsByRequestId(@Param("requestId") Long requestId);

    /**
     * Check if a specific user has already approved/rejected a request
     */
    boolean existsByApprovalRequestIdAndApproverId(Long approvalRequestId, Long approverId);
}
