# Quickstart Guide: JWT Authentication

**Feature**: 001-jwt-api-auth
**Date**: 2026-02-08
**For**: Developers implementing JWT authentication

---

## Overview

This guide provides step-by-step instructions for implementing JWT-based authentication in the Todo List API. The feature adds user login, token refresh, and session management endpoints.

**Scope**: Authentication only - Authorization (permissions/access control) is out of scope.

---

## Prerequisites

- Java 21 installed
- PostgreSQL running (localhost:5432)
- Maven 3.9.11+
- Existing Todo List API codebase
- `.env` file configured with database credentials

---

## Quick Start (5 Steps)

### 1. Add Dependencies

Add to `pom.xml`:

```xml
<!-- JWT Library -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>

<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### 2. Configure JWT Settings

Add to `src/main/resources/application.yml`:

```yaml
jwt:
  access-token:
    secret: ${JWT_ACCESS_SECRET}
    expiration: 900000  # 15 minutes in milliseconds
  refresh-token:
    secret: ${JWT_REFRESH_SECRET}
    expiration: 1209600000  # 14 days in milliseconds
    grace-period: 30000  # 30 seconds in milliseconds

spring:
  security:
    enabled: true
```

Add to `.env`:

```bash
# Generate secrets: openssl rand -base64 32
JWT_ACCESS_SECRET=your-256-bit-secret-here
JWT_REFRESH_SECRET=your-different-256-bit-secret-here
```

### 3. Run Database Migrations

```bash
# Generate migrations for password field and refresh_tokens table
make makemigration NAME=add_password_to_users
make makemigration NAME=create_refresh_tokens_table

# Apply migrations
make migrate
```

Or manually:

```bash
./mvnw liquibase:diff
./mvnw liquibase:update
```

### 4. Build and Run

```bash
# Build
./mvnw clean package

# Run
make run
```

### 5. Test Authentication

```bash
# Login (username-only for MVP)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"johndoe"}'

# Response:
# {
#   "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
#   "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
#   "tokenType": "Bearer",
#   "expiresIn": 900,
#   "username": "johndoe",
#   "role": "USER"
# }

# Use access token for protected endpoints
curl http://localhost:8080/api/todos \
  -H "Authorization: Bearer <access-token>"

# Refresh tokens
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refresh-token>"}'
```

---

## Implementation Steps

### Step 1: Create RefreshToken Entity

**File**: `src/main/java/com/example/todolist/model/RefreshToken.java`

```java
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

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, unique = true, length = 36)
    private String jti;

    @Column(nullable = false, length = 36)
    private String tokenFamilyId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant validUntil;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant replacedAt;
    private String replacedByJti;

    @Column(length = 255)
    private String deviceInfo;

    @Column(nullable = false)
    private boolean revoked = false;
}
```

### Step 2: Update User Entity

**File**: `src/main/java/com/example/todolist/model/User.java`

Add field:

```java
@Column(nullable = true)
private String password;  // Nullable for MVP
```

### Step 3: Create Repositories

**File**: `src/main/java/com/example/todolist/repository/RefreshTokenRepository.java`

```java
package com.example.todolist.repository;

import com.example.todolist.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    Optional<RefreshToken> findByJti(String jti);
    List<RefreshToken> findByUserIdAndRevokedFalseAndExpiresAtAfter(Long userId, Instant now);
    void deleteByExpiresAtBeforeAndRevokedTrue(Instant now);
    void deleteByExpiresAtBefore(Instant cutoff);
    int countByUserIdAndRevokedFalseAndExpiresAtAfter(Long userId, Instant now);
    void deleteByTokenFamilyId(String tokenFamilyId);
}
```

### Step 4: Create DTOs

Create package: `src/main/java/com/example/todolist/dto/`

**LoginRequest.java**:
```java
package com.example.todolist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50)
    private String username;

    @Size(max = 100)
    private String password;  // Optional for MVP
}
```

**LoginResponse.java**:
```java
package com.example.todolist.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private String username;
    private String role;
}
```

Create similar DTOs for `RefreshRequest`, `RefreshResponse`, `SessionInfo`.

### Step 5: Implement JwtService

**File**: `src/main/java/com/example/todolist/service/JwtService.java`

```java
package com.example.todolist.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.Key;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    @Value("${jwt.access-token.secret}")
    private String accessSecret;

    @Value("${jwt.access-token.expiration}")
    private long accessExpiration;

    public String generateAccessToken(String username, Long userId, String role) {
        return Jwts.builder()
            .setSubject(username)
            .claim("userId", userId)
            .claim("role", role)
            .setIssuer("todolist-api")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + accessExpiration))
            .signWith(getSigningKey(accessSecret), SignatureAlgorithm.HS256)
            .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(getSigningKey(accessSecret))
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    private Key getSigningKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }
}
```

### Step 6: Implement AuthenticationService

**File**: `src/main/java/com/example/todolist/service/AuthenticationService.java`

```java
package com.example.todolist.service;

