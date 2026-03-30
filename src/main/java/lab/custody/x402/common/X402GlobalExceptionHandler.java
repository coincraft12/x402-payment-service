package lab.custody.x402.common;

import lab.custody.x402.support.X402IdempotencyConflictException;
import lab.custody.x402.support.X402InvalidRequestException;
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

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage()));
    }

    public record ErrorResponse(int status, String message) {}
}
