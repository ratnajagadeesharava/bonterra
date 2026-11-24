package org.communitybridge.identity;

import org.communitybridge.db.Database;
import org.communitybridge.db.Jdbc;
import org.communitybridge.domain.CallerContext;
import org.communitybridge.domain.Role;
import org.communitybridge.service.error.ServiceException;

/**
 * Authentication: turns a trusted staff ID (CB_STAFF_ID locally) into a CallerContext by reading the
 * staff row. Missing, blank, unknown or inactive IDs fail with UNAUTHENTICATED (NFR-04). The match is
 * exact (SQLite's default BINARY comparison), so case or padding differences do not match.
 * Used at server startup and again on every service call, so a deactivated identity or a changed role
 * takes effect on the next call.
 */
public class StaffIdentityResolver {

    private static final String SQL = """
            SELECT s.id, s.name, s.role, s.active, n.id AS nonprofit_id, n.name AS nonprofit_name
            FROM staff s JOIN nonprofits n ON n.id = s.nonprofit_id
            WHERE s.id = ?""";

    private final Database database;

    public StaffIdentityResolver(Database database) {
        this.database = database;
    }

    /** @throws ServiceException UNAUTHENTICATED */
    public CallerContext resolve(String staffId) {
        if (staffId == null || staffId.isBlank()) {
            throw ServiceException.unauthenticated();
        }
        return database.read(c -> Jdbc.one(c, SQL, rs -> rs.getInt("active") == 1
                        ? new CallerContext(rs.getString("id"), rs.getString("name"), Role.valueOf(rs.getString("role")),
                                rs.getString("nonprofit_id"), rs.getString("nonprofit_name"))
                        : null,
                staffId))
                .orElseThrow(ServiceException::unauthenticated);
    }
}
