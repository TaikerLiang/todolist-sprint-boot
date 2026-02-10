package com.example.todolist.dto;

import com.example.todolist.model.AuditOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for audit log filter criteria
 *
 * Used to encapsulate query parameters for filtering audit logs
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditLogFilter {

    /**
     * Filter by entity type (e.g., "Todo", "Invoice")
     */
    private String entityType;

    /**
     * Filter by specific entity ID
     */
    private Long entityId;

    /**
     * Filter by username who made the change
     */
    private String createdBy;

    /**
     * Filter by operation type (INSERT, UPDATE, DELETE)
     */
    private AuditOperation operation;

    /**
     * Filter by start date (inclusive)
     */
    private Instant startDate;

    /**
     * Filter by end date (exclusive)
     */
    private Instant endDate;

    /**
     * Filter by request ID (for correlation)
     */
    private UUID requestId;
}
