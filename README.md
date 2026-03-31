# x402-payment-service

x402 프로토콜 기반 결제 인가 서비스. EIP-3009 `transferWithAuthorization` 서명 검증, 정책 엔진, 원장, 감사 로그를 포함한다.

---

## 구현 현황

| 단계 | 내용 | 상태 |
|---|---|---|
| 1단계 | EIP-3009 서명 검증 (EIP-712 digest + ecrecover) | ✅ 완료 |
| 2단계 | x402 표준 challenge 응답 (`accepts[]`, network, asset contract) | ✅ 완료 |
| 3단계 | PostgreSQL 전환 (테스트는 H2 유지) | ✅ 완료 |
| 4단계 | Facilitator 외부화 (verify/settle 온체인 브로드캐스트 분리) | 🔲 TODO |

---

## 핵심 흐름

```
클라이언트                          x402-payment-service
    │                                       │
    │── GET /x402/protected/report ────────▶│
    │                                       │ PaymentIntent 생성
    │◀── 402 + accepts[] + paymentIntentId ─│
    │                                       │
    │── POST /authorize (EIP-3009 sig) ────▶│
    │                                       │ EIP-712 digest 검증
    │                                       │ ecrecover → from 주소 확인
    │                                       │ replay 방지 (nonce + digest)
    │◀── PA2_VERIFIED + authorizationId ────│
    │                                       │
    │── POST /capture (authorizationId) ───▶│
    │                                       │ ledger: RESERVE→COMMIT→SETTLE
    │◀── PS2_SETTLED ───────────────────────│
    │                                       │
    │── GET /x402/protected/report ────────▶│ (동일 Idempotency-Key)
    │◀── 200 OK + report payload ───────────│
```

---

## 실행

### 사전 조건

PostgreSQL이 실행 중이어야 한다.

```powershell
# Docker로 빠르게 띄우기
docker run -d `
  --name x402-postgres `
  -e POSTGRES_DB=x402 `
  -e POSTGRES_USER=x402 `
  -e POSTGRES_PASSWORD=x402 `
  -p 5432:5432 `
  postgres:16
```

### 앱 실행

```powershell
cd F:\Workplace\x402-payment-service

# 기본값으로 실행 (DB: localhost:5432/x402, USER/PASSWORD: x402)
.\gradlew.bat bootRun
```

기본 URL: `http://localhost:8081`

환경변수 오버라이드:

```powershell
$env:DB_URL      = "jdbc:postgresql://localhost:5432/x402"
$env:DB_USERNAME = "x402"
$env:DB_PASSWORD = "x402"
.\gradlew.bat bootRun
```

---

## 환경변수 설정

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/x402` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `x402` | DB 사용자명 |
| `DB_PASSWORD` | `x402` | DB 비밀번호 |
| `DB_POOL_SIZE` | `10` | HikariCP 최대 커넥션 수 |
| `JPA_DDL_AUTO` | `update` | Hibernate DDL 전략 |
| `X402_EIP3009_CHAIN_ID` | `8453` | EIP-712 domain chain ID (Base Mainnet) |
| `X402_EIP3009_TOKEN_NAME` | `USD Coin` | EIP-712 domain token name |
| `X402_EIP3009_TOKEN_VERSION` | `2` | EIP-712 domain token version |
| `X402_EIP3009_TOKEN_CONTRACT` | `0xA0b86991...` | USDC 컨트랙트 주소 |
| `X402_POLICY_MAX_AMOUNT` | `10000` | 단일 결제 허용 최대 금액 |
| `X402_ALLOWED_MERCHANTS` | `demo-merchant,lab-merchant` | 허용 merchant ID 목록 |

---

## API

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/x402/protected/report` | 보호 자원 접근. 미결제 시 `402` 반환 |
| `POST` | `/x402/payment-intents` | PaymentIntent 직접 생성 |
| `POST` | `/x402/payment-intents/{id}/authorize` | EIP-3009 서명 제출 및 검증 |
| `POST` | `/x402/payment-intents/{id}/capture` | Authorization 소비 및 정산 |
| `GET` | `/x402/payment-intents/{id}` | Intent 상태 조회 |
| `GET` | `/x402/payment-intents/{id}/audits` | Audit trail 조회 |
| `GET` | `/x402/payment-intents/{id}/ledger` | Ledger entry 조회 |

