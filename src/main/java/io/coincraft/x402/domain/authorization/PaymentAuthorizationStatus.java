package io.coincraft.x402.domain.authorization;

public enum PaymentAuthorizationStatus {
    PA0_ISSUED,
    PA1_PRESENTED,
    PA2_VERIFIED,
    PA3_CONSUMED,
    PA9_REJECTED,
    PA10_EXPIRED,
    PA11_REPLAY_BLOCKED
}
