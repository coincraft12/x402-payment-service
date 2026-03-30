package io.coincraft.x402.orchestration.policy;

import io.coincraft.x402.api.AuthorizePaymentRequest;
import io.coincraft.x402.api.CreatePaymentIntentRequest;
import io.coincraft.x402.domain.intent.PaymentIntent;

public record X402PolicyContext(
        CreatePaymentIntentRequest createRequest,
        PaymentIntent paymentIntent,
        AuthorizePaymentRequest authorizeRequest,
        boolean replayDetected
) {
    public static X402PolicyContext forCreate(CreatePaymentIntentRequest request) {
        return new X402PolicyContext(request, null, null, false);
    }

    public static X402PolicyContext forAuthorize(PaymentIntent intent, AuthorizePaymentRequest request, boolean replayDetected) {
        return new X402PolicyContext(null, intent, request, replayDetected);
    }
}