import com.example.todolist.dto.*;
import com.example.todolist.model.User;
import com.example.todolist.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResponse login(LoginRequest request, String deviceInfo) {
        log.info("Login attempt for username: {}", request.getUsername());

        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new BadCredentialsException("User not found"));

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(
            user.getUsername(), user.getId(), user.getRole().name()
        );

        String refreshToken = refreshTokenService.createRefreshToken(
            user, deviceInfo
        );

        log.info("User {} authenticated successfully", user.getUsername());

        return new LoginResponse(
            accessToken,
            refreshToken,
            "Bearer",
            900L,
            user.getUsername(),
            user.getRole().name()
        );
    }
}
```

### Step 7: Create Security Configuration

**File**: `src/main/java/com/example/todolist/config/SecurityConfig.java`

```java
package com.example.todolist.config;

import com.example.todolist.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/admin/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

### Step 8: Create JWT Authentication Filter

**File**: `src/main/java/com/example/todolist/security/JwtAuthenticationFilter.java`

```java
package com.example.todolist.security;

import com.example.todolist.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            Claims claims = jwtService.validateToken(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            var authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + role)
            );

            var authToken = new UsernamePasswordAuthenticationToken(
                username, null, authorities
            );

            authToken.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
```

### Step 9: Create AuthController

**File**: `src/main/java/com/example/todolist/controller/AuthController.java`

```java
package com.example.todolist.controller;

import com.example.todolist.dto.*;
import com.example.todolist.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        String deviceInfo = getDeviceInfo(httpRequest);
        LoginResponse response = authenticationService.login(request, deviceInfo);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
        @Valid @RequestBody RefreshRequest request,
        HttpServletRequest httpRequest
    ) {
        String deviceInfo = getDeviceInfo(httpRequest);
        RefreshResponse response = authenticationService.refresh(request, deviceInfo);
        return ResponseEntity.ok(response);
    }

    private String getDeviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String ip = request.getRemoteAddr();
        return userAgent + " " + ip;
    }
}
```

---

## Testing

### Manual Testing with cURL

```bash
# 1. Create test user (if not exists)
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","role":"USER"}'

# 2. Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser"}'

# Save tokens from response

# 3. Access protected endpoint
curl http://localhost:8080/api/todos \
  -H "Authorization: Bearer <access-token>"

# 4. Refresh tokens
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refresh-token>"}'

# 5. List sessions
curl http://localhost:8080/api/auth/sessions \
  -H "Authorization: Bearer <access-token>"

# 6. Logout
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refresh-token>"}'
```

### Automated Testing (Optional)

Run tests:

```bash
./mvnw test
```

---

## Troubleshooting

### Common Issues

**Issue**: "401 Unauthorized" on protected endpoints
- **Solution**: Ensure Authorization header format is correct: `Authorization: Bearer <token>`
- Check token hasn't expired (15 minutes for access tokens)

**Issue**: "Refresh token expired"
- **Solution**: Access tokens expire after 15 minutes, refresh tokens after 14 days. Re-authenticate if both expired.

**Issue**: "Refresh token revoked due to suspicious activity"
- **Solution**: Reuse detected. Token used after 30-second grace period. Re-authenticate.

**Issue**: Database migration fails
- **Solution**: Ensure PostgreSQL is running and `.env` has correct credentials. Check `make showmigrations` for status.

**Issue**: "Bad credentials" on login
- **Solution**: User doesn't exist. Create user first or check username spelling.

---

## Next Steps

1. **Add Password Authentication**: Update `User` entity, add password validation in `AuthenticationService`
2. **Add Logout Endpoint**: Implement `/api/auth/logout` to revoke refresh tokens
3. **Add Session Management**: Implement `/api/auth/sessions` endpoints
4. **Add Authorization**: Implement RBAC/ABAC for access control (future feature)
5. **Add Rate Limiting**: Protect login endpoint from brute force
6. **Add MFA**: Multi-factor authentication for enhanced security

---

## Support

For questions or issues:
- Check `research.md` for technical decisions
- Review `data-model.md` for entity structure
- Review `contracts/auth-api.yaml` for API specification
- Check logs: Application logs authentication attempts and errors

---

**Last Updated**: 2026-02-08
**Status**: Ready for implementation
