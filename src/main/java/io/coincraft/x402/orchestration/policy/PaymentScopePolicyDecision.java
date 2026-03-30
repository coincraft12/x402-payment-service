package io.coincraft.x402.orchestration.policy;

public record PaymentScopePolicyDecision(
        boolean allowed,
        String reason
) {
    public static PaymentScopePolicyDecision allow() {
        return new PaymentScopePolicyDecision(true, "ALLOW");
    }

    public static PaymentScopePolicyDecision reject(String reason) {
        return new PaymentScopePolicyDecision(false, reason);
    }
}
