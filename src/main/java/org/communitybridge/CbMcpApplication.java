package org.communitybridge;

import java.nio.file.Path;
import java.util.Arrays;
import org.communitybridge.db.Database;
import org.communitybridge.domain.CallerContext;
import org.communitybridge.identity.StaffIdentityResolver;
import org.communitybridge.mcp.LaunchIdentity;
import org.communitybridge.service.error.ServiceException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Entry point. Two modes, chosen by arguments:
 *
 * <ul>
 *   <li>{@code --reset}: replace all data in CB_DB_PATH with the synthetic seed and exit 0. Needs no identity,
 *       writes nothing to stdout, and never starts the MCP server.</li>
 *   <li>otherwise: the stdio MCP server for one staff member. CB_STAFF_ID is resolved against the database
 *       <em>before</em> Spring starts; a missing, unknown or inactive identity exits with code 2 and the server
 *       never answers initialize (NFR-04, AC-16).</li>
 * </ul>
 *
 * <p>stdout belongs to the MCP protocol; every message here goes to stderr (NFR-09).
 */
@SpringBootApplication
public class CbMcpApplication {

    static final String DEFAULT_DB_PATH = "data/cb.sqlite";
    static final int EXIT_BAD_IDENTITY = 2;

    public static void main(String[] args) {
        Path dbPath = Path.of(envOr("CB_DB_PATH", DEFAULT_DB_PATH));

        if (Arrays.asList(args).contains("--reset")) {
            try (Database db = Database.open(dbPath)) {
                db.reset();
            }
            System.err.println("Database reset to the synthetic seed: " + dbPath.toAbsolutePath());
            System.exit(0);
            return;
        }

        String staffId = System.getenv("CB_STAFF_ID");
        if (staffId == null || staffId.isBlank()) {
            System.err.println("CB_STAFF_ID is not set. Set it in the MCP client's launch config, e.g. CB_STAFF_ID=stf_cb_case.");
            System.exit(EXIT_BAD_IDENTITY);
            return;
        }

        Database db = Database.open(dbPath);
        CallerContext caller;
        try {
            caller = new StaffIdentityResolver(db).resolve(staffId);
        } catch (ServiceException e) {
            System.err.println("Unknown or inactive staff identity in CB_STAFF_ID: '" + staffId + "' (database "
                    + dbPath.toAbsolutePath() + "). If the database is new, run the reset command first.");
            System.exit(EXIT_BAD_IDENTITY);
            return;
        }
        System.err.println("Starting Community Bridge MCP server as " + caller.name() + " (" + caller.role() + ", "
                + caller.nonprofitName() + ")");

        SpringApplication app = new SpringApplication(CbMcpApplication.class);
        app.addInitializers(ctx -> {
            GenericApplicationContext context = (GenericApplicationContext) ctx;
            context.registerBean(Database.class, () -> db);
            context.registerBean(LaunchIdentity.class, () -> new LaunchIdentity(staffId));
        });
        app.run(args);
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
