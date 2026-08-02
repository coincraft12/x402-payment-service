package io.coincraft.x402.gate;

import io.coincraft.x402.api.PaymentChallengeResponse;
import io.coincraft.x402.domain.intent.PaymentIntent;

import java.util.Map;

/**
 * 미결제 요청 시 {@link X402PayAspect}가 throw하는 예외.
 *
 * {@link io.coincraft.x402.common.X402GlobalExceptionHandler}가
 * 이 예외를 잡아 HTTP 402 응답으로 변환한다.
 */
public class X402PaymentRequiredException extends RuntimeException {

    private final PaymentIntent intent;
    private final PaymentChallengeResponse challengeResponse;

    public X402PaymentRequiredException(PaymentIntent intent, PaymentChallengeResponse challengeResponse) {
        super("Payment required");
        this.intent = intent;
        this.challengeResponse = challengeResponse;
    }

    public PaymentIntent getIntent() {
        return intent;
    }

    public PaymentChallengeResponse getChallengeResponse() {
        return challengeResponse;
    }
}
