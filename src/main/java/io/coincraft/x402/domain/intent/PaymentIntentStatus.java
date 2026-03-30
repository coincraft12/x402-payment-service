package io.coincraft.x402.domain.intent;

public enum PaymentIntentStatus {
    PI0_REQUESTED,
    PI1_POLICY_CHECKED,
    PI2_AUTHORIZED,
    PI3_CAPTURED,
    PI4_SETTLED,
    PI9_REJECTED,
    PI10_EXPIRED
}
