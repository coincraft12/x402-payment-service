# x402-payment-lab

독립 실행 가능한 x402 payment authorization 실습 프로젝트다.

이 프로젝트는 `HTTP 402 Payment Required` 스타일의 결제 authorization 흐름을, custody 관점의 `policy`, `replay protection`, `ledger`, `audit` 구조로 실습하기 위한 Spring Boot 앱이다.

핵심 흐름:

1. 보호 자원 접근
2. `402 Payment Required` challenge 수신
3. payment intent authorize
4. capture
5. 같은 요청 재시도
6. `200 OK`로 보호 자원 접근 허용

주요 엔드포인트:

1. `GET /x402/protected/report`
2. `POST /x402/payment-intents/{id}/authorize`
3. `POST /x402/payment-intents/{id}/capture`
4. `GET /x402/payment-intents/{id}`
5. `GET /x402/payment-intents/{id}/audits`
6. `GET /x402/payment-intents/{id}/ledger`

---

## 실행

프로젝트 루트:

```powershell
cd F:\Workplace\custody\x402-payment-lab
```

앱 실행:

```powershell
.\gradlew.bat bootRun
```

기본 URL:

```text
http://localhost:8080
```

H2 Console:

```text
http://localhost:8080/h2
```

---

## 설정

기본 정책 설정은 [application.yaml](./src/main/resources/application.yaml) 에 있다.

```yaml
x402:
  policy:
    max-amount: 10000
    allowed-merchants: demo-merchant,lab-merchant
  challenge:
    report:
      merchant-id: demo-merchant
      endpoint: /x402/protected/report
      asset: USDC
      amount: 1000
      payee: merchant-vault
```

의미:

1. 기본 최대 결제 금액은 `10000`
2. 허용 merchant는 `demo-merchant`, `lab-merchant`
3. 보호 리포트 접근 비용은 `1000 USDC`

환경변수로 덮어쓸 수 있다.

PowerShell 예시:

```powershell
$env:X402_POLICY_MAX_AMOUNT = "20000"
$env:X402_ALLOWED_MERCHANTS = "demo-merchant,lab-merchant,test-merchant"
$env:X402_CHALLENGE_REPORT_AMOUNT = "1500"
.\gradlew.bat bootRun
```

---

## API 요약

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/x402/protected/report` | 보호 자원 접근. 미결제 시 `402 Payment Required` 반환 |
| `POST` | `/x402/payment-intents` | payment intent 직접 생성 |
| `POST` | `/x402/payment-intents/{id}/authorize` | authorization 제출 및 검증 |
| `POST` | `/x402/payment-intents/{id}/capture` | authorization 소비 및 settlement 완료 |
| `GET` | `/x402/payment-intents/{id}` | intent 상태 조회 |
| `GET` | `/x402/payment-intents/{id}/audits` | audit trail 조회 |
| `GET` | `/x402/payment-intents/{id}/ledger` | ledger entry 조회 |

---

## 빠른 시작 / PowerShell 예제 1~5

아래 순서대로 실행하면 된다.

1. 보호 리포트 접근
2. `402` challenge 확인
3. challenge에 담긴 payment intent로 authorize
4. capture 실행
5. 같은 보호 리포트 요청 재시도
6. `200 OK` 응답 확인

---

## PowerShell 예제 0. 공통 변수

```powershell
$BASE_URL = "http://localhost:8080"
```

## PowerShell 예제 1. 보호 자원 접근 -> 402 challenge 받기

```powershell
$challenge = $null

try {
  Invoke-RestMethod -Method GET `
    -Uri "$BASE_URL/x402/protected/report" `
    -Headers @{
      "Idempotency-Key" = "x402-report-001"
      "X-Payer" = "agent-1"
    }
} catch {
  $resp = $_.Exception.Response
  $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
  $raw = $reader.ReadToEnd()
  $challenge = $raw | ConvertFrom-Json
}

