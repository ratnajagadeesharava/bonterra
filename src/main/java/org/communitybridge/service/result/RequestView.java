package org.communitybridge.service.result;

import org.communitybridge.domain.Category;
import org.communitybridge.domain.RequestStatus;

public record RequestView(
        String id,
        String participantId,
        String fundId,
        Category category,
        String purpose,
        long amountCents,
        String amount,
        String currency,
        RequestStatus status,
        String clientRequestId,
        String createdBy,
        String createdAt,
        String decidedBy,
        String decidedAt,
        String rejectionReason) {
}
