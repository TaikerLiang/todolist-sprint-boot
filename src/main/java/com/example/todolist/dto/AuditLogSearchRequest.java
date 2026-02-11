package com.example.todolist.dto;

import com.example.todolist.model.AuditOperation;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for audit log search/filter requests.
 *
 * <p>All fields are optional - null values are ignored in the search.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogSearchRequest {

    /**
     * Filter by entity type (e.g., "Todo", "Invoice").
     */
    private String entityType;

    /**
     * Filter by specific entity ID.
     */
    private Long entityId;

    /**
     * Filter by username who made the change.
     */
    private String username;

    /**
     * Filter by operation type (CREATE, UPDATE, DELETE).
     */
    private AuditOperation operation;

    /**
     * Filter by request ID for correlating changes.
     */
    private UUID requestId;

    /**
     * Filter changes after this timestamp (inclusive).
     */
    private Instant startDate;

    /**
     * Filter changes before this timestamp (inclusive).
     */
    private Instant endDate;

    /**
     * Page number (0-indexed).
     */
    private Integer page = 0;

    /**
     * Page size (number of results per page).
     */
    private Integer size = 20;
}
