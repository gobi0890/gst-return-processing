# Postman Test Guide - Simplified Protected API Flow

## Prerequisites

1. **Server Running**
   ```bash
   mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
   ```
   Server: `http://localhost:8080`

2. **Generate RSA Keys**
   ```bash
   openssl genrsa -out server_private.pem 2048
   openssl rsa -in server_private.pem -pubout -out server_public.pem
   ```

3. **Database Client Setup**
   ```sql
   INSERT INTO clients (client_id, client_secret_hash, rsa_public_key_pem, rsa_private_key_pem, company_name, is_active, created_at, updated_at)
   VALUES (
     'client-001',
     '8846f7eaee8fb117ad06bdd830b7586c0fdc1e4d7b1d7371823f43139f50c0a1',  -- SHA-512 of 'secret-123'
     '-----BEGIN PUBLIC KEY-----\nMII...\n-----END PUBLIC KEY-----',
     '-----BEGIN PRIVATE KEY-----\nMII...\n-----END PRIVATE KEY-----',
     'Test Company',
     true,
     NOW(),
     NOW()
   );
   ```

---

## Simplified Flow

### **Flow Diagram**
```
┌──────────────┐                                              ┌──────────────┐
│   Client     │                                              │   Server     │
└──────┬───────┘                                              └──────┬───────┘
       │                                                              │
       │  1. Generate random AES-256 key (appKey)                    │
       │     appKey = 32 random bytes                               │
       │                                                              │
       │  2. Encrypt appKey with Server's public key                 │
       │     encryptedAppKey = RSA.encrypt(appKey)                   │
       │                                                              │
       │  3. POST /api/v1/auth/login/simple                          │
       │     Headers:                                               │
       │       X-Client-ID: client-001                              │
       │       X-Client-Secret: secret-123                          │
       │     Body: {encryptedAppKey}                                │
       ├─────────────────────────────────────────────────────────────>│
       │                                                              │
       │                  4. Verify credentials                      │
       │                  5. Decrypt appKey with private key         │
       │                  6. Generate JWT token                      │
       │                  7. Generate SEK (AES-256)                  │
       │                                                              │
       │  Response: {auth_token, sek, ...}                          │
       │<─────────────────────────────────────────────────────────────┤
       │                                                              │
       │  8. Store authToken & sek locally                           │
       │     Use sek for request/response encryption                │
```

---

## Test 1: Generate Client AES Key

**Generate random 256-bit AES key:**

```python
import os
import base64

# Generate 32 bytes (256 bits) for AES-256
app_key = os.urandom(32)
app_key_base64 = base64.b64encode(app_key).decode()
print(f"App Key (Base64): {app_key_base64}")
```

**Or with OpenSSL:**
```bash
openssl rand -base64 32
```

**Save this for Test 3.**

---

## Test 2: Encrypt App Key with Server Public Key

**Using OpenSSL:**
```bash
# Save server public key
echo "-----BEGIN PUBLIC KEY-----
<SERVER_PUBLIC_KEY>
-----END PUBLIC KEY-----" > server_public.pem

# Save app key bytes
echo "<APP_KEY_BASE64>" | base64 -d > app_key.bin

# Encrypt with RSA
openssl pkeyutl -encrypt -inkey server_public.pem -pubin -in app_key.bin -out encrypted_app_key.bin

# Convert to Base64
ENCRYPTED_APP_KEY=$(base64 encrypted_app_key.bin | tr -d '\n')
echo "Encrypted App Key: $ENCRYPTED_APP_KEY"
```

**Using Python:**
```python
import base64
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.backends import default_backend

# Server public key
server_public_key_pem = """-----BEGIN PUBLIC KEY-----
<SERVER_PUBLIC_KEY>
-----END PUBLIC KEY-----"""

# Load public key
public_key = serialization.load_pem_public_key(
    server_public_key_pem.encode(),
    backend=default_backend()
)

# App key (from Test 1)
app_key = base64.b64decode("<APP_KEY_BASE64>")

# Encrypt
encrypted = public_key.encrypt(
    app_key,
    padding.PKCS1v15()
)

# Convert to Base64
encrypted_base64 = base64.b64encode(encrypted).decode()
print(f"Encrypted App Key: {encrypted_base64}")
```

