package com.example.todolist.service;

import com.example.todolist.dto.AuditLogFilter;
import com.example.todolist.dto.AuditLogResponse;
import com.example.todolist.model.AuditLog;
import com.example.todolist.model.AuditOperation;
import com.example.todolist.model.FieldChange;
import com.example.todolist.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for creating and managing audit log entries
 *
 * Handles automatic capture of entity changes and recording to audit_log table
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Create an audit log entry for an entity operation
     *
     * @param entityType Type of entity (e.g., "Todo", "Invoice")
     * @param entityId ID of the entity
     * @param operation Operation type (INSERT, UPDATE, DELETE)
     * @param changes Field-level changes (null for INSERT)
     * @param requestId UUID of API request (can be null)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEntityChange(String entityType, Long entityId, AuditOperation operation,
                                 Map<String, FieldChange> changes, UUID requestId) {
        try {
            String username = getCurrentUsername();

            AuditLog auditLog = new AuditLog(
                entityType,
                entityId,
                operation,
                username,
                changes,
                requestId
            );

            auditLogRepository.save(auditLog);

            log.debug("Audit log created: {} {} on {} #{} by {}",
                operation, entityType, entityId, username);

        } catch (Exception e) {
            log.error("Failed to create audit log for {} {} on {} #{}",
                operation, entityType, entityId, e);
            // Don't throw - audit logging failure should not break main transaction
        }
    }

    /**
     * Extract current username from Spring Security context
     *
     * @return Username of authenticated user, or "system" if no authentication
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }

        // Handle anonymous authentication
        if ("anonymousUser".equals(authentication.getPrincipal())) {
            return "system";
        }

        return authentication.getName();
    }

    /**
     * Convert AuditLog entity to AuditLogResponse DTO
     *
     * @param auditLog Audit log entity
     * @return AuditLogResponse DTO
     */
    public AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
            auditLog.getId(),
            auditLog.getEntityType(),
            auditLog.getEntityId(),
            auditLog.getOperation(),
            auditLog.getCreatedBy(),
            auditLog.getCreatedAt(),
            auditLog.getChanges(),
            auditLog.getRequestId()
        );
    }

    /**
     * Get audit history for a specific entity
     *
     * @param entityType Type of entity (e.g., "Todo", "Invoice")
     * @param entityId ID of the entity
     * @param pageable Pagination information
     * @return Page of audit log responses
     */
    public Page<AuditLogResponse> getEntityHistory(String entityType, Long entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable)
            .map(this::toResponse);
    }

    /**
     * Get audit history for a specific entity (non-paginated)
     *
     * @param entityType Type of entity
     * @param entityId ID of the entity
     * @return List of audit log responses, sorted by createdAt descending
     */
    public List<AuditLogResponse> getEntityHistory(String entityType, Long entityId) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, sort)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get a single audit log entry by ID
     *
     * @param id Audit log UUID
     * @return AuditLogResponse if found, null otherwise
     */
    public AuditLogResponse getById(UUID id) {
        return auditLogRepository.findById(id)
            .map(this::toResponse)
            .orElse(null);
    }

    /**
     * Search audit logs with dynamic filtering
     *
     * @param filter Filter criteria
     * @param pageable Pagination and sorting
     * @return Page of audit log responses matching the filter
     */
    public Page<AuditLogResponse> searchAuditLogs(AuditLogFilter filter, Pageable pageable) {
        Specification<AuditLog> spec = buildSpecification(filter);
        return auditLogRepository.findAll(spec, pageable)
            .map(this::toResponse);
    }

    /**
     * Build JPA Specification from filter criteria
     *
     * @param filter Filter criteria
     * @return JPA Specification for dynamic querying
     */
    private Specification<AuditLog> buildSpecification(AuditLogFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getEntityType() != null && !filter.getEntityType().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("entityType"), filter.getEntityType()));
            }

            if (filter.getEntityId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("entityId"), filter.getEntityId()));
            }

            if (filter.getCreatedBy() != null && !filter.getCreatedBy().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), filter.getCreatedBy()));
            }

            if (filter.getOperation() != null) {
                predicates.add(criteriaBuilder.equal(root.get("operation"), filter.getOperation()));
            }

            if (filter.getStartDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), filter.getStartDate()));
            }

            if (filter.getEndDate() != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), filter.getEndDate()));
            }

            if (filter.getRequestId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("requestId"), filter.getRequestId()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