$challenge
$paymentIntentId = $challenge.paymentIntentId
```

예상 결과:

1. HTTP status `402 Payment Required`
2. 응답 헤더에도 결제 메타데이터가 같이 포함됨
3. body 안에 `paymentIntentId`, `authorizePath`, `capturePath` 포함

예상 응답 핵심:

```powershell
status         : 402
error          : payment_required
message        : Payment is required before accessing this protected report.
paymentIntentId: <uuid>
endpoint       : /x402/protected/report
asset          : USDC
amount         : 1000
```

예상 헤더 예시:

```text
X-Payment-Protocol: x402-lab
X-Payment-Required: true
X-Payment-Intent-Id: <uuid>
X-Payment-Merchant: demo-merchant
X-Payment-Endpoint: /x402/protected/report
X-Payment-Asset: USDC
X-Payment-Amount: 1000
X-Payment-Payer: agent-1
X-Payment-Payee: merchant-vault
Link: </x402/payment-intents/<uuid>/authorize>; rel="authorize", </x402/payment-intents/<uuid>/capture>; rel="capture", ...
```

PowerShell에서 헤더를 직접 보고 싶다면:

```powershell
try {
  Invoke-WebRequest -Method GET `
    -Uri "$BASE_URL/x402/protected/report" `
    -Headers @{
      "Idempotency-Key" = "x402-report-headers-001"
      "X-Payer" = "agent-1"
    }
} catch {
  $_.Exception.Response.Headers
}
```

## PowerShell 예제 2. Authorization 제출

```powershell
$auth = Invoke-RestMethod -Method POST `
  -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/authorize" `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body (@{
    nonce     = 1
    deadline  = "2026-12-31T23:59:59Z"
    signature = "demo-signature"
  } | ConvertTo-Json)

$auth
$authorizationId = $auth.id
```

예상 응답 핵심:

```powershell
id              : <authorization-uuid>
paymentIntentId : <intent-uuid>
nonce           : 1
status          : PA2_VERIFIED
consumed        : False
```

## PowerShell 예제 3. Capture 실행

```powershell
$settlement = Invoke-RestMethod -Method POST `
  -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/capture" `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body (@{
    authorizationId = $authorizationId
  } | ConvertTo-Json)

$settlement
```

예상 응답 핵심:

```powershell
id              : <settlement-uuid>
paymentIntentId : <intent-uuid>
authorizationId : <authorization-uuid>
status          : PS2_SETTLED
```

## PowerShell 예제 4. 같은 보호 자원 요청 재시도 -> 200 OK

```powershell
$report = Invoke-RestMethod -Method GET `
  -Uri "$BASE_URL/x402/protected/report" `
  -Headers @{
    "Idempotency-Key" = "x402-report-001"
    "X-Payer" = "agent-1"
  }

$report
```

예상 응답 핵심:

```powershell
accessGranted : True
reportId      : premium-report-001
reportName    : Premium Custody Revenue Report
paymentIntentId : <intent-uuid>
```

## PowerShell 예제 5. Intent / Audit / Ledger 조회

```powershell
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId"
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/audits"
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/ledger"
```

예상 상태:

```powershell
status : PI4_SETTLED
```

예상 audit 이벤트 예시:

1. `intent.created`
2. `policy.evaluated`
3. `authorization.verified`
4. `settlement.completed`

예상 ledger entry 예시:

1. `RESERVE`
2. `COMMIT`
3. `SETTLE`

---

## Payment Intent 직접 생성 예제

challenge endpoint 대신 payment intent를 직접 만들고 싶다면 아래를 사용하면 된다.

```powershell
$intent = Invoke-RestMethod -Method POST `
  -Uri "$BASE_URL/x402/payment-intents" `
  -Headers @{
    "Content-Type" = "application/json"
    "Idempotency-Key" = "x402-intent-001"
  } `
  -Body (@{
    merchantId = "demo-merchant"
    endpoint   = "/premium/report"
    asset      = "USDC"
    amount     = 1000
    payer      = "agent-1"
    payee      = "merchant-vault"
  } | ConvertTo-Json)

$intent
```

---

## 실패 시나리오 예제 1. 허용되지 않은 merchant

```powershell
$badIntent = Invoke-RestMethod -Method POST `
  -Uri "$BASE_URL/x402/payment-intents" `
  -Headers @{
    "Content-Type" = "application/json"
    "Idempotency-Key" = "x402-intent-bad-merchant"
  } `
  -Body (@{
    merchantId = "unknown-merchant"
    endpoint   = "/premium/report"
    asset      = "USDC"
    amount     = 1000
    payer      = "agent-1"
    payee      = "merchant-vault"
  } | ConvertTo-Json)

