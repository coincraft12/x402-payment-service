package io.coincraft.x402.domain.intent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    Optional<PaymentIntent> findByMerchantIdAndEndpointAndIdempotencyKey(String merchantId, String endpoint, String idempotencyKey);
}
