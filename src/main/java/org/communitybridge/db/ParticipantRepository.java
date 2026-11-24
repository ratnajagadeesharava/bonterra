package org.communitybridge.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.communitybridge.domain.Participant;

/** Participants of one nonprofit. Every query is scoped by nonprofitId; nothing loads a row by ID alone. */
public final class ParticipantRepository {

    private static final String COLUMNS = "id, nonprofit_id, name, household_size, city";

    private ParticipantRepository() {
    }

    public static Optional<Participant> find(Connection c, String nonprofitId, String id) throws SQLException {
        return Jdbc.one(c, "SELECT " + COLUMNS + " FROM participants WHERE nonprofit_id = ? AND id = ?",
                ParticipantRepository::map, nonprofitId, id);
    }

    /** Ordered by name, then id. search is a case-insensitive substring of the name; null means all. */
    public static List<Participant> list(Connection c, String nonprofitId, String search, int limit) throws SQLException {
        String like = search == null ? null : "%" + escapeLike(search) + "%";
        return Jdbc.list(c, "SELECT " + COLUMNS + " FROM participants WHERE nonprofit_id = ? "
                        + "AND (? IS NULL OR name LIKE ? ESCAPE '\\') ORDER BY name, id LIMIT ?",
                ParticipantRepository::map, nonprofitId, like, like, limit);
    }

    /** Makes %, _ and \ in user text match literally. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Participant map(ResultSet rs) throws SQLException {
        return new Participant(rs.getString("id"), rs.getString("nonprofit_id"), rs.getString("name"),
                rs.getInt("household_size"), rs.getString("city"));
    }
}
