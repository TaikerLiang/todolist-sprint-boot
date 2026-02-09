package com.example.todolist.repository;

import com.example.todolist.model.AuditLog;
import com.example.todolist.model.AuditOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for accessing audit log entries
 *
 * Provides custom query methods for filtering and searching audit logs
 * Extends JpaSpecificationExecutor for dynamic querying
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    /**
     * Find all audit entries for a specific entity
     *
     * @param entityType Type of entity (e.g., "Todo", "Invoice")
     * @param entityId ID of the entity
     * @param sort Sort order
     * @return List of audit log entries
     */
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId, Sort sort);

    /**
     * Find all audit entries for a specific entity with pagination
     *
     * @param entityType Type of entity
     * @param entityId ID of the entity
     * @param pageable Pagination information
     * @return Page of audit log entries
     */
    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);

    /**
     * Find all audit entries for a specific entity and operation type
     *
     * @param entityType Type of entity
     * @param entityId ID of the entity
     * @param operation Operation type (INSERT, UPDATE, DELETE)
     * @return List of audit log entries
     */
    List<AuditLog> findByEntityTypeAndEntityIdAndOperation(String entityType, Long entityId, AuditOperation operation);

    /**
     * Find all audit entries by a specific user within a date range
     *
     * @param createdBy Username
     * @param startDate Start date (inclusive)
     * @param endDate End date (exclusive)
     * @param sort Sort order
     * @return List of audit log entries
     */
    List<AuditLog> findByCreatedByAndCreatedAtBetween(String createdBy, Instant startDate, Instant endDate, Sort sort);

    /**
     * Find all audit entries by operation type
     *
     * @param operation Operation type
     * @param pageable Pagination information
     * @return Page of audit log entries
     */
    Page<AuditLog> findByOperation(AuditOperation operation, Pageable pageable);

    /**
     * Find all audit entries by entity type
     *
     * @param entityType Type of entity
     * @param pageable Pagination information
     * @return Page of audit log entries
     */
    Page<AuditLog> findByEntityType(String entityType, Pageable pageable);

    /**
     * Find all audit entries by request ID (for correlation)
     *
     * @param requestId UUID of API request
     * @param sort Sort order
     * @return List of audit log entries sharing the same request ID
     */
    List<AuditLog> findByRequestId(UUID requestId, Sort sort);
}
