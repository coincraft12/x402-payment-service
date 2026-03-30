package io.coincraft.x402.domain.ledger;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "x402_payment_ledger_entries",
        indexes = {
                @Index(name = "idx_x402_ledger_intent", columnList = "paymentIntentId")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID paymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentLedgerEntryType type;

    @Column(nullable = false, length = 32)
    private String asset;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 128)
    private String payer;

    @Column(nullable = false, length = 128)
    private String payee;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static PaymentLedgerEntry reserve(UUID paymentIntentId, String asset, long amount, String payer, String payee) {
        return of(paymentIntentId, PaymentLedgerEntryType.RESERVE, asset, amount, payer, payee);
    }

    public static PaymentLedgerEntry commit(UUID paymentIntentId, String asset, long amount, String payer, String payee) {
        return of(paymentIntentId, PaymentLedgerEntryType.COMMIT, asset, amount, payer, payee);
    }

    public static PaymentLedgerEntry settle(UUID paymentIntentId, String asset, long amount, String payer, String payee) {
        return of(paymentIntentId, PaymentLedgerEntryType.SETTLE, asset, amount, payer, payee);
    }

    public static PaymentLedgerEntry release(UUID paymentIntentId, String asset, long amount, String payer, String payee) {
        return of(paymentIntentId, PaymentLedgerEntryType.RELEASE, asset, amount, payer, payee);
    }

    private static PaymentLedgerEntry of(UUID paymentIntentId, PaymentLedgerEntryType type, String asset, long amount, String payer, String payee) {
        return PaymentLedgerEntry.builder()
                .paymentIntentId(paymentIntentId)
                .type(type)
                .asset(asset)
                .amount(amount)
                .payer(payer)
                .payee(payee)
                .createdAt(Instant.now())
                .build();
    }
}
