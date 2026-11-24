package org.communitybridge.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.communitybridge.db.Database;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP tests talk to the real, packaged server (target/cb-mcp.jar) over stdio, exactly like an MCP client such as
 * Claude Desktop would: one server process per staff identity, identity set only through the CB_STAFF_ID
 * environment variable. All processes of a test class share one SQLite file, which is reset in place before every
 * test while the servers keep running.
 *
 * <p>Note (spike S3b): the MCP Java client validates every successful structuredContent against the tool's
 * declared output schema and turns a mismatch into an error. So {@link #ok} passing also proves the success
 * output matched its schema.
 */
abstract class McpTestBase {

    static final Path JAR = Path.of(System.getProperty("cb.jar", "target/cb-mcp.jar")).toAbsolutePath();
    static final String JAVA = ProcessHandle.current().info().command().orElse("java");

    static final List<String> ALL_TOOLS = List.of(
            "whoami", "list_participants", "get_participant", "list_funds", "get_funding_summary",
            "create_request", "approve_request", "reject_request", "record_payment", "get_request", "list_requests");

    static final List<String> TABLES =
            List.of("nonprofits", "staff", "participants", "funds", "requests", "payments", "audit_events");

    static Path dir;
    static Path dbFile;
    static Database db;
    private static final Map<String, McpSyncClient> clients = new HashMap<>();

    @BeforeAll
    static void createDatabase() throws IOException {
        dir = Files.createTempDirectory("cb-mcp-it");
        dbFile = dir.resolve("cb.sqlite");
        db = Database.open(dbFile);
    }

    @BeforeEach
    void resetDatabase() {
        db.reset();
    }

    @AfterAll
    static void stopServers() {
        clients.values().forEach(McpSyncClient::closeGracefully);
        clients.clear();
    }

    // ------------------------------------------------------------ servers and clients

    /** The command that launches the server jar, as an MCP client config would. */
    static List<String> serverCommand(String... extraArgs) {
        List<String> cmd = new ArrayList<>(List.of(JAVA, "--enable-native-access=ALL-UNNAMED", "-jar", JAR.toString()));
        cmd.addAll(List.of(extraArgs));
        return cmd;
    }

    /** A connected, initialized client for this staff identity; started once per test class. */
    static McpSyncClient as(String staffId) {
        return clients.computeIfAbsent(staffId, McpTestBase::start);
    }

    static McpSyncClient start(String staffId) {
        McpSyncClient client = newClient(Map.of("CB_STAFF_ID", staffId, "CB_DB_PATH", dbFile.toString()));
        client.initialize();
        return client;
    }

    static McpSyncClient newClient(Map<String, String> env) {
        List<String> cmd = serverCommand();
        ServerParameters params = ServerParameters.builder(cmd.get(0)).args(cmd.subList(1, cmd.size())).env(env).build();
        StdioClientTransport transport = new StdioClientTransport(params, new JacksonMcpJsonMapper(JsonMapper.builder().build()));
        transport.setStdErrorHandler(line -> { }); // server logs are on stderr by design; not needed here
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(60))
                .build();
    }

    /** Stops and forgets this identity's server, e.g. to prove data survives a restart. */
    static void stop(String staffId) {
        McpSyncClient c = clients.remove(staffId);
        if (c != null) {
            c.closeGracefully();
        }
    }

    // ------------------------------------------------------------ calling tools

    static CallToolResult call(String staffId, String tool, Map<String, Object> arguments) {
        return as(staffId).callTool(new McpSchema.CallToolRequest(tool, arguments));
    }

    /** Asserts success and returns structuredContent. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> ok(CallToolResult r) {
        assertThat(r.isError()).as("isError (text: %s)", text(r)).isNotEqualTo(Boolean.TRUE);
        assertThat(r.structuredContent()).as("structuredContent").isInstanceOf(Map.class);
        assertThat(text(r)).as("short text summary").isNotBlank();
        return (Map<String, Object>) r.structuredContent();
    }

    static Map<String, Object> ok(String staffId, String tool, Map<String, Object> arguments) {
        return ok(call(staffId, tool, arguments));
    }

    /** Asserts an error tool result with this code and returns structuredContent.error. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> error(CallToolResult r, String code) {
        assertThat(r.isError()).as("isError (text: %s)", text(r)).isTrue();
        assertThat(r.structuredContent()).as("structuredContent").isInstanceOf(Map.class);
        Map<String, Object> error = (Map<String, Object>) ((Map<String, Object>) r.structuredContent()).get("error");
        assertThat(error).as("structuredContent.error").isNotNull();
        assertThat(error.get("code")).isEqualTo(code);
        assertThat(error.get("message")).asString().isNotBlank();
        assertThat(error).containsKey("details");
        assertThat(text(r)).as("text message").isNotBlank();
        return error;
    }

    /** Asserts the error AND that every row of every table is identical before and after (NFR-03). */
    static Map<String, Object> errorAndNoChange(String staffId, String tool, Map<String, Object> arguments, String code) {
        Map<String, List<Map<String, Object>>> before = snapshot();
        Map<String, Object> e = error(call(staffId, tool, arguments), code);
        assertThat(snapshot()).as("database state after a failed call").isEqualTo(before);
        return e;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> details(Map<String, Object> error) {
        return (Map<String, Object>) error.get("details");
    }

    /** Field names listed in a VALIDATION_ERROR's details.fields. */
    @SuppressWarnings("unchecked")
    static List<String> fields(Map<String, Object> error) {
        return ((List<Map<String, Object>>) details(error).get("fields")).stream()
                .map(f -> (String) f.get("field")).toList();
    }

    static String text(CallToolResult r) {
        return r.content() == null ? "" : r.content().stream()
                .filter(TextContent.class::isInstance).map(c -> ((TextContent) c).text())
                .reduce("", (a, b) -> a + b);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> list(Object o) {
        return (List<Map<String, Object>>) o;
    }

    static long num(Object o) {
        return ((Number) o).longValue();
    }

    /** Arguments map that allows nulls and keeps order (Map.of does neither). */
    static Map<String, Object> args(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    // ------------------------------------------------------------ flow shortcuts

    static Map<String, Object> createArgs(String participantId, String fundId, String amount) {
        return args("participantId", participantId, "fundId", fundId, "category", "RENT",
                "purpose", "Monthly rent shortfall", "amount", amount);
    }

    /** Priya creates; returns the request id. */
    static String pending(String fundId, String amount) {
        return (String) map(ok("stf_cb_case", "create_request",
                createArgs("par_cb_001", fundId, amount)).get("request")).get("id");
    }

    static String approved(String fundId, String amount) {
        String id = pending(fundId, amount);
        ok("stf_cb_pm", "approve_request", args("requestId", id));
        return id;
    }

    static Map<String, Object> fund(String staffId, String fundId) {
        return list(ok(staffId, "get_funding_summary", args("fundId", fundId)).get("funds")).get(0);
    }

    // ------------------------------------------------------------ database, read directly

    static Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> all = new LinkedHashMap<>();
        try (Connection c = db.dataSource().getConnection()) {
            for (String table : TABLES) {
                List<Map<String, Object>> rows = new ArrayList<>();
                try (ResultSet rs = c.createStatement().executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
                    ResultSetMetaData md = rs.getMetaData();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            row.put(md.getColumnLabel(i), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                }
                all.put(table, rows);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return all;
    }

    static long count(String tableAndWhere) {
        try (Connection c = db.dataSource().getConnection();
                ResultSet rs = c.createStatement().executeQuery("SELECT count(*) FROM " + tableAndWhere)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    static void execute(String sql) {
        try (Connection c = db.dataSource().getConnection()) {
            c.createStatement().executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------ raw processes (no MCP client)

    /** Output of a server process run to completion with the given env and stdin. */
    record ProcessRun(int exitCode, List<String> stdout, List<String> stderr) {
    }

    static ProcessRun runProcess(Map<String, String> env, String stdin, int timeoutSeconds, String... extraArgs)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(serverCommand(extraArgs));
        pb.environment().remove("CB_STAFF_ID");
        pb.environment().remove("CB_DB_PATH");
        pb.environment().putAll(env);
        Path out = Files.createTempFile(dir, "out", ".txt");
        Path err = Files.createTempFile(dir, "err", ".txt");
        Path in = Files.createTempFile(dir, "in", ".txt");
        Files.writeString(in, stdin);
        pb.redirectOutput(out.toFile()).redirectError(err.toFile()).redirectInput(in.toFile());
        Process p = pb.start();
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError("Server process did not exit within " + timeoutSeconds + "s");
        }
        return new ProcessRun(p.exitValue(), Files.readAllLines(out), Files.readAllLines(err));
    }
}
