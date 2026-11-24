package org.communitybridge.mcp;

import static org.communitybridge.mcp.Param.optional;
import static org.communitybridge.mcp.Param.optionalInt;
import static org.communitybridge.mcp.Param.required;
import static org.communitybridge.mcp.Schemas.FUND;
import static org.communitybridge.mcp.Schemas.OUTCOME;
import static org.communitybridge.mcp.Schemas.PARTICIPANT;
import static org.communitybridge.mcp.Schemas.PAYMENT;
import static org.communitybridge.mcp.Schemas.REQUEST;
import static org.communitybridge.mcp.Schemas.TOTALS;
import static org.communitybridge.mcp.Schemas.WHOAMI;
import static org.communitybridge.mcp.Schemas.arrayOf;
import static org.communitybridge.mcp.Schemas.integer;
import static org.communitybridge.mcp.Schemas.object;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.communitybridge.db.Database;
import org.communitybridge.domain.Action;
import org.communitybridge.domain.Category;
import org.communitybridge.domain.Money;
import org.communitybridge.domain.RequestStatus;
import org.communitybridge.service.AssistanceService;
import org.communitybridge.service.error.ServiceException;
import org.communitybridge.service.input.ApproveRequestInput;
import org.communitybridge.service.input.CreateRequestInput;
import org.communitybridge.service.input.FundingSummaryInput;
import org.communitybridge.service.input.GetParticipantInput;
import org.communitybridge.service.input.GetRequestInput;
import org.communitybridge.service.input.Inputs;
import org.communitybridge.service.input.ListParticipantsInput;
import org.communitybridge.service.input.ListRequestsInput;
import org.communitybridge.service.input.RecordPaymentInput;
import org.communitybridge.service.input.RejectRequestInput;
import org.communitybridge.service.result.FundSummary;
import org.communitybridge.service.result.FundingSummaryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * The 11 MCP tools. Each call runs: identity and role (service.checkAccess) -> JSON shape (Args.check) ->
 * the service operation (which validates values and does the work in one transaction) -> structuredContent
 * plus a one-line text summary. Every failure becomes a tool result with isError true and
 * structuredContent { error: { code, message, details } }; nothing is thrown to the SDK.
 */
@Configuration
public class McpToolConfig {

