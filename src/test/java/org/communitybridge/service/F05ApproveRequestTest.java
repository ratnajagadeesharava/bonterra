package org.communitybridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.communitybridge.service.Fixtures.*;

import java.util.Map;
import org.communitybridge.domain.ErrorCode;
import org.communitybridge.domain.Outcome;
import org.communitybridge.domain.RequestStatus;
import org.communitybridge.service.error.ServiceException;
import org.communitybridge.service.input.ApproveRequestInput;
import org.communitybridge.service.input.GetRequestInput;
import org.communitybridge.service.result.ApproveRequestResult;
import org.communitybridge.service.result.FundSummary;
import org.communitybridge.service.result.RequestView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Flow stage 5: a program manager approves. Approval reserves the full amount against the fund,
 * atomically, only if the fund is Active and has enough available. Otherwise nothing changes.
 * Requirements: FR-04, FR-10, AC-02, AC-06, AC-07, AC-09b, NFR-03.
 */
@DisplayName("5 · Approve request")
class F05ApproveRequestTest extends ServiceTestBase {

    private ApproveRequestResult approve(String requestId) {
        return service.approveRequest(ARJUN, new ApproveRequestInput(requestId));
    }

    @Test
    @DisplayName("AC-02 · Arjun approves Priya's $300.00: APPLIED, APPROVED, decided by Arjun now; fund shows $300.00 committed, $0.00 paid, $9,700.00 available")
    void approveReservesFullAmount() {
        RequestView r = pending(HOUSING, "300.00");

        ApproveRequestResult result = approve(r.id());

        assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(result.request().status()).isEqualTo(RequestStatus.APPROVED);
        assertThat(result.request().decidedBy()).isEqualTo(ARJUN);
        assertThat(result.request().decidedAt()).isEqualTo(NOW_TEXT);
        FundSummary f = result.fund();
        assertThat(f.committedCents()).isEqualTo(30_000);
        assertThat(f.paidCents()).isZero();
        assertThat(f.availableCents()).isEqualTo(970_000);
        assertThat(f.committed()).isEqualTo("300.00");
        assertThat(f.available()).isEqualTo("9700.00");
    }

    @Test
    @DisplayName("AC-02 · the fund in the approve result equals the fund summary read afterwards, and the request no longer counts as pending")
    void approveResultMatchesSummary() {
        RequestView r = pending(HOUSING, "300.00");

        FundSummary inResult = approve(r.id()).fund();

        assertThat(inResult).isEqualTo(fund(HOUSING));
        assertThat(inResult.pendingCents()).isZero();
        assertThat(inResult.pendingCount()).isZero();
    }

    @Test
    @DisplayName("AC-02 · only status and decision fields change: the approved request keeps the same amount, purpose, participant, fund and creator")
    void approveChangesOnlyDecisionFields() {
        RequestView before = pending(HOUSING, "300.00");

        RequestView after = approve(before.id()).request();

        assertThat(after).usingRecursiveComparison()
                .ignoringFields("status", "decidedBy", "decidedAt").isEqualTo(before);
        assertThat(service.getRequest(NEHA, new GetRequestInput(before.id())).request()).isEqualTo(after);
    }

    @Test
    @DisplayName("FR-10 · approval adds one APPROVED audit event (PENDING to APPROVED) by Arjun, after the CREATED event")
    void approveWritesAuditEvent() {
        RequestView r = pending(HOUSING, "300.00");
        approve(r.id());

        assertThat(auditEvents(r.id())).containsExactly(
                event("CREATED", null, "PENDING", PRIYA),
                event("APPROVED", "PENDING", "APPROVED", ARJUN));
    }

