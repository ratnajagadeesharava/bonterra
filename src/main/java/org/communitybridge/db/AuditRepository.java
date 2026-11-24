package org.communitybridge.db;

import java.sql.Connection;
import java.sql.SQLException;
import org.communitybridge.domain.AuditEvent;

/** Append-only audit trail (FR-10). Always written inside the same transaction as the change it records. */
public final class AuditRepository {

    private AuditRepository() {
    }

    public static void insert(Connection c, AuditEvent e) throws SQLException {
        Jdbc.update(c, "INSERT INTO audit_events (id, nonprofit_id, request_id, actor_staff_id, action, from_status, "
                        + "to_status, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                e.id(), e.nonprofitId(), e.requestId(), e.actorStaffId(), e.action().name(),
                e.fromStatus() == null ? null : e.fromStatus().name(), e.toStatus().name(), Times.format(e.at()));
    }
}