---

## Test 3: Simple Login Request

### Postman Setup

**Method:** POST  
**URL:** `http://localhost:8080/api/v1/auth/login/simple`

**Headers:**
```
X-Client-ID: client-001
X-Client-Secret: secret-123
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "encryptedAppKey": "<ENCRYPTED_APP_KEY_BASE64_FROM_TEST2>"
}
```

### Response (200 OK)
```json
{
  "success": true,
  "statusCode": "AUTH_SUCCESS",
  "message": "Authentication successful",
  "data": {
    "auth_token": "eyJhbGciOiJIUzUxMiJ9.eyJjbGllbnRJZCI6ImNsaWVudC0wMDEiLCJ0eXBlIjoiQVVUSF9UT0tFTiIsInN1YiI6ImNsaWVudC0wMDEiLCJpYXQiOjE3MDU0MzMwNDUsImV4cCI6MTcwNTQzNDg0NX0...",
    "sek": "BQxvT2hkN1pOVGQ2RjJ2R1pROFN0ZW5FMEQ3TTczZ2pWMHczZ3c9PQ==",
    "token_type": "Bearer",
    "expires_in": 1800000
  },
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-01-15T10:30:45.123456",
  "httpStatusCode": 200
}
```

**📝 Save:**
- `auth_token` - Use for protected endpoints
- `sek` - Use for AES encryption/decryption

---

## Test 4: Use SEK to Encrypt Request Body

**Encrypt a request payload with SEK (AES-256 ECB):**

```python
import base64
import json
from Crypto.Cipher import AES

# SEK from login response (Base64 decoded to get AES key)
sek_base64 = "<SEK_FROM_RESPONSE>"
sek_key = base64.b64decode(sek_base64)

# Request payload
request_payload = {
    "return_id": "GST-2024-001",
    "amount": 50000,
    "description": "GST Return"
}

# Encrypt with AES-256 ECB
cipher = AES.new(sek_key, AES.MODE_ECB)
json_str = json.dumps(request_payload)

# Pad to 16-byte boundary (PKCS5)
block_size = 16
padding_len = block_size - (len(json_str) % block_size)
json_str += chr(padding_len) * padding_len

encrypted = cipher.encrypt(json_str.encode())
encrypted_base64 = base64.b64encode(encrypted).decode()
print(f"Encrypted Request: {encrypted_base64}")
```

---

## Test 5: Send Protected API Request

**Method:** POST  
**URL:** `http://localhost:8080/api/v1/return/submit` (example)

**Headers:**
```
Authorization: Bearer <AUTH_TOKEN_FROM_TEST3>
Content-Type: application/json
```

**Body:**
```json
{
  "encryptedPayload": "<ENCRYPTED_REQUEST_FROM_TEST4>"
}
```

---

## Test 6: Decrypt Response with SEK

```python
import base64
import json
from Crypto.Cipher import AES

# SEK key
sek_base64 = "<SEK_FROM_LOGIN>"
sek_key = base64.b64decode(sek_base64)

# Encrypted response from API
encrypted_response_b64 = "<ENCRYPTED_DATA_FROM_RESPONSE>"
encrypted_response = base64.b64decode(encrypted_response_b64)

# Decrypt with AES-256 ECB
cipher = AES.new(sek_key, AES.MODE_ECB)
decrypted = cipher.decrypt(encrypted_response)

# Remove padding
padding_len = decrypted[-1]
decrypted_text = decrypted[:-padding_len].decode()
response = json.loads(decrypted_text)

print(json.dumps(response, indent=2))
```

---

## Test 7: Logout

**Method:** POST  
**URL:** `http://localhost:8080/api/v1/auth/logout`

**Headers:**
```
Authorization: Bearer <AUTH_TOKEN_FROM_TEST3>
Content-Type: application/json
```