    private static final Logger log = LoggerFactory.getLogger(McpToolConfig.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Bean
    AssistanceService assistanceService(Database database) {
        return new AssistanceService(database, Clock.systemUTC());
    }

    /** Spike S2: the SDK's own input validation answers with text only and before our role check, so it is off. */
    @Bean
    McpSyncServerCustomizer disableSdkInputValidation() {
        return spec -> spec.validateToolInputs(false);
    }

    @Bean
    List<SyncToolSpecification> tools(AssistanceService service, LaunchIdentity identity) {
        Tools t = new Tools(service, identity.staffId());

        Param requestId = required("requestId", "Request ID, e.g. req_3f9a0c1d2b4e5f607182");
        Param limit = optionalInt("limit", "Maximum results, 1-100 (default 50)",
                "minimum", Inputs.LIMIT_MIN, "maximum", Inputs.LIMIT_MAX);

        return List.of(
                t.tool("whoami",
                        "Any role. Show your own staff identity, role and nonprofit, as fixed by how this server was launched.",
                        Action.READ, List.of(), WHOAMI,
                        (s, a) -> s.whoami(t.staffId),
                        r -> "You are " + r.name() + " (" + r.role() + ") at " + r.nonprofitName() + "."),

                t.tool("list_participants",
                        "Any role. List participants in your nonprofit, optionally filtered by name (case-insensitive).",
                        Action.READ,
                        List.of(optional("search", "Part of a participant's name", "maxLength", Inputs.SEARCH_MAX), limit),
                        object("participants", arrayOf(PARTICIPANT)),
                        (s, a) -> s.listParticipants(t.staffId, new ListParticipantsInput(a.str("search"), a.integer("limit"))),
                        r -> r.participants().size() + " participant(s)."),

                t.tool("get_participant",
                        "Any role. Get one participant in your nonprofit.",
                        Action.READ,
                        List.of(required("participantId", "Participant ID, e.g. par_cb_001")),
                        object("participant", PARTICIPANT),
                        (s, a) -> s.getParticipant(t.staffId, new GetParticipantInput(a.str("participantId"))),
                        r -> r.participant().name() + " (" + r.participant().id() + ")."),

                t.tool("list_funds",
                        "Any role. List your nonprofit's funds with allocation, committed, paid, available and pending amounts.",
                        Action.READ, List.of(),
                        object("funds", arrayOf(FUND)),
                        (s, a) -> s.listFunds(t.staffId),
                        r -> r.funds().stream().map(Tools::fundLine).collect(Collectors.joining(" "))),

                t.tool("get_funding_summary",
                        "Any role. Funding summary for one fund (fundId) or all your funds, with totals. "
                                + "available = allocation - committed - paid; pending is shown for information and does not reduce available.",
                        Action.READ,
                        List.of(optional("fundId", "Fund ID, e.g. fnd_cb_housing; omit for all funds")),
                        object("funds", arrayOf(FUND), "totals", TOTALS),
                        (s, a) -> s.getFundingSummary(t.staffId, new FundingSummaryInput(a.str("fundId"))),
                        Tools::summaryText),

                t.tool("create_request",
                        "Case workers only. Submit an assistance request for an existing participant. It starts PENDING and "
                                + "reserves no money. Send a clientRequestId to make retries safe (a repeat returns the same request).",
                        Action.CREATE_REQUEST,
                        List.of(required("participantId", "Participant ID in your nonprofit"),
                                required("fundId", "Fund ID in your nonprofit; must be ACTIVE"),
                                required("category", "What the money is for",
                                        "enum", enumNames(Category.values())),
                                required("purpose", "Short description, stored as plain text",
                                        "minLength", 1, "maxLength", Inputs.PURPOSE_MAX),
                                required("amount", "USD amount as a decimal string with at most 2 decimals, e.g. \"300.50\"; "
                                        + "more than 0 and at most 1000000.00", "pattern", Money.AMOUNT_PATTERN),
                                optional("clientRequestId", "Optional idempotency key you choose, e.g. kavya-march-rent",
                                        "pattern", Inputs.CLIENT_REQUEST_ID_PATTERN)),
                        object("outcome", OUTCOME, "request", REQUEST),
                        (s, a) -> s.createRequest(t.staffId, new CreateRequestInput(a.str("participantId"), a.str("fundId"),
                                a.str("category"), a.str("purpose"), a.str("amount"), a.str("clientRequestId"))),
                        r -> "Request " + r.request().id() + " is " + r.request().status() + " for " + r.request().amount()
                                + " USD; no money reserved (" + r.outcome() + ")."),

                t.tool("approve_request",
                        "Program managers only. Approve a PENDING request and reserve its full amount against its fund, "
                                + "atomically. Fails with INSUFFICIENT_FUNDS if the fund's available amount is too small, "
                                + "or FUND_INACTIVE if the fund is closed. Repeating it is safe.",
                        Action.APPROVE_REQUEST, List.of(requestId),
                        object("outcome", OUTCOME, "request", REQUEST, "fund", FUND),
                        (s, a) -> s.approveRequest(t.staffId, new ApproveRequestInput(a.str("requestId"))),
                        r -> "Request " + r.request().id() + " is " + r.request().status() + "; " + Tools.fundLine(r.fund())
                                + " (" + r.outcome() + ")"),

                t.tool("reject_request",
                        "Program managers only. Reject a PENDING request, with an optional reason. Funding unchanged. "
                                + "Rejection is final. Repeating it is safe.",
                        Action.REJECT_REQUEST,
                        List.of(requestId, optional("reason", "Optional reason", "maxLength", Inputs.REASON_MAX)),
                        object("outcome", OUTCOME, "request", REQUEST),
                        (s, a) -> s.rejectRequest(t.staffId, new RejectRequestInput(a.str("requestId"), a.str("reason"))),
                        r -> "Request " + r.request().id() + " is " + r.request().status() + "; funding unchanged ("
                                + r.outcome() + ")."),

                t.tool("record_payment",
                        "Finance only. Record the simulated payment of an APPROVED request, for its full amount, once. "
                                + "Moves the amount from committed to paid; available does not drop again. Repeating it is safe.",
                        Action.RECORD_PAYMENT, List.of(requestId),
                        object("outcome", OUTCOME, "payment", PAYMENT, "request", REQUEST, "fund", FUND),
                        (s, a) -> s.recordPayment(t.staffId, new RecordPaymentInput(a.str("requestId"))),
                        r -> "Payment " + r.payment().id() + " of " + r.payment().amount() + " USD recorded for request "
                                + r.request().id() + "; " + Tools.fundLine(r.fund()) + " (" + r.outcome() + ")"),

                t.tool("get_request",
                        "Any role. Get one request in your nonprofit, with its status and decision.",
                        Action.READ, List.of(requestId),
                        object("request", REQUEST),
                        (s, a) -> s.getRequest(t.staffId, new GetRequestInput(a.str("requestId"))),
                        r -> "Request " + r.request().id() + ": " + r.request().status() + ", " + r.request().amount() + " USD."),

                t.tool("list_requests",
                        "Any role. List requests in your nonprofit, oldest first, optionally filtered by status, fund or participant.",
                        Action.READ,
                        List.of(optional("status", "PENDING, APPROVED, REJECTED or PAID",
                                        "enum", enumNames(RequestStatus.values())),
                                optional("fundId", "Only requests on this fund"),
                                optional("participantId", "Only requests for this participant"),
                                limit,
                                optionalInt("offset", "Results to skip (default 0)", "minimum", 0)),
                        object("requests", arrayOf(REQUEST), "total", integer()),
                        (s, a) -> s.listRequests(t.staffId, new ListRequestsInput(a.str("status"), a.str("fundId"),
                                a.str("participantId"), a.integer("limit"), a.integer("offset"))),
                        r -> r.requests().size() + " of " + r.total() + " request(s)."));
    }

    private static List<String> enumNames(Enum<?>[] values) {
        List<String> names = new ArrayList<>();
        for (Enum<?> v : values) {
            names.add(v.name());
        }
        return names;
    }

    /** Builds tool specifications that all share the same call path and error mapping. */
    static final class Tools {

        @FunctionalInterface
        interface Call<T> {
            T apply(AssistanceService service, Args args);
        }

        private final AssistanceService service;
        final String staffId;

        Tools(AssistanceService service, String staffId) {
            this.service = service;
            this.staffId = staffId;
        }

        <T> SyncToolSpecification tool(String name, String description, Action action, List<Param> params,
                Map<String, Object> outputSchema, Call<T> call, Function<T, String> summary) {
            McpSchema.Tool tool = McpSchema.Tool.builder()
                    .name(name)
                    .description(description)
                    .inputSchema(inputSchema(params))
                    .outputSchema(outputSchema)
                    .build();
            return SyncToolSpecification.builder()
                    .tool(tool)
                    .callHandler((exchange, request) -> handle(name, action, params, request.arguments(), call, summary))
                    .build();
        }

        private <T> CallToolResult handle(String name, Action action, List<Param> params, Map<String, Object> raw,
                Call<T> call, Function<T, String> summary) {
            try {
                service.checkAccess(staffId, action);           // UNAUTHENTICATED, FORBIDDEN
                Args args = Args.check(raw, params);            // unknown fields, JSON types
                T result = call.apply(service, args);           // value rules, lookups, state, money
                return CallToolResult.builder()
                        .structuredContent(JSON.convertValue(result, Map.class))
                        .addTextContent(summary.apply(result))
                        .build();
            } catch (ServiceException e) {
                return failure(e);
            } catch (RuntimeException e) {
                log.error("Tool {} failed unexpectedly", name, e);
                return failure(ServiceException.internal());
            }
        }

        private static CallToolResult failure(ServiceException e) {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("error", JSON.convertValue(e.toBody(), Map.class));
            return CallToolResult.builder()
                    .isError(true)
                    .structuredContent(content)
                    .addTextContent(e.code() + ": " + e.getMessage())
                    .build();
        }

        private static Map<String, Object> inputSchema(List<Param> params) {
            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> requiredNames = new ArrayList<>();
            for (Param p : params) {
                properties.put(p.name(), p.schema());
                if (p.required()) {
                    requiredNames.add(p.name());
                }
            }
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", properties);
            if (!requiredNames.isEmpty()) {
                schema.put("required", requiredNames);
            }
            schema.put("additionalProperties", false);
            return schema;
        }

        static String fundLine(FundSummary f) {
            return f.name() + ": " + f.allocation() + " allocated, " + f.committed() + " committed, " + f.paid()
                    + " paid, " + f.available() + " available USD.";
        }

        static String summaryText(FundingSummaryResult r) {
            String funds = r.funds().stream().map(Tools::fundLine).collect(Collectors.joining(" "));
            return r.funds().size() == 1 ? funds
                    : funds + " Totals: " + r.totals().available() + " of " + r.totals().allocation() + " USD available.";
        }
    }
}
