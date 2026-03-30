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

```bash
# Docker로 빠르게 띄우기
docker run -d \
  --name x402-postgres \
  -e POSTGRES_DB=x402 \
  -e POSTGRES_USER=x402 \
  -e POSTGRES_PASSWORD=x402 \
  -p 5432:5432 \
  postgres:16
```

### 앱 실행

```bash
cd F:\Workplace\x402-payment-service

# 기본값으로 실행 (localhost:5432, DB/USER/PASSWORD 모두 x402)
.\gradlew.bat bootRun

# 환경변수로 오버라이드
$env:DB_URL      = "jdbc:postgresql://localhost:5432/x402"
$env:DB_USERNAME = "x402"
$env:DB_PASSWORD = "x402"
.\gradlew.bat bootRun
```

기본 URL: `http://localhost:8080`

### 테스트 실행

테스트는 H2 in-memory DB를 사용하므로 PostgreSQL 없이도 실행 가능하다.

```bash
.\gradlew.bat test
```

---

## 환경변수 설정

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/x402` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `x402` | DB 사용자명 |
| `DB_PASSWORD` | `x402` | DB 비밀번호 |
| `DB_POOL_SIZE` | `10` | HikariCP 최대 커넥션 수 |
| `JPA_DDL_AUTO` | `validate` | Hibernate DDL 전략 |
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

응답 헤더:

```
X-Payment-Protocol: x402/1
X-Payment-Required: true
X-Payment-Intent-Id: <uuid>
X-Payment-Merchant: demo-merchant
X-Payment-Endpoint: /x402/protected/report
X-Payment-Asset: USDC
X-Payment-Amount: 1000
X-Payment-Payer: <payer>
Link: </x402/payment-intents/<uuid>/authorize>; rel="authorize", ...
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
X402_FACILITATOR_RPC_URL   = https://mainnet.base.org
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