    @Test
    @DisplayName("AC-06 · approving $600.00 on the $500.00 fund is INSUFFICIENT_FUNDS (availableCents 50000, requestedCents 60000); request stays PENDING; nothing changes")
    void approveMoreThanAvailable() {
        RequestView r = pending(SMALL, "600.00");

        ServiceException e = expectErrorAndNoChange(ErrorCode.INSUFFICIENT_FUNDS, () -> approve(r.id()));

        assertThat(e.details()).isEqualTo(Map.of("availableCents", 50_000L, "requestedCents", 60_000L));
        assertThat(service.getRequest(ARJUN, new GetRequestInput(r.id())).request().status()).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    @DisplayName("FR-04 · approving exactly what is available ($500.00 on the $500.00 fund) succeeds and leaves $0.00 available")
    void approveExactlyAvailable() {
        RequestView r = pending(SMALL, "500.00");

        assertThat(approve(r.id()).fund().availableCents()).isZero();
    }

    @Test
    @DisplayName("AC-07 · on the $500.00 fund, approve $300.00 then $250.00: first APPLIED, second INSUFFICIENT_FUNDS with availableCents 20000")
    void secondApprovalSeesFirstReservation() {
        RequestView first = pending(SMALL, "300.00");
        RequestView second = pending(SMALL, "250.00");

        assertThat(approve(first.id()).outcome()).isEqualTo(Outcome.APPLIED);
        ServiceException e = expectErrorAndNoChange(ErrorCode.INSUFFICIENT_FUNDS, () -> approve(second.id()));

        assertThat(e.details()).isEqualTo(Map.of("availableCents", 20_000L, "requestedCents", 25_000L));
        assertThat(fund(SMALL).committedCents()).isEqualTo(30_000);
    }

    @Test
    @DisplayName("FR-04 · paid money also counts: after $300.00 is paid on the $500.00 fund, approving $250.00 is INSUFFICIENT_FUNDS (20000 available)")
    void paidAmountReducesAvailable() {
        paid(SMALL, "300.00");
        RequestView next = pending(SMALL, "250.00");

        ServiceException e = expectErrorAndNoChange(ErrorCode.INSUFFICIENT_FUNDS, () -> approve(next.id()));

        assertThat(e.details()).containsEntry("availableCents", 20_000L);
    }

    @Test
    @DisplayName("FR-03/FR-04 · pending requests reserve nothing: two $400.00 requests on the $500.00 fund are both accepted, but only the first can be approved")
    void pendingRequestsDoNotReserve() {
        RequestView a = pending(SMALL, "400.00");
        RequestView b = pending(SMALL, "400.00");
        assertThat(fund(SMALL).availableCents()).isEqualTo(50_000);

        approve(a.id());
        expectErrorAndNoChange(ErrorCode.INSUFFICIENT_FUNDS, () -> approve(b.id()));
    }

    @Test
    @DisplayName("AC-09b · a PENDING request whose fund was closed after submission cannot be approved: FUND_INACTIVE; stays PENDING; nothing changes")
    void approveOnFundClosedAfterSubmission() {
        RequestView r = pending(SMALL, "100.00");
        execute("UPDATE funds SET status = 'CLOSED' WHERE id = ?", SMALL);

        expectErrorAndNoChange(ErrorCode.FUND_INACTIVE, () -> approve(r.id()));
    }

    @Test
    @DisplayName("AC-09b · a closed fund is reported as FUND_INACTIVE even when the amount is also too large (status is checked before balance)")
    void closedFundCheckedBeforeBalance() {
        RequestView r = pending(SMALL, "600.00");
        execute("UPDATE funds SET status = 'CLOSED' WHERE id = ?", SMALL);

        expectErrorAndNoChange(ErrorCode.FUND_INACTIVE, () -> approve(r.id()));
    }

    @Test
    @DisplayName("FR-04 · approving a request ID that does not exist is NOT_FOUND and nothing changes")
    void approveUnknownRequest() {
        expectErrorAndNoChange(ErrorCode.NOT_FOUND, () -> approve(NO_SUCH_REQUEST));
    }

    @Test
    @DisplayName("Validation · approving with no requestId is VALIDATION_ERROR on field 'requestId'")
    void approveMissingId() {
        ServiceException e = expectErrorAndNoChange(ErrorCode.VALIDATION_ERROR, () -> approve(null));
        assertThat(F03ReadsAndTenantIsolationTest.fields(e)).containsExactly("requestId");
    }

    @Test
    @DisplayName("FR-04 · approval touches only its own fund: approving on Small leaves Housing Stability unchanged")
    void approveTouchesOnlyItsFund() {
        FundSummary housingBefore = fund(HOUSING);
        approve(pending(SMALL, "100.00").id());

        assertThat(fund(HOUSING)).isEqualTo(housingBefore);
    }
}
