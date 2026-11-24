package org.communitybridge.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;
import org.communitybridge.service.input.Inputs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MCP stage 1: an MCP client launches the server and discovers what it offers. Only tools are exposed, each with a
 * strict input schema, an output schema and a description that names the required role and the funding effect.
 * Requirements: MCP-01, MCP-02, MCP-03, MCP-04, MCP-09, Q11.
 */
@DisplayName("M1 · Discovery")
class M01DiscoveryIT extends McpTestBase {

    private static Map<String, Tool> tools(String staffId) {
        return as(staffId).listTools().tools().stream()
                .collect(java.util.stream.Collectors.toMap(Tool::name, t -> t));
    }

    @Test
    @DisplayName("MCP-01 · the jar starts as a stdio MCP server and completes initialize as 'community-bridge', advertising tools only (no resources, prompts or completions)")
    void initializeHandshake() {
        McpSyncClient client = newClient(Map.of("CB_STAFF_ID", "stf_cb_case", "CB_DB_PATH", dbFile.toString()));
        try {
            InitializeResult init = client.initialize();

            assertThat(init.serverInfo().name()).isEqualTo("community-bridge");
            assertThat(init.capabilities().tools()).isNotNull();
            assertThat(init.capabilities().resources()).as("MCP-09 resources").isNull();
            assertThat(init.capabilities().prompts()).as("MCP-09 prompts").isNull();
            assertThat(init.capabilities().completions()).as("MCP-09 completions").isNull();
        } finally {
            client.closeGracefully();
        }
    }

    @Test
    @DisplayName("MCP-02 · tools/list returns exactly the 11 tools: whoami, participant and fund lookup, create, approve, reject, pay, get/list requests, funding summary")
    void elevenTools() {
        assertThat(tools("stf_cb_case").keySet()).containsExactlyInAnyOrderElementsOf(ALL_TOOLS);
    }

    @Test
    @DisplayName("Q11 · every role sees the same 11 tools with identical definitions (listing is not security; the server checks role on every call)")
    void sameToolsForEveryRole() {
        assertThat(tools("stf_cb_fin")).isEqualTo(tools("stf_cb_case"));
        assertThat(tools("stf_hr_pm")).isEqualTo(tools("stf_cb_case"));
    }

    @ParameterizedTest(name = "{0} description starts with \"{1}\"")
    @CsvSource(delimiter = '|', value = {
        "create_request  | Case workers only.",
        "approve_request | Program managers only.",
        "reject_request  | Program managers only.",
        "record_payment  | Finance only.",
        "whoami          | Any role.",
        "list_participants | Any role.",
        "get_participant | Any role.",
        "list_funds      | Any role.",
        "get_funding_summary | Any role.",
        "get_request     | Any role.",
        "list_requests   | Any role.",
    })
    @DisplayName("MCP-03 · each tool description starts with who may call it, so an AI client can pick the right tool")
    void descriptionsNameTheRole(String tool, String prefix) {
        assertThat(tools("stf_cb_case").get(tool).description()).startsWith(prefix);
    }

    @ParameterizedTest(name = "{0} description mentions \"{1}\"")
    @CsvSource(delimiter = '|', value = {
        "create_request  | reserves no money",
        "approve_request | reserve",
        "reject_request  | Funding unchanged",
        "record_payment  | committed to paid",
    })
    @DisplayName("MCP-03 · each write tool description states its funding effect")
    void descriptionsStateFundingEffect(String tool, String phrase) {
        assertThat(tools("stf_cb_case").get(tool).description()).containsIgnoringCase(phrase);
    }

    @Test
    @DisplayName("MCP-04 · every tool has an object input schema with additionalProperties false, and an object output schema")
    void everyToolHasStrictSchemas() {
        for (Tool t : tools("stf_cb_case").values()) {
            assertThat(t.inputSchema()).as(t.name() + " input").containsEntry("type", "object")
                    .containsEntry("additionalProperties", false);
            assertThat(t.outputSchema()).as(t.name() + " output").containsEntry("type", "object");
        }
    }

    @Test
    @DisplayName("NFR-05 · no tool accepts identity: no input schema has a role, staffId, nonprofitId or caller property")
    void noIdentityArguments() {
        for (Tool t : tools("stf_cb_case").values()) {
            assertThat(map(t.inputSchema().getOrDefault("properties", Map.of())).keySet()).as(t.name())
                    .doesNotContain("role", "staffId", "nonprofitId", "callerStaffId", "createdBy");
        }
    }

    @Test
    @DisplayName("MCP-04 · create_request's schema: 5 required fields, amount is a string, category is the 4-value enum, purpose max 500, clientRequestId optional with its pattern")
    void createRequestSchema() {
        Map<String, Object> schema = tools("stf_cb_case").get("create_request").inputSchema();
        Map<String, Object> props = map(schema.get("properties"));

        assertThat(strings(schema.get("required")))
                .containsExactlyInAnyOrder("participantId", "fundId", "category", "purpose", "amount");
        assertThat(map(props.get("amount"))).containsEntry("type", "string");
        assertThat(strings(map(props.get("category")).get("enum")))
                .containsExactly("RENT", "UTILITIES", "TRANSPORTATION", "OTHER");
        assertThat(num(map(props.get("purpose")).get("maxLength"))).isEqualTo(Inputs.PURPOSE_MAX);
        assertThat(map(props.get("clientRequestId"))).containsEntry("pattern", Inputs.CLIENT_REQUEST_ID_PATTERN);
    }

    @Test
    @DisplayName("MCP-04 · approve_request's output schema declares outcome (APPLIED or ALREADY_APPLIED), request and fund")
    void approveOutputSchema() {
        Map<String, Object> out = tools("stf_cb_case").get("approve_request").outputSchema();
        Map<String, Object> props = map(out.get("properties"));

        assertThat(strings(out.get("required"))).contains("outcome", "request", "fund");
        assertThat(strings(map(props.get("outcome")).get("enum"))).containsExactly("APPLIED", "ALREADY_APPLIED");
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object o) {
        return (List<String>) o;
    }
}
