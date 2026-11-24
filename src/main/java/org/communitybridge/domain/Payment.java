package org.communitybridge.domain;

import java.time.Instant;

/** A simulated payment. At most one per request (UNIQUE request_id); amount always equals the request amount. */
public record Payment(
        String id,
        String nonprofitId,
        String requestId,
        long amountCents,
        String recordedBy,
        Instant recordedAt) {
}
