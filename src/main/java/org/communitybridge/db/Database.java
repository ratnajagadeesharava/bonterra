package org.communitybridge.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * The SQLite file and its connection settings. Every connection gets foreign_keys=ON (so the composite
 * same-tenant keys are enforced), WAL journal mode and busy_timeout=5000 ms (a second writer waits for the
 * lock instead of failing).
 *
 * <p>Each instance hands out fresh connections, so two instances on the same file behave like two server
 * processes. Writes go through {@link #write}, which runs the work inside one explicit {@code BEGIN IMMEDIATE}
 * transaction: the write lock is taken before anything is read, so checks made inside the work cannot be
 * invalidated by another writer before commit.
 */
public final class Database implements AutoCloseable {

    /** Work done on one connection. */
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    static final int BUSY_TIMEOUT_MS = 5000;
    private static final List<String> SCHEMA = SqlScript.load("/db/schema.sql");
    private static final List<String> SEED = SqlScript.load("/db/seed.sql");
    /** Deletion order that satisfies the foreign keys. */
    private static final List<String> TABLES_CHILD_FIRST =
            List.of("audit_events", "payments", "requests", "funds", "participants", "staff", "nonprofits");

    private final Path file;
    private final SQLiteDataSource dataSource;

    private Database(Path file, SQLiteDataSource dataSource) {
        this.file = file;
        this.dataSource = dataSource;
    }

    /** Opens (creating if missing) the database file and applies schema.sql. Never resets or seeds data. */
    public static Database open(Path file) {
        Path absolute = file.toAbsolutePath();
        try {
            if (absolute.getParent() != null) {
                Files.createDirectories(absolute.getParent());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(BUSY_TIMEOUT_MS);
        // Also make any JDBC-managed transaction (setAutoCommit(false)) immediate; write() uses explicit BEGIN IMMEDIATE.
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        SQLiteDataSource ds = new SQLiteDataSource(config);
        ds.setUrl("jdbc:sqlite:" + absolute);

        Database db = new Database(absolute, ds);
        db.write(c -> {
            SqlScript.run(c, SCHEMA);
            return null;
        });
        return db;
    }

    /**
     * Replaces all data with the synthetic seed, in place, inside one BEGIN IMMEDIATE transaction.
     * Never deletes the file, so other open instances stay valid. Running it twice gives identical data.
     */
    public void reset() {
        write(c -> {
            try (Statement st = c.createStatement()) {
                for (String table : TABLES_CHILD_FIRST) {
                    st.executeUpdate("DELETE FROM " + table);
                }
            }
            SqlScript.run(c, SEED);
            return null;
        });
    }

    /** Runs the work in one BEGIN IMMEDIATE transaction; commits if it returns, rolls back if it throws. */
    public <T> T write(Work<T> work) {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("BEGIN IMMEDIATE");
            T result;
            try {
                result = work.run(c);
            } catch (Throwable t) {
                rollback(st, t);
                throw t;
            }
            st.execute("COMMIT");
            return result;
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    /** Runs read-only work on an autocommit connection. Each SQL statement sees one consistent snapshot. */
    public <T> T read(Work<T> work) {
        try (Connection c = dataSource.getConnection()) {
            return work.run(c);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public Path file() {
        return file;
    }

    @Override
    public void close() {
        // Connections are opened per call and closed after it; nothing is held between calls.
    }

    private static void rollback(Statement st, Throwable cause) {
        try {
            st.execute("ROLLBACK");
        } catch (SQLException e) {
            cause.addSuppressed(e);
        }
    }
}
