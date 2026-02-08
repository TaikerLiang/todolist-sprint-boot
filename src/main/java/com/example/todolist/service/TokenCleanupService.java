package com.example.todolist.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenCleanupService {
    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void cleanupExpiredTokens() {
        log.info("Starting scheduled cleanup of expired refresh tokens");
        refreshTokenService.cleanupExpiredTokens();
        log.info("Completed scheduled cleanup of expired refresh tokens");
    }
}
