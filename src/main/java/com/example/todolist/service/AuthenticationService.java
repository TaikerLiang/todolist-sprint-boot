package com.example.todolist.service;

import com.example.todolist.config.JwtProperties;
import com.example.todolist.dto.LoginRequest;
import com.example.todolist.dto.LoginResponse;
import com.example.todolist.dto.RefreshResponse;
import com.example.todolist.dto.SessionInfo;
import com.example.todolist.model.RefreshToken;
import com.example.todolist.model.User;
import com.example.todolist.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

    @Transactional
    public LoginResponse authenticate(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("Authentication failed: user not found - {}", request.getUsername());
                    return new RuntimeException("User not found");
                });

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user);
        String tokenFamilyId = refreshTokenService.generateNewTokenFamilyId();
        String jti = refreshTokenService.generateJti();
        String refreshToken = jwtService.generateRefreshToken(user, jti, tokenFamilyId);

        // Store refresh token
        String deviceInfo = extractDeviceInfo(httpRequest);
        refreshTokenService.createRefreshToken(user, jti, tokenFamilyId, refreshToken, deviceInfo);

        log.info("User authenticated successfully: {}", user.getUsername());

        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtProperties.getAccess().getExpiration(),
                user.getUsername(),
                user.getRole().name()
        );
    }

    @Transactional
    public RefreshResponse refreshAccessToken(String refreshTokenString, HttpServletRequest httpRequest) {
        // Validate JWT structure and signature
        String jti = jwtService.extractJtiFromRefreshToken(refreshTokenString);
        String tokenFamilyId = jwtService.extractTokenFamilyId(refreshTokenString);

        // Find token in database
        RefreshToken refreshToken = refreshTokenService.findByToken(refreshTokenString)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found in database");
                    return new RuntimeException("Invalid refresh token");
                });

        // Check if token is revoked
        if (refreshTokenService.isTokenRevoked(refreshToken)) {
            log.warn("Attempted use of revoked token: {}", jti);
            throw new RuntimeException("Refresh token has been revoked");
        }

        // Check if token is expired
        if (refreshTokenService.isTokenExpired(refreshToken)) {
            log.warn("Attempted use of expired token: {}", jti);
            throw new RuntimeException("Refresh token expired");
        }

        // Check if token was already rotated
        if (refreshToken.getReplacedAt() != null) {
            // Check grace period
            if (refreshTokenService.isTokenWithinGracePeriod(refreshToken)) {
                log.debug("Token used within grace period: {}", jti);
            } else {
                // Reuse detected - revoke entire token family
                log.error("Token reuse detected! Revoking family: {}", tokenFamilyId);
                refreshTokenService.revokeTokenFamily(tokenFamilyId);
                throw new RuntimeException("Refresh token revoked due to suspicious activity");
            }
        }

        User user = refreshToken.getUser();

        // Generate new tokens
        String newAccessToken = jwtService.generateAccessToken(user);
        String newJti = refreshTokenService.generateJti();
        String newRefreshToken = jwtService.generateRefreshToken(user, newJti, tokenFamilyId);

        // Store new refresh token
        String deviceInfo = extractDeviceInfo(httpRequest);
        refreshTokenService.createRefreshToken(user, newJti, tokenFamilyId, newRefreshToken, deviceInfo);

        // Mark old token as rotated
        refreshTokenService.markAsRotated(refreshToken, newJti);

        log.info("Access token refreshed for user: {}", user.getUsername());

        return new RefreshResponse(
                newAccessToken,
                newRefreshToken,
                jwtProperties.getAccess().getExpiration()
        );
    }

    @Transactional
    public void logout(String refreshTokenString) {
        RefreshToken refreshToken = refreshTokenService.findByToken(refreshTokenString)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        refreshTokenService.revokeToken(refreshToken);
        log.info("User logged out: {}", refreshToken.getUser().getUsername());
    }

    @Transactional
    public int logoutAllDevices(User user) {
        int count = refreshTokenService.revokeAllUserTokens(user);
        log.info("User logged out from all devices: {}", user.getUsername());
        return count;
    }

    @Transactional
    public void logoutDevice(User user, Long sessionId) {
        RefreshToken token = refreshTokenService.findByJti(sessionId.toString())
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!token.getUser().getId().equals(user.getId())) {
            log.warn("User {} attempted to revoke session belonging to user {}",
                    user.getUsername(), token.getUser().getUsername());
            throw new RuntimeException("You can only revoke your own sessions");
        }

        refreshTokenService.revokeToken(token);
        log.info("User {} logged out from device: {}", user.getUsername(), sessionId);
    }

    @Transactional(readOnly = true)
    public List<SessionInfo> getActiveSessions(User user, String currentJti) {
        List<RefreshToken> sessions = refreshTokenService.getActiveUserSessions(user);

        return sessions.stream()
                .map(token -> new SessionInfo(
                        token.getId(),
                        token.getDeviceInfo(),
                        token.getCreatedAt(),
                        token.getExpiresAt(),
                        token.getJti().equals(currentJti)
                ))
                .collect(Collectors.toList());
    }

    private String extractDeviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String ipAddress = request.getRemoteAddr();

        if (userAgent == null) {
            userAgent = "Unknown";
        }

        // Simplified: Extract browser/version from user agent
        String browser = extractBrowser(userAgent);

        return String.format("%s %s", browser, ipAddress);
    }

    private String extractBrowser(String userAgent) {
        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Firefox")) return "Firefox";
        if (userAgent.contains("Safari")) return "Safari";
        if (userAgent.contains("Edge")) return "Edge";
        return "Unknown Browser";
    }
}
