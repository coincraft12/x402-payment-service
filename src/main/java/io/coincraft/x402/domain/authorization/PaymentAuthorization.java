package io.coincraft.x402.domain.authorization;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigInteger;
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

    /** payer Ethereum address (0x...) */
    @Column(nullable = false, length = 42)
    private String payer;

    /** payee Ethereum address (0x...) */
    @Column(nullable = false, length = 42)
    private String payee;

    /** EIP-3009 token transfer amount */
    @Column(name = "transfer_value", nullable = false)
    private BigInteger value;

    /** unix timestamp (seconds) — 서명 유효 시작 */
    @Column(nullable = false)
    private long validAfter;

    /** unix timestamp (seconds) — 서명 만료 */
    @Column(nullable = false)
    private long validBefore;

    /** EIP-3009 bytes32 nonce (hex, without 0x) */
    @Column(nullable = false, length = 64)
    private String nonce;

    /** EIP-712 digest of the authorization (hex) */
    @Column(nullable = false, length = 64)
    private String digest;

    /** ECDSA signature v (27 or 28) */
    @Column(nullable = false)
    private int sigV;

    /** ECDSA signature r (0x + 64 hex) */
    @Column(nullable = false, length = 66)
    private String sigR;

    /** ECDSA signature s (0x + 64 hex) */
    @Column(nullable = false, length = 66)
    private String sigS;

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
            BigInteger value,
            long validAfter,
            long validBefore,
            String nonce,
            String digest,
            int sigV,
            String sigR,
            String sigS
    ) {
        Instant now = Instant.now();
        return PaymentAuthorization.builder()
                .paymentIntentId(paymentIntentId)
                .payer(payer)
                .payee(payee)
                .value(value)
                .validAfter(validAfter)
                .validBefore(validBefore)
                .nonce(nonce)
                .digest(digest)
                .sigV(sigV)
                .sigR(sigR)
                .sigS(sigS)
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
