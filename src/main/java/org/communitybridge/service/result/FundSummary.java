package org.communitybridge.service.result;

import org.communitybridge.domain.FundStatus;

/**
 * One fund's figures. available = allocation - committed - paid.
 * pending* is information only and never reduces available.
 */
public record FundSummary(
        String id,
        String name,
        FundStatus status,
        String currency,
        long allocationCents,
        long committedCents,
        long paidCents,
        long availableCents,
        long pendingCents,
        int pendingCount,
        String allocation,
        String committed,
        String paid,
        String available,
        String pending) {
}
