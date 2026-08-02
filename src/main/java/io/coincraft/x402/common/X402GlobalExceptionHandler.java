package io.coincraft.x402.common;

import io.coincraft.x402.api.PaymentChallengeResponse;
import io.coincraft.x402.domain.intent.PaymentIntent;
import io.coincraft.x402.gate.X402PaymentRequiredException;
import io.coincraft.x402.support.X402IdempotencyConflictException;
import io.coincraft.x402.support.X402InvalidRequestException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class X402GlobalExceptionHandler {

    @ExceptionHandler(X402PaymentRequiredException.class)
    public ResponseEntity<PaymentChallengeResponse> handlePaymentRequired(X402PaymentRequiredException ex) {
        PaymentIntent intent = ex.getIntent();
        PaymentChallengeResponse challenge = ex.getChallengeResponse();

        return ResponseEntity.status(HttpStatusCode.valueOf(402))
                .header("X-Payment-Protocol", "x402/1")
                .header("X-Payment-Required", "true")
                .header("X-Payment-Intent-Id", intent.getId().toString())
                .header("X-Payment-Merchant", intent.getMerchantId())
                .header("X-Payment-Endpoint", intent.getEndpoint())
                .header("X-Payment-Asset", intent.getAsset())
                .header("X-Payment-Amount", Long.toString(intent.getAmount()))
                .header("X-Payment-Payer", intent.getPayer())
                .header("X-Payment-Payee", intent.getPayee())
                .header(HttpHeaders.LINK, String.join(", ",
                        "<" + challenge.authorizePath() + ">; rel=\"authorize\"",
                        "<" + challenge.capturePath() + ">; rel=\"capture\"",
                        "<" + challenge.auditsPath() + ">; rel=\"audits\"",
                        "<" + challenge.ledgerPath() + ">; rel=\"ledger\""
                ))
                .body(challenge);
    }

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

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage()));
    }

    public record ErrorResponse(int status, String message) {}
}
