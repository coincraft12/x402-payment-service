package lab.custody.x402.orchestration;

import lab.custody.x402.api.AuthorizePaymentRequest;
import lab.custody.x402.domain.audit.PaymentAuditLog;
import lab.custody.x402.domain.audit.PaymentAuditLogRepository;
import lab.custody.x402.domain.authorization.PaymentAuthorization;
import lab.custody.x402.domain.authorization.PaymentAuthorizationRepository;
import lab.custody.x402.domain.authorization.PaymentAuthorizationStatus;
import lab.custody.x402.domain.intent.PaymentIntent;
import lab.custody.x402.domain.intent.PaymentIntentRepository;
import lab.custody.x402.domain.intent.PaymentIntentStatus;
import lab.custody.x402.orchestration.policy.PaymentScopePolicyDecision;
import lab.custody.x402.orchestration.policy.X402PolicyContext;
import lab.custody.x402.orchestration.policy.X402PolicyEngine;
import lab.custody.x402.support.AuthorizationDigestFactory;
import lab.custody.x402.support.X402InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class X402AuthorizationService {

    private final PaymentIntentRepository intentRepository;
    private final PaymentAuthorizationRepository authorizationRepository;
    private final PaymentAuditLogRepository auditLogRepository;
    private final X402PolicyEngine policyEngine;
    private final X402ReplayGuardService replayGuardService;
    private final AuthorizationDigestFactory digestFactory;
    private final X402LedgerService ledgerService;

    @Transactional
    public PaymentAuthorization authorize(UUID paymentIntentId, AuthorizePaymentRequest request) {
        PaymentIntent intent = intentRepository.findById(paymentIntentId)
                .orElseThrow(() -> new X402InvalidRequestException("payment intent not found: " + paymentIntentId));

        validateAuthorizeRequest(request);

        String digest = digestFactory.digest(intent, request.nonce(), request.deadline());
        boolean replayDetected = replayGuardService.isReplay(intent.getPayer(), request.nonce(), digest);

        PaymentScopePolicyDecision decision = policyEngine.evaluate(
                X402PolicyContext.forAuthorize(intent, request, replayDetected)
        );

        if (!decision.allowed()) {
            if ("AUTHORIZATION_EXPIRED".equals(decision.reason())) {
                intent.transitionTo(PaymentIntentStatus.PI10_EXPIRED);
                intentRepository.save(intent);
            }
            auditLogRepository.save(PaymentAuditLog.of(intent.getId(), false, "authorization.rejected", decision.reason()));
            throw new X402InvalidRequestException(decision.reason());
        }

        PaymentAuthorization authorization = PaymentAuthorization.issued(
                intent.getId(),
                intent.getPayer(),
                intent.getPayee(),
                request.nonce(),
                request.deadline(),
                digest,
                request.signature()
        );
        authorization.transitionTo(PaymentAuthorizationStatus.PA1_PRESENTED);
        authorization.transitionTo(PaymentAuthorizationStatus.PA2_VERIFIED);
        authorization = authorizationRepository.save(authorization);

        intent.transitionTo(PaymentIntentStatus.PI2_AUTHORIZED);
        intentRepository.save(intent);
        ledgerService.reserve(intent);
        auditLogRepository.save(PaymentAuditLog.of(intent.getId(), true, "authorization.verified", "AUTHORIZATION_VERIFIED"));

        log.info("event=x402.authorization.verified paymentIntentId={} authorizationId={} digest={}",
                intent.getId(), authorization.getId(), authorization.getDigest());

        return authorization;
    }

    private void validateAuthorizeRequest(AuthorizePaymentRequest request) {
        if (request == null) {
            throw new X402InvalidRequestException("authorization request is required");
        }
        if (request.nonce() == null || request.nonce() < 0) {
            throw new X402InvalidRequestException("nonce must be >= 0");
        }
        if (request.deadline() == null) {
            throw new X402InvalidRequestException("deadline is required");
        }
        if (request.signature() == null || request.signature().isBlank()) {
            throw new X402InvalidRequestException("signature is required");
        }
    }
}
