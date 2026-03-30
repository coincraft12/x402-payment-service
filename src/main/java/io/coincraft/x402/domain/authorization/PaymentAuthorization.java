package io.coincraft.x402.domain.authorization;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "x402_payment_authorizations",
        indexes = {
                @Index(name = "idx_x402_auth_intent", columnList = "paymentIntentId"),
                @Index(name = "idx_x402_auth_digest", columnList = "digest", unique = true),
                @Index(name = "idx_x402_auth_payer_nonce", columnList = "payer,nonce", unique = true)
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID paymentIntentId;

    @Column(nullable = false, length = 128)
    private String payer;

    @Column(nullable = false, length = 128)
    private String payee;

    @Column(nullable = false)
    private long nonce;

    @Column(nullable = false)
    private Instant deadline;

    @Column(nullable = false, length = 128)
    private String digest;

    @Column(nullable = false, length = 512)
    private String signature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentAuthorizationStatus status;

    @Column(nullable = false)
    private boolean consumed;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static PaymentAuthorization issued(
            UUID paymentIntentId,
            String payer,
            String payee,
            long nonce,
            Instant deadline,
            String digest,
            String signature
    ) {
        Instant now = Instant.now();
        return PaymentAuthorization.builder()
                .paymentIntentId(paymentIntentId)
                .payer(payer)
                .payee(payee)
                .nonce(nonce)
                .deadline(deadline)
                .digest(digest)
                .signature(signature)
                .status(PaymentAuthorizationStatus.PA0_ISSUED)
                .consumed(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void transitionTo(PaymentAuthorizationStatus next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public void markConsumed() {
        this.consumed = true;
        this.status = PaymentAuthorizationStatus.PA3_CONSUMED;
        this.updatedAt = Instant.now();
    }
}
