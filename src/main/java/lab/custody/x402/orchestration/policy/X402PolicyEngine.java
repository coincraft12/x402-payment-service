package lab.custody.x402.orchestration.policy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class X402PolicyEngine {

    private final List<X402PolicyRule> rules;

    public X402PolicyEngine(List<X402PolicyRule> rules) {
        this.rules = rules;
    }

    public PaymentScopePolicyDecision evaluate(X402PolicyContext context) {
        for (X402PolicyRule rule : rules) {
            PaymentScopePolicyDecision decision = rule.evaluate(context);
            if (!decision.allowed()) {
                return decision;
            }
        }
        return PaymentScopePolicyDecision.allow();
    }
}
