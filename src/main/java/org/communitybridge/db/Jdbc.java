package org.communitybridge.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Tiny plain-JDBC helpers. Every value is bound as a parameter; SQL text never contains input. */
public final class Jdbc {

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private Jdbc() {
    }

    public static <T> List<T> list(Connection c, String sql, RowMapper<T> mapper, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, args); ResultSet rs = ps.executeQuery()) {
            List<T> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapper.map(rs));
            }
            return rows;
        }
    }

    /** First row, if any. A mapper returning null also gives an empty result. */
    public static <T> Optional<T> one(Connection c, String sql, RowMapper<T> mapper, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, args); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.ofNullable(mapper.map(rs)) : Optional.empty();
        }
    }

    public static long count(Connection c, String sql, Object... args) throws SQLException {
        return one(c, sql, rs -> rs.getLong(1), args).orElse(0L);
    }

    /** @return number of rows changed */
    public static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, args)) {
            return ps.executeUpdate();
        }
    }

    private static PreparedStatement prepare(Connection c, String sql, Object[] args) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        try {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        } catch (SQLException e) {
            ps.close();
            throw e;
        }
    }
}
