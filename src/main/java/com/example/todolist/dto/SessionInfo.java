package com.example.todolist.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {
    private Long sessionId;
    private String deviceInfo;
    private Instant createdAt;
    private Instant expiresAt;
    private Boolean isCurrentSession;
}
