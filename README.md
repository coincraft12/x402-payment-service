# x402-payment-service

A payment authorization service based on the x402 protocol. Includes EIP-3009 `transferWithAuthorization` signature verification, policy engine, ledger, and audit trail.

> [한국어 문서](README.ko.md)

---

## Implementation Status

| Phase | Description | Status |
|---|---|---|
| Phase 1 | EIP-3009 signature verification (EIP-712 digest + ecrecover) | ✅ Done |
| Phase 2 | x402 standard challenge response (`accepts[]`, network, asset contract) | ✅ Done |
| Phase 3 | PostgreSQL migration (H2 retained for tests) | ✅ Done |
| Phase 4 | Facilitator on-chain broadcast (`transferWithAuthorization` via web3j) | ✅ Done |

---

## Flow

```
Client                              x402-payment-service
    │                                       │
    │── GET /x402/protected/report ────────▶│
    │                                       │ Create PaymentIntent
    │◀── 402 + accepts[] + paymentIntentId ─│
    │                                       │
    │── POST /authorize (EIP-3009 sig) ────▶│
    │                                       │ Verify EIP-712 digest
    │                                       │ ecrecover → verify from address
    │                                       │ Replay guard (nonce + digest)
    │◀── PA2_VERIFIED + authorizationId ────│
    │                                       │
    │── POST /capture (authorizationId) ───▶│
    │                                       │ ledger: RESERVE→COMMIT→SETTLE
    │                                       │ Facilitator: broadcast transferWithAuthorization
    │                                       │ → txHash stored in PaymentSettlement
    │◀── PS2_SETTLED + txHash ──────────────│
    │                                       │
    │── GET /x402/protected/report ────────▶│ (same Idempotency-Key)
    │◀── 200 OK + report payload ───────────│
```

---

## Running

### Prerequisites

PostgreSQL must be running.

```powershell
# Quick start with Docker
docker run -d `
  --name x402-postgres `
  -e POSTGRES_DB=x402 `
  -e POSTGRES_USER=x402 `
  -e POSTGRES_PASSWORD=x402 `
  -p 5432:5432 `
  postgres:16
```

### Start the App

```powershell
cd F:\Workplace\x402-payment-service

# Run with defaults (DB: localhost:5432/x402, USER/PASSWORD: x402)
.\gradlew.bat bootRun
```

Base URL: `http://localhost:8081`

Override with environment variables:

```powershell
$env:DB_URL      = "jdbc:postgresql://localhost:5432/x402"
$env:DB_USERNAME = "x402"
$env:DB_PASSWORD = "x402"
.\gradlew.bat bootRun
```

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/x402` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `x402` | DB username |
| `DB_PASSWORD` | `x402` | DB password |
| `DB_POOL_SIZE` | `10` | HikariCP max pool size |
| `JPA_DDL_AUTO` | `update` | Hibernate DDL strategy |
| `X402_EIP3009_CHAIN_ID` | `8453` | EIP-712 domain chain ID (Base Mainnet) |
| `X402_EIP3009_TOKEN_NAME` | `USD Coin` | EIP-712 domain token name |
| `X402_EIP3009_TOKEN_VERSION` | `2` | EIP-712 domain token version |
| `X402_EIP3009_TOKEN_CONTRACT` | `0xA0b86991...` | USDC contract address |
| `X402_POLICY_MAX_AMOUNT` | `10000` | Maximum allowed amount per payment |
| `X402_ALLOWED_MERCHANTS` | `demo-merchant,lab-merchant` | Allowed merchant IDs |
| `X402_FACILITATOR_ENABLED` | `false` | Enable on-chain broadcast via Facilitator |
| `X402_FACILITATOR_RPC_URL` | `https://sepolia.base.org` | RPC endpoint for on-chain broadcast |
| `X402_FACILITATOR_PRIVATE_KEY` | _(empty)_ | Hot wallet private key (no `0x` prefix) |

---

## API

| Method | Path | Description |
|---|---|---|
| `GET` | `/x402/protected/report` | Access protected resource. Returns `402` if unpaid |
| `POST` | `/x402/payment-intents` | Create PaymentIntent directly |
| `POST` | `/x402/payment-intents/{id}/authorize` | Submit and verify EIP-3009 signature |
| `POST` | `/x402/payment-intents/{id}/capture` | Consume authorization and settle |
| `GET` | `/x402/payment-intents/{id}` | Get intent status |
| `GET` | `/x402/payment-intents/{id}/audits` | Get audit trail |
| `GET` | `/x402/payment-intents/{id}/ledger` | Get ledger entries |

---

## Testing Guide

### A. Automated Tests (Recommended — No PostgreSQL required)

Tests use H2 in-memory DB. No PostgreSQL needed.

