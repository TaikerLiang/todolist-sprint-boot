package com.example.todolist.controller;

import com.example.todolist.dto.AuditLogFilter;
import com.example.todolist.dto.AuditLogResponse;
import com.example.todolist.model.AuditOperation;
import com.example.todolist.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for audit log queries
 *
 * All endpoints require ADMIN role for security
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Slf4j
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Get complete audit history for a specific entity
     *
     * GET /api/audit-logs/entity/{entityType}/{entityId}
     *
     * @param entityType Type of entity (e.g., "Todo", "Invoice")
     * @param entityId ID of the entity
     * @param page Page number (0-indexed, default 0)
     * @param size Page size (default 50)
     * @return Page of audit log entries, sorted by createdAt descending
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogResponse>> getAuditHistoryForEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("Fetching audit history for {} #{} (page={}, size={})",
            entityType, entityId, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogResponse> auditHistory = auditLogService.getEntityHistory(entityType, entityId, pageable);

        return ResponseEntity.ok(auditHistory);
    }

    /**
     * Get a single audit log entry by ID
     *
     * GET /api/audit-logs/{id}
     *
     * @param id Audit log UUID
     * @return Audit log entry if found, 404 otherwise
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuditLogResponse> getAuditLogById(@PathVariable UUID id) {
        log.info("Fetching audit log by ID: {}", id);

        AuditLogResponse response = auditLogService.getById(id);

        if (response == null) {
            log.warn("Audit log not found: {}", id);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Search audit logs with filtering
     *
     * GET /api/audit-logs?entityType=Todo&createdBy=admin&operation=UPDATE&startDate=...&endDate=...
     *
     * @param entityType Filter by entity type (optional)
     * @param entityId Filter by entity ID (optional)
     * @param createdBy Filter by username (optional)
     * @param operation Filter by operation type (optional)
     * @param startDate Filter by start date (optional, ISO 8601)
     * @param endDate Filter by end date (optional, ISO 8601)
     * @param requestId Filter by request ID (optional)
     * @param page Page number (0-indexed, default 0)
     * @param size Page size (default 20)
     * @param sort Sort field and direction (default "createdAt,desc")
     * @return Page of audit log entries matching the filters
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogResponse>> searchAuditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) AuditOperation operation,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) UUID requestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        log.info("Searching audit logs with filters: entityType={}, entityId={}, createdBy={}, operation={}, " +
                "startDate={}, endDate={}, requestId={}, page={}, size={}",
            entityType, entityId, createdBy, operation, startDate, endDate, requestId, page, size);

        // Build filter
        AuditLogFilter filter = new AuditLogFilter(
            entityType, entityId, createdBy, operation, startDate, endDate, requestId
        );

        // Parse sort parameter
        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
        String sortField = sortParts[0];

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        Page<AuditLogResponse> results = auditLogService.searchAuditLogs(filter, pageable);

        log.info("Found {} audit log entries", results.getTotalElements());

        return ResponseEntity.ok(results);
    }
}
