package io.coincraft.x402.domain.settlement;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "x402_payment_settlements",
        indexes = {
                @Index(name = "idx_x402_settlement_intent", columnList = "paymentIntentId"),
                @Index(name = "idx_x402_settlement_auth", columnList = "authorizationId")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID paymentIntentId;

    @Column(nullable = false)
    private UUID authorizationId;

    @Column(nullable = false, length = 128)
    private String settlementRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentSettlementStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static PaymentSettlement reserved(UUID paymentIntentId, UUID authorizationId) {
        Instant now = Instant.now();
        return PaymentSettlement.builder()
                .paymentIntentId(paymentIntentId)
                .authorizationId(authorizationId)
                .settlementRef("settlement:" + UUID.randomUUID())
                .status(PaymentSettlementStatus.PS0_RESERVED)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void transitionTo(PaymentSettlementStatus next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }
}