```powershell
cd F:\Workplace\x402-payment-service
.\gradlew.bat test
```

Expected output:

```
> Task :test

READMEExamplesIntegrationTest > README - Payment Intent direct creation example PASSED
READMEExamplesIntegrationTest > README - Failure scenario 1. Unknown merchant PASSED
READMEExamplesIntegrationTest > README - Failure scenario 2. Amount over limit PASSED
READMEExamplesIntegrationTest > README - Failure scenario 3. Authorization replay PASSED
READMEExamplesIntegrationTest > README - Failure scenario 4. Expired authorization PASSED
READMEExamplesIntegrationTest > README - Quick start: challenge → authorize → capture → 200 OK PASSED

BUILD SUCCESSFUL in ~40s
6 actionable tasks: 4 executed, 2 up-to-date
```

What each test validates:

| Test | Validates |
|---|---|
| Payment Intent direct creation | PI1→PI4 full state transition, 4 audit events, 3 ledger entries |
| Unknown merchant | PI9_REJECTED, MERCHANT_NOT_ALLOWED audit |
| Amount over limit | PI9_REJECTED, ENDPOINT_AMOUNT_LIMIT_EXCEEDED audit |
| Authorization replay | 400 + AUTHORIZATION_REPLAY_BLOCKED on duplicate nonce |
| Expired authorization | 400 + AUTHORIZATION_EXPIRED on past validBefore |
| Quick start (full flow) | 402 → authorize → capture → 200 OK, full response headers |

---

### B. Manual API Tests — No Signature Required

Start the app first (`.\gradlew.bat bootRun`).

```powershell
$BASE_URL = "http://localhost:8081"
```

#### B-1. Receive 402 Challenge

```powershell
try {
  Invoke-RestMethod -Method GET `
    -Uri "$BASE_URL/x402/protected/report" `
    -Headers @{
      "Idempotency-Key" = "test-001"
      "X-Payer"         = "agent-1"
    }
} catch {
  $resp   = $_.Exception.Response
  $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
  $raw    = $reader.ReadToEnd()
  $challenge = $raw | ConvertFrom-Json
}

$challenge
$paymentIntentId = $challenge.paymentIntentId
```

Expected response (HTTP 402):

```json
{
  "x402Version": 1,
  "accepts": [{
    "scheme": "exact",
    "network": "base-mainnet",
    "maxAmountRequired": "1000",
    "resource": "/x402/protected/report",
    "asset": "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
    "payTo": "merchant-vault",
    "maxTimeoutSeconds": 300,
    "extra": { "name": "USD Coin", "version": "2" }
  }],
  "error": "X402 Payment Required",
  "status": 402,
  "paymentIntentId": "<uuid>",
  "authorizePath": "/x402/payment-intents/<uuid>/authorize",
  "capturePath": "/x402/payment-intents/<uuid>/capture"
}
```

Check response headers:

```powershell
try {
  Invoke-WebRequest -Method GET `
    -Uri "$BASE_URL/x402/protected/report" `
    -Headers @{ "Idempotency-Key" = "test-hdr-001"; "X-Payer" = "agent-1" }
} catch {
  $_.Exception.Response.Headers | Format-List
}
```

Expected headers:

```
X-Payment-Protocol : x402/1
X-Payment-Required : true
X-Payment-Intent-Id: <uuid>
X-Payment-Merchant : demo-merchant
X-Payment-Endpoint : /x402/protected/report
X-Payment-Asset    : USDC
X-Payment-Amount   : 1000
X-Payment-Payer    : agent-1
Link               : </x402/payment-intents/<uuid>/authorize>; rel="authorize", ...
```

#### B-2. Unknown Merchant (Policy Rejection)

```powershell
$badIntent = Invoke-RestMethod -Method POST `
  -Uri "$BASE_URL/x402/payment-intents" `
  -Headers @{ "Content-Type" = "application/json"; "Idempotency-Key" = "bad-merchant-001" } `
  -Body (@{
    merchantId = "unknown-merchant"
    endpoint   = "/premium/report"
    asset      = "USDC"
    amount     = 1000
    payer      = "agent-1"
    payee      = "merchant-vault"
  } | ConvertTo-Json)

$badIntent.status   # PI9_REJECTED

Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$($badIntent.id)/audits"
# audit[1].reason = "MERCHANT_NOT_ALLOWED: unknown-merchant"
```

#### B-3. Amount Over Limit (Policy Rejection)

```powershell
$bigIntent = Invoke-RestMethod -Method POST `
  -Uri "$BASE_URL/x402/payment-intents" `
  -Headers @{ "Content-Type" = "application/json"; "Idempotency-Key" = "over-limit-001" } `
  -Body (@{
    merchantId = "demo-merchant"
    endpoint   = "/premium/report"
    asset      = "USDC"
    amount     = 999999
    payer      = "agent-1"
    payee      = "merchant-vault"
  } | ConvertTo-Json)

$bigIntent.status   # PI9_REJECTED

Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$($bigIntent.id)/audits"
# audit[1].reason = "ENDPOINT_AMOUNT_LIMIT_EXCEEDED: max=10000, requested=999999"
```

