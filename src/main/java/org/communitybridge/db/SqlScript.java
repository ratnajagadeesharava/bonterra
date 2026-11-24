package org.communitybridge.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a .sql resource one statement at a time. Lines starting with "--" are comments.
 * A CREATE TRIGGER statement runs until its closing "END;", so the ";" inside its body does not split it.
 */
final class SqlScript {

    private SqlScript() {
    }

    static List<String> load(String resource) {
        try (InputStream in = SqlScript.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            return split(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (line.strip().startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            String text = current.toString().strip();
            boolean trigger = text.regionMatches(true, 0, "CREATE TRIGGER", 0, "CREATE TRIGGER".length());
            boolean complete = trigger ? text.toUpperCase().endsWith("END;") : text.endsWith(";");
            if (complete) {
                statements.add(text);
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            throw new IllegalStateException("SQL script ends without ';': " + current.toString().strip());
        }
        return statements;
    }

    static void run(Connection c, List<String> statements) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }
}
