package com.example.todolist.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Log Entity - Immutable record of entity CRUD operations
 *
 * This entity tracks all create, update, and delete operations on Todo and Invoice entities.
 * Audit logs are append-only (immutable) and persisted even after the original entity is deleted.
 *
 * Schema: Partitioned by created_at (quarterly partitions) for scalability
 * Immutability: Database trigger prevents UPDATE/DELETE operations
 */
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id, created_at DESC"),
    @Index(name = "idx_audit_time", columnList = "created_at"),
    @Index(name = "idx_audit_user", columnList = "created_by, created_at DESC"),
    @Index(name = "idx_audit_operation", columnList = "operation, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    /**
     * Unique identifier (UUID v4)
     * Primary key component (with created_at for partitioning)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Type of entity being audited (e.g., "Todo", "Invoice", "User")
     */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /**
     * ID of the audited entity
     * No FK constraint - allows audit logs to persist after entity deletion
     */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /**
     * Type of operation performed (INSERT, UPDATE, DELETE)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditOperation operation;

    /**
     * Username of person who performed the action
     * Matches User.username but has no FK relation (preserves historical accuracy)
     * Null for system operations
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * Timestamp when audit entry was created (UTC)
     * Partition key for quarterly partitions
     * Immutable - represents append-only semantics
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * Field-level changes captured during the operation
     * Structure: {"fieldName": {"old": value, "new": value}}
     * - UPDATE: Contains before/after values for changed fields only
     * - INSERT: null (no "before" state)
     * - DELETE: Contains final state before deletion (old values, new=null)
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "JSONB")
    private Map<String, FieldChange> changes;

    /**
     * UUID of API request that triggered this audit entry
     * Used for correlation and tracing (e.g., bulk operations share same request_id)
     * Null for operations not triggered by API requests
     */
    @Column(name = "request_id")
    private UUID requestId;

    /**
     * Custom constructor for creating audit log entries
     *
     * @param entityType Type of entity being audited
     * @param entityId ID of audited entity
     * @param operation Operation type (INSERT/UPDATE/DELETE)
     * @param createdBy Username performing the action
     * @param changes Field-level changes (can be null for INSERT)
     * @param requestId UUID of triggering API request (can be null)
     */
    public AuditLog(String entityType, Long entityId, AuditOperation operation,
                    String createdBy, Map<String, FieldChange> changes, UUID requestId) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.operation = operation;
        this.createdBy = createdBy;
        this.changes = changes;
        this.requestId = requestId;
        this.createdAt = Instant.now();
    }
}