---

## 테스트 가이드

### A. 자동화 테스트 (권장 — PostgreSQL 불필요)

테스트는 H2 in-memory DB를 사용한다. PostgreSQL 없이 바로 실행 가능하다.

```powershell
cd F:\Workplace\x402-payment-service
.\gradlew.bat test
```

예상 출력:

```
> Task :test

READMEExamplesIntegrationTest > README - Payment Intent 직접 생성 예제 PASSED
READMEExamplesIntegrationTest > README - 실패 시나리오 예제 1. 허용되지 않은 merchant PASSED
READMEExamplesIntegrationTest > README - 실패 시나리오 예제 2. 금액 초과 PASSED
READMEExamplesIntegrationTest > README - 실패 시나리오 예제 3. Authorization replay PASSED
READMEExamplesIntegrationTest > README - 실패 시나리오 예제 4. 만료된 authorization PASSED
READMEExamplesIntegrationTest > README - 빠른 시작 / PowerShell 예제 1~5. ... PASSED

BUILD SUCCESSFUL in ~40s
6 actionable tasks: 4 executed, 2 up-to-date
```

각 테스트가 검증하는 내용:

| 테스트 | 검증 내용 |
|---|---|
| Payment Intent 직접 생성 | PI1→PI4 전체 상태 전이, audit 4건, ledger 3건 |
| 허용되지 않은 merchant | PI9_REJECTED, MERCHANT_NOT_ALLOWED audit |
| 금액 초과 | PI9_REJECTED, ENDPOINT_AMOUNT_LIMIT_EXCEEDED audit |
| Authorization replay | 동일 nonce 재사용 시 400 + AUTHORIZATION_REPLAY_BLOCKED |
| 만료된 authorization | validBefore 과거 시 400 + AUTHORIZATION_EXPIRED |
| 빠른 시작 (전체 흐름) | 402→authorize→capture→200 OK, 응답 헤더 전체 |

---

### B. 수동 API 테스트 — 서명 불필요 시나리오

앱을 먼저 실행한다 (`.\gradlew.bat bootRun`).

```powershell
$BASE_URL = "http://localhost:8081"
```

#### B-1. 402 Challenge 받기

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

예상 응답 (HTTP 402):

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

응답 헤더 확인:

```powershell
try {
  Invoke-WebRequest -Method GET `
    -Uri "$BASE_URL/x402/protected/report" `
    -Headers @{ "Idempotency-Key" = "test-hdr-001"; "X-Payer" = "agent-1" }
} catch {
  $_.Exception.Response.Headers | Format-List
}
```

예상 헤더:

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

#### B-2. 허용되지 않은 Merchant (정책 거부)

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

#### B-3. 금액 초과 (정책 거부)

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

#### B-4. Intent 상태 / Audit / Ledger 조회

```powershell
# (paymentIntentId는 앞 단계에서 가져온 값)
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId"
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/audits"
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/ledger"
```

---

### C. 수동 API 테스트 — 전체 결제 흐름 (서명 필요)

authorize 단계는 EIP-3009 서명이 필요하다. 아래 Python 스크립트로 서명을 생성한다.

#### 사전 설치

```bash
pip install web3 eth-account
```

#### 서명 생성 스크립트

`generate_sig.py`로 저장 후 실행:

