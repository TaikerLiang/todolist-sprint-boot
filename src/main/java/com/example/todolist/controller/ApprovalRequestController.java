package com.example.todolist.controller;

import com.example.todolist.dto.ApprovalRecordResponseDTO;
import com.example.todolist.dto.ApprovalRequestResponseDTO;
import com.example.todolist.dto.CreateApprovalRequestDTO;
import com.example.todolist.dto.SubmitApprovalDTO;
import com.example.todolist.model.ApprovalRecord;
import com.example.todolist.model.ApprovalRequest;
import com.example.todolist.service.ApprovalWorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for approval workflow operations.
 * Provides endpoints for creating, approving, rejecting, and withdrawing approval requests.
 */
@RestController
@RequestMapping("/api/approval-requests")
public class ApprovalRequestController {

    private final ApprovalWorkflowService approvalWorkflowService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApprovalRequestController(
            ApprovalWorkflowService approvalWorkflowService,
            ObjectMapper objectMapper
    ) {
        this.approvalWorkflowService = approvalWorkflowService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new approval request
     * POST /api/approval-requests
     */
    @PostMapping
    public ResponseEntity<?> createApprovalRequest(
            @RequestBody CreateApprovalRequestDTO dto,
            @RequestParam Long requesterId
    ) {
        try {
            ApprovalRequest request = approvalWorkflowService.createApprovalRequest(
                    dto.getTargetItemType(),
                    dto.getTargetItemId(),
                    dto.getOperation(),
                    dto.getRequestedData(),
                    requesterId
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApprovalRequestResponseDTO.fromEntity(request, objectMapper));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit an approval or rejection decision
     * POST /api/approval-requests/{requestId}/respond
     */
    @PostMapping("/{requestId}/respond")
    public ResponseEntity<?> submitApproval(
            @PathVariable Long requestId,
            @RequestParam Long approverId,
            @RequestBody SubmitApprovalDTO dto
    ) {
        try {
            ApprovalRequest request = approvalWorkflowService.submitApproval(
                    requestId,
                    approverId,
                    dto.getDecision(),
                    dto.getComment()
            );

            return ResponseEntity.ok(ApprovalRequestResponseDTO.fromEntity(request, objectMapper));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Withdraw a pending approval request
     * POST /api/approval-requests/{requestId}/withdraw
     */
    @PostMapping("/{requestId}/withdraw")
    public ResponseEntity<?> withdrawRequest(
            @PathVariable Long requestId,
            @RequestParam Long userId
    ) {
        try {
            ApprovalRequest request = approvalWorkflowService.withdrawRequest(requestId, userId);
            return ResponseEntity.ok(ApprovalRequestResponseDTO.fromEntity(request, objectMapper));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all approval requests created by a specific user
     * GET /api/approval-requests/by-requester/{requesterId}
     */
    @GetMapping("/by-requester/{requesterId}")
    public ResponseEntity<List<ApprovalRequestResponseDTO>> getRequestsByRequester(
            @PathVariable Long requesterId
    ) {
        List<ApprovalRequest> requests = approvalWorkflowService.getRequestsByRequester(requesterId);
        List<ApprovalRequestResponseDTO> dtos = requests.stream()
                .map(r -> ApprovalRequestResponseDTO.fromEntity(r, objectMapper))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get all pending approval requests that a user can approve
     * GET /api/approval-requests/pending-for-approver/{userId}
     */
    @GetMapping("/pending-for-approver/{userId}")
    public ResponseEntity<List<ApprovalRequestResponseDTO>> getPendingRequestsForApprover(
            @PathVariable Long userId
    ) {
        List<ApprovalRequest> requests = approvalWorkflowService.getPendingRequestsForApprover(userId);
        List<ApprovalRequestResponseDTO> dtos = requests.stream()
                .map(r -> ApprovalRequestResponseDTO.fromEntity(r, objectMapper))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get a specific approval request by ID
     * GET /api/approval-requests/{requestId}
     */
    @GetMapping("/{requestId}")
    public ResponseEntity<?> getRequestById(@PathVariable Long requestId) {
        return approvalWorkflowService.getRequestById(requestId)
                .map(request -> ResponseEntity.ok(ApprovalRequestResponseDTO.fromEntity(request, objectMapper)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all approval records for a specific approval request
     * GET /api/approval-requests/{requestId}/records
     */
    @GetMapping("/{requestId}/records")
    public ResponseEntity<List<ApprovalRecordResponseDTO>> getApprovalRecords(
            @PathVariable Long requestId
    ) {
        List<ApprovalRecord> records = approvalWorkflowService.getApprovalRecords(requestId);
        List<ApprovalRecordResponseDTO> dtos = records.stream()
                .map(ApprovalRecordResponseDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
