package lab.custody.x402.orchestration.policy;

import org.springframework.stereotype.Component;

@Component
public class ReplayProtectionRule implements X402PolicyRule {

    @Override
    public PaymentScopePolicyDecision evaluate(X402PolicyContext context) {
        if (context.replayDetected()) {
            return PaymentScopePolicyDecision.reject("AUTHORIZATION_REPLAY_BLOCKED");
        }
        return PaymentScopePolicyDecision.allow();
    }
}
