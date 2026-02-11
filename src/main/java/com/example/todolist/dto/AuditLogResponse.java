package com.example.todolist.dto;

import lombok.*;

import java.util.List;

/**
 * DTO for audit history API responses.
 *
 * <p>Contains entity metadata and list of audit log entries.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {

    /**
     * ID of the audited entity.
     */
    private String entityId;

    /**
     * Type of entity (e.g., "Todo", "Invoice").
     */
    private String entityType;

    /**
     * Complete audit history for the entity.
     */
    private List<AuditEntryDto> history;

    /**
     * Total number of audit entries.
     */
    private int totalEntries;
}
