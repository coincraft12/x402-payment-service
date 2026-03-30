package lab.custody.x402.domain.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "x402_payment_audit_logs",
        indexes = {
                @Index(name = "idx_x402_audit_intent", columnList = "paymentIntentId")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID paymentIntentId;

    @Column(nullable = false)
    private boolean allowed;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static PaymentAuditLog of(UUID paymentIntentId, boolean allowed, String eventType, String reason) {
        return PaymentAuditLog.builder()
                .paymentIntentId(paymentIntentId)
                .allowed(allowed)
                .eventType(eventType)
                .reason(reason)
                .createdAt(Instant.now())
                .build();
    }
}
