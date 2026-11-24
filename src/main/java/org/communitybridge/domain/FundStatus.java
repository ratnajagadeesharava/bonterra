package org.communitybridge.domain;

/** A CLOSED fund takes no new requests or approvals; already-approved requests can still be paid (Q5). */
public enum FundStatus {
    ACTIVE,
    CLOSED
}
