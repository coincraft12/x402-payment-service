package io.coincraft.x402.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "x402.facilitator")
public record FacilitatorProperties(
        boolean enabled,
        String rpcUrl,
        String privateKey
) {}
