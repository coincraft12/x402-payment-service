package lab.custody.x402.api;

import lab.custody.x402.domain.audit.PaymentAuditLog;
import lab.custody.x402.domain.ledger.PaymentLedgerEntry;
import lab.custody.x402.orchestration.X402AuthorizationService;
import lab.custody.x402.orchestration.X402PaymentService;
import lab.custody.x402.orchestration.X402SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/x402/payment-intents")
@Slf4j
public class X402Controller {

    private final X402PaymentService paymentService;
    private final X402AuthorizationService authorizationService;
    private final X402SettlementService settlementService;

    @PostMapping
    public ResponseEntity<PaymentIntentResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreatePaymentIntentRequest request
    ) {
        log.info("event=x402.intent.create.request merchantId={} endpoint={} amount={}",
                request.merchantId(), request.endpoint(), request.amount());
        return ResponseEntity.ok(PaymentIntentResponse.from(paymentService.createOrGet(idempotencyKey, request)));
    }

    @PostMapping("/{id}/authorize")
    public ResponseEntity<PaymentAuthorizationResponse> authorize(
            @PathVariable UUID id,
            @RequestBody AuthorizePaymentRequest request
    ) {
        log.info("event=x402.authorization.request paymentIntentId={} nonce={}", id, request.nonce());
        return ResponseEntity.ok(PaymentAuthorizationResponse.from(authorizationService.authorize(id, request)));
    }

    @PostMapping("/{id}/capture")
    public ResponseEntity<PaymentSettlementResponse> capture(
            @PathVariable UUID id,
            @RequestBody CapturePaymentRequest request
    ) {
        log.info("event=x402.capture.request paymentIntentId={} authorizationId={}", id, request.authorizationId());
        return ResponseEntity.ok(PaymentSettlementResponse.from(settlementService.capture(id, request.authorizationId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentIntentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(PaymentIntentResponse.from(paymentService.get(id)));
    }

    @GetMapping("/{id}/audits")
    public ResponseEntity<List<PaymentAuditLog>> getAudits(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getAudits(id));
    }

    @GetMapping("/{id}/ledger")
    public ResponseEntity<List<PaymentLedgerEntry>> getLedger(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getLedger(id));
    }
}
