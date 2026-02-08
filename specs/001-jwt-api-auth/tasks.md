# Tasks: JWT Authentication for API

**Input**: Design documents from `/specs/001-jwt-api-auth/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/auth-api.yaml

**Tests**: Not requested in specification - tests are OPTIONAL per constitution. Implementation tasks only.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Single Spring Boot project structure:
- `src/main/java/com/example/todolist/` - Java source code
- `src/main/resources/` - Application resources and migrations
- `pom.xml` - Maven dependencies

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add JWT and Spring Security dependencies, configure application properties

- [ ] T001 Add jjwt dependencies to pom.xml (jjwt-api, jjwt-impl, jjwt-jackson version 0.12.6)
- [ ] T002 Add spring-boot-starter-security dependency to pom.xml
- [ ] T003 [P] Add JWT configuration properties to src/main/resources/application.yml (access/refresh token secrets, expiration times, grace period)
- [ ] T004 [P] Generate JWT secrets and update .env file with JWT_ACCESS_SECRET and JWT_REFRESH_SECRET (256-bit secrets)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T005 Create RefreshToken entity in src/main/java/com/example/todolist/model/RefreshToken.java with all fields (id, user_id, token_hash, jti, token_family_id, expires_at, valid_until, created_at, replaced_at, replaced_by_jti, device_info, revoked)
- [ ] T006 Update User entity in src/main/java/com/example/todolist/model/User.java to add nullable password field
- [ ] T007 Create RefreshTokenRepository in src/main/java/com/example/todolist/repository/RefreshTokenRepository.java with query methods (findByTokenHash, findByJti, findByUserIdAndRevokedFalseAndExpiresAtAfter, deleteByExpiresAtBeforeAndRevokedTrue, deleteByExpiresAtBefore, deleteByTokenFamilyId)
- [ ] T008 Generate Liquibase migration for adding password column to users table using make makemigration NAME=add_password_to_users
- [ ] T009 Generate Liquibase migration for creating refresh_tokens table with indexes using make makemigration NAME=create_refresh_tokens_table
- [ ] T010 Apply database migrations using make migrate
- [ ] T011 Create JwtService in src/main/java/com/example/todolist/service/JwtService.java for token generation and validation (generateAccessToken, generateRefreshToken, validateToken, extractClaims, getSigningKey methods)
- [ ] T012 [P] Create LoginRequest DTO in src/main/java/com/example/todolist/dto/LoginRequest.java with username and optional password fields
- [ ] T013 [P] Create LoginResponse DTO in src/main/java/com/example/todolist/dto/LoginResponse.java with accessToken, refreshToken, tokenType, expiresIn, username, role fields
- [ ] T014 [P] Create RefreshRequest DTO in src/main/java/com/example/todolist/dto/RefreshRequest.java with refreshToken field
- [ ] T015 [P] Create RefreshResponse DTO in src/main/java/com/example/todolist/dto/RefreshResponse.java with accessToken, refreshToken, tokenType, expiresIn fields
- [ ] T016 [P] Create SessionInfo DTO in src/main/java/com/example/todolist/dto/SessionInfo.java with sessionId, deviceInfo, createdAt, expiresAt, isCurrentSession fields
- [ ] T017 Create SecurityConfig in src/main/java/com/example/todolist/config/SecurityConfig.java with SecurityFilterChain bean (disable CSRF, configure public/protected endpoints, set stateless session management)
- [ ] T018 Create JwtAuthenticationFilter in src/main/java/com/example/todolist/security/JwtAuthenticationFilter.java extending OncePerRequestFilter (extract Bearer token from Authorization header, validate token, set SecurityContext)
- [ ] T019 Create JwtAuthenticationEntryPoint in src/main/java/com/example/todolist/security/JwtAuthenticationEntryPoint.java implementing AuthenticationEntryPoint (handle 401 unauthorized errors with JSON response)
- [ ] T020 Create UsernameOnlyAuthenticationProvider in src/main/java/com/example/todolist/security/UsernameOnlyAuthenticationProvider.java for MVP username-only authentication (dev profile only, lookup user by username without password validation)
- [ ] T021 Update SecurityConfig to add JWT filter before UsernamePasswordAuthenticationFilter and configure UsernameOnlyAuthenticationProvider

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - User Authentication (Priority: P1) 🎯 MVP

**Goal**: Users can authenticate with username and receive JWT access/refresh tokens to access protected endpoints

**Independent Test**: Attempt to access /api/todos without token (should get 401), then authenticate at /api/auth/login with username, receive tokens, access /api/todos with access token (should succeed)

### Implementation for User Story 1

- [ ] T022 [US1] Create AuthenticationService in src/main/java/com/example/todolist/service/AuthenticationService.java with login method (authenticate user by username, generate access and refresh tokens, create RefreshToken entity, save to database, return LoginResponse)
- [ ] T023 [US1] Create RefreshTokenService in src/main/java/com/example/todolist/service/RefreshTokenService.java with createRefreshToken method (generate refresh token JWT, hash token with SHA-256, create RefreshToken entity with jti/tokenFamilyId/expiresAt/validUntil, save to database, return token string)
- [ ] T024 [US1] Create AuthController in src/main/java/com/example/todolist/controller/AuthController.java with @RestController and @RequestMapping("/api/auth")
- [ ] T025 [US1] Implement POST /api/auth/login endpoint in AuthController (accept LoginRequest, extract device info from HttpServletRequest, call AuthenticationService.login, return LoginResponse with 200 status)
- [ ] T026 [US1] Add validation annotations to LoginRequest DTO (@NotBlank for username, @Size constraints)
- [ ] T027 [US1] Add @Slf4j logging to AuthenticationService for login attempts (log username, success/failure, timestamp)
- [ ] T028 [US1] Add @Slf4j logging to JwtService for token generation and validation operations
- [ ] T029 [US1] Add error handling to AuthController login endpoint (catch UsernameNotFoundException, return 401 with error message, catch general exceptions, return 500 with error message)
- [ ] T030 [US1] Verify JWT token includes required claims (sub=username, userId, role, iss=todolist-api, iat, exp, jti) in JwtService.generateAccessToken
- [ ] T031 [US1] Update SecurityConfig to mark /api/auth/login as permitAll (public endpoint not requiring authentication)

**Checkpoint**: At this point, User Story 1 should be fully functional - users can login and access protected endpoints with JWT tokens

---

## Phase 4: User Story 2 - Token Refresh (Priority: P2)

**Goal**: Users can obtain new access tokens using their refresh token without re-authenticating, maintaining seamless sessions

**Independent Test**: Login to get tokens, wait for access token to expire (or use expired token), call /api/auth/refresh with refresh token, receive new access and refresh tokens, use new access token to access protected endpoint (should succeed)

### Implementation for User Story 2

- [ ] T032 [US2] Implement validateAndRotateRefreshToken method in RefreshTokenService (find token by hash, check not revoked, check not expired, check within grace period or first use, generate new tokens, mark old token as replaced, save new RefreshToken entity, return new tokens)
- [ ] T033 [US2] Implement refresh method in AuthenticationService (call RefreshTokenService.validateAndRotateRefreshToken, extract user from refresh token claims, generate new access token, return RefreshResponse with new access and refresh tokens)
- [ ] T034 [US2] Implement POST /api/auth/refresh endpoint in AuthController (accept RefreshRequest, extract device info, call AuthenticationService.refresh, return RefreshResponse with 200 status)
- [ ] T035 [US2] Add error handling for expired refresh tokens in RefreshTokenService (throw ExpiredJwtException, handle in AuthController with 401 response)
- [ ] T036 [US2] Add error handling for revoked refresh tokens in RefreshTokenService (check revoked flag, throw custom RevokedException, handle in AuthController with 401 response message about suspicious activity)
- [ ] T037 [US2] Add error handling for refresh token reuse detection in RefreshTokenService (if used after grace period and already replaced, revoke entire token family by tokenFamilyId, throw SecurityException)
- [ ] T038 [US2] Add @Slf4j logging to RefreshTokenService for token rotation events (log jti, tokenFamilyId, reuse detection events, revocation events)
- [ ] T039 [US2] Implement scheduled cleanup job in RefreshTokenService with @Scheduled annotation (cron: "0 0 2 * * *" daily at 2 AM, delete expired tokens older than 30 days, delete revoked tokens past expiration)
- [ ] T040 [US2] Add validation annotations to RefreshRequest DTO (@NotBlank for refreshToken)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently - users can login and seamlessly refresh their sessions

---

## Phase 5: User Story 3 - User Identity in Token (Priority: P3)

**Goal**: JWT tokens contain user identity information (user ID, username, role) accessible to the application without database lookups

**Independent Test**: Login to get access token, decode JWT token (using jwt.io or programmatically), verify payload contains sub (username), userId, and role claims, use token to access endpoint and verify SecurityContext has correct user information

### Implementation for User Story 3

- [ ] T041 [US3] Verify JwtService.generateAccessToken includes userId claim in token payload (already should be done from T030, verify implementation)
- [ ] T042 [US3] Verify JwtService.generateAccessToken includes role claim in token payload (already should be done from T030, verify implementation)
- [ ] T043 [US3] Verify JwtAuthenticationFilter extracts userId and role from token claims and makes them available in SecurityContext
- [ ] T044 [US3] Add helper method to JwtService to extract specific claim by name (extractClaim with Function parameter for type-safe claim extraction)
- [ ] T045 [US3] Document JWT token structure and claims in code comments or developer documentation (access token: sub, userId, role, iss, iat, exp; refresh token: sub, userId, jti, tokenFamilyId, iss, iat, exp, typ=refresh)

**Checkpoint**: All user stories should now be independently functional - JWT tokens are self-contained with user identity for stateless authentication

---

## Phase 6: Session Management (Priority: P4) - OPTIONAL ENHANCEMENT

**Goal**: Users can view and manage their active sessions across multiple devices

**Independent Test**: Login from multiple devices/browsers, call GET /api/auth/sessions to see all active sessions, logout from specific device using DELETE /api/auth/sessions/{id}, verify that device can no longer refresh tokens

### Implementation for Session Management

- [ ] T046 [P] Implement findActiveSessions method in RefreshTokenService (find all non-revoked, non-expired refresh tokens for user, map to SessionInfo DTOs with session details)
- [ ] T047 Implement GET /api/auth/sessions endpoint in AuthController (require authentication, get username from SecurityContext, call RefreshTokenService.findActiveSessions, return List<SessionInfo> with 200 status)
- [ ] T048 Implement POST /api/auth/logout endpoint in AuthController (require authentication, accept RefreshRequest, find and revoke specific refresh token, return success message with 200 status)
- [ ] T049 Implement DELETE /api/auth/sessions endpoint in AuthController (require authentication, get username from SecurityContext, revoke all user's refresh tokens, return success message with count of sessions revoked)
- [ ] T050 Implement DELETE /api/auth/sessions/{sessionId} endpoint in AuthController (require authentication, verify session belongs to current user, revoke specific refresh token by ID, return success message or 403 if not user's session or 404 if not found)
- [ ] T051 Add @Slf4j logging to session management operations (log session listing, logout events, revocation events)

**Checkpoint**: Complete session management enables users to view and control their active sessions across all devices

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories and finalize implementation

- [ ] T052 [P] Add JavaDoc comments to all public methods in JwtService, AuthenticationService, RefreshTokenService
- [ ] T053 [P] Add JavaDoc comments to all endpoints in AuthController documenting request/response formats
- [ ] T054 [P] Verify all services use @RequiredArgsConstructor for dependency injection (per constitution Lombok standards)
- [ ] T055 [P] Verify all entities use @Getter, @Setter, @NoArgsConstructor (per constitution Lombok standards)
- [ ] T056 Review and clean up unused imports in all new classes
- [ ] T057 Verify application runs successfully with make run and authentication endpoints are accessible
- [ ] T058 Test complete authentication flow per quickstart.md validation (login → access protected endpoint → refresh token → logout)
- [ ] T059 [P] Verify all database migrations applied correctly with make showmigrations
- [ ] T060 [P] Verify JWT secrets are loaded from environment variables, not hardcoded
- [ ] T061 Add error response handling for common JWT exceptions (MalformedJwtException, ExpiredJwtException, SignatureException) in JwtAuthenticationFilter with appropriate 401 responses

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-5)**: All depend on Foundational phase completion
  - User Story 1 (P1): Can start after Foundational - No dependencies on other stories
  - User Story 2 (P2): Can start after Foundational - Depends on US1 services (AuthenticationService, RefreshTokenService) but logically independent
  - User Story 3 (P3): Can start after Foundational - Primarily validation of existing implementation from US1
  - Session Management (P4 - Optional): Depends on US1 and US2 completion
- **Polish (Phase 7)**: Depends on all implemented user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Foundation for authentication - MUST be completed first
- **User Story 2 (P2)**: Extends US1 with refresh capability - Can start after US1 services exist, but should be completed before deploying to production
- **User Story 3 (P3)**: Validates US1 implementation - Can be done in parallel with US2 as it's mostly verification
- **Session Management (P4)**: Optional enhancement - Can be added any time after US1 and US2 complete

### Within Each User Story

- Phase 1 (Setup): Dependencies (T001-T002) before configuration (T003-T004)
- Phase 2 (Foundational):
  - Entities (T005-T006) before repositories (T007)
  - Migrations (T008-T010) can run in parallel with entity/repo creation
  - DTOs (T012-T016) can all run in parallel [P]
  - JwtService (T011) before AuthenticationService
  - Security config (T017-T021) after DTOs and services
- Phase 3 (US1):
  - Services (T022-T023) before controller (T024)
  - Controller creation (T024) before endpoints (T025)
  - Core implementation (T022-T025) before validation/logging (T026-T029)
- Phase 4 (US2):
  - RefreshTokenService methods (T032) before AuthenticationService.refresh (T033)
  - Service methods before endpoint (T034)
  - Core implementation before error handling (T035-T037)
- Phase 5 (US3):
  - Verification tasks (T041-T043) before enhancements (T044-T045)
- Phase 6 (Session Mgmt):
  - Service method (T046) before endpoints (T047-T050)
- Phase 7 (Polish):
  - Documentation (T052-T053) can run in parallel [P]
  - Verification tasks (T054-T061) can run in parallel [P]

### Parallel Opportunities

**Phase 1 (Setup)**:
- T003 and T004 can run in parallel [P] after dependencies added

**Phase 2 (Foundational)**:
- All DTO creation tasks (T012-T016) can run in parallel [P] (different files)
- Migrations (T008-T009) can be generated in parallel [P]

**Phase 7 (Polish)**:
- JavaDoc tasks (T052-T053) can run in parallel [P] (different files)
- Verification tasks (T054-T056, T059-T061) can run in parallel [P]

---

## Parallel Example: Foundational Phase

```bash
# Launch all DTO creation tasks together (Phase 2):
Task T012: "Create LoginRequest DTO in src/main/java/com/example/todolist/dto/LoginRequest.java"
Task T013: "Create LoginResponse DTO in src/main/java/com/example/todolist/dto/LoginResponse.java"
Task T014: "Create RefreshRequest DTO in src/main/java/com/example/todolist/dto/RefreshRequest.java"
Task T015: "Create RefreshResponse DTO in src/main/java/com/example/todolist/dto/RefreshResponse.java"
Task T016: "Create SessionInfo DTO in src/main/java/com/example/todolist/dto/SessionInfo.java"

