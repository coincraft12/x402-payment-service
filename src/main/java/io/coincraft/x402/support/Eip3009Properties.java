package io.coincraft.x402.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "x402.eip3009")
public record Eip3009Properties(
        long chainId,
        String tokenName,
        String tokenVersion,
        String tokenContract
) {
}
