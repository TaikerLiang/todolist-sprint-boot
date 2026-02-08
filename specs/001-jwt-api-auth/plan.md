# Implementation Plan: JWT Authentication for API

**Branch**: `001-jwt-api-auth` | **Date**: 2026-02-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-jwt-api-auth/spec.md`

**Note**: This plan focuses on authentication only - authorization (ABAC/RBAC) is out of scope for this feature.

## Summary

Implement JWT-based authentication for the Todo List API to verify user identity and protect API endpoints. Users authenticate with username (password support deferred to future phase) and receive two tokens: a short-lived access token (15-30 min) for API requests and a long-lived refresh token (7-30 days) for obtaining new access tokens without re-authentication. The JWT access token includes user identity claims (user ID, username, role) for application use. Protected endpoints require a valid JWT token but do not enforce authorization rules (permissions/access control handled in future feature).

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.1.5, Spring Security, JWT library (NEEDS CLARIFICATION: jjwt vs spring-security-oauth2-jose), Lombok 1.18.36
**Storage**: PostgreSQL (existing), new RefreshToken table via Liquibase migration
**Testing**: JUnit 5, Spring Boot Test, MockMvc (API tests), H2 in-memory database (integration tests)
**Target Platform**: Linux/macOS server (Spring Boot executable JAR)
**Project Type**: Single backend application (existing Spring Boot REST API)
**Performance Goals**: Token validation < 50ms latency, authentication < 3 seconds, 1000 concurrent token validations
**Constraints**: Stateless authentication (no server-side sessions), JWT tokens must be tamper-proof, refresh tokens stored securely in database
**Scale/Scope**: Supports existing User entity, 3 new REST endpoints (login, refresh, logout), 1 new entity (RefreshToken), Spring Security configuration

**Key Technical Decisions Needing Research**:
1. NEEDS CLARIFICATION: JWT library choice - jjwt (io.jsonwebtoken) vs Spring Security OAuth2 JOSE (org.springframework.security.oauth2.jose)
2. NEEDS CLARIFICATION: Spring Security configuration approach - SecurityFilterChain vs WebSecurityConfigurerAdapter (deprecated)
3. NEEDS CLARIFICATION: JWT signing algorithm - HMAC (HS256/HS512) vs RSA (RS256/RS512)
4. NEEDS CLARIFICATION: Token storage in requests - Authorization header format (Bearer token) and extraction mechanism
5. NEEDS CLARIFICATION: Refresh token rotation strategy - single-use refresh tokens vs reusable until expiration
6. NEEDS CLARIFICATION: User authentication without passwords - how to implement username-only auth for MVP

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Layered Architecture ✅ COMPLIANT

- **Model Layer**: New `RefreshToken` JPA entity (no business logic)
- **Repository Layer**: New `RefreshTokenRepository extends JpaRepository`
- **Service Layer**: New `AuthenticationService` for login/refresh logic, `JwtService` for token operations
- **Controller Layer**: New `AuthController` with REST endpoints (`/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`)

**Verification**: Business logic (token generation, validation, user lookup) stays in service layer. Controllers delegate to services. Repositories only accessed by services.

### II. UTC-First Timestamps ✅ COMPLIANT

- `RefreshToken` entity uses `Instant` for `createdAt` and `expiresAt` fields
- JWT `exp` and `iat` claims use Unix epoch timestamps (UTC-based)
- No `LocalDateTime` usage

**Verification**: All temporal fields follow UTC-first principle.

### III. Database Migration Discipline ✅ COMPLIANT

- New `RefreshToken` entity requires Liquibase migration
- Will generate migration with `make makemigration NAME=create_refresh_token_table`
- Hibernate `ddl-auto=none` already configured (no auto-schema generation)
- Migration includes indexes for performance (user_id, token, expiresAt)

**Verification**: No manual schema changes. Migration reviewed before applying.

### IV. RESTful API Standards ✅ COMPLIANT

- Resource-based URLs: `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`
- Proper HTTP methods: POST for login/refresh/logout
- HTTP status codes: 200 (success), 401 (invalid credentials/token), 400 (bad request)
- JSON request/response bodies with camelCase (following existing API convention)
- `@RestController` and `@RequestMapping("/api/auth")` usage

**Verification**: Follows existing API patterns in TodoController and UserController.

### V. Comprehensive Testing ⚠️ OPTIONAL (Not Requested)

Testing is optional unless explicitly requested by the user. If implemented later, will follow:
- **Unit Tests**: `AuthenticationService` with mocked repositories
- **Integration Tests**: Repository layer with H2 test database
- **API Tests**: Controller endpoints with MockMvc

**Status**: Tests not included in this feature unless requested. Test-first approach will be followed if added.

### VI. Build & Dependency Management ✅ COMPLIANT

- Java 21 source/target already configured in pom.xml
- Will add explicit JWT library dependency version (no version ranges)
- Lombok annotation processing already configured
- Maven build: `./mvnw clean package`

**Verification**: Dependency added to pom.xml with explicit version.

### Constitution Compliance Summary

**Status**: ✅ COMPLIANT with all NON-NEGOTIABLE principles

No violations detected. Feature follows:
- Layered architecture (new service/controller/repository layers)
- UTC timestamps for all temporal data
- Liquibase migration for schema changes
- RESTful API conventions
- Maven dependency management standards

**Re-check After Phase 1**: Will verify data model and API contracts maintain compliance.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/main/java/com/example/todolist/
├── model/
│   ├── User.java                    # Existing entity
│   ├── Role.java                    # Existing enum (ADMIN/MANAGER/USER)
│   └── RefreshToken.java            # NEW: Refresh token entity
├── repository/
│   ├── UserRepository.java          # Existing
│   └── RefreshTokenRepository.java  # NEW: Refresh token data access
├── service/
│   ├── UserService.java             # Existing
│   ├── AuthenticationService.java   # NEW: Login/refresh/logout logic
│   └── JwtService.java              # NEW: JWT token generation/validation
├── controller/
│   ├── UserController.java          # Existing
│   ├── TodoController.java          # Existing
│   └── AuthController.java          # NEW: /api/auth/* endpoints
├── config/
│   └── SecurityConfig.java          # NEW: Spring Security configuration
├── security/
│   ├── JwtAuthenticationFilter.java # NEW: Extract/validate JWT from requests
│   └── JwtAuthenticationEntryPoint.java # NEW: Handle auth errors (401)
├── dto/                             # NEW: Request/response DTOs
│   ├── LoginRequest.java
│   ├── LoginResponse.java
│   ├── RefreshRequest.java
│   └── RefreshResponse.java
└── TodolistApplication.java         # Existing main class

src/main/resources/
├── application.yml                   # Existing - add JWT config properties
└── db/changelog/
    ├── db.changelog-master.yaml     # Existing
    └── changes/
        └── 000X_create_refresh_token_table.yaml  # NEW: Migration

src/test/java/com/example/todolist/  # Optional - if tests requested
├── service/
│   └── AuthenticationServiceTest.java
├── controller/
│   └── AuthControllerTest.java
└── repository/
    └── RefreshTokenRepositoryTest.java

pom.xml                              # Updated: Add JWT library dependency
```

