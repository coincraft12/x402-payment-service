package io.coincraft.x402.gate;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * x402 결제 게이팅 어노테이션.
 *
 * <p>이 어노테이션이 붙은 엔드포인트는 결제 완료 전까지 HTTP 402를 반환한다.
 * 결제 완료 후에는 메서드 본체가 실행된다.
 *
 * <p>사용 예:
 * <pre>{@code
 * @GetMapping("/api/premium-data")
 * @X402Pay(amount = 1000, asset = "USDC", merchantId = "demo-merchant", payee = "merchant-vault")
 * public ResponseEntity<?> getPremiumData() {
 *     return ResponseEntity.ok(data);
 * }
 * }</pre>
 *
 * <p>요청 헤더 필수:
 * <ul>
 *   <li>{@code Idempotency-Key} — 멱등성 키</li>
 *   <li>{@code X-Payer} — 결제자 식별자 (지갑 주소 등)</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface X402Pay {

    /** 결제 금액 (토큰 최소 단위). USDC 기준 1000 = $0.001 */
    long amount();

    /** 결제 자산. 기본값 "USDC" */
    String asset() default "USDC";

    /**
     * Merchant ID. 빈 문자열이면 application.yaml 설정값 사용.
     * {@code x402.challenge.report.merchant-id}
     */
    String merchantId() default "";

    /**
     * 수취인 식별자. 빈 문자열이면 application.yaml 설정값 사용.
     * {@code x402.challenge.report.payee}
     */
    String payee() default "";
}
