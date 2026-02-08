# Data Model: JWT Authentication

**Feature**: 001-jwt-api-auth
**Date**: 2026-02-08
**Purpose**: Define data structures for JWT authentication feature

---

## Overview

This feature adds JWT authentication to the existing Todo List API. The data model includes one new entity (`RefreshToken`) and modifications to the existing `User` entity. All entities follow the project constitution's UTC-first timestamp principle using `java.time.Instant`.

---

## Entities

### 1. RefreshToken (NEW)

**Purpose**: Store long-lived refresh tokens for obtaining new access tokens without re-authentication. Supports token rotation with reuse detection and multi-device sessions.

**Table Name**: `refresh_tokens`

**Fields**:

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | Long | PK, Auto-increment | Primary key |
| `user_id` | Long | FK to users, NOT NULL, Indexed | User who owns this token |
| `token_hash` | String | VARCHAR(64), UNIQUE, NOT NULL | SHA-256 hash of refresh token (never store plaintext) |
| `jti` | String | VARCHAR(36), UNIQUE, NOT NULL | JWT ID (UUID) for tracking specific tokens |
| `token_family_id` | String | VARCHAR(36), NOT NULL, Indexed | UUID tracking rotation chain (all tokens in rotation share family ID) |
| `expires_at` | Instant | TIMESTAMP WITH TIME ZONE, NOT NULL, Indexed | Absolute expiration time (14 days from creation) |
| `valid_until` | Instant | TIMESTAMP WITH TIME ZONE, NOT NULL | Grace period expiration (for reuse tolerance) |
| `created_at` | Instant | TIMESTAMP WITH TIME ZONE, NOT NULL, updatable=false | Token creation timestamp (UTC) |
| `replaced_at` | Instant | TIMESTAMP WITH TIME ZONE, NULL | When token was rotated (null if never rotated) |
| `replaced_by_jti` | String | VARCHAR(36), NULL | JTI of token that replaced this one |
| `device_info` | String | VARCHAR(255), NULL | User agent + IP for auditing (e.g., "Chrome/119.0 192.168.1.1") |
| `revoked` | Boolean | NOT NULL, Default: false | True if token explicitly revoked (logout, breach detection) |

**Relationships**:
- `ManyToOne` with `User` (many refresh tokens per user for multi-device support)

