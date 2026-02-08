# Why Multiple Refresh Tokens?

A common question: **"Why do we store multiple refresh tokens instead of just the latest one?"**

This document explains the three critical reasons behind this architectural decision.

---

## TL;DR (Quick Answer)

We store multiple refresh tokens to support:
1. ✅ **Multi-device login** - Users can be logged in on multiple devices simultaneously
2. ✅ **Token rotation chains** - Track token history for security
3. ✅ **Reuse detection** - Detect and prevent token theft attacks

---

## 🔍 Reason 1: Multi-Device Support

### The Problem
Modern users access applications from multiple devices:
- 📱 iPhone Safari
- 💻 MacBook Chrome
- 📱 iPad App
- 🖥️ Desktop Firefox

If we only stored **one** refresh token per user, logging in on your phone would **immediately log out your laptop**!

### Our Solution
Each device gets its own **independent refresh token** with its own **token family**:

```
User's Active Sessions:
├── iPhone Safari    → Token Family A (active)
├── MacBook Chrome   → Token Family B (active)
└── iPad App         → Token Family C (active)
```

### Database Example
```sql
SELECT
  id as session_id,
  token_family_id,
  device_info,
  created_at
FROM refresh_tokens
WHERE user_id = 123
  AND revoked = false
  AND expires_at > NOW();

-- Result:
session_id | token_family_id | device_info           | created_at
-----------+-----------------+-----------------------+------------
42         | family-abc-123  | Chrome 192.168.1.100 | 2026-02-08
43         | family-def-456  | Safari 192.168.1.101 | 2026-02-08
44         | family-ghi-789  | Firefox 192.168.1.102| 2026-02-07
```

Each session is **completely independent** - logging out from iPhone doesn't affect your MacBook session.

---

## 🔄 Reason 2: Token Rotation Chains

### What is Token Rotation?
Following **OAuth 2.1 best practices**, when a user refreshes their access token, we:
1. Generate a **new** refresh token
2. Mark the **old** refresh token as "replaced"
3. Keep both for a **30-second grace period**

### The Rotation Chain
```
Timeline:
─────────────────────────────────────────────────────────────
09:00 AM  Login
          Token #1 (JTI: abc123) → Created, active

09:15 AM  Refresh (access token expired)
          Token #1 → Marked as "replaced", 30s grace period
          Token #2 (JTI: def456) → Created, new active token

09:30 AM  Refresh again
          Token #2 → Marked as "replaced", 30s grace period
          Token #3 (JTI: ghi789) → Created, new active token
```

### Why Keep the Chain?
1. **Grace Period**: Network issues might cause legitimate retry within 30 seconds
2. **Audit Trail**: See the complete history of token usage
3. **Reuse Detection**: Critical for security (see Reason 3!)

### Database Structure
```sql
-- Rotation chain for one device session
id | jti      | replaced_by_jti | replaced_at | valid_until (grace)
---+----------+-----------------+-------------+--------------------
1  | abc123   | def456          | 09:15:00    | 09:15:30 (30s grace)
2  | def456   | ghi789          | 09:30:00    | 09:30:30 (30s grace)
3  | ghi789   | NULL            | NULL        | NULL (active!)
```

---

## 🛡️ Reason 3: Reuse Detection (Critical Security!)

### The Attack Scenario

This is the **most important reason** for storing multiple tokens!

```
Attack Timeline:
────────────────────────────────────────────────────────────

1. ✅ Legitimate user gets Token A from login

2. 🦹 Attacker steals Token A (e.g., via man-in-the-middle)

3. ✅ Legitimate user uses Token A
   → System issues Token B
   → Token A marked as "replaced" with 30s grace period

4. ⏳ 30-second grace period passes

5. 🚨 Attacker tries to use stolen Token A
   → System detects: "This token was replaced 35 seconds ago!"
   → 🚨 REUSE DETECTED! → REVOKE ENTIRE TOKEN FAMILY
```

### The Code

From `AuthenticationService.java`:

```java
// Check if token was already rotated
if (refreshToken.getReplacedAt() != null) {
    // Check grace period
    if (refreshTokenService.isTokenWithinGracePeriod(refreshToken)) {
        log.debug("Token used within grace period: {}", jti);
        // Allow it (network retry tolerance)
    } else {
        // 🚨 Reuse detected - revoke entire token family
        log.error("Token reuse detected! Revoking family: {}", tokenFamilyId);
        refreshTokenService.revokeTokenFamily(tokenFamilyId);
        throw new RuntimeException("Refresh token revoked due to suspicious activity");
    }
}
```

