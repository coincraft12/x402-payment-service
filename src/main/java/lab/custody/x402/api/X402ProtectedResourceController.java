package lab.custody.x402.api;

import lab.custody.x402.domain.intent.PaymentIntent;
import lab.custody.x402.orchestration.X402ChallengeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class X402ProtectedResourceController {

    private final X402ChallengeService challengeService;

    @GetMapping("/x402/protected/report")
    public ResponseEntity<?> getProtectedReport(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Payer") String payer
    ) {
        PaymentIntent intent = challengeService.loadOrCreateChallengeIntent(payer, idempotencyKey);

        if (!challengeService.isPaid(intent)) {
            log.info("event=x402.challenge.issued paymentIntentId={} payer={} status={}",
                    intent.getId(), payer, intent.getStatus());
            PaymentChallengeResponse challenge = challengeService.toChallenge(intent);
            return ResponseEntity.status(HttpStatusCode.valueOf(402))
                    .header("X-Payment-Protocol", "x402-lab")
                    .header("X-Payment-Required", "true")
                    .header("X-Payment-Intent-Id", intent.getId().toString())
                    .header("X-Payment-Merchant", intent.getMerchantId())
                    .header("X-Payment-Endpoint", intent.getEndpoint())
                    .header("X-Payment-Asset", intent.getAsset())
                    .header("X-Payment-Amount", Long.toString(intent.getAmount()))
                    .header("X-Payment-Payer", intent.getPayer())
                    .header("X-Payment-Payee", intent.getPayee())
                    .header(HttpHeaders.LINK, String.join(", ",
                            "<" + challenge.authorizePath() + ">; rel=\"authorize\"",
                            "<" + challenge.capturePath() + ">; rel=\"capture\"",
                            "<" + challenge.auditsPath() + ">; rel=\"audits\"",
                            "<" + challenge.ledgerPath() + ">; rel=\"ledger\""
                    ))
                    .body(challenge);
        }

        log.info("event=x402.protected_resource.access_granted paymentIntentId={} payer={}",
                intent.getId(), payer);
        return ResponseEntity.ok(challengeService.toReport(intent));
    }
}
