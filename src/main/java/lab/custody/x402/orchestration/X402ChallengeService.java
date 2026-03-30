package lab.custody.x402.orchestration;

import lab.custody.x402.api.CreatePaymentIntentRequest;
import lab.custody.x402.api.PaymentChallengeResponse;
import lab.custody.x402.api.ProtectedReportResponse;
import lab.custody.x402.domain.intent.PaymentIntent;
import lab.custody.x402.domain.intent.PaymentIntentStatus;
import lab.custody.x402.support.X402InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class X402ChallengeService {

    private final X402PaymentService paymentService;

    @Value("${x402.challenge.report.merchant-id:demo-merchant}")
    private String reportMerchantId;

    @Value("${x402.challenge.report.endpoint:/x402/protected/report}")
    private String reportEndpoint;

    @Value("${x402.challenge.report.asset:USDC}")
    private String reportAsset;

    @Value("${x402.challenge.report.amount:1000}")
    private long reportAmount;

    @Value("${x402.challenge.report.payee:merchant-vault}")
    private String reportPayee;

    public PaymentIntent loadOrCreateChallengeIntent(String payer, String idempotencyKey) {
        if (payer == null || payer.isBlank()) {
            throw new X402InvalidRequestException("X-Payer header is required");
        }
        return paymentService.createOrGet(
                idempotencyKey,
                new CreatePaymentIntentRequest(
                        reportMerchantId,
                        reportEndpoint,
                        reportAsset,
                        reportAmount,
                        payer,
                        reportPayee
                )
        );
    }

    public boolean isPaid(PaymentIntent intent) {
        return intent.getStatus() == PaymentIntentStatus.PI4_SETTLED;
    }

    public PaymentChallengeResponse toChallenge(PaymentIntent intent) {
        String basePath = "/x402/payment-intents/" + intent.getId();
        return new PaymentChallengeResponse(
                402,
                "payment_required",
                "Payment is required before accessing this protected report.",
                intent.getId(),
                intent.getMerchantId(),
                intent.getEndpoint(),
                intent.getAsset(),
                intent.getAmount(),
                intent.getPayer(),
                intent.getPayee(),
                intent.getIdempotencyKey(),
                basePath + "/authorize",
                basePath + "/capture",
                basePath + "/audits",
                basePath + "/ledger"
        );
    }

    public ProtectedReportResponse toReport(PaymentIntent intent) {
        return new ProtectedReportResponse(
                true,
                "premium-report-001",
                "Premium Custody Revenue Report",
                "Paid access granted. This is the protected premium report payload.",
                intent.getId()
        );
    }
}
