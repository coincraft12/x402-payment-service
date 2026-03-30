package io.coincraft.x402.orchestration;

import io.coincraft.x402.api.CreatePaymentIntentRequest;
import io.coincraft.x402.domain.audit.PaymentAuditLog;
import io.coincraft.x402.domain.audit.PaymentAuditLogRepository;
import io.coincraft.x402.domain.intent.PaymentIntent;
import io.coincraft.x402.domain.intent.PaymentIntentRepository;
import io.coincraft.x402.domain.intent.PaymentIntentStatus;
import io.coincraft.x402.domain.ledger.PaymentLedgerEntry;
import io.coincraft.x402.orchestration.policy.PaymentScopePolicyDecision;
import io.coincraft.x402.orchestration.policy.X402PolicyContext;
import io.coincraft.x402.orchestration.policy.X402PolicyEngine;
import io.coincraft.x402.support.X402IdempotencyConflictException;
import io.coincraft.x402.support.X402InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class X402PaymentService {

    private final PaymentIntentRepository intentRepository;
    private final PaymentAuditLogRepository auditLogRepository;
    private final X402PolicyEngine policyEngine;
    private final X402LedgerService ledgerService;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentHashMap<String, ReentrantLock> idempotencyLocks = new ConcurrentHashMap<>();

    public PaymentIntent createOrGet(String idempotencyKey, CreatePaymentIntentRequest request) {
        validateCreateRequest(request, idempotencyKey);
        String lockKey = request.merchantId() + "|" + request.endpoint() + "|" + idempotencyKey;
        ReentrantLock lock = idempotencyLocks.computeIfAbsent(lockKey, ignored -> new ReentrantLock());
        lock.lock();
        try {
            PaymentIntent result = transactionTemplate.execute(status ->
                    intentRepository.findByMerchantIdAndEndpointAndIdempotencyKey(request.merchantId(), request.endpoint(), idempotencyKey)
                            .map(existing -> validateIdempotentRequest(existing, request))
                            .orElseGet(() -> createIntent(idempotencyKey, request))
            );
            if (result == null) {
                throw new IllegalStateException("failed to create or get payment intent");
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Transactional(readOnly = true)
    public PaymentIntent get(UUID id) {
        return intentRepository.findById(id)
                .orElseThrow(() -> new X402InvalidRequestException("payment intent not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<PaymentAuditLog> getAudits(UUID paymentIntentId) {
        return auditLogRepository.findByPaymentIntentIdOrderByCreatedAtAsc(paymentIntentId);
    }

    @Transactional(readOnly = true)
    public List<PaymentLedgerEntry> getLedger(UUID paymentIntentId) {
        return ledgerService.getEntries(paymentIntentId);
    }

    private PaymentIntent createIntent(String idempotencyKey, CreatePaymentIntentRequest request) {
        PaymentIntent intent = PaymentIntent.requested(
                request.merchantId(),
                request.endpoint(),
                request.asset(),
                request.amount(),
                request.payer(),
                request.payee(),
                idempotencyKey
        );
        intent = intentRepository.save(intent);

        auditLogRepository.save(PaymentAuditLog.of(intent.getId(), true, "intent.created", "INTENT_CREATED"));

        PaymentScopePolicyDecision decision = policyEngine.evaluate(X402PolicyContext.forCreate(request));
        auditLogRepository.save(PaymentAuditLog.of(intent.getId(), decision.allowed(), "policy.evaluated", decision.reason()));

        if (!decision.allowed()) {
            intent.transitionTo(PaymentIntentStatus.PI9_REJECTED);
            return intentRepository.save(intent);
        }

        intent.transitionTo(PaymentIntentStatus.PI1_POLICY_CHECKED);
        intent = intentRepository.save(intent);

        log.info("event=x402.intent.created paymentIntentId={} merchantId={} endpoint={} amount={} status={}",
                intent.getId(), intent.getMerchantId(), intent.getEndpoint(), intent.getAmount(), intent.getStatus());

        return intent;
    }

    private PaymentIntent validateIdempotentRequest(PaymentIntent existing, CreatePaymentIntentRequest request) {
        boolean matches =
                existing.getMerchantId().equals(request.merchantId())
                        && existing.getEndpoint().equals(request.endpoint())
                        && existing.getAsset().equals(request.asset())
                        && existing.getAmount() == request.amount()
                        && existing.getPayer().equals(request.payer())
                        && existing.getPayee().equals(request.payee());

        if (!matches) {
            throw new X402IdempotencyConflictException("same Idempotency-Key cannot be used with a different x402 payment intent body");
        }
        return existing;
    }

    private void validateCreateRequest(CreatePaymentIntentRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new X402InvalidRequestException("Idempotency-Key is required");
        }
        if (request == null) {
            throw new X402InvalidRequestException("request body is required");
        }
        if (request.merchantId() == null || request.merchantId().isBlank()) {
            throw new X402InvalidRequestException("merchantId is required");
        }
        if (request.endpoint() == null || request.endpoint().isBlank()) {
            throw new X402InvalidRequestException("endpoint is required");
        }
        if (request.asset() == null || request.asset().isBlank()) {
            throw new X402InvalidRequestException("asset is required");
        }
        if (request.amount() == null || request.amount() <= 0) {
            throw new X402InvalidRequestException("amount must be > 0");
        }
        if (request.payer() == null || request.payer().isBlank()) {
            throw new X402InvalidRequestException("payer is required");
        }
        if (request.payee() == null || request.payee().isBlank()) {
            throw new X402InvalidRequestException("payee is required");
        }
    }
}
