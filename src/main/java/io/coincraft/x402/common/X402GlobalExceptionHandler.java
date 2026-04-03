package io.coincraft.x402.common;

import io.coincraft.x402.support.X402IdempotencyConflictException;
import io.coincraft.x402.support.X402InvalidRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class X402GlobalExceptionHandler {

    @ExceptionHandler(X402InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(X402InvalidRequestException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(X402IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(X402IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), mapConstraintMessage(ex)));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage()));
    }

    private String mapConstraintMessage(DataIntegrityViolationException ex) {
        String message = extractMessage(ex).toLowerCase();

        if (message.contains("idx_x402_intent_merchant_endpoint_idem")) {
            return "same Idempotency-Key cannot be used with a different x402 payment intent body";
        }
        if (message.contains("idx_x402_auth_digest") || message.contains("idx_x402_auth_payer_nonce")) {
            return "AUTHORIZATION_REPLAY_BLOCKED";
        }
        if (message.contains("idx_x402_auth_intent")) {
            return "PAYMENT_INTENT_ALREADY_AUTHORIZED";
        }
        if (message.contains("idx_x402_settlement_intent")) {
            return "PAYMENT_INTENT_ALREADY_CAPTURED";
        }
        if (message.contains("idx_x402_settlement_auth")) {
            return "AUTHORIZATION_ALREADY_CAPTURED";
        }

        return "DATA_INTEGRITY_VIOLATION";
    }

    private String extractMessage(Throwable throwable) {
        Throwable current = throwable;
        Throwable deepest = throwable;
        while (current != null) {
            deepest = current;
            current = current.getCause();
        }
        return deepest.getMessage() == null ? throwable.getMessage() : deepest.getMessage();
    }

    public record ErrorResponse(int status, String message) {}
}
