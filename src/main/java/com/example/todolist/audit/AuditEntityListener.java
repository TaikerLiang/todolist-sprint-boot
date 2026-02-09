package com.example.todolist.audit;

import com.example.todolist.interceptor.RequestIdInterceptor;
import com.example.todolist.model.AuditOperation;
import com.example.todolist.model.FieldChange;
import com.example.todolist.service.AuditLogService;
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/**
 * JPA Entity Listener for automatic audit logging
 *
 * Captures entity lifecycle events (@PostPersist, @PreUpdate, @PreRemove)
 * and creates corresponding audit log entries
 *
 * Usage: Add @EntityListeners(AuditEntityListener.class) to entities
 */
@Component
@Slf4j
public class AuditEntityListener {

    private static AuditLogService auditLogService;

    /**
     * Inject AuditLogService via setter injection
     * (Constructor injection doesn't work for JPA listeners)
     */
    @Autowired
    public void setAuditLogService(AuditLogService service) {
        AuditEntityListener.auditLogService = service;
    }

    /**
     * After entity is loaded, capture snapshot for potential update tracking
     */
    @PostLoad
    public void postLoad(Object entity) {
        Map<String, Object> snapshot = FieldComparator.captureSnapshot(entity);
        AuditContext.storeSnapshot(entity, snapshot);
    }

    /**
     * After entity is persisted (INSERT operation)
     */
    @PostPersist
    public void postPersist(Object entity) {
        try {
            String entityType = entity.getClass().getSimpleName();
            Long entityId = getEntityId(entity);

            if (entityId != null) {
                UUID requestId = RequestIdInterceptor.getCurrentRequestId();
                // INSERT operations have no "before" state, so changes is null
                auditLogService.logEntityChange(
                    entityType,
                    entityId,
                    AuditOperation.INSERT,
                    null,  // No changes for INSERT
                    requestId
                );
            }
        } finally {
            AuditContext.removeSnapshot(entity);
        }
    }

    /**
     * Before entity is updated, compare with snapshot to detect changes
     */
    @PreUpdate
    public void preUpdate(Object entity) {
        try {
            String entityType = entity.getClass().getSimpleName();
            Long entityId = getEntityId(entity);

            if (entityId != null) {
                Map<String, Object> oldSnapshot = AuditContext.getSnapshot(entity);

                if (oldSnapshot != null) {
                    Map<String, FieldChange> changes = FieldComparator.computeChanges(
                        oldSnapshot,
                        entity
                    );

                    // Only log if there are actual changes
                    if (!changes.isEmpty()) {
                        UUID requestId = RequestIdInterceptor.getCurrentRequestId();
                        auditLogService.logEntityChange(
                            entityType,
                            entityId,
                            AuditOperation.UPDATE,
                            changes,
                            requestId
                        );
                    }
                }
            }
        } finally {
            // Update snapshot for potential subsequent updates
            Map<String, Object> newSnapshot = FieldComparator.captureSnapshot(entity);
            AuditContext.storeSnapshot(entity, newSnapshot);
        }
    }

    /**
     * Before entity is removed (DELETE operation)
     */
    @PreRemove
    public void preRemove(Object entity) {
        try {
            String entityType = entity.getClass().getSimpleName();
            Long entityId = getEntityId(entity);

            if (entityId != null) {
                // Capture final state before deletion
                Map<String, FieldChange> changes = FieldComparator.captureDeletionState(entity);
                UUID requestId = RequestIdInterceptor.getCurrentRequestId();

                auditLogService.logEntityChange(
                    entityType,
                    entityId,
                    AuditOperation.DELETE,
                    changes,
                    requestId
                );
            }
        } finally {
            AuditContext.removeSnapshot(entity);
        }
    }

    /**
     * Extract entity ID using reflection
     * Assumes entity has a field annotated with @Id
     *
     * @param entity The entity
     * @return Entity ID as Long, or null if not found
     */
    private Long getEntityId(Object entity) {
        try {
            Class<?> clazz = entity.getClass();
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    if (value instanceof Long) {
                        return (Long) value;
                    } else if (value instanceof Integer) {
                        return ((Integer) value).longValue();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract entity ID from {}", entity.getClass().getSimpleName(), e);
        }
        return null;
    }
}
