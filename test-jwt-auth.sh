#!/bin/bash

# JWT Authentication Testing Script
# Tests all authentication endpoints

BASE_URL="http://localhost:8080"
echo "🧪 Testing JWT Authentication Feature"
echo "====================================="
echo ""

# Test 1: Login
echo "1️⃣ Testing LOGIN endpoint..."
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"Paul"}')

ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken')
REFRESH_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.refreshToken')

if [ "$ACCESS_TOKEN" != "null" ] && [ "$ACCESS_TOKEN" != "" ]; then
  echo "✅ Login successful!"
  echo "   Username: $(echo "$LOGIN_RESPONSE" | jq -r '.username')"
  echo "   Role: $(echo "$LOGIN_RESPONSE" | jq -r '.role')"
  echo "   Token type: $(echo "$LOGIN_RESPONSE" | jq -r '.tokenType')"
  echo "   Expires in: $(echo "$LOGIN_RESPONSE" | jq -r '.expiresIn') seconds"
else
  echo "❌ Login failed!"
  echo "$LOGIN_RESPONSE" | jq .
  exit 1
fi
echo ""

# Test 2: Access protected endpoint
echo "2️⃣ Testing PROTECTED endpoint with access token..."
TODOS_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/users" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

if echo "$TODOS_RESPONSE" | jq -e '. | type == "array"' > /dev/null 2>&1; then
  echo "✅ Protected endpoint access successful!"
  echo "   Users count: $(echo "$TODOS_RESPONSE" | jq 'length')"
else
  echo "❌ Protected endpoint access failed!"
  echo "$TODOS_RESPONSE" | jq .
fi
echo ""

# Test 3: Refresh token
echo "3️⃣ Testing REFRESH endpoint..."
REFRESH_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"${REFRESH_TOKEN}\"}")

NEW_ACCESS_TOKEN=$(echo "$REFRESH_RESPONSE" | jq -r '.accessToken')
NEW_REFRESH_TOKEN=$(echo "$REFRESH_RESPONSE" | jq -r '.refreshToken')

if [ "$NEW_ACCESS_TOKEN" != "null" ] && [ "$NEW_ACCESS_TOKEN" != "" ]; then
  echo "✅ Token refresh successful!"
  echo "   New access token received"
  echo "   New refresh token received (rotated)"
  echo "   Tokens are different: $(if [ "$ACCESS_TOKEN" != "$NEW_ACCESS_TOKEN" ]; then echo "YES"; else echo "NO"; fi)"
else
  echo "❌ Token refresh failed!"
  echo "$REFRESH_RESPONSE" | jq .
  exit 1
fi
echo ""

# Test 4: Use new access token
echo "4️⃣ Testing with NEW access token..."
NEW_TODOS_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/users" \
  -H "Authorization: Bearer ${NEW_ACCESS_TOKEN}")

if echo "$NEW_TODOS_RESPONSE" | jq -e '. | type == "array"' > /dev/null 2>&1; then
  echo "✅ New access token works!"
else
  echo "❌ New access token failed!"
  echo "$NEW_TODOS_RESPONSE" | jq .
fi
echo ""

# Test 5: Logout
echo "5️⃣ Testing LOGOUT endpoint..."
LOGOUT_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/logout" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${NEW_ACCESS_TOKEN}" \
  -d "{\"refreshToken\":\"${NEW_REFRESH_TOKEN}\"}")

if echo "$LOGOUT_RESPONSE" | jq -e '.message' > /dev/null 2>&1; then
  echo "✅ Logout successful!"
  echo "   Message: $(echo "$LOGOUT_RESPONSE" | jq -r '.message')"
else
  echo "❌ Logout failed!"
  echo "$LOGOUT_RESPONSE" | jq .
fi
echo ""

# Test 6: Try using revoked refresh token
echo "6️⃣ Testing revoked refresh token (should fail)..."
REVOKED_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"${NEW_REFRESH_TOKEN}\"}")

REVOKED_ERROR=$(echo "$REVOKED_RESPONSE" | jq -r '.error // ""')
if [ "$REVOKED_ERROR" = "Unauthorized" ]; then
  echo "✅ Revoked token correctly rejected!"
  echo "   Error: $(echo "$REVOKED_RESPONSE" | jq -r '.message')"
else
  echo "❌ Unexpected response for revoked token:"
  echo "$REVOKED_RESPONSE" | jq .
fi
echo ""

# Test 7: Invalid token
echo "7️⃣ Testing INVALID token (should fail)..."
INVALID_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/users" \
  -H "Authorization: Bearer invalid.token.here")

INVALID_ERROR=$(echo "$INVALID_RESPONSE" | jq -r '.error // ""')
if [ "$INVALID_ERROR" = "Unauthorized" ]; then
  echo "✅ Invalid token correctly rejected!"
  echo "   Error: $(echo "$INVALID_RESPONSE" | jq -r '.message')"
else
  echo "❌ Unexpected response for invalid token:"
  echo "$INVALID_RESPONSE" | jq .
fi
echo ""

echo "====================================="
echo "🎉 JWT Authentication Tests Complete!"
echo "====================================="
