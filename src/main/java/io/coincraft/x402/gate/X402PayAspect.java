package io.coincraft.x402.gate;

import io.coincraft.x402.api.PaymentChallengeResponse;
import io.coincraft.x402.domain.intent.PaymentIntent;
import io.coincraft.x402.orchestration.X402ChallengeService;
import io.coincraft.x402.support.X402InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link X402Pay} 어노테이션이 붙은 엔드포인트를 가로채 결제 게이팅을 수행하는 AOP Aspect.
 *
 * <p>흐름:
 * <ol>
 *   <li>요청 헤더에서 {@code Idempotency-Key}, {@code X-Payer} 추출</li>
 *   <li>PaymentIntent 조회 또는 생성</li>
 *   <li>미결제 → {@link X402PaymentRequiredException} throw → GlobalExceptionHandler가 402 반환</li>
 *   <li>결제 완료 → 메서드 본체 실행</li>
 * </ol>
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class X402PayAspect {

    private final X402ChallengeService challengeService;

    @Value("${x402.challenge.report.merchant-id:demo-merchant}")
    private String defaultMerchantId;

    @Value("${x402.challenge.report.payee:merchant-vault}")
    private String defaultPayee;

    @Around("@annotation(x402Pay)")
    public Object gate(ProceedingJoinPoint pjp, X402Pay x402Pay) throws Throwable {
        HttpServletRequest request = currentRequest();

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new X402InvalidRequestException("Idempotency-Key header is required");
        }

        String payer = request.getHeader("X-Payer");
        if (payer == null || payer.isBlank()) {
            throw new X402InvalidRequestException("X-Payer header is required");
        }

        String merchantId = x402Pay.merchantId().isBlank() ? defaultMerchantId : x402Pay.merchantId();
        String payee      = x402Pay.payee().isBlank()      ? defaultPayee      : x402Pay.payee();
        String endpoint   = request.getRequestURI();

        PaymentIntent intent = challengeService.loadOrCreateChallengeIntent(
                payer, idempotencyKey,
                merchantId, endpoint, x402Pay.asset(), x402Pay.amount(), payee
        );

        if (!challengeService.isPaid(intent)) {
            log.info("event=x402.gate.payment_required paymentIntentId={} payer={} endpoint={}",
                    intent.getId(), payer, endpoint);
            PaymentChallengeResponse challenge = challengeService.toChallenge(intent);

            // resolved intent를 request attribute에 저장 — 컨트롤러 메서드가 필요 시 참조 가능
            request.setAttribute("x402.resolved.intent", intent);

            throw new X402PaymentRequiredException(intent, challenge);
        }

        log.info("event=x402.gate.access_granted paymentIntentId={} payer={} endpoint={}",
                intent.getId(), payer, endpoint);

        // 결제 완료 — 컨트롤러 메서드가 intent를 참조할 수 있도록 attribute 설정
        request.setAttribute("x402.resolved.intent", intent);

        return pjp.proceed();
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest();
    }
}
