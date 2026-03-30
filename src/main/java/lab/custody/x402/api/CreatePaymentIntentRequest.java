package lab.custody.x402.api;

public record CreatePaymentIntentRequest(
        String merchantId,
        String endpoint,
        String asset,
        Long amount,
        String payer,
        String payee
) {
}
