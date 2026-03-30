package io.coincraft.x402.domain.intent;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "x402_payment_intents",
        indexes = {
                @Index(name = "idx_x402_intent_merchant_endpoint_idem", columnList = "merchantId,endpoint,idempotencyKey", unique = true)
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 128)
    private String merchantId;

    @Column(nullable = false, length = 256)
    private String endpoint;

    @Column(nullable = false, length = 32)
    private String asset;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 128)
    private String payer;

    @Column(nullable = false, length = 128)
    private String payee;

    @Column(nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentIntentStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static PaymentIntent requested(
            String merchantId,
            String endpoint,
            String asset,
            long amount,
            String payer,
            String payee,
            String idempotencyKey
    ) {
        Instant now = Instant.now();
        return PaymentIntent.builder()
                .merchantId(merchantId)
                .endpoint(endpoint)
                .asset(asset)
                .amount(amount)
                .payer(payer)
                .payee(payee)
                .idempotencyKey(idempotencyKey)
                .status(PaymentIntentStatus.PI0_REQUESTED)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void transitionTo(PaymentIntentStatus next) {
        if (next.ordinal() < this.status.ordinal()) {
            throw new IllegalStateException("invalid payment intent status transition: " + this.status + " -> " + next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }
}
