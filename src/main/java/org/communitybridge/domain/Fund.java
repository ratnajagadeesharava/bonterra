package org.communitybridge.domain;

/**
 * A grant fund with a fixed allocation. committed and paid are stored counters, kept equal to the
 * sums of APPROVED and PAID request amounts. available = allocation - committed - paid.
 * Database CHECKs: committed >= 0, paid >= 0, committed + paid <= allocation.
 */
public record Fund(
        String id,
        String nonprofitId,
        String name,
        FundStatus status,
        long allocationCents,
        long committedCents,
        long paidCents) {
}
