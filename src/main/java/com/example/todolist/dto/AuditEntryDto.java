package com.example.todolist.dto;

import com.example.todolist.model.AuditOperation;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a single audit log entry in API responses.
 *
 * <p>Contains all audit metadata plus field-level change details.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEntryDto {

    /**
     * Unique identifier for the audit log entry.
     */
    private UUID id;

    /**
     * When the change occurred (UTC timestamp).
     */
    private Instant timestamp;

    /**
     * Type of operation (CREATE, UPDATE, DELETE).
     */
    private AuditOperation operation;

    /**
     * Type of entity that was changed (e.g., "Todo", "Invoice").
     */
    private String entityType;

    /**
     * ID of the changed entity.
     */
    private Long entityId;

    /**
     * Username of the user who made the change (nullable).
     */
    private String username;

    /**
     * Request ID for correlating related changes (nullable).
     */
    private UUID requestId;

    /**
     * List of field changes (only populated for UPDATE operations).
     */
    private List<FieldChangeDto> changedFields;
}
