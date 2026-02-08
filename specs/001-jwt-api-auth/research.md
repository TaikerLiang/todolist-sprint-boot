# Research: JWT Authentication Implementation

**Feature**: 001-jwt-api-auth
**Date**: 2026-02-08
**Purpose**: Resolve technical unknowns and establish implementation approach for JWT authentication

---

## Research Questions Resolved

This document consolidates research findings for 6 key technical decisions needed for JWT authentication implementation.

---

## 1. JWT Library Choice

### Decision: **jjwt (io.jsonwebtoken) v0.12.6**

### Rationale:
- **Simplicity for use case**: Straightforward API for token generation/validation without OAuth2 complexity
- **Lower learning curve**: Clean, intuitive builder pattern API
- **Excellent performance**: Benchmarks show top-tier performance, CPU time primarily in cryptographic operations (unavoidable)
- **Active maintenance**: 100% test coverage, RFC-compliant, consistent development
- **Wide Spring Boot adoption**: Extensive community tutorials and examples

### Maven Dependencies:
```xml
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
```

### Alternatives Considered:
**Spring Security OAuth2 JOSE** - Rejected because:
- Designed for full OAuth2 authorization servers, overkill for simple authentication
- Steeper learning curve requiring deep Spring Security knowledge
- More abstractions and configuration complexity
- Better suited for microservices with external JWT consumers

---

## 2. Spring Security Configuration Approach

### Decision: **SecurityFilterChain with Lambda DSL**

### Rationale:
- **Modern Spring Boot 3.1.5 approach**: `WebSecurityConfigurerAdapter` deprecated in Spring Security 5.7+, removed in 6.x
- **Lambda DSL**: Clean, functional configuration style aligned with Spring Boot 3.x
- **Proper for JWT**: Supports stateless session management and custom filter chains

