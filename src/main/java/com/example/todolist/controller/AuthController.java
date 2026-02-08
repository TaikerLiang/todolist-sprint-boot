package com.example.todolist.controller;

import com.example.todolist.dto.*;
import com.example.todolist.model.User;
import com.example.todolist.repository.UserRepository;
import com.example.todolist.service.AuthenticationService;
import com.example.todolist.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("Login attempt for user: {}", request.getUsername());
        LoginResponse response = authenticationService.authenticate(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("Token refresh requested");
        RefreshResponse response = authenticationService.refreshAccessToken(request.getRefreshToken(), httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @Valid @RequestBody RefreshRequest request
    ) {
        log.info("Logout requested");
        authenticationService.logout(request.getRefreshToken());

        Map<String, String> response = new HashMap<>();
        response.put("message", "Successfully logged out");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionInfo>> getSessions(
            @AuthenticationPrincipal String username,
            @RequestHeader("Authorization") String authHeader
    ) {
        log.info("Listing sessions for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = authHeader.substring(7);
        String currentJti = jwtService.extractJtiFromRefreshToken(token);

        List<SessionInfo> sessions = authenticationService.getActiveSessions(user, currentJti);
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Map<String, Object>> logoutAllDevices(
            @AuthenticationPrincipal String username
    ) {
        log.info("Logout from all devices requested for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int count = authenticationService.logoutAllDevices(user);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Logged out from all devices");
        response.put("sessionsRevoked", count);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, String>> logoutDevice(
            @AuthenticationPrincipal String username,
            @PathVariable Long sessionId
    ) {
        log.info("Logout from device {} requested by user: {}", sessionId, username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        authenticationService.logoutDevice(user, sessionId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Session revoked successfully");
        return ResponseEntity.ok(response);
    }
}
