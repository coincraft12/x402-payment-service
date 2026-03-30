package io.coincraft.x402.api;

import java.util.Map;

/**
 * x402 표준 결제 스킴 제안 — 402 응답의 accepts[] 배열 원소.
 *
 * <ul>
 *   <li>{@code scheme}             — "exact" (EIP-3009 transferWithAuthorization)</li>
 *   <li>{@code network}            — 체인 식별자 (e.g. "base-mainnet", "base-sepolia")</li>
 *   <li>{@code maxAmountRequired}  — 결제 금액 (토큰 최소 단위 문자열, e.g. "1000000" = 1 USDC)</li>
 *   <li>{@code resource}           — 보호 리소스 경로</li>
 *   <li>{@code description}        — 사람이 읽을 수 있는 설명</li>
 *   <li>{@code mimeType}           — 보호 리소스 Content-Type</li>
 *   <li>{@code payTo}              — 수취인 Ethereum 주소 (0x...)</li>
 *   <li>{@code maxTimeoutSeconds}  — 클라이언트가 설정해야 할 최대 validBefore 창 (초)</li>
 *   <li>{@code asset}              — 토큰 컨트랙트 주소 (0x...)</li>
 *   <li>{@code extra}              — 토큰 메타데이터 (name, version)</li>
 * </ul>
 */
public record PaymentSchemeOffer(
        String scheme,
        String network,
        String maxAmountRequired,
        String resource,
        String description,
        String mimeType,
        String payTo,
        int maxTimeoutSeconds,
        String asset,
        Map<String, String> extra
) {
}
