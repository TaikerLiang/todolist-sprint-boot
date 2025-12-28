package com.example.todolist.service;

import com.example.todolist.model.ApprovalRequest;
import com.example.todolist.model.User;

/**
 * Service interface for sending notifications.
 * Initially implemented with print statements, can be replaced with email/SMS later.
 */
public interface NotificationService {

    /**
     * Notify approvers that a new approval request has been created
     */
    void notifyApprovalRequested(ApprovalRequest approvalRequest, Iterable<User> approvers);

    /**
     * Notify requester that their request has been approved
     */
    void notifyRequestApproved(ApprovalRequest approvalRequest);

    /**
     * Notify requester that their request has been rejected
     */
    void notifyRequestRejected(ApprovalRequest approvalRequest, String reason);

    /**
     * Notify approvers that a request has been withdrawn
     */
    void notifyRequestWithdrawn(ApprovalRequest approvalRequest, Iterable<User> approvers);

    /**
     * Notify requester that an approver has responded to their request
     */
    void notifyApproverResponded(ApprovalRequest approvalRequest, User approver, boolean approved);
}
