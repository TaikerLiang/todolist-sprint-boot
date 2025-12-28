package com.example.todolist.service;

import com.example.todolist.model.ApprovalRequest;
import com.example.todolist.model.User;
import org.springframework.stereotype.Service;

import java.util.stream.StreamSupport;

/**
 * Mock implementation of NotificationService that prints to console.
 * In production, replace with actual email/SMS implementation.
 */
@Service
public class PrintNotificationService implements NotificationService {

    @Override
    public void notifyApprovalRequested(ApprovalRequest approvalRequest, Iterable<User> approvers) {
        System.out.println("=== EMAIL NOTIFICATION ===");
        System.out.println("To: " + formatRecipients(approvers));
        System.out.println("Subject: New Approval Request #" + approvalRequest.getId());
        System.out.println("Body:");
        System.out.println("  A new approval request requires your attention:");
        System.out.println("  - Request ID: " + approvalRequest.getId());
        System.out.println("  - Item Type: " + approvalRequest.getTargetItemType());
        System.out.println("  - Operation: " + approvalRequest.getOperation());
        System.out.println("  - Requester: " + approvalRequest.getRequester().getUsername());
        System.out.println("  - Status: " + approvalRequest.getStatus());
        System.out.println("  Please review and approve/reject this request.");
        System.out.println("==========================\n");
    }

    @Override
    public void notifyRequestApproved(ApprovalRequest approvalRequest) {
        System.out.println("=== EMAIL NOTIFICATION ===");
        System.out.println("To: " + approvalRequest.getRequester().getUsername());
        System.out.println("Subject: Your Request #" + approvalRequest.getId() + " Has Been Approved");
        System.out.println("Body:");
        System.out.println("  Your approval request has been APPROVED:");
        System.out.println("  - Request ID: " + approvalRequest.getId());
        System.out.println("  - Item Type: " + approvalRequest.getTargetItemType());
        System.out.println("  - Operation: " + approvalRequest.getOperation());
        System.out.println("  The requested operation will now be executed.");
        System.out.println("==========================\n");
    }

    @Override
    public void notifyRequestRejected(ApprovalRequest approvalRequest, String reason) {
        System.out.println("=== EMAIL NOTIFICATION ===");
        System.out.println("To: " + approvalRequest.getRequester().getUsername());
        System.out.println("Subject: Your Request #" + approvalRequest.getId() + " Has Been Rejected");
        System.out.println("Body:");
        System.out.println("  Your approval request has been REJECTED:");
        System.out.println("  - Request ID: " + approvalRequest.getId());
        System.out.println("  - Item Type: " + approvalRequest.getTargetItemType());
        System.out.println("  - Operation: " + approvalRequest.getOperation());
        if (reason != null && !reason.isEmpty()) {
            System.out.println("  - Reason: " + reason);
        }
        System.out.println("==========================\n");
    }

    @Override
    public void notifyRequestWithdrawn(ApprovalRequest approvalRequest, Iterable<User> approvers) {
        System.out.println("=== EMAIL NOTIFICATION ===");
        System.out.println("To: " + formatRecipients(approvers));
        System.out.println("Subject: Request #" + approvalRequest.getId() + " Has Been Withdrawn");
        System.out.println("Body:");
        System.out.println("  An approval request has been withdrawn by the requester:");
        System.out.println("  - Request ID: " + approvalRequest.getId());
        System.out.println("  - Item Type: " + approvalRequest.getTargetItemType());
        System.out.println("  - Operation: " + approvalRequest.getOperation());
        System.out.println("  - Requester: " + approvalRequest.getRequester().getUsername());
        System.out.println("  No further action is required.");
        System.out.println("==========================\n");
    }

    @Override
    public void notifyApproverResponded(ApprovalRequest approvalRequest, User approver, boolean approved) {
        String decision = approved ? "APPROVED" : "REJECTED";
        System.out.println("=== EMAIL NOTIFICATION ===");
        System.out.println("To: " + approvalRequest.getRequester().getUsername());
        System.out.println("Subject: " + approver.getUsername() + " " + decision + " Your Request #" + approvalRequest.getId());
        System.out.println("Body:");
        System.out.println("  " + approver.getUsername() + " has " + decision.toLowerCase() + " your request:");
        System.out.println("  - Request ID: " + approvalRequest.getId());
        System.out.println("  - Item Type: " + approvalRequest.getTargetItemType());
        System.out.println("  - Operation: " + approvalRequest.getOperation());
        System.out.println("  - Current Status: " + approvalRequest.getStatus());
        System.out.println("==========================\n");
    }

    private String formatRecipients(Iterable<User> users) {
        return StreamSupport.stream(users.spliterator(), false)
                .map(User::getUsername)
                .reduce((a, b) -> a + ", " + b)
                .orElse("(no recipients)");
    }
}
