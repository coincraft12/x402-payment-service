# x402-payment-service

x402 프로토콜 기반 결제 인가 서비스. EIP-3009 `transferWithAuthorization` 서명 검증, 정책 엔진, 원장, 감사 로그를 포함한다.

---

## 구현 현황

| 단계 | 내용 | 상태 |
|---|---|---|
| 1단계 | EIP-3009 서명 검증 (EIP-712 digest + ecrecover) | ✅ 완료 |
| 2단계 | x402 표준 challenge 응답 (`accepts[]`, network, asset contract) | ✅ 완료 |
| 3단계 | PostgreSQL 전환 (테스트는 H2 유지) | ✅ 완료 |
| 4단계 | Facilitator 온체인 브로드캐스트 (`transferWithAuthorization` via web3j) | ✅ 완료 |

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
    │                                       │ Facilitator: transferWithAuthorization 브로드캐스트
    │                                       │ → txHash를 PaymentSettlement에 저장
    │◀── PS2_SETTLED + txHash ──────────────│
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
| `X402_EIP3009_TOKEN_CONTRACT` | `0x833589fC...` | Base Mainnet USDC 컨트랙트 주소 |
| `X402_POLICY_MAX_AMOUNT` | `10000` | 단일 결제 허용 최대 금액 |
| `X402_ALLOWED_MERCHANTS` | `demo-merchant,lab-merchant` | 허용 merchant ID 목록 |
| `X402_FACILITATOR_ENABLED` | `false` | 온체인 브로드캐스트 활성화 |
| `X402_FACILITATOR_RPC_URL` | `https://sepolia.base.org` | 온체인 브로드캐스트용 RPC 엔드포인트 |
| `X402_FACILITATOR_PRIVATE_KEY` | _(없음)_ | 핫월렛 개인키 (`0x` 접두사 없이) |

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
    "asset": "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
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

### C. 수동 API 테스트 — 전체 결제 흐름 + 온체인 정산 (Base Sepolia)

authorize 단계는 EIP-3009 서명이 필요하다. 포함된 Node.js 스크립트로 서명을 생성한다.

#### 사전 조건

Node.js v18 이상 필요.

```powershell
cd F:\Workplace\x402-payment-service
npm install
```

#### 환경변수 (Base Sepolia)

```powershell
$env:X402_EIP3009_TOKEN_NAME      = "USDC"
$env:X402_EIP3009_CHAIN_ID        = "84532"
$env:X402_EIP3009_TOKEN_CONTRACT  = "0x036CbD53842c5426634e7929541eC2318f3dCF7e"
$env:X402_FACILITATOR_ENABLED     = "true"
$env:X402_FACILITATOR_RPC_URL     = "https://sepolia.base.org"
$env:X402_FACILITATOR_PRIVATE_KEY = "<핫월렛 개인키, 0x 없이>"
.\gradlew.bat bootRun
```

> **Base Sepolia 수도꼭지**
> - ETH (가스비): https://www.alchemy.com/faucets/base-sepolia
> - USDC: https://faucet.circle.com (Base Sepolia 선택)

#### USDC 컨트랙트 도메인 확인 (선택)

```powershell
node check_domain.js
# name   : USDC
# version: 2
# match: true
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

#### C-2. 서명 생성 및 Authorize

서명 생성 + 실행 가능한 PowerShell 커맨드 자동 출력:

```powershell
node sign_eip3009.js $paymentIntentId
```

스크립트가 `Invoke-RestMethod` 커맨드를 출력한다. 그대로 실행하면 된다:

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
  "status": "PS2_SETTLED",
  "txHash": "0x9ec59e2ac252474cd07adb1fdf7eca5d40114d8cee318c04ada9fefc6f1b76f2"
}
```

BaseScan에서 트랜잭션 확인:
```
https://sepolia.basescan.org/tx/<txHash>
```
USDC 컨트랙트의 `transferWithAuthorization` 호출이 확인되면 온체인 정산 성공이다.

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
      "asset": "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
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

## 4단계: Facilitator 온체인 브로드캐스트

Facilitator는 non-custodial 중간 레이어다. 사용자 자산을 보관하지 않고, 미리 서명된 EIP-3009 authorization을 체인에 브로드캐스트하는 역할만 한다.

### 아키텍처

```
클라이언트 EIP-3009 서명 → x402-payment-service 검증 → FacilitatorClient.settle()
                                                              │
                                                              ▼
                                                   BaseFacilitatorClient
                                                   - transferWithAuthorization ABI 인코딩
                                                   - web3j로 nonce + gasPrice 조회
                                                   - RawTransaction 서명 (EIP-155, chainId)
                                                   - ethSendRawTransaction → txHash
```

### 주요 설계 결정

| 결정 | 이유 |
|---|---|
| `@ConditionalOnProperty` — disabled 시 `NoOpFacilitatorClient` | RPC/지갑 없이 테스트 가능 |
| Legacy `RawTransaction` (EIP-1559 아님) | Base Sepolia 호환성 |
| txHash를 `PaymentSettlement`에 저장 | 온체인 정산 증거 |
| Facilitator 호출을 `@Transactional` 내부에서 실행 | 프로토타입 단순화 — 아래 TODO 참조 |

### 네트워크별 설정

| 네트워크 | Chain ID | USDC 컨트랙트 | Token Name |
|---|---|---|---|
| Base Mainnet | `8453` | `0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913` | `USD Coin` |
| Base Sepolia | `84532` | `0x036CbD53842c5426634e7929541eC2318f3dCF7e` | `USDC` |

### TODO (프로덕션 준비)

- **Outbox 패턴**: Facilitator 호출을 `@Transactional` 밖으로 분리 — 온체인 성공 후 DB 커밋 실패 시 txHash 유실 위험
- **잔액 사전 확인**: 브로드캐스트 전 payer USDC 잔액 검증
- **비동기 confirmation**: 브로드캐스트 후 블록 확인 폴링 (현재 fire-and-forget)
- **가스 추정**: 고정값 `GAS_LIMIT=100_000` 대신 `eth_estimateGas` 사용

### TODO (설계 결정 필요)

- **정산 아키텍처**: settlement를 outbox 기반 비동기 워크플로로 옮길지, 아니면 동기 capture를 유지하면서 보상/복구 전략을 둘지 결정
- **키 관리 방식**: Facilitator 서명 키를 비운영 환경에서만 env var로 둘지, 운영에서는 KMS/HSM 기반 서명으로 옮길지 결정
- **권한 모델**: `create`, `authorize`, `capture`, `status`, `audits`, `ledger`를 누가 호출할 수 있는지와 API key, 서비스 간 인증, wallet proof, 혼합 모델 중 어떤 방식을 쓸지 정의
- **온체인 finality 기준**: settlement 성공을 tx 브로드캐스트 시점으로 볼지, 첫 블록 포함 시점으로 볼지, N-block confirmation 기준으로 볼지 결정
- **재시도 및 복구 운영 정책**: 실패한 settlement를 어떻게 재시도할지, 누가 replay/recovery를 수행할지, 수동 개입 시 어떤 audit trail이 필요한지 정의

---

## 관련 레포

- `f:\Workplace\custody\x402-payment-lab` — 학습용 mock 구현 (reference)
- `f:\Workplace\custody\custody track\` — x402 설계 문서
