package io.coincraft.x402;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class READMEExamplesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
                """
                {
                  "nonce": 1,
                  "deadline": "2026-12-31T23:59:59Z",
                  "signature": "demo-signature"
                }
                """,
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

        postJson(
                "/x402/payment-intents/" + paymentIntentId + "/authorize",
                """
                {
                  "nonce": 7,
                  "deadline": "2026-12-31T23:59:59Z",
                  "signature": "demo-signature"
                }
                """,
                null
        );

        mockMvc.perform(post("/x402/payment-intents/{id}/authorize", paymentIntentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nonce": 7,
                                  "deadline": "2026-12-31T23:59:59Z",
                                  "signature": "demo-signature"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("AUTHORIZATION_REPLAY_BLOCKED"));
    }

    @Test
    @DisplayName("README - 실패 시나리오 예제 4. 만료된 authorization")
    void readme_failureExample_expiredAuthorization() throws Exception {
        String paymentIntentId = createReadmeStyleIntent("readme-expired-" + UUID.randomUUID());

        mockMvc.perform(post("/x402/payment-intents/{id}/authorize", paymentIntentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nonce": 99,
                                  "deadline": "2024-01-01T00:00:00Z",
                                  "signature": "expired-signature"
                                }
                                """))
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
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.error").value("payment_required"))
                .andExpect(jsonPath("$.endpoint").value("/x402/protected/report"))
                .andExpect(jsonPath("$.asset").value("USDC"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Payment-Protocol", "x402-lab"))
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
                """
                {
                  "nonce": 21,
                  "deadline": "2026-12-31T23:59:59Z",
                  "signature": "demo-signature"
                }
                """,
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
}
