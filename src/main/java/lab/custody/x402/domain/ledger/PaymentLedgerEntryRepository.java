package lab.custody.x402.domain.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentLedgerEntryRepository extends JpaRepository<PaymentLedgerEntry, UUID> {
    List<PaymentLedgerEntry> findByPaymentIntentIdOrderByCreatedAtAsc(UUID paymentIntentId);
}
