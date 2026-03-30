package io.coincraft.x402.support;

public class X402IdempotencyConflictException extends RuntimeException {
    public X402IdempotencyConflictException(String message) {
        super(message);
    }
}
