package io.coincraft.x402.orchestration.policy;

public interface X402PolicyRule {
    PaymentScopePolicyDecision evaluate(X402PolicyContext context);
}
