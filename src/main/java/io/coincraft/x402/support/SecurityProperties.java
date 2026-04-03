package io.coincraft.x402.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "x402.security")
public record SecurityProperties(
        String apiKey
) {}
