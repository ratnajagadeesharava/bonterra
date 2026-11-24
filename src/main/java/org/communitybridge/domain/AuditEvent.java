package org.communitybridge.domain;

import java.time.Instant;

/**
 * Append-only record of who did what (FR-10), written in the same transaction as the change.
 *
 * @param fromStatus null for CREATED
 */
public record AuditEvent(
        String id,
        String nonprofitId,
        String requestId,
        String actorStaffId,
        AuditAction action,
        RequestStatus fromStatus,
        RequestStatus toStatus,
        Instant at) {
}
