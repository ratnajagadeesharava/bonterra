package org.communitybridge.demo;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reproducible MCP demonstration (MCP-08), no language model involved. Launches the packaged server three times,
 * once per fixture identity, exactly as an MCP client config would, then:
 * discovers the tools, Priya creates a $300.00 rent request, Arjun approves it, Neha records the payment, and the
 * final funding summary is read. Every tool call and its structured result is printed.
 *
 * <p>Usage: {@code DemoClient <path to cb-mcp.jar> <database file>}. Run scripts/demo.ps1 or scripts/demo.sh,
 * which reset the demo database first.
 */
public final class DemoClient {

    private static final JsonMapper PRETTY = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private DemoClient() {
    }

    public static void main(String[] args) {
        Path jar = Path.of(args.length > 0 ? args[0] : "target/cb-mcp.jar").toAbsolutePath();
        Path db = Path.of(args.length > 1 ? args[1] : "data/demo.sqlite").toAbsolutePath();
        System.out.println("# Community Bridge MCP demo");
        System.out.println("server: java -jar " + jar.getFileName() + "   database: " + db.getFileName());

        McpSyncClient priya = connect(jar, db, "stf_cb_case");
        McpSyncClient arjun = connect(jar, db, "stf_cb_pm");
        McpSyncClient neha = connect(jar, db, "stf_cb_fin");
        try {
            step("1. Discover tools (as Priya)");
            List<McpSchema.Tool> tools = priya.listTools().tools();
            System.out.println(tools.size() + " tools: " + tools.stream().map(McpSchema.Tool::name).collect(Collectors.joining(", ")));

            step("2. Who is who (each server process has its own launch identity)");
            call("Priya", priya, "whoami", Map.of());
            call("Arjun", arjun, "whoami", Map.of());
            call("Neha", neha, "whoami", Map.of());

            step("3. Funding before (as Priya)");
            call("Priya", priya, "get_funding_summary", Map.of("fundId", "fnd_cb_housing"));

            step("4. Priya (case worker) submits a $300.00 rent request for Kavya");
            Map<String, Object> created = call("Priya", priya, "create_request", args(
                    "participantId", "par_cb_001", "fundId", "fnd_cb_housing", "category", "RENT",
                    "purpose", "March rent shortfall", "amount", "300.00", "clientRequestId", "demo-kavya-march-rent"));
            String requestId = (String) map(created.get("request")).get("id");

            step("5. Arjun (program manager) approves it: $300.00 is reserved");
            call("Arjun", arjun, "approve_request", Map.of("requestId", requestId));

            step("6. Neha (finance) records the payment: committed moves to paid");
            call("Neha", neha, "record_payment", Map.of("requestId", requestId));

            step("7. Final funding summary (as Priya)");
            Map<String, Object> summary = call("Priya", priya, "get_funding_summary", Map.of("fundId", "fnd_cb_housing"));
            Map<String, Object> fund = map(((List<?>) summary.get("funds")).get(0));
            System.out.printf("%nResult: committed %s, paid %s, available %s USD%n",
                    fund.get("committed"), fund.get("paid"), fund.get("available"));

            step("8. A wrong role is refused (Priya tries to approve)");
            call("Priya", priya, "approve_request", Map.of("requestId", requestId));
        } finally {
            priya.closeGracefully();
            arjun.closeGracefully();
            neha.closeGracefully();
        }
    }

    private static McpSyncClient connect(Path jar, Path db, String staffId) {
        ServerParameters params = ServerParameters.builder("java")
                .args("--enable-native-access=ALL-UNNAMED", "-jar", jar.toString())
                .env(Map.of("CB_STAFF_ID", staffId, "CB_DB_PATH", db.toString()))
                .build();
        StdioClientTransport transport = new StdioClientTransport(params, new JacksonMcpJsonMapper(JsonMapper.builder().build()));
        transport.setStdErrorHandler(line -> { }); // server logs go to its stderr; not part of the demo output
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(60))
                .build();
        client.initialize();
        return client;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> call(String who, McpSyncClient client, String tool, Map<String, Object> arguments) {
        System.out.println("\n> " + who + " calls " + tool + " " + json(arguments));
        CallToolResult r = client.callTool(new McpSchema.CallToolRequest(tool, arguments));
        String text = r.content().stream().filter(TextContent.class::isInstance)
                .map(c -> ((TextContent) c).text()).collect(Collectors.joining(" "));
        System.out.println("< isError=" + Boolean.TRUE.equals(r.isError()) + "  " + text);
        System.out.println(json(r.structuredContent()));
        return (Map<String, Object>) r.structuredContent();
    }

    private static void step(String title) {
        System.out.println("\n## " + title);
    }

    private static String json(Object o) {
        return PRETTY.writeValueAsString(o);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    private static Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
