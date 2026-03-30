package io.coincraft.x402.api;

import io.coincraft.x402.domain.authorization.PaymentAuthorization;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

public record PaymentAuthorizationResponse(
        UUID id,
        UUID paymentIntentId,
        String payer,
        String payee,
        BigInteger value,
        long validAfter,
        long validBefore,
        String nonce,
        String digest,
        String status,
        boolean consumed,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentAuthorizationResponse from(PaymentAuthorization authorization) {
        return new PaymentAuthorizationResponse(
                authorization.getId(),
                authorization.getPaymentIntentId(),
                authorization.getPayer(),
                authorization.getPayee(),
                authorization.getValue(),
                authorization.getValidAfter(),
                authorization.getValidBefore(),
                authorization.getNonce(),
                authorization.getDigest(),
                authorization.getStatus().name(),
                authorization.isConsumed(),
                authorization.getCreatedAt(),
                authorization.getUpdatedAt()
        );
    }
}
