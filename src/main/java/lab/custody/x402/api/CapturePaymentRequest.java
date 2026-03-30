package lab.custody.x402.api;

import java.util.UUID;

public record CapturePaymentRequest(
        UUID authorizationId
) {
}
