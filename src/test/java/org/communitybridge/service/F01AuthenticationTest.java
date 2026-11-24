package org.communitybridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.communitybridge.service.Fixtures.*;

import java.util.stream.Stream;
import org.communitybridge.domain.CallerContext;
import org.communitybridge.domain.ErrorCode;
import org.communitybridge.domain.Role;
import org.communitybridge.identity.StaffIdentityResolver;
import org.communitybridge.service.input.ApproveRequestInput;
import org.communitybridge.service.input.CreateRequestInput;
import org.communitybridge.service.input.FundingSummaryInput;
import org.communitybridge.service.input.GetParticipantInput;
import org.communitybridge.service.input.GetRequestInput;
import org.communitybridge.service.input.ListParticipantsInput;
import org.communitybridge.service.input.ListRequestsInput;
import org.communitybridge.service.input.RecordPaymentInput;
import org.communitybridge.service.input.RejectRequestInput;
import org.communitybridge.service.result.WhoAmIResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Flow stage 1: who is calling. Identity comes only from the trusted staff ID and is resolved on the
 * server; nothing else can be done without a valid, active identity.
 * Requirements: NFR-04, MCP-06, FR-11.
 */
@DisplayName("1 · Authentication")
class F01AuthenticationTest extends ServiceTestBase {

    @ParameterizedTest(name = "{0} resolves to {1}, {2}, {3}")
    @CsvSource({
        "stf_cb_case, Priya Sharma, CASE_WORKER,     np_cb, Community Bridge",
        "stf_cb_pm,   Arjun Patel,  PROGRAM_MANAGER, np_cb, Community Bridge",
        "stf_cb_fin,  Neha Gupta,   FINANCE,         np_cb, Community Bridge",
        "stf_hr_case, Ananya Das,   CASE_WORKER,     np_hr, Harbor Relief",
        "stf_hr_pm,   Vikram Singh, PROGRAM_MANAGER, np_hr, Harbor Relief",
        "stf_hr_fin,  Isha Nair,    FINANCE,         np_hr, Harbor Relief",
    })
    @DisplayName("FR-11 · whoami returns the caller's own identity, role and nonprofit, read from the staff table")
    void whoamiReturnsResolvedIdentity(String staffId, String name, Role role, String npId, String npName) {
        WhoAmIResult me = service.whoami(staffId);

        assertThat(me).isEqualTo(new WhoAmIResult(staffId, name, role, npId, npName));
    }

    @Test
    @DisplayName("NFR-04 · the resolver builds a CallerContext only from the database row for a valid, active staff ID")
    void resolverBuildsCallerContextFromDatabase() {
        CallerContext ctx = new StaffIdentityResolver(db).resolve(ARJUN);

        assertThat(ctx).isEqualTo(new CallerContext(ARJUN, "Arjun Patel", Role.PROGRAM_MANAGER, NP_CB, NP_CB_NAME));
    }

    @ParameterizedTest(name = "staff ID [{0}] is refused")
    @NullSource
    @ValueSource(strings = {"", "   ", "stf_unknown", "STF_CB_CASE", " stf_cb_case", "stf_cb_former"})
    @DisplayName("AC-16 (service level) · missing, blank, unknown, wrongly cased, padded or inactive staff IDs are UNAUTHENTICATED at the resolver (server startup uses this)")
    void resolverRejectsBadIdentities(String staffId) {
        expectError(ErrorCode.UNAUTHENTICATED, () -> new StaffIdentityResolver(db).resolve(staffId));
    }

    static Stream<Arguments> everyOperationWithBadIdentity() {
        Stream.Builder<Arguments> all = Stream.builder();
        for (String badId : new String[] {null, "", "stf_unknown", RAHUL_INACTIVE}) {
            for (Op op : everyOperation()) {
                all.add(Arguments.of(badId, op));
            }
        }
        return all.build();
    }

    @ParameterizedTest(name = "identity [{0}] calling {1}")
    @MethodSource("everyOperationWithBadIdentity")
    @DisplayName("NFR-04 · every one of the 11 operations refuses a missing, unknown or inactive identity with UNAUTHENTICATED and changes nothing")
    void everyOperationRequiresValidIdentity(String badId, Op op) {
        expectErrorAndNoChange(ErrorCode.UNAUTHENTICATED, () -> op.call().accept(service, badId));
    }

    @Test
    @DisplayName("NFR-04 · identity is checked before role: an unknown ID calling approve gets UNAUTHENTICATED, not FORBIDDEN")
    void identityIsCheckedBeforeRole() {
        expectErrorAndNoChange(ErrorCode.UNAUTHENTICATED,
                () -> service.approveRequest("stf_unknown", new ApproveRequestInput(NO_SUCH_REQUEST)));
    }

    @Test
    @DisplayName("NFR-04 · identity is re-read on every call: Priya deactivated after one request cannot submit another, nothing inserted")
    void deactivatedMidSessionIsRefused() {
        pending(HOUSING, "100.00");
        execute("UPDATE staff SET active = 0 WHERE id = ?", PRIYA);

        expectErrorAndNoChange(ErrorCode.UNAUTHENTICATED,
                () -> service.createRequest(PRIYA, input(KAVYA, HOUSING, "200.00")));
        assertThat(count("requests")).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR-04 · a role changed in the database takes effect on the next call (role is never cached from startup)")
    void roleChangeTakesEffectImmediately() {
        execute("UPDATE staff SET role = 'FINANCE' WHERE id = ?", PRIYA);

        expectErrorAndNoChange(ErrorCode.FORBIDDEN,
                () -> service.createRequest(PRIYA, input(KAVYA, HOUSING, "200.00")));
        assertThat(service.whoami(PRIYA).role()).isEqualTo(Role.FINANCE);
    }

    /** All 11 operations with harmless, well-formed arguments. */
    static Op[] everyOperation() {
        return new Op[] {
            new Op("whoami", (s, id) -> s.whoami(id)),
            new Op("list_participants", (s, id) -> s.listParticipants(id, new ListParticipantsInput(null, null))),
            new Op("get_participant", (s, id) -> s.getParticipant(id, new GetParticipantInput(KAVYA))),
            new Op("list_funds", (s, id) -> s.listFunds(id)),
            new Op("get_funding_summary", (s, id) -> s.getFundingSummary(id, new FundingSummaryInput(null))),
            new Op("create_request", (s, id) -> s.createRequest(id,
                    new CreateRequestInput(KAVYA, HOUSING, "RENT", "Rent", "100.00", null))),
            new Op("approve_request", (s, id) -> s.approveRequest(id, new ApproveRequestInput(NO_SUCH_REQUEST))),
            new Op("reject_request", (s, id) -> s.rejectRequest(id, new RejectRequestInput(NO_SUCH_REQUEST, null))),
            new Op("record_payment", (s, id) -> s.recordPayment(id, new RecordPaymentInput(NO_SUCH_REQUEST))),
            new Op("get_request", (s, id) -> s.getRequest(id, new GetRequestInput(NO_SUCH_REQUEST))),
            new Op("list_requests", (s, id) -> s.listRequests(id, new ListRequestsInput(null, null, null, null, null))),
        };
    }
}
