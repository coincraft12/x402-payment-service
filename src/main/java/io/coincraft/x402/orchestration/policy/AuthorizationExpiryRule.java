package io.coincraft.x402.orchestration.policy;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AuthorizationExpiryRule implements X402PolicyRule {

    @Override
    public PaymentScopePolicyDecision evaluate(X402PolicyContext context) {
        if (context.authorizeRequest() == null) {
            return PaymentScopePolicyDecision.allow();
        }
        long now = Instant.now().getEpochSecond();

        if (context.authorizeRequest().validAfter() > now) {
            return PaymentScopePolicyDecision.reject("AUTHORIZATION_NOT_YET_VALID");
        }
        if (context.authorizeRequest().validBefore() < now) {
            return PaymentScopePolicyDecision.reject("AUTHORIZATION_EXPIRED");
        }
        return PaymentScopePolicyDecision.allow();
    }
}
