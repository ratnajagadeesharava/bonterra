package org.communitybridge.domain;

/**
 * Lifecycle of an assistance request (spec: Request states).
 * Allowed transitions: PENDING -> APPROVED, PENDING -> REJECTED, APPROVED -> PAID.
 * REJECTED and PAID are terminal.
 */
public enum RequestStatus {
    /** Submitted, waiting for a decision. No money reserved. */
    PENDING,
    /** Approved; full amount reserved (committed) against the fund. */
    APPROVED,
    /** Rejected; no money was ever reserved. Terminal. */
    REJECTED,
    /** Paid; amount moved from committed to paid. Terminal. */
    PAID
}
