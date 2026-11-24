package org.communitybridge.service;

import org.communitybridge.domain.Action;
import org.communitybridge.domain.CallerContext;
import org.communitybridge.domain.Role;
import org.communitybridge.service.error.ServiceException;

/**
 * The whole role matrix (spec: Roles). Called before any input is read or any record is looked up,
 * so the answer never depends on whether a target ID exists.
 */
final class Authorizer {

    private Authorizer() {
    }

    /** The one role allowed to perform the action, or null when every role may (reads). */
    static Role requiredRole(Action action) {
        return switch (action) {
            case READ -> null;
            case CREATE_REQUEST -> Role.CASE_WORKER;
            case APPROVE_REQUEST, REJECT_REQUEST -> Role.PROGRAM_MANAGER;
            case RECORD_PAYMENT -> Role.FINANCE;
        };
    }

    /** @throws ServiceException FORBIDDEN with details.requiredRole */
    static void authorize(CallerContext caller, Action action) {
        Role required = requiredRole(action);
        if (required != null && caller.role() != required) {
            throw ServiceException.forbidden(required);
        }
    }
}
