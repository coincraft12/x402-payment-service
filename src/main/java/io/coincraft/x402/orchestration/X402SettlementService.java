package io.coincraft.x402.orchestration;

import io.coincraft.x402.domain.audit.PaymentAuditLog;
import io.coincraft.x402.domain.audit.PaymentAuditLogRepository;
import io.coincraft.x402.domain.authorization.PaymentAuthorization;
import io.coincraft.x402.domain.authorization.PaymentAuthorizationRepository;
import io.coincraft.x402.domain.intent.PaymentIntent;
import io.coincraft.x402.domain.intent.PaymentIntentRepository;
import io.coincraft.x402.domain.intent.PaymentIntentStatus;
import io.coincraft.x402.domain.settlement.PaymentSettlement;
import io.coincraft.x402.domain.settlement.PaymentSettlementRepository;
import io.coincraft.x402.domain.settlement.PaymentSettlementStatus;
import io.coincraft.x402.facilitator.FacilitatorClient;
import io.coincraft.x402.facilitator.SettleResult;
import io.coincraft.x402.support.X402InvalidRequestException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class X402SettlementService {

    private final PaymentIntentRepository intentRepository;
    private final PaymentAuthorizationRepository authorizationRepository;
    private final PaymentSettlementRepository settlementRepository;
    private final PaymentAuditLogRepository auditLogRepository;
    private final X402LedgerService ledgerService;
    private final FacilitatorClient facilitatorClient;
    private final MeterRegistry meterRegistry;

    @Transactional
    public PaymentSettlement capture(UUID paymentIntentId, UUID authorizationId) {
        PaymentIntent intent = intentRepository.findById(paymentIntentId)
                .orElseThrow(() -> new X402InvalidRequestException("payment intent not found: " + paymentIntentId));

        PaymentAuthorization authorization = authorizationRepository.findById(authorizationId)
                .orElseThrow(() -> new X402InvalidRequestException("authorization not found: " + authorizationId));

        if (!authorization.getPaymentIntentId().equals(paymentIntentId)) {
            throw new X402InvalidRequestException("authorization does not belong to payment intent");
        }
        if (intent.getStatus() == PaymentIntentStatus.PI4_SETTLED) {
            throw new X402InvalidRequestException("PAYMENT_INTENT_ALREADY_SETTLED");
        }
        if (intent.getStatus() == PaymentIntentStatus.PI3_CAPTURED) {
            throw new X402InvalidRequestException("PAYMENT_INTENT_CAPTURE_IN_PROGRESS");
        }
        if (intent.getStatus() != PaymentIntentStatus.PI2_AUTHORIZED) {
            throw new X402InvalidRequestException("PAYMENT_INTENT_NOT_READY_FOR_CAPTURE");
        }
        if (authorization.isConsumed()) {
            throw new X402InvalidRequestException("authorization already consumed");
        }
        if (settlementRepository.existsByPaymentIntentId(paymentIntentId)) {
            throw new X402InvalidRequestException("PAYMENT_INTENT_ALREADY_CAPTURED");
        }
        if (settlementRepository.existsByAuthorizationId(authorizationId)) {
            throw new X402InvalidRequestException("AUTHORIZATION_ALREADY_CAPTURED");
        }

        PaymentSettlement settlement = PaymentSettlement.reserved(paymentIntentId, authorizationId);
        settlement = settlementRepository.save(settlement);

        intent.transitionTo(PaymentIntentStatus.PI3_CAPTURED);
        intentRepository.save(intent);
        ledgerService.commit(intent);

        settlement.transitionTo(PaymentSettlementStatus.PS1_COMMITTED);
        settlement = settlementRepository.save(settlement);

        authorization.markConsumed();
        authorizationRepository.save(authorization);

        ledgerService.settle(intent);

        // Facilitator: 온체인 transferWithAuthorization 브로드캐스트
        SettleResult settleResult = facilitatorClient.settle(authorization);
        settlement.recordTxHash(settleResult.txHash());

        settlement.transitionTo(PaymentSettlementStatus.PS2_SETTLED);
        settlement = settlementRepository.save(settlement);

        intent.transitionTo(PaymentIntentStatus.PI4_SETTLED);
        intentRepository.save(intent);

        auditLogRepository.save(PaymentAuditLog.of(paymentIntentId, true, "settlement.completed", "SETTLEMENT_COMPLETED"));

        log.info("event=x402.settlement.completed paymentIntentId={} authorizationId={} settlementId={} txHash={}",
                paymentIntentId, authorizationId, settlement.getId(), settleResult.txHash());
        meterRegistry.counter("x402.payment.settlement.completed").increment();

        return settlement;
    }
}
