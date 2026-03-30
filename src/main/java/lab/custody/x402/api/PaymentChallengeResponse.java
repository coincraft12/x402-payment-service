package lab.custody.x402.api;

import java.util.UUID;

public record PaymentChallengeResponse(
        int status,
        String error,
        String message,
        UUID paymentIntentId,
        String merchantId,
        String endpoint,
        String asset,
        long amount,
        String payer,
        String payee,
        String idempotencyKey,
        String authorizePath,
        String capturePath,
        String auditsPath,
        String ledgerPath
) {
}
