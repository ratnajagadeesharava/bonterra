package org.communitybridge.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MCP stage 4: the business rules, through the real server, with each role in its own server process.
 * These repeat the key acceptance criteria at the MCP boundary; the full matrix is covered at service level.
 * Requirements: AC-01 to AC-15, AC-23, AC-24, FR-09, NFR-03, NFR-06.
 */
@DisplayName("M4 · Workflow over MCP")
class M04WorkflowIT extends McpTestBase {

    private static final String PRIYA = "stf_cb_case";
    private static final String ARJUN = "stf_cb_pm";
    private static final String NEHA = "stf_cb_fin";
    private static final String HOUSING = "fnd_cb_housing";
    private static final String SMALL = "fnd_cb_small";

    @Test
    @DisplayName("AC-01 · Priya's create_request for $300.00 RENT returns outcome APPLIED and a PENDING request with amountCents 30000, amount \"300.00\", USD; available stays $10,000.00")
    void createOverMcp() {
        Map<String, Object> result = ok(PRIYA, "create_request", createArgs("par_cb_001", HOUSING, "300.00"));
        Map<String, Object> request = map(result.get("request"));

        assertThat(result).containsEntry("outcome", "APPLIED");
        assertThat(request).containsEntry("status", "PENDING").containsEntry("amount", "300.00")
                .containsEntry("currency", "USD").containsEntry("createdBy", PRIYA);
        assertThat(num(request.get("amountCents"))).isEqualTo(30_000);
        assertThat(fund(PRIYA, HOUSING)).containsEntry("available", "10000.00").containsEntry("pending", "300.00");
    }

    @Test
    @DisplayName("AC-02 · Arjun's approve_request returns APPLIED and the fund at $300.00 committed, $0.00 paid, $9,700.00 available")
    void approveOverMcp() {
        String id = pending(HOUSING, "300.00");

        Map<String, Object> result = ok(ARJUN, "approve_request", Map.of("requestId", id));

        assertThat(result).containsEntry("outcome", "APPLIED");
        assertThat(map(result.get("request"))).containsEntry("status", "APPROVED").containsEntry("decidedBy", ARJUN);
        assertThat(map(result.get("fund"))).containsEntry("committed", "300.00").containsEntry("paid", "0.00")
                .containsEntry("available", "9700.00");
    }

    @Test
    @DisplayName("AC-03 · Neha's record_payment returns APPLIED, a payment for $300.00, and the fund at $0.00 committed, $300.00 paid, $9,700.00 available")
    void payOverMcp() {
        String id = approved(HOUSING, "300.00");

        Map<String, Object> result = ok(NEHA, "record_payment", Map.of("requestId", id));

        assertThat(result).containsEntry("outcome", "APPLIED");
        assertThat(map(result.get("payment"))).containsEntry("requestId", id).containsEntry("amount", "300.00")
                .containsEntry("recordedBy", NEHA);
        assertThat(map(result.get("fund"))).containsEntry("committed", "0.00").containsEntry("paid", "300.00")
                .containsEntry("available", "9700.00");
    }

    @Test
    @DisplayName("AC-04 · Arjun's reject_request with reason 'Duplicate' returns REJECTED with the reason; funding unchanged")
    void rejectOverMcp() {
        String id = pending(HOUSING, "200.00");

        Map<String, Object> result = ok(ARJUN, "reject_request", Map.of("requestId", id, "reason", "Duplicate"));

        assertThat(map(result.get("request"))).containsEntry("status", "REJECTED").containsEntry("rejectionReason", "Duplicate");
        assertThat(fund(ARJUN, HOUSING)).containsEntry("committed", "0.00").containsEntry("available", "10000.00");
    }

    @Test
    @DisplayName("AC-06 · approving $600.00 on the $500.00 fund is INSUFFICIENT_FUNDS with details availableCents 50000, requestedCents 60000; state unchanged")
    void insufficientFundsOverMcp() {
        String id = pending(SMALL, "600.00");

        Map<String, Object> e = errorAndNoChange(ARJUN, "approve_request", Map.of("requestId", id), "INSUFFICIENT_FUNDS");

        assertThat(num(details(e).get("availableCents"))).isEqualTo(50_000);
        assertThat(num(details(e).get("requestedCents"))).isEqualTo(60_000);
    }

    @Test
    @DisplayName("AC-09 · create_request on the closed fund is FUND_INACTIVE; nothing inserted")
    void closedFundOverMcp() {
        errorAndNoChange(PRIYA, "create_request", createArgs("par_cb_001", "fnd_cb_closed", "10.00"), "FUND_INACTIVE");
    }

