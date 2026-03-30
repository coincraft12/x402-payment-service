package io.coincraft.x402.support;

import io.coincraft.x402.domain.intent.PaymentIntent;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Component
public class AuthorizationDigestFactory {

    public String digest(PaymentIntent intent, long nonce, Instant deadline) {
        String payload = String.join("|",
                intent.getId().toString(),
                intent.getMerchantId(),
                intent.getEndpoint(),
                intent.getAsset(),
                Long.toString(intent.getAmount()),
                intent.getPayer(),
                intent.getPayee(),
                Long.toString(nonce),
                deadline.toString()
        );
        return sha256Hex(payload);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
