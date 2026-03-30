package io.coincraft.x402.orchestration.policy;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AuthorizationExpiryRule implements X402PolicyRule {

    @Override
    public PaymentScopePolicyDecision evaluate(X402PolicyContext context) {
        if (context.authorizeRequest() == null || context.authorizeRequest().deadline() == null) {
            return PaymentScopePolicyDecision.allow();
        }
        if (context.authorizeRequest().deadline().isBefore(Instant.now())) {
            return PaymentScopePolicyDecision.reject("AUTHORIZATION_EXPIRED");
        }
        return PaymentScopePolicyDecision.allow();
    }
}