```python
from eth_account import Account
from eth_account.messages import encode_typed_data

# ── 설정 (실제 값으로 교체) ──────────────────────────────────────
PRIVATE_KEY      = "0x4c0883a69102937d6231471b5dbb6e538eba2ef2d28aa3e45bed14d0a37f52ea"
FROM_ADDRESS     = "0xabad1fd3fe392a6b45f6a80955263c4575defb91"  # PRIVATE_KEY에 대응하는 주소
TO_ADDRESS       = "0x000000000000000000000000000000000000dead"   # payee 주소
VALUE            = 1000
VALID_AFTER      = 0
VALID_BEFORE     = 1798761599   # 2026-12-31T23:59:59Z
NONCE            = "0x" + "ab" * 32   # 32바이트 hex, 사용마다 새 값
CHAIN_ID         = 8453              # Base Mainnet (로컬 테스트면 1337)
TOKEN_CONTRACT   = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
TOKEN_NAME       = "USD Coin"
TOKEN_VERSION    = "2"
# ─────────────────────────────────────────────────────────────────

typed_data = {
    "types": {
        "EIP712Domain": [
            {"name": "name",              "type": "string"},
            {"name": "version",           "type": "string"},
            {"name": "chainId",           "type": "uint256"},
            {"name": "verifyingContract", "type": "address"},
        ],
        "TransferWithAuthorization": [
            {"name": "from",        "type": "address"},
            {"name": "to",          "type": "address"},
            {"name": "value",       "type": "uint256"},
            {"name": "validAfter",  "type": "uint256"},
            {"name": "validBefore", "type": "uint256"},
            {"name": "nonce",       "type": "bytes32"},
        ],
    },
    "primaryType": "TransferWithAuthorization",
    "domain": {
        "name":              TOKEN_NAME,
        "version":           TOKEN_VERSION,
        "chainId":           CHAIN_ID,
        "verifyingContract": TOKEN_CONTRACT,
    },
    "message": {
        "from":        FROM_ADDRESS,
        "to":          TO_ADDRESS,
        "value":       VALUE,
        "validAfter":  VALID_AFTER,
        "validBefore": VALID_BEFORE,
        "nonce":       bytes.fromhex(NONCE[2:]),
    },
}

account = Account.from_key(PRIVATE_KEY)
signed  = account.sign_typed_data(typed_data)

print(f'"from":        "{FROM_ADDRESS}"')
print(f'"to":          "{TO_ADDRESS}"')
print(f'"value":       {VALUE}')
print(f'"validAfter":  {VALID_AFTER}')
print(f'"validBefore": {VALID_BEFORE}')
print(f'"nonce":       "{NONCE}"')
print(f'"v":           {signed.v}')
print(f'"r":           "0x{signed.r.to_bytes(32, "big").hex()}"')
print(f'"s":           "0x{signed.s.to_bytes(32, "big").hex()}"')
```

실행:

```bash
python generate_sig.py
```

출력 예시:

```
"from":        "0xabad1fd3fe392a6b45f6a80955263c4575defb91"
"to":          "0x000000000000000000000000000000000000dead"
"value":       1000
"validAfter":  0
"validBefore": 1798761599
"nonce":       "0xabab...abab"
"v":           28
"r":           "0x1a2b3c..."
"s":           "0x4d5e6f..."
```

#### C-1. Intent 생성 → 402 Challenge

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

예상: HTTP 402, `$challenge.x402Version = 1`

#### C-2. Authorize (Python 스크립트 출력값 사용)

```powershell
$auth = Invoke-RestMethod -Method POST `
  -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/authorize" `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body (@{
    from        = "0xabad1fd3fe392a6b45f6a80955263c4575defb91"
    to          = "0x000000000000000000000000000000000000dead"
    value       = 1000
    validAfter  = 0
    validBefore = 1798761599
    nonce       = "0xabab...abab"   # Python 출력값
    v           = 28                # Python 출력값
    r           = "0x1a2b3c..."     # Python 출력값
    s           = "0x4d5e6f..."     # Python 출력값
  } | ConvertTo-Json) `

$authorizationId = $auth.id
Write-Host "AuthorizationId: $authorizationId"
Write-Host "Status: $($auth.status)"   # PA2_VERIFIED
```

예상 응답:

```json
{
  "id": "<uuid>",
  "paymentIntentId": "<uuid>",
  "payer": "0xabad1fd3...",
  "status": "PA2_VERIFIED",
  "consumed": false
}
```

실패 케이스:
- 서명 주소 불일치 → `400 SIGNATURE_MISMATCH`
- 만료된 서명 → `400 AUTHORIZATION_EXPIRED`
- 동일 nonce 재사용 → `400 AUTHORIZATION_REPLAY_BLOCKED`

#### C-3. Capture

```powershell
$settlement = Invoke-RestMethod -Method POST `
  -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/capture" `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body (@{ authorizationId = $authorizationId } | ConvertTo-Json)

