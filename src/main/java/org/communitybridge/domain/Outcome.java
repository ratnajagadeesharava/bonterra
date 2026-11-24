package org.communitybridge.domain;

/** Result of a write operation (FR-09, FR-12). */
public enum Outcome {
    /** This call made the change. */
    APPLIED,
    /** The change had already happened; this call changed nothing and returns the current state. */
    ALREADY_APPLIED
}
