package org.communitybridge.domain;

/**
 * Every operation the role matrix decides on (spec: Permissions, section 3).
 * READ covers all seven read tools; each write action is allowed for exactly one role:
 * CREATE_REQUEST -> CASE_WORKER, APPROVE_REQUEST and REJECT_REQUEST -> PROGRAM_MANAGER,
 * RECORD_PAYMENT -> FINANCE.
 */
public enum Action {
    READ,
    CREATE_REQUEST,
    APPROVE_REQUEST,
    REJECT_REQUEST,
    RECORD_PAYMENT
}