**Indexes**:
- Primary key on `id`
- Unique index on `token_hash` (for fast lookup during validation)
- Unique index on `jti` (for rotation chain tracking)
- Index on `user_id` (for finding user's active sessions)
- Index on `token_family_id` (for revoking entire rotation chain)
- Index on `expires_at` (for cleanup queries)
- Composite index on `user_id, revoked, expires_at` (for active session queries)

**Validation Rules**:
- `token_hash` must be exactly 64 characters (SHA-256 hex output)
- `jti` and `token_family_id` must be valid UUIDs
- `expires_at` must be in the future at creation
- `valid_until` must be between `created_at` and `expires_at`
- `replaced_at` can only be set once (no re-rotation of same token)
- If `revoked` is true, token cannot be used regardless of expiration

**State Transitions**:
```
ACTIVE → ROTATED → EXPIRED/REVOKED
  ↓
REVOKED (if breach detected)
```

1. **ACTIVE**: Token created, not yet rotated, not revoked, not expired
2. **ROTATED**: Token used once, `replaced_at` set, still valid within grace period
3. **EXPIRED**: `expires_at` or `valid_until` has passed
4. **REVOKED**: Manually revoked (logout) or auto-revoked (reuse detection)

**Cleanup Strategy**:
- Expired tokens older than 30 days → DELETE
- Revoked tokens → DELETE after expiration
- Keep active rotation chains until family expires

---

### 2. User (MODIFIED)

**Purpose**: Existing user entity extended with optional password field for future password-based authentication.

**Table Name**: `users`

**New Fields**:

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `password` | String | VARCHAR(255), NULL | BCrypt-hashed password (null for MVP username-only auth) |

**Existing Fields** (unchanged):

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | Long | PK, Auto-increment | Primary key |
| `username` | String | VARCHAR(255), UNIQUE, NOT NULL | Unique username for authentication |
| `role` | Role enum | VARCHAR(8), NOT NULL | User role (ADMIN, MANAGER, USER) |
| `created_at` | Instant | TIMESTAMP WITH TIME ZONE, NOT NULL, updatable=false | Account creation timestamp (UTC) |

**Relationships**:
- `OneToMany` with `Todo` (existing)
- `OneToMany` with `Invoice` (existing)
- `OneToMany` with `RefreshToken` (NEW)

**Validation Rules**:
- `username` must be unique across all users
- `password` format validated only if not null:
  - Must start with `{bcrypt}` prefix (DelegatingPasswordEncoder format)
  - BCrypt hash length: 60 characters after prefix
- `role` must be one of: ADMIN, MANAGER, USER

**Migration Notes**:
- `password` column added as nullable for backward compatibility
- Existing users have `password = null` (username-only authentication)
- Future users will have `password != null` (password-based authentication)
- Hybrid authentication provider handles both cases

---

## Data Transfer Objects (DTOs)

DTOs separate API contracts from internal entities, preventing direct entity exposure and enabling independent evolution.

### 3. LoginRequest

**Purpose**: Capture user credentials for authentication

**Fields**:

| Field | Type | Validation | Description |
|-------|------|------------|-------------|
| `username` | String | @NotBlank, @Size(min=3, max=50) | Username for authentication |
| `password` | String | @Size(max=100) (optional for MVP) | Password (ignored in username-only auth) |

**Example JSON**:
```json
{
  "username": "johndoe"
}
```

**Notes**:
- `password` field included for API consistency but ignored in MVP
- Future password authentication will use same DTO structure

---

### 4. LoginResponse

**Purpose**: Return authentication tokens to client

**Fields**:

| Field | Type | Description |
|-------|------|-------------|
| `accessToken` | String | Short-lived JWT (15 minutes) |
| `refreshToken` | String | Long-lived JWT (14 days) |
| `tokenType` | String | Always "Bearer" |
| `expiresIn` | Long | Access token expiration in seconds (900) |
| `username` | String | Authenticated username |
| `role` | String | User role (ADMIN/MANAGER/USER) |

**Example JSON**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "username": "johndoe",
  "role": "USER"
}
```

---

### 5. RefreshRequest

**Purpose**: Request new access token using refresh token

**Fields**:

| Field | Type | Validation | Description |
|-------|------|------------|-------------|
| `refreshToken` | String | @NotBlank | Current refresh token |

**Example JSON**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

### 6. RefreshResponse

**Purpose**: Return new tokens after successful refresh

**Fields**:

| Field | Type | Description |
|-------|------|-------------|
| `accessToken` | String | New short-lived JWT (15 minutes) |
| `refreshToken` | String | New long-lived JWT (14 days) - rotated |
| `tokenType` | String | Always "Bearer" |
| `expiresIn` | Long | Access token expiration in seconds (900) |

**Example JSON**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Notes**:
- Both access token AND refresh token are rotated on each refresh
- Old refresh token becomes invalid after grace period (30 seconds)

---

### 7. SessionInfo

**Purpose**: Display active user sessions for multi-device management

**Fields**:

| Field | Type | Description |
|-------|------|-------------|
| `sessionId` | Long | RefreshToken ID |
| `deviceInfo` | String | User agent + IP |
| `createdAt` | Instant | When session started (ISO-8601 format) |
| `expiresAt` | Instant | When session expires (ISO-8601 format) |
| `isCurrentSession` | Boolean | True if this is the requesting session |

**Example JSON**:
```json
{
  "sessionId": 42,
  "deviceInfo": "Chrome/119.0 192.168.1.100",
  "createdAt": "2026-02-08T10:30:00Z",
  "expiresAt": "2026-02-22T10:30:00Z",
  "isCurrentSession": true
}
```

---

## JWT Token Structure

JWT tokens are self-contained and include claims for stateless authentication.

### Access Token Claims

**Header**:
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload**:
```json
{
  "sub": "johndoe",           // Subject (username)
  "userId": 123,               // User ID for quick lookup
  "role": "USER",              // User role (for future authorization)
  "iss": "todolist-api",       // Issuer
  "iat": 1707390600,           // Issued at (Unix timestamp)
  "exp": 1707391500,           // Expires at (Unix timestamp, +15 min)
  "jti": "a1b2c3d4-e5f6-..."   // JWT ID (not stored, for uniqueness)
}
```

**Expiration**: 15 minutes (900 seconds)

---

### Refresh Token Claims

**Header**:
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload**:
```json
{
  "sub": "johndoe",                      // Subject (username)
  "userId": 123,                         // User ID
  "jti": "a1b2c3d4-e5f6-...",           // JWT ID (stored in DB)
  "tokenFamilyId": "f9e8d7c6-b5a4-...", // Rotation chain ID
  "iss": "todolist-api",                 // Issuer
  "iat": 1707390600,                     // Issued at
  "exp": 1708600200,                     // Expires at (+14 days)
  "typ": "refresh"                       // Token type identifier
}
```

**Expiration**: 14 days (1,209,600 seconds)

**Storage**: SHA-256 hash stored in `refresh_tokens` table

---

## Database Migrations

### Migration 1: Add password to users

**File**: `src/main/resources/db/changelog/changes/0007_add_password_to_users.yaml`

```yaml
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

  - changeSet:
      id: tag-0007
      author: system
      changes:
        - tagDatabase:
            tag: "0007"
