package org.communitybridge.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.communitybridge.domain.AssistanceRequest;
import org.communitybridge.domain.Category;
import org.communitybridge.domain.RequestStatus;

/**
 * Requests of one nonprofit. Status changes are guarded updates ("... AND status = 'PENDING'"), so they
 * report 0 rows instead of applying a transition from the wrong state; the database triggers refuse any
 * other transition or edit as a second layer.
 */
public final class RequestRepository {

    private static final String COLUMNS = "id, nonprofit_id, participant_id, fund_id, category, purpose, amount_cents, "
            + "status, client_request_id, created_by, created_at, decided_by, decided_at, rejection_reason";

    private RequestRepository() {
    }

    public static Optional<AssistanceRequest> find(Connection c, String nonprofitId, String id) throws SQLException {
        return Jdbc.one(c, "SELECT " + COLUMNS + " FROM requests WHERE nonprofit_id = ? AND id = ?",
                RequestRepository::map, nonprofitId, id);
    }

    /** The request this caller already created with this clientRequestId, if any (FR-12). */
    public static Optional<AssistanceRequest> findByClientKey(Connection c, String nonprofitId, String createdBy, String key)
            throws SQLException {
        return Jdbc.one(c, "SELECT " + COLUMNS + " FROM requests "
                        + "WHERE nonprofit_id = ? AND created_by = ? AND client_request_id = ?",
                RequestRepository::map, nonprofitId, createdBy, key);
    }

    public static void insert(Connection c, AssistanceRequest r) throws SQLException {
        Jdbc.update(c, "INSERT INTO requests (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.id(), r.nonprofitId(), r.participantId(), r.fundId(), r.category().name(), r.purpose(),
                r.amountCents(), r.status().name(), r.clientRequestId(), r.createdBy(), Times.format(r.createdAt()),
                r.decidedBy(), Times.format(r.decidedAt()), r.rejectionReason());
    }

    /** PENDING -> APPROVED. @return rows changed (0 if no longer PENDING) */
    public static int approve(Connection c, String nonprofitId, String id, String by, Instant at) throws SQLException {
        return Jdbc.update(c, "UPDATE requests SET status = 'APPROVED', decided_by = ?, decided_at = ? "
                + "WHERE id = ? AND nonprofit_id = ? AND status = 'PENDING'", by, Times.format(at), id, nonprofitId);
    }

    /** PENDING -> REJECTED. @return rows changed (0 if no longer PENDING) */
    public static int reject(Connection c, String nonprofitId, String id, String by, Instant at, String reason)
            throws SQLException {
        return Jdbc.update(c, "UPDATE requests SET status = 'REJECTED', decided_by = ?, decided_at = ?, rejection_reason = ? "
                + "WHERE id = ? AND nonprofit_id = ? AND status = 'PENDING'", by, Times.format(at), reason, id, nonprofitId);
    }

    /** APPROVED -> PAID. @return rows changed (0 if no longer APPROVED) */
    public static int markPaid(Connection c, String nonprofitId, String id) throws SQLException {
        return Jdbc.update(c, "UPDATE requests SET status = 'PAID' WHERE id = ? AND nonprofit_id = ? AND status = 'APPROVED'",
                id, nonprofitId);
    }

    /** Filters are optional (null = any) and combined with AND. Ordered by created_at, then id. */
    public static List<AssistanceRequest> list(Connection c, String nonprofitId, RequestStatus status, String fundId,
            String participantId, int limit, int offset) throws SQLException {
        Filter f = filter(nonprofitId, status, fundId, participantId);
        f.args.add(limit);
        f.args.add(offset);
        return Jdbc.list(c, "SELECT " + COLUMNS + " FROM requests WHERE " + f.where
                + " ORDER BY created_at, id LIMIT ? OFFSET ?", RequestRepository::map, f.args.toArray());
    }

    public static long count(Connection c, String nonprofitId, RequestStatus status, String fundId, String participantId)
            throws SQLException {
        Filter f = filter(nonprofitId, status, fundId, participantId);
        return Jdbc.count(c, "SELECT count(*) FROM requests WHERE " + f.where, f.args.toArray());
    }

    private record Filter(String where, List<Object> args) {
    }

    private static Filter filter(String nonprofitId, RequestStatus status, String fundId, String participantId) {
        List<Object> args = new ArrayList<>();
        args.add(nonprofitId);
        args.add(status == null ? null : status.name());
        args.add(status == null ? null : status.name());
        args.add(fundId);
        args.add(fundId);
        args.add(participantId);
        args.add(participantId);
        return new Filter("nonprofit_id = ? AND (? IS NULL OR status = ?) AND (? IS NULL OR fund_id = ?) "
                + "AND (? IS NULL OR participant_id = ?)", args);
    }

    private static AssistanceRequest map(ResultSet rs) throws SQLException {
        return new AssistanceRequest(rs.getString("id"), rs.getString("nonprofit_id"), rs.getString("participant_id"),
                rs.getString("fund_id"), Category.valueOf(rs.getString("category")), rs.getString("purpose"),
                rs.getLong("amount_cents"), RequestStatus.valueOf(rs.getString("status")),
                rs.getString("client_request_id"), rs.getString("created_by"), Times.parse(rs.getString("created_at")),
                rs.getString("decided_by"), Times.parse(rs.getString("decided_at")), rs.getString("rejection_reason"));
    }
}
