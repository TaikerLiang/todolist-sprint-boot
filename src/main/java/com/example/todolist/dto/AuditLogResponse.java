package com.example.todolist.dto;

import com.example.todolist.model.AuditOperation;
import com.example.todolist.model.FieldChange;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for audit log API responses
 *
 * Represents a single audit log entry with all relevant information
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditLogResponse {

    /**
     * Unique audit log entry UUID
     */
    private UUID id;

    /**
     * Type of entity that was changed
     */
    private String entityType;

    /**
     * ID of the entity that was changed
     */
    private Long entityId;

    /**
     * Operation type (INSERT, UPDATE, DELETE)
     */
    private AuditOperation operation;

    /**
     * Username of person who made the change (null for system operations)
     */
    private String createdBy;

    /**
     * Timestamp when audit entry was created (UTC)
     */
    private Instant createdAt;

    /**
     * Field-level changes with before/after values
     * Null for INSERT operations (no "before" state)
     */
    private Map<String, FieldChange> changes;

    /**
     * UUID of API request that triggered this audit entry (for correlation)
     */
    private UUID requestId;
}
