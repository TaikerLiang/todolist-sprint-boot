package com.example.todolist.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "admin_portal_user_action_logs", indexes = {
    @Index(name = "idx_user_action_logs_entity_type", columnList = "entity_type"),
    @Index(name = "idx_user_action_logs_request_id", columnList = "request_id"),
    @Index(name = "idx_user_action_logs_created_by", columnList = "created_by"),
    @Index(name = "idx_user_action_logs_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor  // Required by JPA
@AllArgsConstructor  // Required for @Builder
@Builder
public class UserActionLogs {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditOperation operation;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Type(JsonBinaryType.class)
    @Column(name = "changes", columnDefinition = "JSONB")
    private Map<String, FieldChange> changes;

    @Column(name = "request_id")
    private UUID requestId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
