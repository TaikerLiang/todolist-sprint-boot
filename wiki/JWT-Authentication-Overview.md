# JWT Authentication Overview

Complete guide to JWT-based authentication in the Todo List API.

## 📋 Quick Facts

- **Authentication Method**: JWT (JSON Web Tokens)
- **Current Implementation**: Username-only (MVP)
- **Future**: Password authentication with BCrypt
- **Token Types**: Access tokens (15 min) + Refresh tokens (14 days)
- **Security**: HS256 signing, token rotation, reuse detection
- **Standard**: OAuth 2.1 compliant

---

## 🔑 How It Works

### 1. Login Flow

```
Client                          Server
  |                               |
  | POST /api/auth/login          |
  | {"username": "paul"}          |
  |------------------------------>|
  |                               | 1. Find user
  |                               | 2. Generate access token (15 min)
  |                               | 3. Generate refresh token (14 days)
  |                               | 4. Store refresh token hash in DB
  |                               |
  | 200 OK                        |
  | {                             |
  |   "accessToken": "eyJ...",    |
  |   "refreshToken": "eyJ...",   |
  |   "expiresIn": 900,           |
  |   "username": "paul",         |
  |   "role": "USER"              |
  | }                             |
  |<------------------------------|
```

### 2. Protected Endpoint Access

```
Client                          Server
  |                               |
  | GET /api/users                |
  | Authorization: Bearer eyJ...  |
  |------------------------------>|
  |                               | 1. Extract JWT from header
  |                               | 2. Validate signature
  |                               | 3. Check expiration
  |                               | 4. Extract user identity
  |                               | 5. Set security context
  |                               |
  | 200 OK                        |
  | [users data]                  |
  |<------------------------------|
```

### 3. Token Refresh Flow

```
Client                          Server
  |                               |
  | POST /api/auth/refresh        |
  | {"refreshToken": "eyJ..."}    |
  |------------------------------>|
  |                               | 1. Validate refresh token
  |                               | 2. Check if revoked
  |                               | 3. Check for reuse (security!)
  |                               | 4. Generate NEW access token
  |                               | 5. Generate NEW refresh token (rotation)
  |                               | 6. Mark old token as "replaced"
  |                               |
  | 200 OK                        |
  | {                             |
  |   "accessToken": "eyJ...",    |
  |   "refreshToken": "eyJ...",   |
  |   "expiresIn": 900            |
  | }                             |
  |<------------------------------|
```

---

## 🎫 Token Structure

### Access Token (JWT)

**Expiration**: 15 minutes

**Header**:
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload (Claims)**:
```json
{
  "sub": "paul",              // Subject (username)
  "userId": 2,                // User ID for quick lookup
  "role": "USER",             // User role (for authorization)
  "iss": "todolist-api",      // Issuer
  "iat": 1707390600,          // Issued at (Unix timestamp)
  "exp": 1707391500,          // Expires at (+15 min)
  "jti": "unique-id"          // JWT ID
}
```

**Signature**: HMAC-SHA256 with secret key

### Refresh Token (JWT)

**Expiration**: 14 days

**Payload (Claims)**:
```json
{
  "sub": "paul",                    // Subject (username)
  "userId": 2,                      // User ID
  "jti": "token-specific-id",       // Stored in database
  "tokenFamilyId": "family-id",     // For rotation tracking
  "typ": "refresh",                 // Token type
  "iss": "todolist-api",            // Issuer
  "iat": 1707390600,                // Issued at
  "exp": 1708600200                 // Expires at (+14 days)
}
```

**Storage**: SHA-256 hash stored in `refresh_tokens` table (never plaintext!)

---

## 🔒 Security Features

### 1. Token Rotation
Every time a refresh token is used, a **new one is issued**:
- Old token marked as "replaced"
- 30-second grace period for network retries
- After grace period, old token becomes invalid

**Why?** See [Why Multiple Refresh Tokens](Why-Multiple-Refresh-Tokens.md)

### 2. Reuse Detection
If a replaced token is used **after** the grace period:
- 🚨 Suspicious activity detected!
- Entire token family revoked
- User forced to re-login
- Attacker locked out

### 3. Secure Storage
- Refresh tokens stored as SHA-256 hashes
- Never stored in plaintext
- Access tokens are stateless (not stored)

