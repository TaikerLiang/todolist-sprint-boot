package com.example.todolist.repository;

import com.example.todolist.model.ApprovalRequest;
import com.example.todolist.model.ApprovalStatus;
import com.example.todolist.model.RequestOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    /**
     * Find all approval requests for a specific item (by type and ID)
     */
    List<ApprovalRequest> findByTargetItemTypeAndTargetItemId(String targetItemType, Long targetItemId);

    /**
     * Find active (PENDING or PARTIALLY_APPROVED) request for a specific item
     * Due to the unique partial index, there can be at most one active request per item
     */
    Optional<ApprovalRequest> findByTargetItemTypeAndTargetItemIdAndStatusIn(
            String targetItemType,
            Long targetItemId,
            List<ApprovalStatus> statuses
    );

    /**
     * Find all requests by requester
     */
    List<ApprovalRequest> findByRequesterId(Long requesterId);

    /**
     * Find all requests by status
     */
    List<ApprovalRequest> findByStatus(ApprovalStatus status);

    /**
     * Find all requests by requester and status
     */
    List<ApprovalRequest> findByRequesterIdAndStatus(Long requesterId, ApprovalStatus status);

    /**
     * Find all requests for a specific item type and operation
     */
    List<ApprovalRequest> findByTargetItemTypeAndOperation(String targetItemType, RequestOperation operation);
}
