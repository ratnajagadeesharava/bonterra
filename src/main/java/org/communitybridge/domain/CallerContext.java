package org.communitybridge.domain;

import java.util.Objects;

/**
 * Who is calling, resolved on the server from the trusted identity (CB_STAFF_ID locally,
 * an OAuth token's iss+sub in production). Never built from tool arguments (NFR-04, NFR-05).
 * Passed into every service and repository call; nonprofitId scopes every query (NFR-06).
 */
public record CallerContext(String staffId, String name, Role role, String nonprofitId, String nonprofitName) {
    public CallerContext {
        Objects.requireNonNull(staffId, "staffId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(nonprofitId, "nonprofitId");
        Objects.requireNonNull(nonprofitName, "nonprofitName");
    }
}
