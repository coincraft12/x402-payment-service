package io.coincraft.x402.facilitator;

import io.coincraft.x402.domain.authorization.PaymentAuthorization;

public interface FacilitatorClient {

    /**
     * EIP-3009 서명을 온체인에 브로드캐스트하여 실제 토큰 전송을 실행한다.
     *
     * @param authorization 검증된 PaymentAuthorization (v/r/s 포함)
     * @return 트랜잭션 해시
     */
    SettleResult settle(PaymentAuthorization authorization);
}
