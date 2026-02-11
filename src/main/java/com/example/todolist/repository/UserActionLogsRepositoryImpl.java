package com.example.todolist.repository;

import com.example.todolist.model.AuditOperation;
import com.example.todolist.model.UserActionLogs;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of custom query methods for {@link UserActionLogs}.
 *
 * <p>Follows the project's repository pattern (Custom/Impl) using JPQL
 * for flexible filtering and searching.
 */
@Repository
public class UserActionLogsRepositoryImpl implements UserActionLogsRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<UserActionLogs> findByEntityTypeAndEntityId(String entityType, Long entityId) {
        String jpql = "SELECT u FROM UserActionLogs u " +
                      "WHERE u.entityType = :entityType AND u.entityId = :entityId " +
                      "ORDER BY u.createdAt DESC";

        TypedQuery<UserActionLogs> query = entityManager.createQuery(jpql, UserActionLogs.class);
        query.setParameter("entityType", entityType);
        query.setParameter("entityId", entityId);

        return query.getResultList();
    }

    @Override
    public Page<UserActionLogs> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable) {
        String jpql = "SELECT u FROM UserActionLogs u " +
                      "WHERE u.entityType = :entityType AND u.entityId = :entityId " +
                      "ORDER BY u.createdAt DESC";

        TypedQuery<UserActionLogs> query = entityManager.createQuery(jpql, UserActionLogs.class);
        query.setParameter("entityType", entityType);
        query.setParameter("entityId", entityId);

        // Apply pagination
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<UserActionLogs> results = query.getResultList();

        // Count total for pagination
        long total = countByEntityTypeAndEntityId(entityType, entityId);

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * Helper method to count total audit logs for a specific entity.
     */
    private long countByEntityTypeAndEntityId(String entityType, Long entityId) {
        String jpql = "SELECT COUNT(u) FROM UserActionLogs u " +
                      "WHERE u.entityType = :entityType AND u.entityId = :entityId";

        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        query.setParameter("entityType", entityType);
        query.setParameter("entityId", entityId);

        return query.getSingleResult();
    }

    @Override
    public Page<UserActionLogs> searchAuditLogs(
            String entityType,
            Long entityId,
            String username,
            AuditOperation operation,
            UUID requestId,
            Instant startDate,
            Instant endDate,
            Pageable pageable
    ) {
        // Build dynamic JPQL query based on provided filters
        StringBuilder jpql = new StringBuilder("SELECT u FROM UserActionLogs u WHERE 1=1");

        if (entityType != null) {
            jpql.append(" AND u.entityType = :entityType");
        }
        if (entityId != null) {
            jpql.append(" AND u.entityId = :entityId");
        }
        if (username != null) {
            jpql.append(" AND u.createdBy = :username");
        }
        if (operation != null) {
            jpql.append(" AND u.operation = :operation");
        }
        if (requestId != null) {
            jpql.append(" AND u.requestId = :requestId");
        }
        if (startDate != null) {
            jpql.append(" AND u.createdAt >= :startDate");
        }
        if (endDate != null) {
            jpql.append(" AND u.createdAt <= :endDate");
        }

        jpql.append(" ORDER BY u.createdAt DESC");

        // Create query and set parameters
        TypedQuery<UserActionLogs> query = entityManager.createQuery(jpql.toString(), UserActionLogs.class);

        if (entityType != null) {
            query.setParameter("entityType", entityType);
        }
        if (entityId != null) {
            query.setParameter("entityId", entityId);
        }
        if (username != null) {
            query.setParameter("username", username);
        }
        if (operation != null) {
            query.setParameter("operation", operation);
        }
        if (requestId != null) {
            query.setParameter("requestId", requestId);
        }
        if (startDate != null) {
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
        }

        // Apply pagination
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<UserActionLogs> results = query.getResultList();

        // Count total for pagination
        long total = countAuditLogs(entityType, entityId, username, operation, requestId, startDate, endDate);

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public List<UserActionLogs> findByRequestId(UUID requestId) {
        String jpql = "SELECT u FROM UserActionLogs u WHERE u.requestId = :requestId ORDER BY u.createdAt ASC";

        TypedQuery<UserActionLogs> query = entityManager.createQuery(jpql, UserActionLogs.class);
        query.setParameter("requestId", requestId);

        return query.getResultList();
    }

    /**
     * Helper method to count total matching audit logs for pagination.
     */
    private long countAuditLogs(
            String entityType,
            Long entityId,
            String username,
            AuditOperation operation,
            UUID requestId,
            Instant startDate,
            Instant endDate
    ) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(u) FROM UserActionLogs u WHERE 1=1");

        if (entityType != null) {
            jpql.append(" AND u.entityType = :entityType");
        }
        if (entityId != null) {
            jpql.append(" AND u.entityId = :entityId");
        }
        if (username != null) {
            jpql.append(" AND u.createdBy = :username");
        }
        if (operation != null) {
            jpql.append(" AND u.operation = :operation");
        }
        if (requestId != null) {
            jpql.append(" AND u.requestId = :requestId");
        }
        if (startDate != null) {
            jpql.append(" AND u.createdAt >= :startDate");
        }
        if (endDate != null) {
            jpql.append(" AND u.createdAt <= :endDate");
        }

        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);

        if (entityType != null) {
            query.setParameter("entityType", entityType);
        }
        if (entityId != null) {
            query.setParameter("entityId", entityId);
        }
        if (username != null) {
            query.setParameter("username", username);
        }
        if (operation != null) {
            query.setParameter("operation", operation);
        }
        if (requestId != null) {
            query.setParameter("requestId", requestId);
        }
        if (startDate != null) {
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
        }

        return query.getSingleResult();
    }
}
