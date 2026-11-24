package org.communitybridge.domain;

/** What an audit event records (FR-10). One event per creation and per status change. */
public enum AuditAction {
    CREATED,
    APPROVED,
    REJECTED,
    PAID
}
