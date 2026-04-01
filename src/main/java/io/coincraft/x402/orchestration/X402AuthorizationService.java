package io.coincraft.x402.orchestration;

import io.coincraft.x402.api.AuthorizePaymentRequest;
import io.coincraft.x402.domain.audit.PaymentAuditLog;
import io.coincraft.x402.domain.audit.PaymentAuditLogRepository;
import io.coincraft.x402.domain.authorization.PaymentAuthorization;
import io.coincraft.x402.domain.authorization.PaymentAuthorizationRepository;
import io.coincraft.x402.domain.authorization.PaymentAuthorizationStatus;
import io.coincraft.x402.domain.intent.PaymentIntent;
import io.coincraft.x402.domain.intent.PaymentIntentRepository;
import io.coincraft.x402.domain.intent.PaymentIntentStatus;
import io.coincraft.x402.orchestration.policy.PaymentScopePolicyDecision;
import io.coincraft.x402.orchestration.policy.X402PolicyContext;
import io.coincraft.x402.orchestration.policy.X402PolicyEngine;
import io.coincraft.x402.support.Eip3009Verifier;
import io.coincraft.x402.support.X402InvalidRequestException;
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
    private final Eip3009Verifier eip3009Verifier;
    private final X402LedgerService ledgerService;

    @Transactional
    public PaymentAuthorization authorize(UUID paymentIntentId, AuthorizePaymentRequest request) {
        PaymentIntent intent = intentRepository.findById(paymentIntentId)
                .orElseThrow(() -> new X402InvalidRequestException("payment intent not found: " + paymentIntentId));

        validateAuthorizeRequest(request);

        // EIP-3009 서명 검증 — 실패 시 X402InvalidRequestException throw
        eip3009Verifier.verify(request);

        String digest = eip3009Verifier.digest(request);
        String nonce = normalizeNonce(request.nonce());
        boolean replayDetected = replayGuardService.isReplay(request.from(), nonce, digest);

        PaymentScopePolicyDecision decision = policyEngine.evaluate(
                X402PolicyContext.forAuthorize(intent, request, replayDetected)
        );

        if (!decision.allowed()) {
            if ("AUTHORIZATION_EXPIRED".equals(decision.reason())
                    || "AUTHORIZATION_NOT_YET_VALID".equals(decision.reason())) {
                intent.transitionTo(PaymentIntentStatus.PI10_EXPIRED);
                intentRepository.save(intent);
            }
            auditLogRepository.save(PaymentAuditLog.of(intent.getId(), false, "authorization.rejected", decision.reason()));
            throw new X402InvalidRequestException(decision.reason());
        }

        PaymentAuthorization authorization = PaymentAuthorization.issued(
                intent.getId(),
                request.from(),
                request.to(),
                request.value(),
                request.validAfter(),
                request.validBefore(),
                nonce,
                digest,
                request.v(),
                request.r(),
                request.s()
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
        if (request.from() == null || request.from().isBlank()) {
            throw new X402InvalidRequestException("from address is required");
        }
        if (request.to() == null || request.to().isBlank()) {
            throw new X402InvalidRequestException("to address is required");
        }
        if (request.value() == null || request.value().signum() <= 0) {
            throw new X402InvalidRequestException("value must be > 0");
        }
        if (request.validBefore() <= 0) {
            throw new X402InvalidRequestException("validBefore is required");
        }
        if (request.nonce() == null || request.nonce().isBlank()) {
            throw new X402InvalidRequestException("nonce is required");
        }
        if (request.r() == null || request.s() == null) {
            throw new X402InvalidRequestException("signature r,s are required");
        }
    }

    /** bytes32 nonce를 0x 없는 소문자 hex 64자로 정규화 */
    private static String normalizeNonce(String nonce) {
        String h = nonce.startsWith("0x") || nonce.startsWith("0X") ? nonce.substring(2) : nonce;
        return h.toLowerCase();
    }
}
