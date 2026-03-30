package lab.custody.x402.orchestration;

import lab.custody.x402.domain.authorization.PaymentAuthorizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class X402ReplayGuardService {

    private final PaymentAuthorizationRepository authorizationRepository;

    public boolean isReplay(String payer, long nonce, String digest) {
        return authorizationRepository.existsByDigest(digest)
                || authorizationRepository.existsByPayerAndNonce(payer, nonce);
    }
}