```

---

### Migration 2: Create refresh_tokens table

**File**: `src/main/resources/db/changelog/changes/0008_create_refresh_tokens_table.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 0008-create-refresh-tokens-table
      author: system
      changes:
        - createTable:
            tableName: refresh_tokens
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    nullable: false

              - column:
                  name: user_id
                  type: BIGINT
                  constraints:
                    nullable: false
                    foreignKeyName: fk_refresh_token_user
                    references: users(id)

              - column:
                  name: token_hash
                  type: VARCHAR(64)
                  constraints:
                    nullable: false
                    unique: true

              - column:
                  name: jti
                  type: VARCHAR(36)
                  constraints:
                    nullable: false
                    unique: true

              - column:
                  name: token_family_id
                  type: VARCHAR(36)
                  constraints:
                    nullable: false

              - column:
                  name: expires_at
                  type: TIMESTAMP WITH TIME ZONE
                  constraints:
                    nullable: false

              - column:
                  name: valid_until
                  type: TIMESTAMP WITH TIME ZONE
                  constraints:
                    nullable: false

              - column:
                  name: created_at
                  type: TIMESTAMP WITH TIME ZONE
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false

              - column:
                  name: replaced_at
                  type: TIMESTAMP WITH TIME ZONE

              - column:
                  name: replaced_by_jti
                  type: VARCHAR(36)

              - column:
                  name: device_info
                  type: VARCHAR(255)

              - column:
                  name: revoked
                  type: BOOLEAN
                  defaultValueBoolean: false
                  constraints:
                    nullable: false

        - createIndex:
            tableName: refresh_tokens
            indexName: idx_refresh_token_user_id
            columns:
              - column:
                  name: user_id

        - createIndex:
            tableName: refresh_tokens
            indexName: idx_refresh_token_family_id
            columns:
              - column:
                  name: token_family_id

        - createIndex:
            tableName: refresh_tokens
            indexName: idx_refresh_token_expires_at
            columns:
              - column:
                  name: expires_at

        - createIndex:
            tableName: refresh_tokens
            indexName: idx_refresh_token_active_sessions
            columns:
              - column:
                  name: user_id
              - column:
                  name: revoked
              - column:
                  name: expires_at

  - changeSet:
      id: tag-0008
      author: system
      changes:
        - tagDatabase:
            tag: "0008"
```

---

## Entity Relationship Diagram

```
┌─────────────┐
│    User     │
├─────────────┤
│ id (PK)     │
│ username    │
│ role        │
│ password    │◄─── NEW (nullable)
│ created_at  │
└──────┬──────┘
       │
       │ 1:N
       │
       ▼
┌──────────────────┐
│  RefreshToken    │◄─── NEW ENTITY
├──────────────────┤
│ id (PK)          │
│ user_id (FK)     │
│ token_hash       │
│ jti              │
│ token_family_id  │
│ expires_at       │
│ valid_until      │
│ created_at       │
│ replaced_at      │
│ replaced_by_jti  │
│ device_info      │
│ revoked          │
└──────────────────┘
```

---

## Validation Summary

All entities comply with project constitution:

✅ **UTC-First Timestamps**: All temporal fields use `Instant`
✅ **Layered Architecture**: Entities in model layer, no business logic
✅ **Database Migration**: Schema changes via Liquibase migrations
✅ **Naming Convention**: snake_case in database (via CamelCaseToUnderscoresNamingStrategy)
✅ **Lombok Usage**: `@Getter`, `@Setter`, `@NoArgsConstructor` on entities

---

## Security Considerations

1. **Never store refresh tokens in plaintext**: Use SHA-256 hash in database
2. **Token rotation**: New refresh token issued on every use
3. **Grace period**: 30-second window for legitimate retries
4. **Reuse detection**: Using token after grace period triggers family revocation
5. **Multi-device support**: Multiple active refresh tokens per user
6. **Audit trail**: Device info stored for security monitoring
7. **Automatic cleanup**: Expired tokens removed via scheduled job

---

## Next Steps

1. Create JPA entities (`RefreshToken.java`, update `User.java`)
2. Create repositories (`RefreshTokenRepository.java`)
3. Generate Liquibase migrations
4. Create DTOs for API contracts
5. Proceed to API contract definition (contracts/ directory)
