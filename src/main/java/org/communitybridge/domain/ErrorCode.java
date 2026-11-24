package org.communitybridge.domain;

/** The complete list of error codes (spec: Error behavior, MCP-05). Nothing else is ever returned. */
public enum ErrorCode {
    /** Staff ID missing, unknown or inactive. details: none. */
    UNAUTHENTICATED,
    /** The caller's role cannot perform this action. details: requiredRole. */
    FORBIDDEN,
    /** Input breaks the schema or a validation rule. details: fields[]. */
    VALIDATION_ERROR,
    /** ID does not exist or belongs to another nonprofit (indistinguishable). details: none. */
    NOT_FOUND,
    /** Current status does not allow this action. details: currentStatus. */
    INVALID_TRANSITION,
    /** Amount is more than the fund's available. details: availableCents, requestedCents. */
    INSUFFICIENT_FUNDS,
    /** Fund is CLOSED (on create or approve). details: none. */
    FUND_INACTIVE,
    /** clientRequestId reused by the same caller with different input. details: existingRequestId. */
    IDEMPOTENCY_CONFLICT,
    /** Unexpected error or write lock not obtained in time; transaction rolled back. details: none. */
    INTERNAL
}
