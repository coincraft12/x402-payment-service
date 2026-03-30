package lab.custody.x402.orchestration;

import lab.custody.x402.domain.intent.PaymentIntent;
import lab.custody.x402.domain.ledger.PaymentLedgerEntry;
import lab.custody.x402.domain.ledger.PaymentLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class X402LedgerService {

    private final PaymentLedgerEntryRepository ledgerEntryRepository;

    public void reserve(PaymentIntent intent) {
        ledgerEntryRepository.save(PaymentLedgerEntry.reserve(
                intent.getId(), intent.getAsset(), intent.getAmount(), intent.getPayer(), intent.getPayee()
        ));
    }

    public void commit(PaymentIntent intent) {
        ledgerEntryRepository.save(PaymentLedgerEntry.commit(
                intent.getId(), intent.getAsset(), intent.getAmount(), intent.getPayer(), intent.getPayee()
        ));
    }

    public void settle(PaymentIntent intent) {
        ledgerEntryRepository.save(PaymentLedgerEntry.settle(
                intent.getId(), intent.getAsset(), intent.getAmount(), intent.getPayer(), intent.getPayee()
        ));
    }

    public void release(PaymentIntent intent) {
        ledgerEntryRepository.save(PaymentLedgerEntry.release(
                intent.getId(), intent.getAsset(), intent.getAmount(), intent.getPayer(), intent.getPayee()
        ));
    }

    public List<PaymentLedgerEntry> getEntries(UUID paymentIntentId) {
        return ledgerEntryRepository.findByPaymentIntentIdOrderByCreatedAtAsc(paymentIntentId);
    }
}
