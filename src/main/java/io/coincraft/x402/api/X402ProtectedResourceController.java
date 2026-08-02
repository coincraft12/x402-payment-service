package io.coincraft.x402.api;

import io.coincraft.x402.domain.intent.PaymentIntent;
import io.coincraft.x402.gate.X402Pay;
import io.coincraft.x402.orchestration.X402ChallengeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class X402ProtectedResourceController {

    private final X402ChallengeService challengeService;

    @GetMapping("/x402/protected/report")
    @X402Pay(amount = 1000, asset = "USDC", merchantId = "demo-merchant", payee = "merchant-vault")
    public ResponseEntity<ProtectedReportResponse> getProtectedReport(HttpServletRequest request) {
        PaymentIntent intent = (PaymentIntent) request.getAttribute("x402.resolved.intent");
        return ResponseEntity.ok(challengeService.toReport(intent));
    }
}
