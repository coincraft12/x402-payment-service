package io.coincraft.x402.orchestration;

import io.coincraft.x402.domain.authorization.PaymentAuthorizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class X402ReplayGuardService {

    private final PaymentAuthorizationRepository authorizationRepository;

    /**
     * @param payer  payer Ethereum address
     * @param nonce  EIP-3009 bytes32 nonce (hex, without 0x)
     * @param digest EIP-712 digest (hex)
     */
    public boolean isReplay(String payer, String nonce, String digest) {
        return authorizationRepository.existsByDigest(digest)
                || authorizationRepository.existsByPayerAndNonce(payer, nonce);
    }
}
