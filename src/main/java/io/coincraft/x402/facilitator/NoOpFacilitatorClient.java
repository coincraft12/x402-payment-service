package io.coincraft.x402.facilitator;

import io.coincraft.x402.domain.authorization.PaymentAuthorization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Facilitator 비활성 상태 (x402.facilitator.enabled=false 또는 미설정).
 * 온체인 브로드캐스트 없이 더미 txHash를 반환한다.
 * 테스트 및 로컬 개발 환경에서 사용된다.
 */
@Component
@ConditionalOnProperty(name = "x402.facilitator.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class NoOpFacilitatorClient implements FacilitatorClient {

    private static final String DUMMY_TX_HASH = "0x" + "00".repeat(32);

    @Override
    public SettleResult settle(PaymentAuthorization authorization) {
        log.info("event=facilitator.noop.settle authorizationId={} payer={} — no on-chain broadcast",
                authorization.getId(), authorization.getPayer());
        return new SettleResult(DUMMY_TX_HASH);
    }
}
