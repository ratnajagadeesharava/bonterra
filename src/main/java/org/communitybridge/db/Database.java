package org.communitybridge.db;

import java.nio.file.Path;
import javax.sql.DataSource;

/**
 * The SQLite file and its connection settings (foreign_keys=ON, WAL, busy_timeout=5000,
 * transaction_mode=IMMEDIATE). Each instance hands out its own connections, so two instances
 * on the same file behave like two server processes.
 *
 * <p>Stub: implemented in step 3/4.
 */
public final class Database implements AutoCloseable {

    private Database() {
    }

    /** Opens (creating if missing) the database file and applies schema.sql. Never resets or seeds data. */
    public static Database open(Path file) {
        throw new UnsupportedOperationException("Implemented in step 3");
    }

    /**
     * Replaces all data with the synthetic seed, in place, inside one BEGIN IMMEDIATE transaction.
     * Never deletes the file, so other open instances stay valid. Running it twice gives identical data.
     */
    public void reset() {
        throw new UnsupportedOperationException("Implemented in step 4");
    }

    public DataSource dataSource() {
        throw new UnsupportedOperationException("Implemented in step 3");
    }

    @Override
    public void close() {
        // nothing yet
    }
}
