package io.coincraft.x402.api;

import io.coincraft.x402.domain.settlement.PaymentSettlement;

import java.time.Instant;
import java.util.UUID;

public record PaymentSettlementResponse(
        UUID id,
        UUID paymentIntentId,
        UUID authorizationId,
        String settlementRef,
        String status,
        String txHash,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentSettlementResponse from(PaymentSettlement settlement) {
        return new PaymentSettlementResponse(
                settlement.getId(),
                settlement.getPaymentIntentId(),
                settlement.getAuthorizationId(),
                settlement.getSettlementRef(),
                settlement.getStatus().name(),
                settlement.getTxHash(),
                settlement.getCreatedAt(),
                settlement.getUpdatedAt()
        );
    }
}
