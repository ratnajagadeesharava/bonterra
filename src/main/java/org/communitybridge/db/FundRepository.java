package org.communitybridge.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.communitybridge.domain.Fund;
import org.communitybridge.domain.FundStatus;

/** Funds of one nonprofit, and the only two statements that ever move money between their counters. */
public final class FundRepository {

    /** A fund plus its PENDING total, read in one statement so the figures are one consistent snapshot. */
    public record FundFigures(Fund fund, long pendingCents, int pendingCount) {
    }

    private static final String FIGURES = """
            SELECT f.id, f.nonprofit_id, f.name, f.status, f.allocation_cents, f.committed_cents, f.paid_cents,
                   coalesce(p.pending_cents, 0) AS pending_cents, coalesce(p.pending_count, 0) AS pending_count
            FROM funds f
            LEFT JOIN (SELECT fund_id, sum(amount_cents) AS pending_cents, count(*) AS pending_count
                       FROM requests WHERE nonprofit_id = ? AND status = 'PENDING' GROUP BY fund_id) p
                   ON p.fund_id = f.id
            WHERE f.nonprofit_id = ? AND (? IS NULL OR f.id = ?)
            ORDER BY f.id""";

    private FundRepository() {
    }

    public static Optional<Fund> find(Connection c, String nonprofitId, String id) throws SQLException {
        return Jdbc.one(c, "SELECT id, nonprofit_id, name, status, allocation_cents, committed_cents, paid_cents "
                + "FROM funds WHERE nonprofit_id = ? AND id = ?", FundRepository::map, nonprofitId, id);
    }

    /** One fund (fundId given) or every fund of the nonprofit (fundId null), ordered by id. */
    public static List<FundFigures> figures(Connection c, String nonprofitId, String fundId) throws SQLException {
        return Jdbc.list(c, FIGURES,
                rs -> new FundFigures(map(rs), rs.getLong("pending_cents"), rs.getInt("pending_count")),
                nonprofitId, nonprofitId, fundId, fundId);
    }

    /**
     * Approval: committed += amount, only while the fund is ACTIVE and available covers it.
     *
     * @return 1 if reserved, 0 if the guard refused (closed or not enough available)
     */
    public static int reserve(Connection c, String nonprofitId, String fundId, long cents) throws SQLException {
        return Jdbc.update(c, """
                UPDATE funds SET committed_cents = committed_cents + ?
                WHERE id = ? AND nonprofit_id = ? AND status = 'ACTIVE'
                  AND allocation_cents - committed_cents - paid_cents >= ?""",
                cents, fundId, nonprofitId, cents);
    }

    /**
     * Payment: committed -= amount, paid += amount; available does not change.
     *
     * @return 1 if moved, 0 if committed did not cover the amount (would mean a bug elsewhere)
     */
    public static int settle(Connection c, String nonprofitId, String fundId, long cents) throws SQLException {
        return Jdbc.update(c, """
                UPDATE funds SET committed_cents = committed_cents - ?, paid_cents = paid_cents + ?
                WHERE id = ? AND nonprofit_id = ? AND committed_cents >= ?""",
                cents, cents, fundId, nonprofitId, cents);
    }

    private static Fund map(ResultSet rs) throws SQLException {
        return new Fund(rs.getString("id"), rs.getString("nonprofit_id"), rs.getString("name"),
                FundStatus.valueOf(rs.getString("status")), rs.getLong("allocation_cents"),
                rs.getLong("committed_cents"), rs.getLong("paid_cents"));
    }
}
