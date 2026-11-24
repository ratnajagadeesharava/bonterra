package org.communitybridge.service.result;

/** Sums over the funds in the same result. */
public record FundingTotals(
        String currency,
        long allocationCents,
        long committedCents,
        long paidCents,
        long availableCents,
        long pendingCents,
        String allocation,
        String committed,
        String paid,
        String available,
        String pending) {
}
