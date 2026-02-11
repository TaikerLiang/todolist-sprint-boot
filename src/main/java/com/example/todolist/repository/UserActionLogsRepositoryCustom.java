package com.example.todolist.repository;

import com.example.todolist.model.AuditOperation;
import com.example.todolist.model.UserActionLogs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Custom query methods for {@link UserActionLogs} repository.
 *
 * <p>Implemented by {@link UserActionLogsRepositoryImpl} following the
 * project's repository pattern.
 */
public interface UserActionLogsRepositoryCustom {

    /**
     * Finds all audit logs for a specific entity ordered by timestamp (newest first).
     *
     * @param entityType the entity type (e.g., "Todo", "Invoice")
     * @param entityId   the entity ID
     * @return list of audit logs for the entity
     */
    List<UserActionLogs> findByEntityTypeAndEntityId(String entityType, Long entityId);

    /**
     * Finds audit logs for a specific entity with pagination support.
     *
     * @param entityType the entity type (e.g., "Todo", "Invoice")
     * @param entityId   the entity ID
     * @param pageable   pagination parameters
     * @return page of audit logs for the entity
     */
    Page<UserActionLogs> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);

    /**
     * Searches audit logs with flexible filtering and pagination.
     *
     * @param entityType  optional entity type filter
     * @param entityId    optional entity ID filter
     * @param username    optional username filter (createdBy)
     * @param operation   optional operation type filter (CREATE/UPDATE/DELETE)
     * @param requestId   optional request ID filter for correlating changes
     * @param startDate   optional start date (inclusive)
     * @param endDate     optional end date (inclusive)
     * @param pageable    pagination and sorting parameters
     * @return page of matching audit logs
     */
    Page<UserActionLogs> searchAuditLogs(
            String entityType,
            Long entityId,
            String username,
            AuditOperation operation,
            UUID requestId,
            Instant startDate,
            Instant endDate,
            Pageable pageable
    );

    /**
     * Finds all audit logs for a specific request ID (transaction correlation).
     *
     * @param requestId the request ID
     * @return list of audit logs with matching request ID, ordered by timestamp (oldest first)
     */
    List<UserActionLogs> findByRequestId(UUID requestId);
}
