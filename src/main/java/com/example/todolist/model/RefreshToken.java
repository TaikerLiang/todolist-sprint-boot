package com.example.todolist.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, unique = true, length = 36)
    private String jti;

    @Column(name = "token_family_id", nullable = false, length = 36)
    private String tokenFamilyId;

    @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant expiresAt;

    @Column(name = "valid_until", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant validUntil;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    @Column(name = "replaced_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant replacedAt;

    @Column(name = "replaced_by_jti", length = 36)
    private String replacedByJti;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(nullable = false)
    private Boolean revoked = false;

    public RefreshToken(User user, String tokenHash, String jti, String tokenFamilyId,
                        Instant expiresAt, Instant validUntil, String deviceInfo) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.jti = jti;
        this.tokenFamilyId = tokenFamilyId;
        this.expiresAt = expiresAt;
        this.validUntil = validUntil;
        this.deviceInfo = deviceInfo;
    }
}