**Body:**
```json
{}
```

**Response (200 OK):**
```json
{
  "success": true,
  "statusCode": "LOGOUT_SUCCESS",
  "message": "Logout successful",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-01-15T10:30:45.123456"
}
```

---

## Error Responses

### Missing Header (400)
```json
{
  "success": false,
  "statusCode": "3003",
  "message": "X-Client-ID header is required",
  "traceId": "...",
  "httpStatusCode": 400
}
```

### Invalid Credentials (401)
```json
{
  "success": false,
  "statusCode": "1002",
  "message": "Invalid client secret",
  "traceId": "...",
  "httpStatusCode": 401
}
```

### Decryption Failed (400)
```json
{
  "success": false,
  "statusCode": "2001",
  "message": "Failed to decrypt app key with RSA",
  "traceId": "...",
  "httpStatusCode": 400
}
```

---

## Complete Bash Test Script

```bash
#!/bin/bash

echo "=== 1. Generate App Key ==="
APP_KEY=$(openssl rand -base64 32)
echo "App Key: $APP_KEY"

echo "\n=== 2. Encrypt App Key ==="
echo "$APP_KEY" | base64 -d > app_key.bin
openssl pkeyutl -encrypt -inkey server_public.pem -pubin -in app_key.bin -out encrypted_app_key.bin
ENCRYPTED_APP_KEY=$(base64 encrypted_app_key.bin | tr -d '\n')
echo "Encrypted: ${ENCRYPTED_APP_KEY:0:50}..."

echo "\n=== 3. Login ==="
LOGIN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login/simple \
  -H "X-Client-ID: client-001" \
  -H "X-Client-Secret: secret-123" \
  -H "Content-Type: application/json" \
  -d "{\"encryptedAppKey\": \"$ENCRYPTED_APP_KEY\"}")

echo $LOGIN | jq .

echo "\n=== 4. Extract Token & SEK ==="
AUTH_TOKEN=$(echo $LOGIN | jq -r '.data.auth_token')
SEK=$(echo $LOGIN | jq -r '.data.sek')

echo "Auth Token: ${AUTH_TOKEN:0:50}..."
echo "SEK: $SEK"

echo "\n=== 5. Logout ==="
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{}" | jq .
```

---

## Key Points

✅ **Headers-based credentials** - No encryption needed  
✅ **RSA only for app key** - Client generates random AES key  
✅ **Server decrypts app key** - Uses stored private key  
✅ **JWT token** - 30-minute expiration  
✅ **SEK (Base64 AES)** - Use for request/response encryption  
✅ **AES-256 ECB** - PKCS5 padding  
✅ **All responses** - Include traceId for debugging  

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│              REQUEST ENCRYPTION FLOW                 │
├─────────────────────────────────────────────────────┤
│ 1. Client generates random 32-byte AES key          │
│ 2. Client encrypts AES key with Server's RSA public │
│ 3. Client sends: X-Client-ID, X-Client-Secret      │
│ 4. Client sends: {encryptedAppKey}                  │
│                                                     │
│ Server:                                             │
│ 5. Verify headers (clientId + clientSecret)         │
│ 6. Decrypt AES key with Server's RSA private        │
│ 7. Generate JWT token (30 min)                      │
│ 8. Generate new SEK (Session Encryption Key)        │
│ 9. Return: {auth_token, sek}                        │
│                                                     │
│ Client:                                             │
│ 10. Store auth_token & sek                          │
│ 11. For future requests: Encrypt with SEK (AES-256) │
│ 12. Send: Authorization: Bearer {auth_token}        │
│ 13. Send encrypted body with SEK                    │
│                                                     │
│ Server:                                             │
│ 14. Validate JWT token                              │
│ 15. Decrypt request with SEK                        │
│ 16. Process request                                 │
│ 17. Encrypt response with SEK                       │
│ 18. Send encrypted response                         │
│                                                     │
│ Client:                                             │
│ 19. Decrypt response with SEK                       │
│ 20. Process response                                │
└─────────────────────────────────────────────────────┘
```
