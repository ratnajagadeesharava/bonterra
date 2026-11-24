package org.communitybridge.service.result;

public record PaymentView(
        String id,
        String requestId,
        long amountCents,
        String amount,
        String currency,
        String recordedBy,
        String recordedAt) {
}
