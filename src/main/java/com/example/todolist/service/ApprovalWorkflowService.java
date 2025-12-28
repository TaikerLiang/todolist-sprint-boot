package com.example.todolist.service;

import com.example.todolist.model.*;
import com.example.todolist.repository.ApprovalRecordRepository;
import com.example.todolist.repository.ApprovalRequestRepository;
import com.example.todolist.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core service for managing the approval workflow.
 * Handles creating requests, approving/rejecting, withdrawing, and executing operations.
 */
@Service
public class ApprovalWorkflowService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalRecordRepository approvalRecordRepository;
    private final UserRepository userRepository;
    private final ApprovalRuleService approvalRuleService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final TodoService todoService;
    private final InvoiceService invoiceService;

    @Autowired
    public ApprovalWorkflowService(
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalRecordRepository approvalRecordRepository,
            UserRepository userRepository,
            ApprovalRuleService approvalRuleService,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            @Lazy TodoService todoService,
            @Lazy InvoiceService invoiceService
    ) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.approvalRecordRepository = approvalRecordRepository;
        this.userRepository = userRepository;
        this.approvalRuleService = approvalRuleService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.todoService = todoService;
        this.invoiceService = invoiceService;
    }

    /**
     * Check if an operation requires approval based on configured rules.
     *
     * @param itemType The item type (e.g., "TODO", "INVOICE")
     * @param operation The operation (CREATE, UPDATE, DELETE)
     * @param itemData The item data to evaluate
     * @return true if approval is required, false if operation can proceed immediately
     */
    public boolean requiresApproval(String itemType, RequestOperation operation, Map<String, Object> itemData) {
        return approvalRuleService.findMatchingRule(itemType, operation, itemData).isPresent();
    }

    /**
     * Create a new approval request.
     *
     * @param itemType The target item type
     * @param targetItemId The target item ID (null for CREATE operations)
     * @param operation The operation being requested
     * @param requestedData The data for the operation (as Map)
     * @param requesterId The ID of the user making the request
     * @return The created approval request
     * @throws IllegalStateException if there's already an active request for this item
     * @throws IllegalArgumentException if no approval rule matches or requester not found
     */
    @Transactional
    public ApprovalRequest createApprovalRequest(
            String itemType,
            Long targetItemId,
            RequestOperation operation,
            Map<String, Object> requestedData,
            Long requesterId
    ) {
        // Find the requester
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Requester not found: " + requesterId));

        // Check if approval rule exists
        Optional<ApprovalRule> ruleOpt = approvalRuleService.findMatchingRule(itemType, operation, requestedData);
        if (ruleOpt.isEmpty()) {
            throw new IllegalArgumentException(
                    "No approval rule found for " + itemType + " " + operation + ". Operation can proceed immediately."
            );
        }

        // Check if there's already an active request for this item (for UPDATE/DELETE)
        if (targetItemId != null) {
            Optional<ApprovalRequest> existingRequest = approvalRequestRepository
                    .findByTargetItemTypeAndTargetItemIdAndStatusIn(
                            itemType,
                            targetItemId,
                            List.of(ApprovalStatus.PENDING, ApprovalStatus.PARTIALLY_APPROVED)
                    );

            if (existingRequest.isPresent()) {
                throw new IllegalStateException(
                        "There is already an active approval request for this item: #" +
                                existingRequest.get().getId()
                );
            }
        }

        // Create the approval request
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetItemType(itemType);
        request.setTargetItemId(targetItemId);
        request.setOperation(operation);
        request.setRequestedData(serializeData(requestedData));
        request.setStatus(ApprovalStatus.PENDING);
        request.setRequester(requester);
        request.setCreatedAt(Instant.now());
        request.setUpdatedAt(Instant.now());

        request = approvalRequestRepository.save(request);

        // Find approvers based on the rule
        ApprovalRule rule = ruleOpt.get();
        List<Role> requiredRoles = new ArrayList<>();
        requiredRoles.addAll(approvalRuleService.getMandatoryRoles(rule));
        requiredRoles.addAll(approvalRuleService.getOptionalRoles(rule));

        List<User> approvers = userRepository.findByRoleIn(requiredRoles);

        // Send notifications to approvers
        notificationService.notifyApprovalRequested(request, approvers);

        return request;
    }

    /**
     * Submit an approval or rejection for a request.
     *
     * @param requestId The approval request ID
     * @param approverId The ID of the user submitting the decision
     * @param decision true for approve, false for reject
     * @param comment Optional comment explaining the decision
     * @return The updated approval request
     * @throws IllegalArgumentException if request or approver not found
     * @throws IllegalStateException if request is not in pending state or approver already responded
     */
    @Transactional
    public ApprovalRequest submitApproval(
            Long requestId,
            Long approverId,
            boolean decision,
            String comment
    ) {
        // Find the request
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + requestId));

        // Check if request is still pending
        if (!request.isPending()) {
            throw new IllegalStateException(
                    "Request #" + requestId + " is no longer pending (status: " + request.getStatus() + ")"
            );
        }

        // Find the approver
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("Approver not found: " + approverId));

        // Check if approver has already responded
        if (approvalRecordRepository.existsByApprovalRequestIdAndApproverId(requestId, approverId)) {
            throw new IllegalStateException("You have already submitted a decision for this request");
        }

        // Create the approval record
        ApprovalRecord record = new ApprovalRecord(request, approver, decision, comment);
        approvalRecordRepository.save(record);

        // If rejected, mark request as REJECTED immediately
        if (!decision) {
            request.setStatus(ApprovalStatus.REJECTED);
            request.setUpdatedAt(Instant.now());
            approvalRequestRepository.save(request);

            // Notify requester
            notificationService.notifyRequestRejected(request, comment);
            return request;
        }

        // If approved, check if all required approvals are satisfied
        Map<String, Object> requestedData = deserializeData(request.getRequestedData());
        Optional<ApprovalRule> ruleOpt = approvalRuleService.findMatchingRule(
                request.getTargetItemType(),
                request.getOperation(),
                requestedData
        );

        if (ruleOpt.isEmpty()) {
            throw new IllegalStateException("Approval rule no longer exists for this request");
        }

        ApprovalRule rule = ruleOpt.get();

        // Get all approval records for this request
        List<ApprovalRecord> allRecords = approvalRecordRepository.findByApprovalRequestId(requestId);
        List<Role> approvedRoles = allRecords.stream()
                .filter(ApprovalRecord::getDecision)
                .map(ar -> ar.getApprover().getRole())
                .distinct()
                .collect(Collectors.toList());

        // Check if rule is satisfied
        if (approvalRuleService.isRuleSatisfied(rule, approvedRoles)) {
            request.setStatus(ApprovalStatus.APPROVED);
            request.setUpdatedAt(Instant.now());
            approvalRequestRepository.save(request);

            // Execute the approved change
            try {
                executeApprovedChange(request);
                notificationService.notifyRequestApproved(request);
            } catch (Exception e) {
                // If execution fails, mark as rejected
                request.setStatus(ApprovalStatus.REJECTED);
                request.setUpdatedAt(Instant.now());
                approvalRequestRepository.save(request);
                notificationService.notifyRequestRejected(request, "Failed to execute change: " + e.getMessage());
                throw new IllegalStateException("Failed to execute approved change: " + e.getMessage(), e);
            }
        } else {
            request.setStatus(ApprovalStatus.PARTIALLY_APPROVED);
            notificationService.notifyApproverResponded(request, approver, true);
        }

        request.setUpdatedAt(Instant.now());
        return approvalRequestRepository.save(request);
    }

    /**
     * Withdraw a pending approval request.
     *
     * @param requestId The approval request ID
     * @param userId The ID of the user withdrawing (must be the requester)
     * @return The withdrawn approval request
     * @throws IllegalArgumentException if request not found
     * @throws IllegalStateException if request is not pending or user is not the requester
     */
    @Transactional
    public ApprovalRequest withdrawRequest(Long requestId, Long userId) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + requestId));

        if (!request.isPending()) {
            throw new IllegalStateException("Cannot withdraw a request that is not pending");
        }

        if (!request.getRequester().getId().equals(userId)) {
            throw new IllegalStateException("Only the requester can withdraw this request");
        }

        request.setStatus(ApprovalStatus.WITHDRAWN);
        request.setUpdatedAt(Instant.now());
        approvalRequestRepository.save(request);

        // Notify approvers that the request has been withdrawn
        Map<String, Object> requestedData = deserializeData(request.getRequestedData());
        Optional<ApprovalRule> ruleOpt = approvalRuleService.findMatchingRule(
                request.getTargetItemType(),
                request.getOperation(),
                requestedData
        );

        if (ruleOpt.isPresent()) {
            ApprovalRule rule = ruleOpt.get();
            List<Role> requiredRoles = new ArrayList<>();
            requiredRoles.addAll(approvalRuleService.getMandatoryRoles(rule));
            requiredRoles.addAll(approvalRuleService.getOptionalRoles(rule));
            List<User> approvers = userRepository.findByRoleIn(requiredRoles);
            notificationService.notifyRequestWithdrawn(request, approvers);
        }

        return request;
    }

    /**
     * Get all approval requests for a specific user (as requester)
     */
    public List<ApprovalRequest> getRequestsByRequester(Long requesterId) {
        return approvalRequestRepository.findByRequesterId(requesterId);
    }

    /**
     * Get all pending approval requests that a user can approve (based on their role)
     */
    public List<ApprovalRequest> getPendingRequestsForApprover(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<ApprovalRequest> allPending = approvalRequestRepository.findByStatus(ApprovalStatus.PENDING);
        allPending.addAll(approvalRequestRepository.findByStatus(ApprovalStatus.PARTIALLY_APPROVED));

        // Filter to only requests where user hasn't responded yet
        return allPending.stream()
                .filter(request -> !approvalRecordRepository.existsByApprovalRequestIdAndApproverId(
                        request.getId(), userId))
                .filter(request -> canUserApprove(request, user))
                .collect(Collectors.toList());
    }

    /**
     * Get approval request by ID
     */
    public Optional<ApprovalRequest> getRequestById(Long requestId) {
        return approvalRequestRepository.findById(requestId);
    }

    /**
     * Get all approval records for a specific request
     */
    public List<ApprovalRecord> getApprovalRecords(Long requestId) {
        return approvalRecordRepository.findByApprovalRequestId(requestId);
    }

    /**
     * Execute an approved change (CREATE/UPDATE/DELETE operation).
     * This method should be called when an approval request reaches APPROVED status.
     *
     * @param request The approved approval request
     * @return The result of the operation (created/updated entity or null for DELETE)
     * @throws IllegalStateException if request is not approved or operation fails
     */
    @Transactional
    public Object executeApprovedChange(ApprovalRequest request) {
        if (request.getStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Can only execute approved requests");
        }

        Map<String, Object> requestedData = deserializeData(request.getRequestedData());

        switch (request.getTargetItemType()) {
            case "TODO":
                return executeTodoChange(request, requestedData);
            case "INVOICE":
                return executeInvoiceChange(request, requestedData);
            default:
                throw new IllegalArgumentException("Unsupported item type: " + request.getTargetItemType());
        }
    }

    private Object executeTodoChange(ApprovalRequest request, Map<String, Object> data) {
        switch (request.getOperation()) {
            case CREATE:
                return createTodoFromData(data);
            case UPDATE:
                if (request.getTargetItemId() == null) {
                    throw new IllegalStateException("Target ID required for UPDATE");
                }
                return updateTodoFromData(request.getTargetItemId(), data);
            case DELETE:
                if (request.getTargetItemId() == null) {
                    throw new IllegalStateException("Target ID required for DELETE");
                }
                todoService.deleteTodo(request.getTargetItemId());
                return null;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + request.getOperation());
        }
    }

    private Object executeInvoiceChange(ApprovalRequest request, Map<String, Object> data) {
        switch (request.getOperation()) {
            case CREATE:
                return createInvoiceFromData(data);
            case UPDATE:
                if (request.getTargetItemId() == null) {
                    throw new IllegalStateException("Target ID required for UPDATE");
                }
                return updateInvoiceFromData(request.getTargetItemId(), data);
            case DELETE:
                if (request.getTargetItemId() == null) {
                    throw new IllegalStateException("Target ID required for DELETE");
                }
                invoiceService.deleteInvoice(request.getTargetItemId());
                return null;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + request.getOperation());
        }
    }

    private Todo createTodoFromData(Map<String, Object> data) {
        Todo todo = new Todo();
        todo.setTitle((String) data.get("title"));
        todo.setDescription((String) data.get("description"));
        if (data.get("level") != null) {
            todo.setLevel(Level.valueOf((String) data.get("level")));
        }
        if (data.get("completed") != null) {
            todo.setCompleted((Boolean) data.get("completed"));
        }
        if (data.get("userId") != null) {
            User user = userRepository.findById(((Number) data.get("userId")).longValue())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            todo.setUser(user);
        }
        return todoService.createTodo(todo);
    }

    private Todo updateTodoFromData(Long todoId, Map<String, Object> data) {
        Todo todo = new Todo();
        if (data.get("title") != null) todo.setTitle((String) data.get("title"));
        if (data.get("description") != null) todo.setDescription((String) data.get("description"));
        if (data.get("level") != null) todo.setLevel(Level.valueOf((String) data.get("level")));
        if (data.get("completed") != null) todo.setCompleted((Boolean) data.get("completed"));
        if (data.get("userId") != null) {
            User user = userRepository.findById(((Number) data.get("userId")).longValue())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            todo.setUser(user);
        }
        return todoService.updateTodo(todoId, todo);
    }

    private Invoice createInvoiceFromData(Map<String, Object> data) {
        Invoice invoice = new Invoice();
        if (data.get("amount") != null) {
            Object amountObj = data.get("amount");
            if (amountObj instanceof Number) {
                invoice.setAmount(new BigDecimal(amountObj.toString()));
            }
        }
        if (data.get("status") != null) {
            invoice.setStatus(InvoiceStatus.valueOf((String) data.get("status")));
        }
        if (data.get("level") != null) {
            invoice.setLevel(Level.valueOf((String) data.get("level")));
        }
        if (data.get("userId") != null) {
            User user = userRepository.findById(((Number) data.get("userId")).longValue())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            invoice.setUser(user);
        }
        return invoiceService.createInvoice(invoice);
    }

    private Invoice updateInvoiceFromData(Long invoiceId, Map<String, Object> data) {
        Invoice invoice = new Invoice();
        if (data.get("amount") != null) {
            Object amountObj = data.get("amount");
            if (amountObj instanceof Number) {
                invoice.setAmount(new BigDecimal(amountObj.toString()));
            }
        }
        if (data.get("status") != null) {
            invoice.setStatus(InvoiceStatus.valueOf((String) data.get("status")));
        }
        if (data.get("level") != null) {
            invoice.setLevel(Level.valueOf((String) data.get("level")));
        }
        if (data.get("userId") != null) {
            User user = userRepository.findById(((Number) data.get("userId")).longValue())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            invoice.setUser(user);
        }
        return invoiceService.updateInvoice(invoiceId, invoice);
    }

    // Helper methods

    private boolean canUserApprove(ApprovalRequest request, User user) {
        Map<String, Object> requestedData = deserializeData(request.getRequestedData());
        Optional<ApprovalRule> ruleOpt = approvalRuleService.findMatchingRule(
                request.getTargetItemType(),
                request.getOperation(),
                requestedData
        );

        if (ruleOpt.isEmpty()) {
            return false;
        }

        ApprovalRule rule = ruleOpt.get();
        return rule.getRoleRequirements().containsKey(user.getRole());
    }

    private String serializeData(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize data", e);
        }
    }

    private Map<String, Object> deserializeData(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize data", e);
        }
    }
}
