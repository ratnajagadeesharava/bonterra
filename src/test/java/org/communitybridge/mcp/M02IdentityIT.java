package org.communitybridge.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * MCP stage 2: identity comes only from the launch environment (CB_STAFF_ID), is checked before the server will
 * talk at all, and is re-checked on every call. Errors come back as tool results, never crashes.
 * Requirements: NFR-04, MCP-05, MCP-06, AC-13, AC-16.
 */
@DisplayName("M2 · Identity and error results over MCP")
class M02IdentityIT extends McpTestBase {

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",\
            "capabilities":{},"clientInfo":{"name":"probe","version":"1"}}}
            """;

    static Stream<Arguments> badIdentities() {
        return Stream.of(
                Arguments.of("CB_STAFF_ID not set", null, "CB_STAFF_ID"),
                Arguments.of("CB_STAFF_ID empty", "", "CB_STAFF_ID"),
                Arguments.of("unknown staff ID", "stf_unknown", "staff"),
                Arguments.of("inactive staff (Rahul)", "stf_cb_former", "staff"));
    }

    @ParameterizedTest(name = "{0} -> exit code 2, nothing on stdout")
    @MethodSource("badIdentities")
    @DisplayName("AC-16 · a server launched with a missing, empty, unknown or inactive identity exits with code 2 before answering initialize; reason on stderr; stdout empty")
    void badIdentityStopsStartup(String label, String staffId, String stderrMentions) throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("CB_DB_PATH", dbFile.toString());
        if (staffId != null) {
            env.put("CB_STAFF_ID", staffId);
        }

        ProcessRun run = runProcess(env, INITIALIZE, 60);

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).as("stdout").isEmpty();
        assertThat(String.join("\n", run.stderr())).containsIgnoringCase(stderrMentions);
    }

    @ParameterizedTest(name = "launched as {0} -> whoami says {1}, {2}, {3}")
    @CsvSource({
        "stf_cb_case, Priya Sharma, CASE_WORKER,     np_cb",
        "stf_cb_pm,   Arjun Patel,  PROGRAM_MANAGER, np_cb",
        "stf_hr_pm,   Vikram Singh, PROGRAM_MANAGER, np_hr",
    })
    @DisplayName("MCP-06 · the same jar launched with different CB_STAFF_ID values is a different caller: whoami reports that identity, resolved on the server")
    void identityComesFromLaunchEnvironment(String staffId, String name, String role, String nonprofitId) {
        Map<String, Object> me = ok(staffId, "whoami", Map.of());

        assertThat(me).containsEntry("staffId", staffId).containsEntry("name", name)
                .containsEntry("role", role).containsEntry("nonprofitId", nonprofitId);
    }

    @Test
    @DisplayName("NFR-04 · a staff member deactivated while their server runs gets UNAUTHENTICATED tool results; the server keeps running and answering")
    void deactivatedWhileRunning() {
        ok("stf_cb_case", "whoami", Map.of());
        execute("UPDATE staff SET active = 0 WHERE id = 'stf_cb_case'");

        error(call("stf_cb_case", "whoami", Map.of()), "UNAUTHENTICATED");
        errorAndNoChange("stf_cb_case", "create_request", createArgs("par_cb_001", "fnd_cb_housing", "10.00"),
                "UNAUTHENTICATED");
        assertThat(as("stf_cb_case").listTools().tools()).hasSize(11);
    }

    @ParameterizedTest(name = "{0} calls {1} -> FORBIDDEN, requiredRole {2}")
    @CsvSource({
        "stf_cb_case, approve_request, PROGRAM_MANAGER",
        "stf_cb_pm,   record_payment,  FINANCE",
        "stf_cb_fin,  create_request,  CASE_WORKER",
    })
    @DisplayName("AC-13 · a wrong role gets a tool result with isError true, code FORBIDDEN, details.requiredRole and a text message; state unchanged")
    void forbiddenIsAToolResult(String staffId, String tool, String requiredRole) {
        String requestId = approved("fnd_cb_housing", "50.00");
        Map<String, Object> arguments = tool.equals("create_request")
                ? createArgs("par_cb_001", "fnd_cb_housing", "10.00")
                : Map.of("requestId", requestId);

        Map<String, Object> e = errorAndNoChange(staffId, tool, arguments, "FORBIDDEN");

        assertThat(details(e)).containsEntry("requiredRole", requiredRole);
    }

    @Test
    @DisplayName("AC-13 · FORBIDDEN over MCP is identical for a real and a random request ID")
    void forbiddenSameForRealAndRandomId() {
        String real = pending("fnd_cb_housing", "50.00");

        Map<String, Object> forReal = error(call("stf_cb_case", "approve_request", Map.of("requestId", real)), "FORBIDDEN");
        Map<String, Object> forRandom = error(call("stf_cb_case", "approve_request",
                Map.of("requestId", "req_does_not_exist")), "FORBIDDEN");

        assertThat(forReal).isEqualTo(forRandom);
    }

    @Test
    @DisplayName("MCP-05 · an unknown tool name is answered without crashing the server, and the next call still works")
    void unknownToolDoesNotCrash() {
        try {
            call("stf_cb_case", "delete_everything", Map.of());
        } catch (RuntimeException expected) {
            // the SDK reports unknown tools as a JSON-RPC error; either way the server must survive
        }
        ok("stf_cb_case", "whoami", Map.of());
        assertThat(count("requests")).isZero();
    }
}
