package io.coincraft.x402.domain.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, UUID> {
    List<PaymentAuditLog> findByPaymentIntentIdOrderByCreatedAtAsc(UUID paymentIntentId);
}
