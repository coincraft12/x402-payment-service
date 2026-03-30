package io.coincraft.x402;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coincraft.x402.support.Eip3009Verifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

import java.math.BigInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class READMEExamplesIntegrationTest {

    /** 테스트 전용 고정 private key (절대 실제 환경에 사용 금지) */
    private static final String TEST_PRIVATE_KEY =
            "4c0883a69102937d6231471b5dbb6e538eba2ef2d28aa3e45bed14d0a37f52ea";

    private static ECKeyPair testKeyPair;
    private static String testFromAddress;
    private static final String TEST_TO_ADDRESS = "0x000000000000000000000000000000000000dead";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Eip3009Verifier eip3009Verifier;

    @BeforeAll
    static void setupKeyPair() {
        testKeyPair = ECKeyPair.create(new BigInteger(TEST_PRIVATE_KEY, 16));
        testFromAddress = "0x" + Keys.getAddress(testKeyPair);
    }

    // ── EIP-3009 서명 헬퍼 ──────────────────────────────────────────────────

    /**
     * 테스트용 AuthorizePaymentRequest JSON 생성.
     * Eip3009Verifier.digest()로 EIP-712 hash 계산 후 testKeyPair로 서명.
     */
    private String buildAuthRequestJson(long value, long validBefore, String nonceHex) throws Exception {
        io.coincraft.x402.api.AuthorizePaymentRequest tempReq = new io.coincraft.x402.api.AuthorizePaymentRequest(
                testFromAddress,
                TEST_TO_ADDRESS,
                BigInteger.valueOf(value),
                0L,
                validBefore,
                nonceHex,
                27, "0x" + "00".repeat(32), "0x" + "00".repeat(32)
        );

        String digestHex = eip3009Verifier.digest(tempReq);
        byte[] digestBytes = hexToBytes(digestHex);

        Sign.SignatureData sig = Sign.signMessage(digestBytes, testKeyPair, false);

        int v = sig.getV()[0] & 0xFF;
        String r = "0x" + bytesToHex(sig.getR());
        String s = "0x" + bytesToHex(sig.getS());

        return """
                {
                  "from": "%s",
                  "to": "%s",
                  "value": %d,
                  "validAfter": 0,
                  "validBefore": %d,
                  "nonce": "%s",
                  "v": %d,
                  "r": "%s",
                  "s": "%s"
                }
                """.formatted(testFromAddress, TEST_TO_ADDRESS, value, validBefore, nonceHex, v, r, s);
    }

    private static String randomNonce() {
        UUID uuid = UUID.randomUUID();
        return "0x" + String.format("%032x", uuid.getMostSignificantBits()) +
               String.format("%032x", uuid.getLeastSignificantBits());
    }

    private static long futureDeadline() {
        return java.time.Instant.parse("2026-12-31T23:59:59Z").getEpochSecond();
    }

    private static long pastDeadline() {
        return java.time.Instant.parse("2024-01-01T00:00:00Z").getEpochSecond();
    }

    // ── 테스트 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("README - Payment Intent 직접 생성 예제")
    void readme_paymentIntentDirectCreationExample() throws Exception {
        String idempotencyKey = "readme-happy-" + UUID.randomUUID();

        JsonNode intent = postJson(
                "/x402/payment-intents",
                """
                {
                  "merchantId": "demo-merchant",
                  "endpoint": "/premium/report",
                  "asset": "USDC",
                  "amount": 1000,
                  "payer": "agent-1",
                  "payee": "merchant-vault"
                }
                """,
                idempotencyKey
        );

        String paymentIntentId = intent.get("id").asText();
        assertThat(intent.get("status").asText()).isEqualTo("PI1_POLICY_CHECKED");

        JsonNode authorization = postJson(
                "/x402/payment-intents/" + paymentIntentId + "/authorize",
                buildAuthRequestJson(1000, futureDeadline(), randomNonce()),
                null
        );

        String authorizationId = authorization.get("id").asText();
        assertThat(authorization.get("status").asText()).isEqualTo("PA2_VERIFIED");
        assertThat(authorization.get("consumed").asBoolean()).isFalse();

        JsonNode settlement = postJson(
                "/x402/payment-intents/" + paymentIntentId + "/capture",
                """
                {
                  "authorizationId": "%s"
                }
                """.formatted(authorizationId),
                null
        );

        assertThat(settlement.get("status").asText()).isEqualTo("PS2_SETTLED");

        mockMvc.perform(get("/x402/payment-intents/{id}", paymentIntentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PI4_SETTLED"));

        mockMvc.perform(get("/x402/payment-intents/{id}/audits", paymentIntentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("intent.created"))
                .andExpect(jsonPath("$[1].eventType").value("policy.evaluated"))
                .andExpect(jsonPath("$[2].eventType").value("authorization.verified"))
                .andExpect(jsonPath("$[3].eventType").value("settlement.completed"));

        mockMvc.perform(get("/x402/payment-intents/{id}/ledger", paymentIntentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("RESERVE"))
                .andExpect(jsonPath("$[1].type").value("COMMIT"))
                .andExpect(jsonPath("$[2].type").value("SETTLE"));
    }

    @Test
    @DisplayName("README - 실패 시나리오 예제 1. 허용되지 않은 merchant")
    void readme_failureExample_unknownMerchant() throws Exception {
        String idempotencyKey = "readme-bad-merchant-" + UUID.randomUUID();

        JsonNode intent = postJson(
                "/x402/payment-intents",
                """
                {
                  "merchantId": "unknown-merchant",
                  "endpoint": "/premium/report",
                  "asset": "USDC",
                  "amount": 1000,
                  "payer": "agent-1",
                  "payee": "merchant-vault"
                }
                """,
                idempotencyKey
        );

        String paymentIntentId = intent.get("id").asText();
        assertThat(intent.get("status").asText()).isEqualTo("PI9_REJECTED");

        mockMvc.perform(get("/x402/payment-intents/{id}/audits", paymentIntentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].reason").value("MERCHANT_NOT_ALLOWED: unknown-merchant"));
    }

    @Test
    @DisplayName("README - 실패 시나리오 예제 2. 금액 초과")
    void readme_failureExample_amountOverLimit() throws Exception {
        String idempotencyKey = "readme-over-limit-" + UUID.randomUUID();

        JsonNode intent = postJson(
                "/x402/payment-intents",
                """
                {
                  "merchantId": "demo-merchant",
                  "endpoint": "/premium/report",
                  "asset": "USDC",
                  "amount": 999999,
                  "payer": "agent-1",
                  "payee": "merchant-vault"
                }
                """,
                idempotencyKey
        );

        String paymentIntentId = intent.get("id").asText();
        assertThat(intent.get("status").asText()).isEqualTo("PI9_REJECTED");

        mockMvc.perform(get("/x402/payment-intents/{id}/audits", paymentIntentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].reason").value("ENDPOINT_AMOUNT_LIMIT_EXCEEDED: max=10000, requested=999999"));
    }

    @Test
    @DisplayName("README - 실패 시나리오 예제 3. Authorization replay")
    void readme_failureExample_authorizationReplay() throws Exception {
        String paymentIntentId = createReadmeStyleIntent("readme-replay-" + UUID.randomUUID());
        String nonce = randomNonce();
        String authBody = buildAuthRequestJson(1000, futureDeadline(), nonce);

        postJson("/x402/payment-intents/" + paymentIntentId + "/authorize", authBody, null);

        mockMvc.perform(post("/x402/payment-intents/{id}/authorize", paymentIntentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("AUTHORIZATION_REPLAY_BLOCKED"));
    }

    @Test
    @DisplayName("README - 실패 시나리오 예제 4. 만료된 authorization")
    void readme_failureExample_expiredAuthorization() throws Exception {
        String paymentIntentId = createReadmeStyleIntent("readme-expired-" + UUID.randomUUID());

        mockMvc.perform(post("/x402/payment-intents/{id}/authorize", paymentIntentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildAuthRequestJson(1000, pastDeadline(), randomNonce())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("AUTHORIZATION_EXPIRED"));
    }

    @Test
    @DisplayName("README - 빠른 시작 / PowerShell 예제 1~5. 보호 자원 접근 -> 402 challenge -> authorize -> capture -> 200 OK")
    void readme_quickStart_challengeAuthorizeCaptureAndProtectedResourceAccess() throws Exception {
        String idempotencyKey = "readme-challenge-" + UUID.randomUUID();

        MvcResult challengeResult = mockMvc.perform(get("/x402/protected/report")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Payer", "agent-1"))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.x402Version").value(1))
                .andExpect(jsonPath("$.error").value("X402 Payment Required"))
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.accepts[0].scheme").value("exact"))
                .andExpect(jsonPath("$.accepts[0].resource").value("/x402/protected/report"))
                .andExpect(jsonPath("$.accepts[0].asset").value("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"))
                .andExpect(jsonPath("$.accepts[0].network").value("base-mainnet"))
                .andExpect(jsonPath("$.accepts[0].extra.name").value("USD Coin"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Payment-Protocol", "x402/1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Payment-Required", "true"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Payment-Merchant", "demo-merchant"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Payment-Endpoint", "/x402/protected/report"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Payment-Asset", "USDC"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Payment-Amount", "1000"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Payment-Payer", "agent-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Payment-Intent-Id"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Link", org.hamcrest.Matchers.containsString("rel=\"authorize\"")))
                .andReturn();

        JsonNode challenge = objectMapper.readTree(challengeResult.getResponse().getContentAsString());
        String paymentIntentId = challenge.get("paymentIntentId").asText();

        JsonNode authorization = postJson(
                "/x402/payment-intents/" + paymentIntentId + "/authorize",
                buildAuthRequestJson(1000, futureDeadline(), randomNonce()),
                null
        );

        postJson(
                "/x402/payment-intents/" + paymentIntentId + "/capture",
                """
                {
                  "authorizationId": "%s"
                }
                """.formatted(authorization.get("id").asText()),
                null
        );

        mockMvc.perform(get("/x402/protected/report")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Payer", "agent-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessGranted").value(true))
                .andExpect(jsonPath("$.reportId").value("premium-report-001"))
                .andExpect(jsonPath("$.paymentIntentId").value(paymentIntentId));
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────────

    private String createReadmeStyleIntent(String idempotencyKey) throws Exception {
        JsonNode intent = postJson(
                "/x402/payment-intents",
                """
                {
                  "merchantId": "demo-merchant",
                  "endpoint": "/premium/report",
                  "asset": "USDC",
                  "amount": 1000,
                  "payer": "agent-1",
                  "payee": "merchant-vault"
                }
                """,
                idempotencyKey
        );
        return intent.get("id").asText();
    }

    private JsonNode postJson(String path, String body, String idempotencyKey) throws Exception {
        var request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);

        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