Write-Host "Settlement Status: $($settlement.status)"   # PS2_SETTLED
```

예상 응답:

```json
{
  "id": "<uuid>",
  "paymentIntentId": "<uuid>",
  "authorizationId": "<uuid>",
  "status": "PS2_SETTLED"
}
```

#### C-4. 보호 자원 재접근 (200 OK)

```powershell
# 동일한 Idempotency-Key로 재요청
$report = Invoke-RestMethod -Method GET `
  -Uri "$BASE_URL/x402/protected/report" `
  -Headers @{ "Idempotency-Key" = "flow-001"; "X-Payer" = "agent-1" }

$report
```

예상 응답 (HTTP 200):

```json
{
  "accessGranted": true,
  "reportId": "premium-report-001",
  "reportName": "Premium Custody Revenue Report",
  "payload": "Paid access granted. This is the protected premium report payload.",
  "paymentIntentId": "<uuid>"
}
```

#### C-5. 전체 상태 확인

```powershell
# Intent 최종 상태
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId"
# status: PI4_SETTLED

# Audit trail (4건)
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/audits"
# [0] intent.created
# [1] policy.evaluated
# [2] authorization.verified
# [3] settlement.completed

# Ledger (3건)
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/ledger"
# [0] RESERVE
# [1] COMMIT
# [2] SETTLE
```

---

## 402 Challenge 응답 구조

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

## Authorization 요청 구조 (EIP-3009)

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

서명 검증 방식:

1. EIP-712 domain separator 계산 (chainId, tokenName, tokenVersion, tokenContract)
2. `TransferWithAuthorization` struct hash 계산
3. EIP-712 final digest = `keccak256(0x1901 || domainSeparator || structHash)`
4. `ecrecover(digest, v, r, s)` → 복원 주소 = `from` 확인

---

## PaymentIntent 상태머신

```
PI0_REQUESTED
    │
    ▼
PI1_POLICY_CHECKED ──(정책 거부)──▶ PI9_REJECTED
    │
    ▼
PI2_AUTHORIZED ──(만료)──▶ PI10_EXPIRED
    │
    ▼
PI3_CAPTURED
    │
    ▼
PI4_SETTLED
```

---

## TODO — 4단계: Facilitator 외부화

현재 settlement는 내부 ledger 업데이트만 수행한다. 실제 온체인 결제를 처리하려면 아래 작업이 필요하다.

### 배경

x402 프로토콜에서 **Facilitator**는 non-custodial 중간 레이어로, 두 가지 책임을 분리한다:

| 역할 | 설명 |
|---|---|
| **Verifier** | EIP-3009 서명의 온체인 유효성 검증 (잔액, nonce, 주소 확인) |
| **Settler** | 검증된 authorization을 실제 체인에 브로드캐스트 |

### 구현 항목

**[ ] FacilitatorClient 인터페이스 정의**
```java
public interface FacilitatorClient {
    VerifyResult verify(AuthorizePaymentRequest request);
    SettleResult settle(UUID paymentIntentId, UUID authorizationId);
}
```

**[ ] BaseFacilitatorClient 구현**
- Base RPC 연동 (`eth_call` → USDC 잔액, allowance 확인)
- `transferWithAuthorization` 트랜잭션 브로드캐스트
- 트랜잭션 해시 수신 및 저장

**[ ] X402SettlementService 수정**
- `ledgerService.settle()` 이후 `facilitatorClient.settle()` 호출
- 트랜잭션 해시를 `PaymentSettlement`에 저장
- 외부 시스템 호출이 포함되므로 트랜잭션 경계 재설계 필요

**[ ] 온체인 settlement 상태 추적**
- `PaymentSettlement`에 `txHash`, `blockNumber`, `onchainStatus` 필드 추가
- 비동기 confirmation 폴링 또는 webhook 수신

**[ ] 환경변수 추가**
```
X402_FACILITATOR_RPC_URL     = https://mainnet.base.org
X402_FACILITATOR_PRIVATE_KEY = <hot wallet key>
```

### 참고

- Facilitator는 custodial이 아니어야 함 — 자산을 보관하지 않고 브로드캐스트만 수행
- verify 단계에서 온체인 잔액 확인 → settle 실패 사전 차단
- Base Mainnet USDC: `0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913`
- Base Sepolia USDC: `0x036CbD53842c5426634e7929541eC2318f3dCF7e`

---

## 관련 레포

- `f:\Workplace\custody\x402-payment-lab` — 학습용 mock 구현 (reference)
- `f:\Workplace\custody\custody track\` — x402 설계 문서
