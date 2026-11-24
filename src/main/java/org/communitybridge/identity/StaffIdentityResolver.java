package org.communitybridge.identity;

import org.communitybridge.db.Database;
import org.communitybridge.domain.CallerContext;

/**
 * Authentication: turns a trusted staff ID (CB_STAFF_ID locally) into a CallerContext by reading the
 * staff row. Missing, blank, unknown or inactive IDs fail with UNAUTHENTICATED (NFR-04).
 * Used at server startup and again on every service call, so a deactivated identity stops working at once.
 *
 * <p>Stub: implemented in step 3.
 */
public class StaffIdentityResolver {

    public StaffIdentityResolver(Database database) {
    }

    /** @throws org.communitybridge.service.error.ServiceException UNAUTHENTICATED */
    public CallerContext resolve(String staffId) {
        throw new UnsupportedOperationException("Implemented in step 3");
    }
}
