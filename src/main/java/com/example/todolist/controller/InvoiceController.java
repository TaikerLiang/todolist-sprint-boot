package com.example.todolist.controller;

import com.example.todolist.dto.ApprovalRequestResponseDTO;
import com.example.todolist.model.*;
import com.example.todolist.service.ApprovalWorkflowService;
import com.example.todolist.service.InvoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;
    private final ApprovalWorkflowService approvalWorkflowService;
    private final ObjectMapper objectMapper;

    public InvoiceController(
            InvoiceService invoiceService,
            ApprovalWorkflowService approvalWorkflowService,
            ObjectMapper objectMapper
    ) {
        this.invoiceService = invoiceService;
        this.approvalWorkflowService = approvalWorkflowService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<Invoice> getAllInvoices() {
        return invoiceService.getAllInvoices();
    }

    @GetMapping("/{id}")
    public Invoice getInvoiceById(@PathVariable Long id) {
        return invoiceService.getInvoiceById(id);
    }

    /**
     * Create invoice - Routes through approval workflow if needed
     * POST /api/invoices?requesterId={userId}
     */
    @PostMapping
    public ResponseEntity<?> createInvoice(
            @RequestBody Invoice invoice,
            @RequestParam Long requesterId
    ) {
        try {
            // Convert invoice to data map
            Map<String, Object> data = invoiceToMap(invoice);

            // Check if approval is required
            if (approvalWorkflowService.requiresApproval("INVOICE", RequestOperation.CREATE, data)) {
                // Create approval request
                ApprovalRequest request = approvalWorkflowService.createApprovalRequest(
                        "INVOICE",
                        null,  // No target ID for CREATE
                        RequestOperation.CREATE,
                        data,
                        requesterId
                );

                logger.info("Invoice creation requires approval. Request ID: {}", request.getId());
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(Map.of(
                                "message", "Invoice creation requires approval",
                                "approvalRequest", ApprovalRequestResponseDTO.fromEntity(request, objectMapper)
                        ));
            }

            // No approval needed - execute immediately
            Invoice created = invoiceService.createInvoice(invoice);
            logger.info("Invoice created immediately (no approval required): {}", created.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create invoice: " + e.getMessage()));
        }
    }

    /**
     * Update invoice - Routes through approval workflow if needed
     * PUT /api/invoices/{id}?requesterId={userId}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateInvoice(
            @PathVariable Long id,
            @RequestBody Invoice invoice,
            @RequestParam Long requesterId
    ) {
        try {
            // Check if invoice exists
            Invoice existingInvoice = invoiceService.getInvoiceById(id);

            // Convert updated invoice to data map
            Map<String, Object> data = invoiceToMap(invoice);

            // Check if approval is required
            if (approvalWorkflowService.requiresApproval("INVOICE", RequestOperation.UPDATE, data)) {
                // Create approval request
                ApprovalRequest request = approvalWorkflowService.createApprovalRequest(
                        "INVOICE",
                        id,
                        RequestOperation.UPDATE,
                        data,
                        requesterId
                );

                logger.info("Invoice update requires approval. Request ID: {}", request.getId());
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(Map.of(
                                "message", "Invoice update requires approval",
                                "approvalRequest", ApprovalRequestResponseDTO.fromEntity(request, objectMapper)
                        ));
            }

            // No approval needed - execute immediately
            Invoice updated = invoiceService.updateInvoice(id, invoice);
            logger.info("Invoice updated immediately (no approval required): {}", id);
            return ResponseEntity.ok(updated);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update invoice: " + e.getMessage()));
        }
    }

    /**
     * Delete invoice - Routes through approval workflow if needed
     * DELETE /api/invoices/{id}?requesterId={userId}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteInvoice(
            @PathVariable Long id,
            @RequestParam Long requesterId
    ) {
        try {
            // Check if invoice exists
            Invoice existingInvoice = invoiceService.getInvoiceById(id);

            // Empty data for delete
            Map<String, Object> data = new HashMap<>();

            // Check if approval is required
            if (approvalWorkflowService.requiresApproval("INVOICE", RequestOperation.DELETE, data)) {
                // Create approval request
                ApprovalRequest request = approvalWorkflowService.createApprovalRequest(
                        "INVOICE",
                        id,
                        RequestOperation.DELETE,
                        data,
                        requesterId
                );

                logger.info("Invoice deletion requires approval. Request ID: {}", request.getId());
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(Map.of(
                                "message", "Invoice deletion requires approval",
                                "approvalRequest", ApprovalRequestResponseDTO.fromEntity(request, objectMapper)
                        ));
            }

            // No approval needed - execute immediately
            invoiceService.deleteInvoice(id);
            logger.info("Invoice deleted immediately (no approval required): {}", id);
            return ResponseEntity.noContent().build();

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deleting invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete invoice: " + e.getMessage()));
        }
    }

    // Helper method to convert Invoice to Map
    private Map<String, Object> invoiceToMap(Invoice invoice) {
        Map<String, Object> data = new HashMap<>();
        if (invoice.getInvoiceId() != null) data.put("invoiceId", invoice.getInvoiceId().toString());
        if (invoice.getAmount() != null) data.put("amount", invoice.getAmount());
        if (invoice.getStatus() != null) data.put("status", invoice.getStatus().toString());
        if (invoice.getLevel() != null) data.put("level", invoice.getLevel().toString());
        if (invoice.getUser() != null && invoice.getUser().getId() != null) {
            data.put("userId", invoice.getUser().getId());
        }
        return data;
    }
}
