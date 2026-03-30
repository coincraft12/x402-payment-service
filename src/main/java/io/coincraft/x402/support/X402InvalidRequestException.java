package io.coincraft.x402.support;

public class X402InvalidRequestException extends IllegalArgumentException {
    public X402InvalidRequestException(String message) {
        super(message);
    }
}
