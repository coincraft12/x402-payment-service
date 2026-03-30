package lab.custody.x402.orchestration.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MerchantAllowlistRule implements X402PolicyRule {

    private final Set<String> allowedMerchants;

    public MerchantAllowlistRule(@Value("${x402.policy.allowed-merchants:demo-merchant}") String allowedMerchants) {
        this.allowedMerchants = Arrays.stream(allowedMerchants.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    @Override
    public PaymentScopePolicyDecision evaluate(X402PolicyContext context) {
        if (context.createRequest() == null || context.createRequest().merchantId() == null) {
            return PaymentScopePolicyDecision.allow();
        }
        if (!allowedMerchants.contains(context.createRequest().merchantId())) {
            return PaymentScopePolicyDecision.reject("MERCHANT_NOT_ALLOWED: " + context.createRequest().merchantId());
        }
        return PaymentScopePolicyDecision.allow();
    }
}
