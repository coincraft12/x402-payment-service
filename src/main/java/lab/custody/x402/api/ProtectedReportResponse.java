package lab.custody.x402.api;

import java.util.UUID;

public record ProtectedReportResponse(
        boolean accessGranted,
        String reportId,
        String reportName,
        String content,
        UUID paymentIntentId
) {
}