# All 5 DTOs can be created simultaneously (different files, no dependencies)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T004)
2. Complete Phase 2: Foundational (T005-T021) - CRITICAL foundation
3. Complete Phase 3: User Story 1 (T022-T031) - Basic authentication
4. **STOP and VALIDATE**: Test login flow and protected endpoint access
5. Deploy MVP if ready

**MVP Scope**: After Phase 3, users can authenticate and access protected endpoints with JWT tokens. This is a complete, working authentication system.

### Incremental Delivery

1. **Foundation** (Phase 1-2): Setup + Infrastructure → Ready for development
2. **MVP** (Phase 3): User Story 1 → Users can login and access protected endpoints
3. **Enhanced UX** (Phase 4): User Story 2 → Seamless token refresh, no re-authentication needed
4. **Validation** (Phase 5): User Story 3 → Verify token contains user identity (mostly validation of existing work)
5. **Optional** (Phase 6): Session Management → Multi-device session control
6. **Production Ready** (Phase 7): Polish → Documentation, cleanup, final validation

Each phase adds value without breaking previous functionality.

### Parallel Team Strategy

With multiple developers:

1. **Together**: Complete Setup (Phase 1) and Foundational (Phase 2)
2. **Once Foundational is done**:
   - Developer A: User Story 1 (Phase 3) - Core authentication
   - Developer B: Can start User Story 2 (Phase 4) in parallel once T022-T023 from US1 are done
   - Developer C: Can work on documentation and polish (Phase 7) in parallel
