package com.example.todolist.controller;

import com.example.todolist.dto.AuditEntryDto;
import com.example.todolist.dto.AuditLogResponse;
import com.example.todolist.dto.AuditLogSearchRequest;
import com.example.todolist.dto.FieldChangeDto;
import com.example.todolist.model.AuditOperation;
import com.example.todolist.model.FieldChange;
import com.example.todolist.model.UserActionLogs;
import com.example.todolist.repository.UserActionLogsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API controller for querying audit logs.
 *
 * <p>All endpoints require ADMIN role for access.
 */
@RestController
@RequestMapping("/api/audit")
@Slf4j
@RequiredArgsConstructor
public class AuditLogController {

    private final UserActionLogsRepository userActionLogsRepository;

    /**
     * Get audit history for a specific Todo item.
     *
     * @param id the Todo ID
     * @param page page number (0-indexed, default 0)
     * @param size page size (default 20)
     * @return audit history response with pagination
     */
    @GetMapping("/todos/{id}/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getTodoAuditHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Retrieving audit history for Todo ID: {} (page: {}, size: {})", id, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<UserActionLogs> auditPage = userActionLogsRepository
                .findByEntityTypeAndEntityId("Todo", id, pageable);

        if (auditPage.isEmpty()) {
            log.warn("No audit history found for Todo ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        List<AuditEntryDto> entries = auditPage.getContent().stream()
                .map(this::toAuditEntryDto)
                .collect(Collectors.toList());

        Map<String, Object> response = Map.of(
                "entityId", id.toString(),
                "entityType", "Todo",
                "history", entries,
                "pagination", Map.of(
                        "page", auditPage.getNumber(),
                        "size", auditPage.getSize(),
                        "totalElements", auditPage.getTotalElements(),
                        "totalPages", auditPage.getTotalPages()
                )
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get audit history for a specific Invoice item.
     *
     * @param id the Invoice ID
     * @param page page number (0-indexed, default 0)
     * @param size page size (default 20)
     * @return audit history response with pagination
     */
    @GetMapping("/invoices/{id}/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getInvoiceAuditHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Retrieving audit history for Invoice ID: {} (page: {}, size: {})", id, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<UserActionLogs> auditPage = userActionLogsRepository
                .findByEntityTypeAndEntityId("Invoice", id, pageable);

        if (auditPage.isEmpty()) {
            log.warn("No audit history found for Invoice ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        List<AuditEntryDto> entries = auditPage.getContent().stream()
                .map(this::toAuditEntryDto)
                .collect(Collectors.toList());

        Map<String, Object> response = Map.of(
                "entityId", id.toString(),
                "entityType", "Invoice",
                "history", entries,
                "pagination", Map.of(
                        "page", auditPage.getNumber(),
                        "size", auditPage.getSize(),
                        "totalElements", auditPage.getTotalElements(),
                        "totalPages", auditPage.getTotalPages()
                )
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Search and filter audit logs across all entities.
     *
     * @param entityType optional entity type filter
     * @param entityId optional entity ID filter
     * @param username optional username filter
     * @param operation optional operation type filter
     * @param requestId optional request ID filter
     * @param startDate optional start date filter (ISO 8601 format)
     * @param endDate optional end date filter (ISO 8601 format)
     * @param page page number (0-indexed, default 0)
     * @param size page size (default 20)
     * @return paginated search results
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> searchAuditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) AuditOperation operation,
            @RequestParam(required = false) UUID requestId,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Searching audit logs with filters - entityType: {}, username: {}, operation: {}",
                entityType, username, operation);

        Pageable pageable = PageRequest.of(page, size);

        Page<UserActionLogs> auditPage = userActionLogsRepository.searchAuditLogs(
                entityType,
                entityId,
                username,
                operation,
                requestId,
                startDate,
                endDate,
                pageable
        );

        List<AuditEntryDto> entries = auditPage.getContent().stream()
                .map(this::toAuditEntryDto)
                .collect(Collectors.toList());

        Map<String, Object> response = Map.of(
                "results", entries,
                "pagination", Map.of(
                        "page", auditPage.getNumber(),
                        "size", auditPage.getSize(),
                        "totalElements", auditPage.getTotalElements(),
                        "totalPages", auditPage.getTotalPages()
                )
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get all audit logs for a specific request ID (transaction correlation).
     *
     * @param requestId the request ID (UUID)
     * @return list of all changes that occurred in the same request/transaction
     */
    @GetMapping("/revisions/{requestId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRevisionDetails(@PathVariable UUID requestId) {
        log.info("Retrieving audit logs for request ID: {}", requestId);

        List<UserActionLogs> auditLogs = userActionLogsRepository.findByRequestId(requestId);

        if (auditLogs.isEmpty()) {
            log.warn("No audit logs found for request ID: {}", requestId);
            return ResponseEntity.notFound().build();
        }

        List<AuditEntryDto> entries = auditLogs.stream()
                .map(this::toAuditEntryDto)
                .collect(Collectors.toList());

        // Extract metadata from first entry (all entries in same request share these)
        UserActionLogs firstLog = auditLogs.get(0);

        Map<String, Object> response = Map.of(
                "requestId", requestId,
                "timestamp", firstLog.getCreatedAt(),
                "username", firstLog.getCreatedBy() != null ? firstLog.getCreatedBy() : "unknown",
                "changes", entries,
                "totalChanges", entries.size()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Converts UserActionLogs entity to AuditEntryDto for API response.
     */
    private AuditEntryDto toAuditEntryDto(UserActionLogs audit) {
        List<FieldChangeDto> changedFields = null;

        if (audit.getChanges() != null && !audit.getChanges().isEmpty()) {
            changedFields = audit.getChanges().entrySet().stream()
                    .map(entry -> toFieldChangeDto(entry.getValue()))
                    .collect(Collectors.toList());
        }

        return AuditEntryDto.builder()
                .id(audit.getId())
                .timestamp(audit.getCreatedAt())
                .operation(audit.getOperation())
                .entityType(audit.getEntityType())
                .entityId(audit.getEntityId())
                .username(audit.getCreatedBy())
                .requestId(audit.getRequestId())
                .changedFields(changedFields)
                .build();
    }

    /**
     * Converts FieldChange entity to FieldChangeDto for API response.
     */
    private FieldChangeDto toFieldChangeDto(FieldChange fieldChange) {
        return FieldChangeDto.builder()
                .fieldName(fieldChange.getFieldName())
                .oldValue(fieldChange.getOldValue())
                .newValue(fieldChange.getNewValue())
                .fieldType(fieldChange.getFieldType())
                .build();
    }
}
