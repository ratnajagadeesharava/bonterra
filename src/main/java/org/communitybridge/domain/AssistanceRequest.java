package org.communitybridge.domain;

import java.time.Instant;

/**
 * A request for assistance. After insert only status, decidedBy, decidedAt and rejectionReason
 * ever change (FR-08); the database refuses updates to any other column.
 *
 * @param clientRequestId optional caller-chosen idempotency key (FR-12); null when not sent
 * @param decidedBy       program manager who approved or rejected; null while PENDING
 * @param rejectionReason optional, only when REJECTED
 */
public record AssistanceRequest(
        String id,
        String nonprofitId,
        String participantId,
        String fundId,
        Category category,
        String purpose,
        long amountCents,
        RequestStatus status,
        String clientRequestId,
        String createdBy,
        Instant createdAt,
        String decidedBy,
        Instant decidedAt,
        String rejectionReason) {
}
