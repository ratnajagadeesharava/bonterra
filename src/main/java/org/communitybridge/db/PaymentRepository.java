package org.communitybridge.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.communitybridge.domain.Payment;

/** Payments of one nonprofit. UNIQUE(request_id) in the schema allows at most one per request. */
public final class PaymentRepository {

    private static final String COLUMNS = "id, nonprofit_id, request_id, amount_cents, recorded_by, recorded_at";

    private PaymentRepository() {
    }

    public static Optional<Payment> findByRequest(Connection c, String nonprofitId, String requestId) throws SQLException {
        return Jdbc.one(c, "SELECT " + COLUMNS + " FROM payments WHERE nonprofit_id = ? AND request_id = ?",
                PaymentRepository::map, nonprofitId, requestId);
    }

    public static void insert(Connection c, Payment p) throws SQLException {
        Jdbc.update(c, "INSERT INTO payments (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?)",
                p.id(), p.nonprofitId(), p.requestId(), p.amountCents(), p.recordedBy(), Times.format(p.recordedAt()));
    }

    private static Payment map(ResultSet rs) throws SQLException {
        return new Payment(rs.getString("id"), rs.getString("nonprofit_id"), rs.getString("request_id"),
                rs.getLong("amount_cents"), rs.getString("recorded_by"), Times.parse(rs.getString("recorded_at")));
    }
}