#### B-4. Query Intent Status / Audit / Ledger

```powershell
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId"
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/audits"
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/ledger"
```

---

### C. Manual API Tests — Full Payment Flow with On-Chain Settlement (Base Sepolia)

The authorize step requires an EIP-3009 signature. Use the included Node.js script to generate one.

#### Prerequisites

Node.js v18+ required.

```powershell
cd F:\Workplace\x402-payment-service
npm install
```

#### Environment Variables (Base Sepolia)

```powershell
$env:X402_EIP3009_TOKEN_NAME      = "USDC"
$env:X402_EIP3009_CHAIN_ID        = "84532"
$env:X402_EIP3009_TOKEN_CONTRACT  = "0x036CbD53842c5426634e7929541eC2318f3dCF7e"
$env:X402_FACILITATOR_ENABLED     = "true"
$env:X402_FACILITATOR_RPC_URL     = "https://sepolia.base.org"
$env:X402_FACILITATOR_PRIVATE_KEY = "<your hot wallet private key, no 0x>"
.\gradlew.bat bootRun
```

> **Base Sepolia faucets**
> - ETH (gas): https://www.alchemy.com/faucets/base-sepolia
> - USDC: https://faucet.circle.com (select Base Sepolia)

#### Verify USDC contract domain (optional sanity check)

```powershell
node check_domain.js
# name   : USDC
# version: 2
# match: true
```

#### C-1. Create Intent → 402 Challenge

```powershell
$BASE_URL = "http://localhost:8081"

try {
  Invoke-RestMethod -Method GET `
    -Uri "$BASE_URL/x402/protected/report" `
    -Headers @{ "Idempotency-Key" = "flow-001"; "X-Payer" = "agent-1" }
} catch {
  $reader    = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
  $challenge = $reader.ReadToEnd() | ConvertFrom-Json
}

$paymentIntentId = $challenge.paymentIntentId
Write-Host "PaymentIntentId: $paymentIntentId"
```

Expected: HTTP 402, `$challenge.x402Version = 1`

#### C-2. Sign & Authorize

Generate signature and get ready-to-run PowerShell command:

```powershell
node sign_eip3009.js $paymentIntentId
```

The script prints an `Invoke-RestMethod` command. Run it directly:

```
=== EIP-3009 서명 완료 ===
signer : 0x91ffcbB6f6dC947C01d402eA5703b9D27e8aA363
nonce  : 0x8f3a...
v      : 28  r: 0x...  s: 0x...

=== Authorize curl (PowerShell) ===
Invoke-RestMethod -Method POST -Uri "http://localhost:8081/x402/payment-intents/<id>/authorize" ...
```

```powershell
$authorizationId = $auth.id
Write-Host "AuthorizationId: $authorizationId"
Write-Host "Status: $($auth.status)"   # PA2_VERIFIED
```

Expected response:

```json
{
  "id": "<uuid>",
  "paymentIntentId": "<uuid>",
  "payer": "0xabad1fd3...",
  "status": "PA2_VERIFIED",
  "consumed": false
}
```

Failure cases:
- Signature address mismatch → `400 SIGNATURE_MISMATCH`
- Expired signature → `400 AUTHORIZATION_EXPIRED`
- Duplicate nonce → `400 AUTHORIZATION_REPLAY_BLOCKED`

#### C-3. Capture

```powershell
$settlement = Invoke-RestMethod -Method POST `
  -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/capture" `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body (@{ authorizationId = $authorizationId } | ConvertTo-Json)

Write-Host "Settlement Status: $($settlement.status)"   # PS2_SETTLED
```

Expected response:

```json
{
  "id": "<uuid>",
  "paymentIntentId": "<uuid>",
  "authorizationId": "<uuid>",
  "status": "PS2_SETTLED",
  "txHash": "0x9ec59e2ac252474cd07adb1fdf7eca5d40114d8cee318c04ada9fefc6f1b76f2"
}
```

Verify on BaseScan:
```
https://sepolia.basescan.org/tx/<txHash>
```
The transaction should show a `transferWithAuthorization` call on the USDC contract.

#### C-4. Access Protected Resource (200 OK)

```powershell
# Re-request with the same Idempotency-Key
$report = Invoke-RestMethod -Method GET `
  -Uri "$BASE_URL/x402/protected/report" `
  -Headers @{ "Idempotency-Key" = "flow-001"; "X-Payer" = "agent-1" }

