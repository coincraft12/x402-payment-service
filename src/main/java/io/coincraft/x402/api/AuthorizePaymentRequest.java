package io.coincraft.x402.api;

import java.time.Instant;

public record AuthorizePaymentRequest(
        Long nonce,
        Instant deadline,
        String signature
) {
}
