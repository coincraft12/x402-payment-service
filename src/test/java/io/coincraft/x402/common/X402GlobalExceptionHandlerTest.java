package io.coincraft.x402.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class X402GlobalExceptionHandlerTest {

    private final X402GlobalExceptionHandler handler = new X402GlobalExceptionHandler();

    @Test
    void mapsAuthorizationReplayConstraintToConflict() {
        ResponseEntity<X402GlobalExceptionHandler.ErrorResponse> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException(
                        "outer",
                        new RuntimeException("constraint [idx_x402_auth_payer_nonce]")
                ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("AUTHORIZATION_REPLAY_BLOCKED");
    }

    @Test
    void mapsSettlementConstraintToConflict() {
        ResponseEntity<X402GlobalExceptionHandler.ErrorResponse> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException(
                        "outer",
                        new RuntimeException("constraint [idx_x402_settlement_auth]")
                ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("AUTHORIZATION_ALREADY_CAPTURED");
    }

    @Test
    void fallsBackToGenericConflictMessage() {
        ResponseEntity<X402GlobalExceptionHandler.ErrorResponse> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("something else"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("DATA_INTEGRITY_VIOLATION");
    }
}
