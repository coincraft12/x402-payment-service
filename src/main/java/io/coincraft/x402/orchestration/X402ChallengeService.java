package io.coincraft.x402.orchestration;

import io.coincraft.x402.api.CreatePaymentIntentRequest;
import io.coincraft.x402.api.PaymentChallengeResponse;
import io.coincraft.x402.api.PaymentSchemeOffer;
import io.coincraft.x402.api.ProtectedReportResponse;
import io.coincraft.x402.domain.intent.PaymentIntent;
import io.coincraft.x402.domain.intent.PaymentIntentStatus;
import io.coincraft.x402.support.Eip3009Properties;
import io.coincraft.x402.support.X402InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class X402ChallengeService {

    private static final int X402_VERSION = 1;
    private static final int MAX_TIMEOUT_SECONDS = 300;

    private final X402PaymentService paymentService;
    private final Eip3009Properties eip3009Properties;

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

        PaymentSchemeOffer offer = new PaymentSchemeOffer(
                "exact",
                resolveNetwork(eip3009Properties.chainId()),
                String.valueOf(intent.getAmount()),
                intent.getEndpoint(),
                "Payment required to access " + intent.getEndpoint(),
                "application/json",
                intent.getPayee(),
                MAX_TIMEOUT_SECONDS,
                eip3009Properties.tokenContract(),
                Map.of(
                        "name", eip3009Properties.tokenName(),
                        "version", eip3009Properties.tokenVersion()
                )
        );

        return new PaymentChallengeResponse(
                X402_VERSION,
                List.of(offer),
                "X402 Payment Required",
                402,
                "Payment is required before accessing this protected report.",
                intent.getId(),
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

    private static String resolveNetwork(long chainId) {
        return switch ((int) chainId) {
            case 1     -> "ethereum-mainnet";
            case 11155111 -> "ethereum-sepolia";
            case 8453  -> "base-mainnet";
            case 84532 -> "base-sepolia";
            case 137   -> "polygon-mainnet";
            case 42161 -> "arbitrum-one";
            case 1337  -> "local";
            default    -> "chain-" + chainId;
        };
    }
}
