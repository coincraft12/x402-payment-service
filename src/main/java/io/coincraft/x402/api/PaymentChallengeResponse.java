package io.coincraft.x402.api;

import java.util.List;
import java.util.UUID;

/**
 * HTTP 402 응답 페이로드.
 *
 * <p>x402 표준 필드({@code x402Version}, {@code accepts})와
 * 서비스 워크플로우 필드({@code paymentIntentId}, {@code authorizePath} 등)를 함께 포함한다.
 */
public record PaymentChallengeResponse(
        // ── x402 표준 ──────────────────────────────────────────────
        int x402Version,
        List<PaymentSchemeOffer> accepts,
        String error,

        // ── 서비스 워크플로우 ────────────────────────────────────────
        int status,
        String message,
        UUID paymentIntentId,
        String authorizePath,
        String capturePath,
        String auditsPath,
        String ledgerPath
) {
}
