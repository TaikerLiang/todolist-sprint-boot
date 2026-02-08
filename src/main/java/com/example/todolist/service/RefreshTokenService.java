package com.example.todolist.service;

import com.example.todolist.config.JwtProperties;
import com.example.todolist.model.RefreshToken;
import com.example.todolist.model.User;
import com.example.todolist.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public RefreshToken createRefreshToken(User user, String jti, String tokenFamilyId, String token, String deviceInfo) {
        String tokenHash = hashToken(token);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.getRefresh().getExpiration());
        Instant validUntil = now.plusSeconds(jwtProperties.getRefresh().getGracePeriod());

        RefreshToken refreshToken = new RefreshToken(
                user,
                tokenHash,
                jti,
                tokenFamilyId,
                expiresAt,
                validUntil,
                deviceInfo
        );

        refreshToken = refreshTokenRepository.save(refreshToken);
        log.info("Created refresh token for user: {} with jti: {}", user.getUsername(), jti);
        return refreshToken;
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String token) {
        String tokenHash = hashToken(token);
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByJti(String jti) {
        return refreshTokenRepository.findByJti(jti);
    }

    @Transactional
    public void markAsRotated(RefreshToken oldToken, String newJti) {
        oldToken.setReplacedAt(Instant.now());
        oldToken.setReplacedByJti(newJti);
        Instant extendedValidUntil = Instant.now().plusSeconds(jwtProperties.getRefresh().getGracePeriod());
        oldToken.setValidUntil(extendedValidUntil);
        refreshTokenRepository.save(oldToken);
        log.info("Marked token as rotated: {} -> {}", oldToken.getJti(), newJti);
    }

    @Transactional
    public void revokeToken(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        log.info("Revoked refresh token: {}", token.getJti());
    }

    @Transactional
    public void revokeTokenFamily(String tokenFamilyId) {
        refreshTokenRepository.revokeTokenFamily(tokenFamilyId);
        log.warn("Revoked entire token family: {} (reuse detected)", tokenFamilyId);
    }

    @Transactional
    public int revokeAllUserTokens(User user) {
        int count = refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Revoked all tokens for user: {} (count: {})", user.getUsername(), count);
        return count;
    }

    @Transactional(readOnly = true)
    public List<RefreshToken> getActiveUserSessions(User user) {
        return refreshTokenRepository.findByUserAndRevokedFalseAndExpiresAtAfter(user, Instant.now());
    }

    @Transactional
    public void cleanupExpiredTokens() {
        Instant threshold = Instant.now().minusSeconds(30 * 24 * 60 * 60); // 30 days ago
        refreshTokenRepository.deleteExpiredTokens(threshold);
        log.info("Cleaned up expired tokens older than 30 days");
    }

    public boolean isTokenExpired(RefreshToken token) {
        return Instant.now().isAfter(token.getExpiresAt());
    }

    public boolean isTokenWithinGracePeriod(RefreshToken token) {
        Instant now = Instant.now();
        return token.getReplacedAt() != null && now.isBefore(token.getValidUntil());
    }

    public boolean isTokenRevoked(RefreshToken token) {
        return token.getRevoked();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to hash token", e);
        }
    }

    public String generateNewTokenFamilyId() {
        return UUID.randomUUID().toString();
    }

    public String generateJti() {
        return UUID.randomUUID().toString();
    }
}
