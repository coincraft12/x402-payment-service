package io.coincraft.x402.domain.authorization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAuthorizationRepository extends JpaRepository<PaymentAuthorization, UUID> {
    boolean existsByDigest(String digest);
    boolean existsByPayerAndNonce(String payer, String nonce);
    boolean existsByPaymentIntentId(UUID paymentIntentId);
    List<PaymentAuthorization> findByPaymentIntentIdOrderByCreatedAtAsc(UUID paymentIntentId);
}