### What Gets Revoked?

When reuse is detected, **ALL tokens in the family** are immediately revoked:

```sql
-- Before reuse detection:
family_id  | jti    | revoked
-----------+--------+--------
family-123 | abc    | false   ← Stolen token (old, replaced)
family-123 | def    | false   ← Current legitimate token
family-123 | ghi    | false   ← Newer legitimate token

-- After attacker uses abc token (outside grace period):
family_id  | jti    | revoked
-----------+--------+--------
family-123 | abc    | TRUE    ← Attacker locked out
family-123 | def    | TRUE    ← Legitimate user forced to re-login
family-123 | ghi    | TRUE    ← Entire chain revoked
```

**Result:**
- ❌ Attacker is **locked out completely**
- ⚠️ Legitimate user gets "Your session has been revoked due to suspicious activity"
- ✅ User must **re-login** (inconvenient but safe!)
- 🔒 Stolen token becomes **permanently useless**

### Why This Works

Without storing the rotation chain, we would have **no way to detect** that Token A was already used and replaced!

---

## 📊 Alternative Design: "Only Store Latest Token"

### What if we used a simple design?

```sql
-- Hypothetical "simple" design:
CREATE TABLE user_refresh_token (
    user_id BIGINT PRIMARY KEY,          -- Only ONE token per user
    token_hash VARCHAR(64),
    expires_at TIMESTAMP
);
```

### Problems with This Approach

| Issue | Impact |
|-------|--------|
| ❌ **No multi-device support** | Login on phone kicks out laptop |
| ❌ **No rotation chain tracking** | Can't detect token reuse |
| ❌ **No grace period** | Network retries would fail |
| ❌ **Weak security** | Stolen token stays valid for 14 days! |
| ❌ **No audit trail** | Can't see "where you're logged in" |
| ❌ **Poor UX** | Can't logout from specific devices |

---

## 🧹 Cleanup & Maintenance

### Don't worry about table growth!

The `TokenCleanupService` runs **daily at 2 AM**:

```java
@Scheduled(cron = "0 0 2 * * *")
public void cleanupExpiredTokens() {
    // Delete tokens older than 30 days
    Instant threshold = Instant.now().minusSeconds(30 * 24 * 60 * 60);
    refreshTokenRepository.deleteExpiredTokens(threshold);
    log.info("Cleaned up expired tokens older than 30 days");
}
```

### Cleanup Rules

| Token State | Retention | Action |
|-------------|-----------|--------|
| **Active** (not replaced, not expired) | Forever | Keep (user is logged in) |
| **Rotated** (replaced, within expiration) | Until expired + 30 days | Keep (for audit trail) |
| **Expired** | 30 days after expiration | Delete automatically |
| **Revoked** | Until expired + 30 days | Delete automatically |

Typical user with 2-3 active devices: ~20-50 tokens in database at any given time (mostly rotated tokens from the last 14 days).

---

## ✅ Summary: Benefits of Multiple Tokens

| Feature | Our Design | Single Token |
|---------|------------|--------------|
| **Multi-device login** | ✅ Each device independent | ❌ Last login kicks out others |
| **Token rotation** | ✅ Full rotation chain | ❌ No chain tracking |
| **Reuse detection** | ✅ Detects and revokes family | ❌ No detection possible |
| **Grace period** | ✅ 30s for network retry | ❌ Instant invalidation |
| **Security level** | ✅ OAuth 2.1 compliant | ❌ Vulnerable to theft |
| **Session management** | ✅ See/manage all devices | ❌ No visibility |
| **User experience** | ✅ Logout specific devices | ❌ All-or-nothing |
| **Audit trail** | ✅ Complete token history | ❌ No history |

---

## 🌐 Industry Standard

This **multiple-token design** is used by:
- Google (Gmail, YouTube, etc.)
- GitHub
- Auth0
- Firebase Authentication
- AWS Cognito
- Microsoft Azure AD

It's the **OAuth 2.1 recommended practice** for a reason! 🔒

---

## 🔗 Related Documentation

- [Token Rotation & Security](Token-Rotation-Security.md)
- [Session Management](Session-Management.md)
- [JWT Authentication Overview](JWT-Authentication-Overview.md)
- [Database Schema](Database-Schema.md)

---

**Last Updated**: 2026-02-08
**Related Issue**: JWT Authentication Implementation (#001)