**Structure Decision**: This is a single Spring Boot backend application following the existing layered architecture. New authentication feature adds:
- 1 new entity (RefreshToken)
- 1 new repository (RefreshTokenRepository)
- 2 new services (AuthenticationService, JwtService)
- 1 new controller (AuthController)
- 2 new security components (JwtAuthenticationFilter, JwtAuthenticationEntryPoint)
- 1 new config class (SecurityConfig)
- 4 new DTOs for API contracts
- 1 new Liquibase migration

The structure maintains separation of concerns per constitution principles.

## Complexity Tracking

**Status**: ✅ NO VIOLATIONS

No complexity violations detected. Feature follows all constitution principles without deviations.

---

## Phase 0: Research (COMPLETED)

**Output**: `research.md`

All technical unknowns resolved:

1. ✅ **JWT Library**: jjwt (io.jsonwebtoken) v0.12.6
2. ✅ **Spring Security**: SecurityFilterChain with lambda DSL
3. ✅ **Signing Algorithm**: HS256 (HMAC with SHA-256)
4. ✅ **Token Storage**: Authorization: Bearer token (HTTP header)
5. ✅ **Refresh Strategy**: Token rotation with 30-second grace period
6. ✅ **Username-Only Auth**: Custom AuthenticationProvider for MVP

**Research Date**: 2026-02-08
**Status**: All decisions documented with rationale and alternatives considered

---

## Phase 1: Design & Contracts (COMPLETED)

### Data Model

**Output**: `data-model.md`

**Entities Defined**:
- `RefreshToken` (NEW) - Refresh token storage with rotation support
- `User` (MODIFIED) - Added nullable password field

**DTOs Defined**:
- `LoginRequest` - Authentication credentials
- `LoginResponse` - Access + refresh tokens
- `RefreshRequest` - Refresh token payload
- `RefreshResponse` - New rotated tokens
- `SessionInfo` - Active session details

**Migrations Planned**:
- `0007_add_password_to_users.yaml` - Add nullable password column
- `0008_create_refresh_tokens_table.yaml` - Create refresh_tokens table with indexes

### API Contracts

**Output**: `contracts/auth-api.yaml` (OpenAPI 3.0)

**Endpoints Defined**:
- `POST /api/auth/login` - Authenticate and obtain tokens
- `POST /api/auth/refresh` - Refresh access token
- `POST /api/auth/logout` - Revoke specific refresh token
- `GET /api/auth/sessions` - List active sessions
- `DELETE /api/auth/sessions` - Logout from all devices
- `DELETE /api/auth/sessions/{id}` - Logout from specific device

**HTTP Methods**: POST (auth operations), GET (session list), DELETE (session management)
**Status Codes**: 200 (success), 400 (bad request), 401 (unauthorized), 403 (forbidden), 404 (not found), 500 (server error)
**Content Type**: application/json

### Quickstart Guide