    @Test
    @DisplayName("AC-10/AC-11 · repeating approve and pay returns outcome ALREADY_APPLIED, the same payment ID, and writes nothing")
    void retriesOverMcp() {
        String id = approved(HOUSING, "300.00");
        Map<String, Object> firstPay = map(ok(NEHA, "record_payment", Map.of("requestId", id)).get("payment"));
        Map<String, java.util.List<Map<String, Object>>> before = snapshot();

        Map<String, Object> approveAgain = ok(ARJUN, "approve_request", Map.of("requestId", id));
        Map<String, Object> payAgain = ok(NEHA, "record_payment", Map.of("requestId", id));

        assertThat(approveAgain).containsEntry("outcome", "ALREADY_APPLIED");
        assertThat(payAgain).containsEntry("outcome", "ALREADY_APPLIED");
        assertThat(map(payAgain.get("payment"))).containsEntry("id", firstPay.get("id"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-12 · approve on a REJECTED request is INVALID_TRANSITION with details.currentStatus REJECTED; state unchanged")
    void invalidTransitionOverMcp() {
        String id = pending(HOUSING, "10.00");
        ok(ARJUN, "reject_request", Map.of("requestId", id));

        Map<String, Object> e = errorAndNoChange(ARJUN, "approve_request", Map.of("requestId", id), "INVALID_TRANSITION");

        assertThat(details(e)).containsEntry("currentStatus", "REJECTED");
    }

    @Test
    @DisplayName("AC-14 · Vikram (Harbor Relief) approving a Community Bridge request and Ananya reading one both get NOT_FOUND, identical to a random ID")
    void crossNonprofitOverMcp() {
        String id = pending(SMALL, "10.00");

        Map<String, Object> approve = errorAndNoChange("stf_hr_pm", "approve_request", Map.of("requestId", id), "NOT_FOUND");
        Map<String, Object> read = error(call("stf_hr_case", "get_request", Map.of("requestId", id)), "NOT_FOUND");
        Map<String, Object> random = error(call("stf_hr_case", "get_request",
                Map.of("requestId", "req_does_not_exist")), "NOT_FOUND");

        assertThat(read).isEqualTo(random);
        assertThat(approve.get("message")).isEqualTo(random.get("message"));
    }

    @Test
    @DisplayName("AC-15 · Priya's create_request for Harbor Relief's participant is NOT_FOUND; nothing inserted")
    void otherNonprofitParticipantOverMcp() {
        errorAndNoChange(PRIYA, "create_request", createArgs("par_hr_001", HOUSING, "10.00"), "NOT_FOUND");
    }

    @Test
    @DisplayName("AC-23/AC-24 · a keyed create retried returns ALREADY_APPLIED with the same request; the same key with $350.00 is IDEMPOTENCY_CONFLICT with existingRequestId")
    void idempotentCreateOverMcp() {
        Map<String, Object> keyed = createArgs("par_cb_001", HOUSING, "300.00");
        keyed.put("clientRequestId", "kavya-march-rent");
        String id = (String) map(ok(PRIYA, "create_request", keyed).get("request")).get("id");

        Map<String, Object> again = ok(PRIYA, "create_request", keyed);
        assertThat(again).containsEntry("outcome", "ALREADY_APPLIED");
        assertThat(map(again.get("request"))).containsEntry("id", id);

        Map<String, Object> changed = createArgs("par_cb_001", HOUSING, "350.00");
        changed.put("clientRequestId", "kavya-march-rent");
        Map<String, Object> e = errorAndNoChange(PRIYA, "create_request", changed, "IDEMPOTENCY_CONFLICT");
        assertThat(details(e)).containsEntry("existingRequestId", id);
    }

    @Test
    @DisplayName("FR-07 · list_requests over MCP filters by status and returns total; get_participant and list_funds return the seed data")
    void readsOverMcp() {
        pending(HOUSING, "10.00");
        approved(HOUSING, "20.00");

        Map<String, Object> pendingOnly = ok(NEHA, "list_requests", Map.of("status", "PENDING"));
        assertThat(num(pendingOnly.get("total"))).isEqualTo(1);
        assertThat(map(ok(NEHA, "get_participant", Map.of("participantId", "par_cb_001")).get("participant")))
                .containsEntry("name", "Kavya Reddy");
        assertThat(list(ok(NEHA, "list_funds", Map.of()).get("funds"))).extracting(f -> f.get("id"))
                .containsExactly("fnd_cb_closed", "fnd_cb_housing", "fnd_cb_small");
        assertThat(list(ok(NEHA, "list_participants", Map.of("search", "rohan")).get("participants")))
                .extracting(p -> p.get("id")).containsExactly("par_cb_002");
    }
}
