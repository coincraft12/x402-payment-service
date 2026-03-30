package io.coincraft.x402.api;

import java.math.BigInteger;

/**
 * EIP-3009 transferWithAuthorization 서명 파라미터.
 *
 * <ul>
 *   <li>{@code from}       — 서명자(payer) Ethereum 주소 (0x...)</li>
 *   <li>{@code to}         — 수취인(payee) Ethereum 주소 (0x...)</li>
 *   <li>{@code value}      — 전송 금액 (토큰 최소 단위, e.g. USDC 6 decimals)</li>
 *   <li>{@code validAfter} — 서명 유효 시작 unix timestamp (seconds)</li>
 *   <li>{@code validBefore}— 서명 만료 unix timestamp (seconds)</li>
 *   <li>{@code nonce}      — bytes32 hex (0x...) — replay 방지용 일회성 값</li>
 *   <li>{@code v}          — ECDSA recovery id (27 or 28)</li>
 *   <li>{@code r}          — ECDSA r component (0x... bytes32)</li>
 *   <li>{@code s}          — ECDSA s component (0x... bytes32)</li>
 * </ul>
 */
public record AuthorizePaymentRequest(
        String from,
        String to,
        BigInteger value,
        long validAfter,
        long validBefore,
        String nonce,
        int v,
        String r,
        String s
) {
}
