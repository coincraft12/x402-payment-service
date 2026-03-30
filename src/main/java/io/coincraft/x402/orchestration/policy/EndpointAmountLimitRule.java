package io.coincraft.x402.orchestration.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EndpointAmountLimitRule implements X402PolicyRule {

    private final long maxAmount;

    public EndpointAmountLimitRule(@Value("${x402.policy.max-amount:10000}") long maxAmount) {
        this.maxAmount = maxAmount;
    }

    @Override
    public PaymentScopePolicyDecision evaluate(X402PolicyContext context) {
        if (context.createRequest() == null || context.createRequest().amount() == null) {
            return PaymentScopePolicyDecision.allow();
        }
        if (context.createRequest().amount() > maxAmount) {
            return PaymentScopePolicyDecision.reject("ENDPOINT_AMOUNT_LIMIT_EXCEEDED: max=" + maxAmount + ", requested=" + context.createRequest().amount());
        }
        return PaymentScopePolicyDecision.allow();
    }
}
