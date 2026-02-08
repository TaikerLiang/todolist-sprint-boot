package com.example.todolist.service;

import com.example.todolist.config.JwtProperties;
import com.example.todolist.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class JwtService {
    private final JwtProperties jwtProperties;

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(jwtProperties.getAccess().getExpiration());

        String token = Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .id(UUID.randomUUID().toString())
                .signWith(getAccessTokenKey())
                .compact();

        log.debug("Generated access token for user: {}", user.getUsername());
        return token;
    }

    public String generateRefreshToken(User user, String jti, String tokenFamilyId) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(jwtProperties.getRefresh().getExpiration());

        String token = Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("jti", jti)
                .claim("tokenFamilyId", tokenFamilyId)
                .claim("typ", "refresh")
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(getRefreshTokenKey())
                .compact();

        log.debug("Generated refresh token for user: {} with jti: {}", user.getUsername(), jti);
        return token;
    }

    public Claims validateAccessToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getAccessTokenKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Access token validation failed: {}", e.getMessage());
            throw e;
        }
    }

    public Claims validateRefreshToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getRefreshTokenKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Refresh token validation failed: {}", e.getMessage());
            throw e;
        }
    }

    public String extractUsername(String token) {
        return validateAccessToken(token).getSubject();
    }

    public Long extractUserId(String token) {
        return validateAccessToken(token).get("userId", Long.class);
    }

    public String extractJtiFromRefreshToken(String token) {
        return validateRefreshToken(token).get("jti", String.class);
    }

    public String extractTokenFamilyId(String token) {
        return validateRefreshToken(token).get("tokenFamilyId", String.class);
    }

    private SecretKey getAccessTokenKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getAccess().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getRefreshTokenKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getRefresh().getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