### 4. Multi-Device Support
Each device gets its own token family:
- Login on phone doesn't logout laptop
- Can view all active sessions
- Can logout specific devices

### 5. Automatic Cleanup
- Expired tokens deleted after 30 days
- Runs daily at 2 AM
- Prevents database bloat

---

## 🌐 API Endpoints

### Authentication Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/auth/login` | Authenticate and get tokens | No |
| POST | `/api/auth/refresh` | Refresh access token | No |
| POST | `/api/auth/logout` | Revoke refresh token | Yes |
| GET | `/api/auth/sessions` | List active sessions | Yes |
| DELETE | `/api/auth/sessions` | Logout from all devices | Yes |
| DELETE | `/api/auth/sessions/{id}` | Logout from specific device | Yes |

### Example: Login

**Request**:
```bash
curl -X POST 'http://localhost:8080/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"paul"}'
```

**Response**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "username": "paul",
  "role": "USER"
}
```

### Example: Access Protected Endpoint

**Request**:
```bash
curl -X GET 'http://localhost:8080/api/users' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...'
```

### Example: Refresh Token

**Request**:
```bash
curl -X POST 'http://localhost:8080/api/auth/refresh' \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"eyJhbGciOiJIUzI1NiJ9..."}'
```

**Response**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

---

## ⚙️ Configuration

### Environment Variables

Required in `.env`:

```bash
# JWT Secrets (256-bit minimum)
JWT_ACCESS_SECRET=<base64-secret-here>
JWT_REFRESH_SECRET=<different-base64-secret-here>

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=todolist
DB_USER=your_user
DB_PASSWORD=your_password
```

### Application Properties

In `application.yml`:

```yaml
jwt:
  access:
    secret: ${JWT_ACCESS_SECRET}
    expiration: 900           # 15 minutes in seconds
  refresh:
    secret: ${JWT_REFRESH_SECRET}
    expiration: 1209600       # 14 days in seconds
    grace-period: 30          # 30 seconds for rotation tolerance
  issuer: todolist-api
```

---

## 🧪 Testing

### Test Script

Run comprehensive tests:
```bash
./test-jwt-auth.sh
```

Tests include:
1. ✅ Login (username-only)
2. ✅ Protected endpoint access
3. ✅ Token refresh
4. ✅ Logout
5. ✅ Revoked token rejection
6. ✅ Invalid token rejection

### Manual Testing

See [API Testing with cURL](API-Testing-cURL.md) for more examples.

---

## 📊 Database Schema

### refresh_tokens Table

```sql
CREATE TABLE refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    jti VARCHAR(36) NOT NULL UNIQUE,
    token_family_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    replaced_at TIMESTAMP WITH TIME ZONE,
    replaced_by_jti VARCHAR(36),
    device_info VARCHAR(255),
    revoked BOOLEAN NOT NULL DEFAULT false,

    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Indexes for performance
CREATE INDEX idx_refresh_token_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_token_family_id ON refresh_tokens(token_family_id);
CREATE INDEX idx_refresh_token_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_token_active_sessions ON refresh_tokens(user_id, revoked, expires_at);
```

See [Database Schema](Database-Schema.md) for complete schema.

---

## 🔮 Future Enhancements

### Planned Features

1. **Password Authentication**
   - BCrypt password hashing
   - Password update endpoint
   - Password reset flow

2. **Authorization Layer**
   - ABAC/PBAC permissions
   - Role-based access control
   - Fine-grained permissions

3. **Additional Security**
   - Rate limiting (prevent brute force)
   - Email verification
   - Two-factor authentication (2FA)
   - IP-based restrictions

4. **Session Management UI**
   - Frontend for viewing sessions
   - One-click device logout
   - Session activity history

---

## 🔗 Related Documentation

- [Why Multiple Refresh Tokens?](Why-Multiple-Refresh-Tokens.md) - Architecture decision
- [Token Rotation & Security](Token-Rotation-Security.md) - Detailed security guide
- [Session Management](Session-Management.md) - Multi-device sessions
- [API Design Guidelines](API-Design-Guidelines.md) - API standards

---

## 📚 References

- [OAuth 2.1 Specification](https://oauth.net/2.1/)
- [RFC 7519 - JWT](https://tools.ietf.org/html/rfc7519)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)

---

**Last Updated**: 2026-02-08
**Implementation**: Complete (MVP)
**Status**: ✅ Production Ready