$badIntent
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$($badIntent.id)/audits"
```

예상 결과:

1. intent 상태 `PI9_REJECTED`
2. audit에 `MERCHANT_NOT_ALLOWED`

## 실패 시나리오 예제 2. 금액 초과

```powershell
$tooLargeIntent = Invoke-RestMethod -Method POST `
  -Uri "$BASE_URL/x402/payment-intents" `
  -Headers @{
    "Content-Type" = "application/json"
    "Idempotency-Key" = "x402-intent-too-large"
  } `
  -Body (@{
    merchantId = "demo-merchant"
    endpoint   = "/premium/report"
    asset      = "USDC"
    amount     = 999999
    payer      = "agent-1"
    payee      = "merchant-vault"
  } | ConvertTo-Json)

$tooLargeIntent
Invoke-RestMethod -Uri "$BASE_URL/x402/payment-intents/$($tooLargeIntent.id)/audits"
```

예상 결과:

1. intent 상태 `PI9_REJECTED`
2. audit에 `ENDPOINT_AMOUNT_LIMIT_EXCEEDED`

## 실패 시나리오 예제 3. Authorization replay

동일한 `payer + nonce` 또는 동일 digest를 다시 보내면 거절된다.

```powershell
try {
  Invoke-RestMethod -Method POST `
    -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/authorize" `
    -Headers @{ "Content-Type" = "application/json" } `
    -Body (@{
      nonce     = 1
      deadline  = "2026-12-31T23:59:59Z"
      signature = "demo-signature"
    } | ConvertTo-Json)
} catch {
  $resp = $_.Exception.Response
  $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
  $reader.ReadToEnd()
}
```

예상 결과:

1. `400 Bad Request`
2. 메시지: `AUTHORIZATION_REPLAY_BLOCKED`

## 실패 시나리오 예제 4. 만료된 authorization

```powershell
try {
  Invoke-RestMethod -Method POST `
    -Uri "$BASE_URL/x402/payment-intents/$paymentIntentId/authorize" `
    -Headers @{ "Content-Type" = "application/json" } `
    -Body (@{
      nonce     = 99
      deadline  = "2024-01-01T00:00:00Z"
      signature = "expired-signature"
    } | ConvertTo-Json)
} catch {
  $resp = $_.Exception.Response
  $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
  $reader.ReadToEnd()
}
```

예상 결과:

1. `400 Bad Request`
2. 메시지: `AUTHORIZATION_EXPIRED`

## 실패 시나리오 예제 5. Idempotency 충돌

같은 `Idempotency-Key`로 다른 body를 보내면 충돌이 난다.

```powershell
try {
  Invoke-RestMethod -Method POST `
    -Uri "$BASE_URL/x402/payment-intents" `
    -Headers @{
      "Content-Type" = "application/json"
      "Idempotency-Key" = "x402-intent-001"
    } `
    -Body (@{
      merchantId = "demo-merchant"
      endpoint   = "/premium/report"
      asset      = "USDC"
      amount     = 2000
      payer      = "agent-1"
      payee      = "merchant-vault"
    } | ConvertTo-Json)
} catch {
  $resp = $_.Exception.Response
  $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
  $reader.ReadToEnd()
}
```

예상 결과:

1. `409 Conflict`
2. 메시지: `same Idempotency-Key cannot be used with a different x402 payment intent body`

---

## 관련 문서

- [Session4_ x402 Package Structure Draft.md](/f:/Workplace/custody/custody%20track/Session4_%20x402%20Package%20Structure%20Draft.md)
- [Session4_ x402 Payment Authorization.md](/f:/Workplace/custody/custody%20track/Session4_%20x402%20Payment%20Authorization.md)

---

## 다음 단계

현재는 스캐폴딩과 로컬 실습 중심이다. 다음으로 자연스러운 확장 방향은 다음과 같다.

1. challenge 응답에 만료 시간, 체인/토큰 메타데이터를 더 풍부하게 넣기
2. 실제 `HTTP 402` 헤더 표현 방식 추가
3. signature 검증 로직을 mock 검증에서 구조화 검증으로 강화
4. provider 또는 relayer 연동으로 settlement 외부화
