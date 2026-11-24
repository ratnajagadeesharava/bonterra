package org.communitybridge.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP stage 5: the whole story through real MCP clients, plus the process-level guarantees: data survives a
 * restart, stdout carries only JSON-RPC, the reset command is separate from startup, and competing approvals
 * from separate server processes cannot overspend.
 * Requirements: AC-20, AC-21, AC-22, AC-08 (stretch), MCP-07, MCP-08, NFR-07, NFR-08, NFR-09.
 */
@DisplayName("M5 · End to end over MCP")
class M05EndToEndIT extends McpTestBase {

    private static final String PRIYA = "stf_cb_case";
    private static final String ARJUN = "stf_cb_pm";
    private static final String NEHA = "stf_cb_fin";

    @Test
    @DisplayName("AC-20 · a real MCP client discovers 11 tools, then Priya creates $300.00, Arjun approves, Neha pays, and the summary shows $0.00 committed, $300.00 paid, $9,700.00 available")
    void fullFlow() {
        assertThat(as(PRIYA).listTools().tools()).hasSize(11);

        String id = (String) map(ok(PRIYA, "create_request",
                createArgs("par_cb_001", "fnd_cb_housing", "300.00")).get("request")).get("id");
        assertThat(ok(ARJUN, "approve_request", Map.of("requestId", id))).containsEntry("outcome", "APPLIED");
        assertThat(ok(NEHA, "record_payment", Map.of("requestId", id))).containsEntry("outcome", "APPLIED");

        assertThat(fund(PRIYA, "fnd_cb_housing")).containsEntry("committed", "0.00").containsEntry("paid", "300.00")
                .containsEntry("available", "9700.00");
        assertThat(map(ok(PRIYA, "get_request", Map.of("requestId", id)).get("request"))).containsEntry("status", "PAID");
    }

    @Test
    @DisplayName("AC-21 · after the flow, every server is stopped and a new one started on the same file: the PAID request and its payment are still there (normal startup never resets)")
    void survivesRestart() {
        String id = approved("fnd_cb_housing", "300.00");
        ok(NEHA, "record_payment", Map.of("requestId", id));
        stop(PRIYA);
        stop(ARJUN);
        stop(NEHA);

        Map<String, Object> request = map(ok(NEHA, "get_request", Map.of("requestId", id)).get("request"));

        assertThat(request).containsEntry("status", "PAID");
        assertThat(count("payments")).isEqualTo(1);
        assertThat(fund(NEHA, "fnd_cb_housing")).containsEntry("paid", "300.00");
    }

    @Test
    @DisplayName("AC-22 · driving the server with raw JSON-RPC on stdin: every stdout line is a JSON-RPC 2.0 message answering ids 1-3; logs go to stderr; exit 0 when stdin closes")
    void stdoutIsOnlyJsonRpc() throws Exception {
        String stdin = String.join("\n",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\","
                        + "\"capabilities\":{},\"clientInfo\":{\"name\":\"probe\",\"version\":\"1\"}}}",
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}",
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"whoami\",\"arguments\":{}}}",
                "");

        ProcessRun run = runProcess(Map.of("CB_STAFF_ID", PRIYA, "CB_DB_PATH", dbFile.toString()), stdin, 60);

        JsonMapper json = JsonMapper.builder().build();
        List<Integer> ids = new ArrayList<>();
        for (String line : run.stdout()) {
            JsonNode node = json.readTree(line); // throws if any stdout line is not JSON
            assertThat(node.path("jsonrpc").asString()).as(line).isEqualTo("2.0");
            ids.add(node.path("id").asInt());
        }
        assertThat(ids).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(run.stderr()).as("logs on stderr").isNotEmpty();
        assertThat(run.exitCode()).isZero();
    }

    @Test
    @DisplayName("NFR-08 · 'java -jar cb-mcp.jar --reset' is a separate command: it needs no identity, prints nothing on stdout, exits 0 and restores the seed")
    void resetCommand() throws Exception {
        pending("fnd_cb_housing", "10.00");
        assertThat(count("requests")).isEqualTo(1);

        ProcessRun run = runProcess(Map.of("CB_DB_PATH", dbFile.toString()), "", 60, "--reset");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).isEmpty();
        assertThat(count("requests")).isZero();
        assertThat(count("staff")).isEqualTo(7);
    }

    @Test
    @Timeout(180)
    @DisplayName("AC-08 (stretch) · 4 separate program-manager server processes approve 20 different $100.00 requests on the $500.00 fund at once: exactly 5 APPLIED, 15 INSUFFICIENT_FUNDS; committed $500.00")
    void competingApprovalsAcrossProcesses() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ids.add(pending("fnd_cb_small", "100.00"));
        }
        List<McpSyncClient> managers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            McpSyncClient c = newClient(Map.of("CB_STAFF_ID", ARJUN, "CB_DB_PATH", dbFile.toString()));
            c.initialize();
            managers.add(c);
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<List<String>>> futures = new ArrayList<>();
            for (int p = 0; p < 4; p++) {
                McpSyncClient client = managers.get(p);
                List<String> mine = ids.subList(p * 5, p * 5 + 5);
                futures.add(pool.submit(() -> {
                    go.await();
                    List<String> outcomes = new ArrayList<>();
                    for (String id : mine) {
                        McpSchema.CallToolResult r = client.callTool(
                                new McpSchema.CallToolRequest("approve_request", Map.of("requestId", id)));
                        outcomes.add(Boolean.TRUE.equals(r.isError())
                                ? (String) map(map(r.structuredContent()).get("error")).get("code")
                                : (String) map(r.structuredContent()).get("outcome"));
                    }
                    return outcomes;
                }));
            }
            go.countDown();
            List<String> all = new ArrayList<>();
            for (Future<List<String>> f : futures) {
                all.addAll(f.get(120, TimeUnit.SECONDS));
            }

            assertThat(all.stream().collect(Collectors.groupingBy(o -> o, Collectors.counting())))
                    .isEqualTo(Map.of("APPLIED", 5L, "INSUFFICIENT_FUNDS", 15L));
            assertThat(fund(PRIYA, "fnd_cb_small")).containsEntry("committed", "500.00").containsEntry("available", "0.00");
        } finally {
            pool.shutdownNow();
            managers.forEach(McpSyncClient::closeGracefully);
        }
    }
}
