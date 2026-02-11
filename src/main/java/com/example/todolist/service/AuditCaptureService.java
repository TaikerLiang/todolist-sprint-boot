package com.example.todolist.service;

import com.example.todolist.model.AuditOperation;
import com.example.todolist.model.FieldChange;
import com.example.todolist.model.UserActionLogs;
import com.example.todolist.repository.UserActionLogsRepository;
import com.example.todolist.util.EntityComparator;
import com.example.todolist.util.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for capturing audit log entries for entity changes.
 *
 * <p>Provides methods to capture CREATE, UPDATE, and DELETE operations
 * with field-level change tracking using JSONB storage.
 *
 * <p><b>Transactional Behavior</b>: All audit capture methods participate
 * in the caller's transaction. If audit write fails, the entire transaction
 * (including the business operation) will be rolled back.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditCaptureService {

    private final UserActionLogsRepository repository;

    /**
     * Captures an audit log for entity creation (CREATE operation).
     *
     * @param entityType the entity type (e.g., "Todo", "Invoice")
     * @param entityId   the entity ID
     */
    @Transactional
    public void captureCreate(String entityType, Long entityId) {
        log.debug("Capturing CREATE audit for {} ID {}", entityType, entityId);

        UserActionLogs audit = UserActionLogs.builder()
                .entityType(entityType)
                .entityId(entityId)
                .operation(AuditOperation.CREATE)
                .createdBy(RequestContext.getUserId())
                .requestId(parseRequestId(RequestContext.getRequestId()))
                .changes(new HashMap<>()) // Empty for CREATE
                .build();

        repository.save(audit);

        log.info("CREATE audit captured for {} ID {}", entityType, entityId);
    }

    /**
     * Captures an audit log for entity update (UPDATE operation) with field-level changes.
     *
     * @param entityType  the entity type (e.g., "Todo", "Invoice")
     * @param entityId    the entity ID
     * @param oldEntity   the entity before update
     * @param newEntity   the entity after update
     */
    @Transactional
    public void captureUpdate(String entityType, Long entityId, Object oldEntity, Object newEntity) {
        log.debug("Capturing UPDATE audit for {} ID {}", entityType, entityId);

        // Compare entities to detect field changes
        Map<String, FieldChange> changes = EntityComparator.compareEntities(oldEntity, newEntity);

        if (changes.isEmpty()) {
            log.debug("No field changes detected for {} ID {}, skipping audit", entityType, entityId);
            return;
        }

        UserActionLogs audit = UserActionLogs.builder()
                .entityType(entityType)
                .entityId(entityId)
                .operation(AuditOperation.UPDATE)
                .createdBy(RequestContext.getUserId())
                .requestId(parseRequestId(RequestContext.getRequestId()))
                .changes(changes)
                .build();

        repository.save(audit);

        log.info("UPDATE audit captured for {} ID {} ({} fields changed)",
                entityType, entityId, changes.size());
    }

    /**
     * Captures an audit log for entity deletion (DELETE operation).
     *
     * @param entityType  the entity type (e.g., "Todo", "Invoice")
     * @param entityId    the entity ID
     */
    @Transactional
    public void captureDelete(String entityType, Long entityId) {
        log.debug("Capturing DELETE audit for {} ID {}", entityType, entityId);

        UserActionLogs audit = UserActionLogs.builder()
                .entityType(entityType)
                .entityId(entityId)
                .operation(AuditOperation.DELETE)
                .createdBy(RequestContext.getUserId())
                .requestId(parseRequestId(RequestContext.getRequestId()))
                .changes(new HashMap<>()) // Empty for DELETE
                .build();

        repository.save(audit);

        log.info("DELETE audit captured for {} ID {}", entityType, entityId);
    }

    /**
     * Safely parses request ID from string to UUID.
     *
     * @param requestIdStr the request ID string
     * @return UUID or null if invalid/missing
     */
    private UUID parseRequestId(String requestIdStr) {
        if (requestIdStr == null || requestIdStr.isEmpty()) {
            log.warn("No request ID available for audit logging");
            return null;
        }

        try {
            return UUID.fromString(requestIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request ID format: {}", requestIdStr);
            return null;
        }
    }
}