3. Stories integrate independently without conflicts

---

## Task Count Summary

- **Phase 1 (Setup)**: 4 tasks
- **Phase 2 (Foundational)**: 17 tasks (BLOCKING - must complete before user stories)
- **Phase 3 (User Story 1 - MVP)**: 10 tasks
- **Phase 4 (User Story 2)**: 9 tasks
- **Phase 5 (User Story 3)**: 5 tasks
- **Phase 6 (Session Management - Optional)**: 6 tasks
- **Phase 7 (Polish)**: 10 tasks

**Total**: 61 tasks

**Parallel Tasks**: 11 tasks marked [P] can run in parallel
**MVP Tasks**: 31 tasks (Phases 1-3) for minimum viable product
**Production Ready**: 50 tasks (Phases 1-5, 7) excluding optional session management

---

## Notes

- [P] tasks = different files, no dependencies, can run in parallel
- [Story] label maps task to specific user story for traceability
- Each user story should be independently testable at its checkpoint
- Tests are OPTIONAL per constitution - not included in this task list
- Follow Lombok standards: @RequiredArgsConstructor for services/controllers, @Getter/@Setter/@NoArgsConstructor for entities
- Follow Slf4j logging standard: @Slf4j annotation on all classes that need logging
- All timestamps use Instant (UTC-first per constitution)
- All database changes via Liquibase migrations (ddl-auto=none per constitution)
- Commit after each logical task group or phase completion
- Stop at checkpoints to validate user story independently before proceeding