$report
```

Expected response (HTTP 200):

```json
{
  "accessGranted": true,
  "reportId": "premium-report-001",
  "reportName": "Premium Custody Revenue Report",
  "payload": "Paid access granted. This is the protected premium report payload.",
  "paymentIntentId": "<uuid>"
}
```

#### C-5. Verify Final State

```powershell
# Final intent status
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId"
# status: PI4_SETTLED

# Audit trail (4 entries)
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/audits"
# [0] intent.created
# [1] policy.evaluated
# [2] authorization.verified
# [3] settlement.completed

# Ledger (3 entries)
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/ledger"
# [0] RESERVE
# [1] COMMIT
# [2] SETTLE
```

---

## 402 Challenge Response Structure

```json
{
  "x402Version": 1,
  "accepts": [
    {
      "scheme": "exact",
      "network": "base-mainnet",
      "maxAmountRequired": "1000",
      "resource": "/x402/protected/report",
      "description": "Payment required to access /x402/protected/report",
      "mimeType": "application/json",
      "payTo": "merchant-vault",
      "maxTimeoutSeconds": 300,
      "asset": "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
      "extra": {
        "name": "USD Coin",
        "version": "2"
      }
    }
  ],
  "error": "X402 Payment Required",
  "status": 402,
  "paymentIntentId": "<uuid>",
  "authorizePath": "/x402/payment-intents/<uuid>/authorize",
  "capturePath": "/x402/payment-intents/<uuid>/capture",
  "auditsPath": "/x402/payment-intents/<uuid>/audits",
  "ledgerPath": "/x402/payment-intents/<uuid>/ledger"
}
```

---

## Authorization Request Structure (EIP-3009)

```json
{
  "from": "0x<payer address>",
  "to": "0x<payee address>",
  "value": 1000,
  "validAfter": 0,
  "validBefore": 1798761599,
  "nonce": "0x<bytes32 hex>",
  "v": 28,
  "r": "0x<bytes32 hex>",
  "s": "0x<bytes32 hex>"
}
```

Signature verification process:

1. Compute EIP-712 domain separator (chainId, tokenName, tokenVersion, tokenContract)
2. Compute `TransferWithAuthorization` struct hash
3. EIP-712 final digest = `keccak256(0x1901 || domainSeparator || structHash)`
4. `ecrecover(digest, v, r, s)` → recovered address must match `from`

---

## PaymentIntent State Machine

```
PI0_REQUESTED
    │
    ▼
PI1_POLICY_CHECKED ──(policy rejected)──▶ PI9_REJECTED
    │
    ▼
PI2_AUTHORIZED ──(expired)──▶ PI10_EXPIRED
    │
    ▼
PI3_CAPTURED
    │
    ▼
PI4_SETTLED
```

---

## Phase 4: Facilitator On-Chain Broadcast

The Facilitator is a non-custodial middleware layer. It does **not** hold user funds — it only broadcasts pre-signed EIP-3009 authorizations on-chain.

### Architecture

```
Client signs EIP-3009 → x402-payment-service verifies → FacilitatorClient.settle()
                                                              │
                                                              ▼
                                                   BaseFacilitatorClient
                                                   - encode transferWithAuthorization ABI
                                                   - get nonce + gasPrice via web3j
                                                   - sign RawTransaction (EIP-155, chainId)
                                                   - ethSendRawTransaction → txHash
```

### Key Design Decisions

| Decision | Reason |
|---|---|
| `@ConditionalOnProperty` — `NoOpFacilitatorClient` when disabled | Tests run without RPC/wallet |
| Legacy `RawTransaction` (not EIP-1559) | Base Sepolia compatibility |
| txHash stored in `PaymentSettlement` | On-chain proof of settlement |
| Facilitator call inside `@Transactional` | Prototype simplicity — see TODO below |

### Network Config

| Network | Chain ID | USDC Contract | Token Name |
|---|---|---|---|
| Base Mainnet | `8453` | `0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913` | `USD Coin` |
| Base Sepolia | `84532` | `0x036CbD53842c5426634e7929541eC2318f3dCF7e` | `USDC` |

### TODO (Production Readiness)

- **Outbox pattern**: Facilitator call outside `@Transactional` — txHash loss risk if DB commit fails after on-chain success
- **Balance pre-check**: Verify payer USDC balance before broadcasting
- **Async confirmation**: Poll block confirmation after broadcast (currently fire-and-forget)
- **Gas estimation**: Replace fixed `GAS_LIMIT=100_000` with `eth_estimateGas`

---

## Related Repositories

- `custody/x402-payment-lab` — Mock implementation for learning (reference)
- `custody/custody track/` — x402 design documents