**Output**: `quickstart.md`

Developer guide with:
- 5-step quick start
- Step-by-step implementation instructions
- Code examples for all components
- Testing guide with cURL commands
- Troubleshooting section

---

## Final Constitution Check (Post-Design)

### Re-validation After Phase 1

**Status**: ✅ STILL COMPLIANT

All design decisions maintain constitution compliance:

1. ✅ **Layered Architecture**:
   - RefreshToken entity in model layer (no business logic)
   - RefreshTokenRepository in repository layer
   - AuthenticationService, JwtService in service layer
   - AuthController in controller layer

2. ✅ **UTC-First Timestamps**:
   - All RefreshToken temporal fields use `Instant`
   - JWT claims use Unix timestamps (UTC-based)

3. ✅ **Database Migration Discipline**:
   - Two Liquibase migrations defined
   - Hibernate ddl-auto remains `none`
   - All schema changes via migrations

4. ✅ **RESTful API Standards**:
   - Resource-based URLs (/api/auth/*)
   - Proper HTTP methods (POST for actions, GET for queries, DELETE for removal)
   - Standard status codes (200, 400, 401, 403, 404, 500)
   - JSON request/response bodies

5. ✅ **Build & Dependency Management**:
   - Explicit dependency versions (jjwt 0.12.6)
   - Java 21 compatibility maintained
   - Maven reproducible builds

**No new violations introduced during design phase.**

---

## Implementation Checklist

Use this checklist during implementation:

### Dependencies
- [ ] Add jjwt dependencies to pom.xml
- [ ] Add Spring Security dependency to pom.xml
- [ ] Verify Java 21 compatibility

### Configuration
- [ ] Add JWT configuration to application.yml
- [ ] Generate and store JWT secrets in .env file
- [ ] Configure Spring Security (stateless, CSRF disabled)

### Database
- [ ] Generate migration: add password to users
- [ ] Generate migration: create refresh_tokens table
- [ ] Apply migrations (make migrate)
- [ ] Verify indexes created

### Entities
- [ ] Create RefreshToken entity with Lombok annotations
- [ ] Update User entity with password field
- [ ] Verify relationships (User 1:N RefreshToken)

### Repositories
- [ ] Create RefreshTokenRepository with query methods
- [ ] Add findByUsername to UserRepository (if not exists)

### DTOs
- [ ] Create LoginRequest
- [ ] Create LoginResponse
- [ ] Create RefreshRequest
- [ ] Create RefreshResponse
- [ ] Create SessionInfo

### Services
- [ ] Implement JwtService (token generation/validation)
- [ ] Implement RefreshTokenService (CRUD, rotation, cleanup)
- [ ] Implement AuthenticationService (login/refresh/logout)
- [ ] Add @Slf4j logging to all services

### Security
- [ ] Create SecurityConfig with SecurityFilterChain
- [ ] Create JwtAuthenticationFilter
- [ ] Create UsernameOnlyAuthenticationProvider (dev profile)
- [ ] Create JwtAuthenticationEntryPoint (401 handler)

### Controllers
- [ ] Create AuthController with all endpoints
- [ ] Add @Valid validation
- [ ] Add @Slf4j logging
- [ ] Extract device info from requests

### Scheduled Jobs
- [ ] Create scheduled cleanup job for expired tokens
- [ ] Configure cron schedule (daily at 2 AM)

### Testing (Optional)
- [ ] Write unit tests for JwtService
- [ ] Write unit tests for AuthenticationService
- [ ] Write integration tests for AuthController
- [ ] Write repository tests for RefreshTokenRepository

### Documentation
- [ ] Update API documentation
- [ ] Update README if needed
- [ ] Document environment variables

---

## Deployment Notes

**Environment Variables Required**:
```bash
JWT_ACCESS_SECRET=<256-bit-secret>
JWT_REFRESH_SECRET=<different-256-bit-secret>
DB_HOST=localhost
DB_PORT=5432
DB_NAME=demo
DB_USER=default_user
DB_PASSWORD=default_pass
DB_URL=jdbc:postgresql://localhost:5432/demo
```

**Pre-Deployment Checklist**:
- [ ] JWT secrets are 256+ bits
- [ ] Secrets stored securely (not hardcoded)
- [ ] HTTPS enabled in production
- [ ] Database migrations applied
- [ ] Cleanup job configured
- [ ] Passwordless auth disabled in production profile

---

## Success Metrics

After implementation, verify these success criteria from spec.md:

- [ ] SC-001: Users authenticate and receive tokens within 3 seconds
- [ ] SC-002: System rejects 100% of invalid/expired tokens
- [ ] SC-003: Token tampering prevented via signature validation
- [ ] SC-004: 95% of authentication requests succeed on first attempt
- [ ] SC-005: 1000 concurrent token validations without degradation
- [ ] SC-006: Token validation adds < 50ms latency
- [ ] SC-007: All authentication events logged with timestamps
- [ ] SC-008: Refresh token flow completes within 2 seconds
- [ ] SC-009: JWT tokens contain correct user identity (ID, username, role)
