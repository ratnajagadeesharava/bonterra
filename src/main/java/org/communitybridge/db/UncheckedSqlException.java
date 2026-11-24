package org.communitybridge.db;

import java.sql.SQLException;

/** A database failure (including a write lock not obtained within busy_timeout). The service maps it to INTERNAL. */
public class UncheckedSqlException extends RuntimeException {

    public UncheckedSqlException(SQLException cause) {
        super(cause.getMessage(), cause);
    }
}
