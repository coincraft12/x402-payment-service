package io.coincraft.x402.api;

import io.coincraft.x402.domain.intent.PaymentIntent;

import java.time.Instant;
import java.util.UUID;

public record PaymentIntentResponse(
        UUID id,
        String merchantId,
        String endpoint,
        String asset,
        long amount,
        String payer,
        String payee,
        String idempotencyKey,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentIntentResponse from(PaymentIntent intent) {
        return new PaymentIntentResponse(
                intent.getId(),
                intent.getMerchantId(),
                intent.getEndpoint(),
                intent.getAsset(),
                intent.getAmount(),
                intent.getPayer(),
                intent.getPayee(),
                intent.getIdempotencyKey(),
                intent.getStatus().name(),
                intent.getCreatedAt(),
                intent.getUpdatedAt()
        );
    }
}