### Implementation Pattern:
```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Stateless JWT
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
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

### Key Configuration Elements:
- **Disable CSRF**: Not needed for stateless JWT (tokens in headers, not cookies)
- **Stateless sessions**: `SessionCreationPolicy.STATELESS` - no server-side session storage
- **Custom filter order**: JWT filter runs before `UsernamePasswordAuthenticationFilter`
- **Public endpoints**: `/api/auth/*` accessible without authentication

---

## 3. JWT Signing Algorithm

### Decision: **HS256 (HMAC with SHA-256)**

### Rationale:
- **Appropriate for single backend**: Same service issues and validates tokens, no need for asymmetric keys
- **Performance**: Fastest option - symmetric HMAC operations less expensive than RSA
- **Simplicity**: Single secret key to manage vs public/private key pair
- **Sufficient security**: With strong 256-bit secret and HTTPS, provides adequate protection

### Security Requirements:
- Cryptographically strong secret (minimum 256 bits)
- Store secret in environment variables (never hardcode)
- Use HTTPS exclusively in production
- Short-lived access tokens (15 minutes)

### When to Switch to RS256:
Consider asymmetric signing if:
- Architecture evolves to microservices
- External services need to validate tokens
- Regulatory compliance requires non-repudiation
- Tokens shared across organizational boundaries

### Code Example:
```java
public String generateToken(UserDetails userDetails) {
    return Jwts.builder()
        .setSubject(userDetails.getUsername())
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
        .compact();
}
```

---

## 4. Token Storage and Transmission

### Decision: **Authorization: Bearer Token** (HTTP Header)

### Rationale:
- **CSRF protection**: Headers not automatically sent by browsers, eliminating CSRF risk
- **Universal compatibility**: Works with web, mobile, and third-party clients
- **Stateless architecture**: Aligns with JWT stateless design
- **No cookie limitations**: Avoids 4KB cookie size limit and SameSite restrictions

### Standard Format:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Token Extraction in Filter:
```java
String header = request.getHeader("Authorization");

if (header == null || !header.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;
}

String token = header.substring(7); // Remove "Bearer " prefix
```

### Client-Side Storage Recommendations:

**Web Applications**:
- **Best**: Short-lived access token in memory (React state/context)
- **Good**: Refresh token in HttpOnly cookie
- **Acceptable**: sessionStorage (cleared on tab close)
- **Avoid**: localStorage (vulnerable to XSS unless strong protection)

**Mobile Applications**:
- Use secure device storage (iOS Keychain, Android Keystore)
- Never use plain SharedPreferences on Android

### Security Requirements:
- **HTTPS mandatory**: JWTs are encoded (Base64), not encrypted
- **Never store sensitive data in payload**: Username/role OK, passwords/secrets never
- **Short access token lifetime**: 5-15 minutes recommended
- **XSS protection**: Content Security Policy headers, input sanitization

---

## 5. Refresh Token Strategy

### Decision: **Refresh Token Rotation with Reuse Grace Period**

### Rationale:
- **Security-UX balance**: Strong protection against token theft + tolerance for network issues
- **Multi-device support**: Users log in from web/mobile with potentially unstable connections
- **OAuth 2.1 compliance**: Meets RFC 9700 rotation requirement with practical implementation
- **Automatic breach detection**: Reuse after grace period signals compromise
- **Mobile-friendly**: 10-30 second grace period handles legitimate retries

### Token Lifetimes:
- **Access token**: 15 minutes (balances security and UX)
- **Refresh token**: 14 days (good for mobile apps, limits exposure)
- **Grace period**: 30 seconds (handles network issues without significant risk)

### Database Schema:
```java
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String tokenHash;  // SHA-256 hash for security

    @Column(nullable = false, unique = true)
    private String jti;  // JWT ID for tracking rotation

    @Column(nullable = false)
    private String tokenFamilyId;  // UUID for rotation chain tracking

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant validUntil;  // For grace period

    private Instant replacedAt;  // When rotated
    private String replacedByJti;  // Reference to new token
    private String deviceInfo;  // User agent + IP for auditing

    @Column(nullable = false)
    private boolean revoked = false;
}
```

### Rotation Flow:
1. User requests token refresh with existing refresh token
2. Validate token hasn't expired and isn't revoked
3. Check if still within grace period (30 seconds) OR is first use
4. Generate new access token + new refresh token
5. Mark old token with `validUntil = now + gracePeriod`
6. Store new token with reference to old token (rotation chain)
7. Return both tokens to client

### Reuse Detection:
- Token used after grace period → revoke entire token family
- Send security alert to user
- Force re-authentication
- Log incident for audit

### Multi-Device Support:
- Store device info (user agent, IP) with each refresh token
- Endpoint to list active sessions: `GET /api/auth/sessions`
- Selective logout: `DELETE /api/auth/sessions/{tokenId}`
- Global logout: `DELETE /api/auth/sessions` (revoke all)

### Scheduled Cleanup:
```java
@Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
public void cleanupExpiredTokens() {
    Instant now = Instant.now();
    refreshTokenRepository.deleteByExpiresAtBeforeAndRevokedTrue(now);
    refreshTokenRepository.deleteByExpiresAtBefore(now.minus(30, ChronoUnit.DAYS));
}
```

### Alternatives Considered:

**Single-use tokens (no grace period)** - Rejected because:
- Network issues cause false positive security alerts
- Mobile apps with poor connectivity would suffer frequent re-auth
- Grace period provides same security with better UX

**Reusable tokens** - Rejected because:
- Does not meet OAuth 2.1 security standards
- No automatic breach detection
- Higher risk if token stolen (valid for full lifetime)

---

## 6. Username-Only Authentication (MVP)

### Decision: **Custom AuthenticationProvider with Username Lookup**

### Rationale:
- **Quick MVP delivery**: Bypass password validation without complex auth flows
- **Clear migration path**: Add password field later without breaking changes
- **Spring Security integration**: Standard framework patterns, not custom hacks
- **Profile-based safety**: Dev-only feature, disabled in production

### Implementation Approach:

**Custom AuthenticationProvider**:
```java
@Component
@Profile("dev")  // Dev environment only
public class UsernameOnlyAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        String username = authentication.getName();

        // Find user by username only - NO password check
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new BadCredentialsException("User not found"));

        var authorities = Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );

        return new UsernamePasswordAuthenticationToken(
            username, null, authorities
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
```

### Database Schema Strategy:

**Add nullable password field immediately**:
```yaml
# Migration: 0007_add_password_to_users.yaml
databaseChangeLog:
  - changeSet:
      id: 0007-add-password-to-users
      author: system
      changes:
        - addColumn:
            tableName: users
            columns:
              - column:
                  name: password
                  type: VARCHAR(255)
                  constraints:
                    nullable: true
```

**Benefits**:
- No schema changes needed during migration
- Can gradually add passwords to users
- Clear distinction: password=null → legacy, password!=null → new

### Migration Path to Passwords:

**Phase 1 (Now)**: Username-only auth with nullable password field

**Phase 2 (Pre-Production)**:
```java
@Component
public class HybridAuthenticationProvider implements AuthenticationProvider {

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new BadCredentialsException("Invalid username"));

        if (user.getPassword() == null) {
            // Legacy user - allow username-only
            log.warn("User {} authenticated without password", username);
        } else {
            // New user - validate password
            if (!passwordEncoder.matches(password, user.getPassword())) {
                throw new BadCredentialsException("Invalid password");
            }
        }

        // Return authenticated token
    }
}
```

**Phase 3 (Production)**:
- Force all users to set passwords
- Make password column NOT NULL
- Remove username-only provider

### Security Constraints:

**Acceptable Use**:
- Local development (localhost)
- Internal testing on VPN-protected networks
- MVP demos to stakeholders (secured networks)

**NOT Acceptable**:
- Production with real user data
- Internet-facing applications
- Compliance-required systems (PCI-DSS, HIPAA, SOC2)

### Profile-Based Safety:
```yaml
# application.yml
spring:
  profiles:
    active: dev

---
spring:
  config:
    activate:
      on-profile: dev
security:
  passwordless:
    enabled: true

---
spring:
  config:
    activate:
      on-profile: prod
security:
  passwordless:
    enabled: false  # Force password auth in production
```

---

## Configuration Summary

### Application Properties

```yaml
# JWT Configuration
jwt:
  access-token:
    secret: ${JWT_ACCESS_SECRET}  # 256-bit secret from environment
    expiration: 900000  # 15 minutes in milliseconds

  refresh-token:
    secret: ${JWT_REFRESH_SECRET}  # Different secret from access
    expiration: 1209600000  # 14 days in milliseconds
    grace-period: 30000  # 30 seconds in milliseconds

# Spring Security
spring:
  security:
    headers:
      content-security-policy: default-src 'self'
      x-content-type-options: nosniff
      x-frame-options: DENY
      x-xss-protection: 1; mode=block

# Development Profile (passwordless auth)
---
spring:
  config:
    activate:
      on-profile: dev
security:
  passwordless:
    enabled: true
```

### Environment Variables Required

```bash
# .env file (not in git)
JWT_ACCESS_SECRET=<256-bit-random-string>
JWT_REFRESH_SECRET=<256-bit-different-random-string>
DB_HOST=localhost
DB_PORT=5432
DB_NAME=demo
DB_USER=default_user
DB_PASSWORD=default_pass
DB_URL=jdbc:postgresql://localhost:5432/demo
```

### Secret Generation

```bash
# Generate 256-bit secrets for HS256
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"

# Or using openssl
openssl rand -base64 32
```

---

## Best Practices Applied

1. ✅ **Stateless JWT authentication** - No server-side sessions
2. ✅ **Short-lived access tokens** (15 min) + long-lived refresh tokens (14 days)
3. ✅ **Token rotation with grace period** - Security + mobile reliability
4. ✅ **OAuth 2.1 compliance** - Refresh token rotation required by RFC 9700
5. ✅ **HTTPS mandatory** - JWTs transmitted securely
6. ✅ **Secrets in environment variables** - Never hardcoded
7. ✅ **HS256 signing** - Appropriate for single backend
8. ✅ **Bearer token in Authorization header** - Industry standard
9. ✅ **Profile-based dev auth** - Passwordless only in dev environment
10. ✅ **Clear migration path** - Nullable password field for future enhancement

---

## Implementation Components Required

### New Classes to Create:

**Models**:
- `RefreshToken.java` - Entity for storing refresh tokens

**DTOs**:
- `LoginRequest.java` - Username for authentication
- `LoginResponse.java` - Access token + refresh token
- `RefreshRequest.java` - Refresh token payload
- `RefreshResponse.java` - New access token + refresh token

**Services**:
- `JwtService.java` - Token generation, validation, claims extraction
- `AuthenticationService.java` - Login, refresh, logout logic
- `RefreshTokenService.java` - Refresh token CRUD, rotation, cleanup

**Repositories**:
- `RefreshTokenRepository.java` - Database access for refresh tokens

**Controllers**:
- `AuthController.java` - `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`

**Security**:
- `SecurityConfig.java` - Spring Security configuration
- `JwtAuthenticationFilter.java` - Extract/validate JWT from requests
- `JwtAuthenticationEntryPoint.java` - Handle 401 errors
- `UsernameOnlyAuthenticationProvider.java` - Dev-only passwordless auth

**Configuration**:
- `JwtProperties.java` - `@ConfigurationProperties` for JWT settings

---

## Testing Strategy

### Unit Tests:
- `JwtServiceTest` - Token generation/validation
- `AuthenticationServiceTest` - Login/refresh/logout logic
- `RefreshTokenServiceTest` - Rotation, reuse detection

### Integration Tests:
- `RefreshTokenRepositoryTest` - Database operations with H2
- `AuthenticationFlowTest` - End-to-end auth flow

### API Tests:
- `AuthControllerTest` - REST endpoint testing with MockMvc

**Note**: Tests are optional per constitution unless explicitly requested. If implemented, will follow test-first approach.

---

## Security Audit Checklist

Before deployment, verify:

- [ ] JWT secrets are 256+ bits and stored in environment variables
- [ ] HTTPS enabled in production
- [ ] CSRF disabled (stateless JWT)
- [ ] Session policy set to STATELESS
- [ ] Access tokens expire in 15 minutes or less
- [ ] Refresh tokens expire in 30 days or less
- [ ] Refresh token rotation enabled
- [ ] Reuse grace period set (30 seconds)
- [ ] Reuse detection triggers session revocation
- [ ] No sensitive data in JWT payload (only user ID, username, role)
- [ ] Token validation includes signature, expiration, issuer checks
- [ ] Passwordless auth disabled in production profile
- [ ] Security headers configured (CSP, X-Frame-Options, etc.)
- [ ] Scheduled cleanup job for expired tokens
- [ ] Logging for authentication events (success/failure)

---

## References

This research synthesizes findings from industry best practices, official documentation, and security standards:

- OAuth 2.1 Security Best Current Practice (RFC 9700)
- OWASP JWT Security Cheat Sheet
- Spring Security Reference Documentation (v6.x)
- jjwt Official Documentation and GitHub
- Auth0 JWT Best Practices
- Multiple Spring Boot JWT implementation guides

**Research Date**: 2026-02-08
**Status**: ✅ All technical decisions resolved
**Next Step**: Proceed to Phase 1 (Data Model and API Contracts)
