package io.coincraft.x402.domain.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentSettlementRepository extends JpaRepository<PaymentSettlement, UUID> {
    boolean existsByPaymentIntentId(UUID paymentIntentId);
    boolean existsByAuthorizationId(UUID authorizationId);
    List<PaymentSettlement> findByPaymentIntentIdOrderByCreatedAtAsc(UUID paymentIntentId);
}
