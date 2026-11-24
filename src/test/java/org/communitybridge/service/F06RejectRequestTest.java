package org.communitybridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.communitybridge.service.F03ReadsAndTenantIsolationTest.fields;
import static org.communitybridge.service.Fixtures.*;

import org.communitybridge.domain.ErrorCode;
import org.communitybridge.domain.Outcome;
import org.communitybridge.domain.RequestStatus;
import org.communitybridge.service.error.ServiceException;
import org.communitybridge.service.input.RejectRequestInput;
import org.communitybridge.service.result.FundSummary;
import org.communitybridge.service.result.RejectRequestResult;
import org.communitybridge.service.result.RequestView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Flow stage 6: a program manager rejects a PENDING request. Funding never changes; rejection is final.
 * Requirements: FR-05, FR-10, AC-04.
 */
@DisplayName("6 · Reject request")
class F06RejectRequestTest extends ServiceTestBase {

    private RejectRequestResult reject(String requestId, String reason) {
        return service.rejectRequest(ARJUN, new RejectRequestInput(requestId, reason));
    }

    @Test
    @DisplayName("AC-04 · Arjun rejects a PENDING $200.00 request with reason 'Duplicate': APPLIED, REJECTED, reason stored, decided by Arjun now")
    void rejectWithReason() {
        RequestView r = pending(HOUSING, "200.00");

        RejectRequestResult result = reject(r.id(), "Duplicate");

        assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(result.request().status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(result.request().rejectionReason()).isEqualTo("Duplicate");
        assertThat(result.request().decidedBy()).isEqualTo(ARJUN);
        assertThat(result.request().decidedAt()).isEqualTo(NOW_TEXT);
    }

    @Test
    @DisplayName("AC-04 · rejecting leaves committed, paid and available unchanged; only the pending total drops by the rejected amount")
    void rejectLeavesFundingUnchanged() {
        RequestView r = pending(HOUSING, "200.00");
        FundSummary before = fund(HOUSING);

        reject(r.id(), "Duplicate");

        FundSummary after = fund(HOUSING);
        assertThat(after.committedCents()).isEqualTo(before.committedCents());
        assertThat(after.paidCents()).isEqualTo(before.paidCents());
        assertThat(after.availableCents()).isEqualTo(before.availableCents());
        assertThat(after.pendingCents()).isEqualTo(before.pendingCents() - 20_000);
    }

    @Test
    @DisplayName("FR-05 · a reason is optional: rejecting without one stores no reason; a whitespace-only reason is stored as none")
    void reasonOptional() {
        assertThat(reject(pending(HOUSING, "1.00").id(), null).request().rejectionReason()).isNull();
        assertThat(reject(pending(HOUSING, "2.00").id(), "   ").request().rejectionReason()).isNull();
    }

    @Test
    @DisplayName("FR-05 · a 500-character reason is accepted; 501 characters is VALIDATION_ERROR on field 'reason' and the request stays PENDING")
    void reasonLength() {
        assertThat(reject(pending(HOUSING, "1.00").id(), "r".repeat(500)).request().rejectionReason()).hasSize(500);

        String id = pending(HOUSING, "2.00").id();
        ServiceException e = expectErrorAndNoChange(ErrorCode.VALIDATION_ERROR, () -> reject(id, "r".repeat(501)));
        assertThat(fields(e)).containsExactly("reason");
    }

    @Test
    @DisplayName("FR-10 · rejection adds one REJECTED audit event (PENDING to REJECTED) by Arjun")
    void rejectWritesAuditEvent() {
        RequestView r = pending(HOUSING, "200.00");
        reject(r.id(), "Duplicate");

        assertThat(auditEvents(r.id())).containsExactly(
                event("CREATED", null, "PENDING", PRIYA),
                event("REJECTED", "PENDING", "REJECTED", ARJUN));
    }

    @Test
    @DisplayName("Assumption · a PENDING request on a fund closed after submission can still be rejected")
    void rejectOnClosedFundAllowed() {
        RequestView r = pending(SMALL, "100.00");
        execute("UPDATE funds SET status = 'CLOSED' WHERE id = ?", SMALL);

        assertThat(reject(r.id(), "Fund closed").request().status()).isEqualTo(RequestStatus.REJECTED);
    }

    @Test
    @DisplayName("FR-05 · rejecting a request ID that does not exist is NOT_FOUND and nothing changes")
    void rejectUnknownRequest() {
        expectErrorAndNoChange(ErrorCode.NOT_FOUND, () -> reject(NO_SUCH_REQUEST, null));
    }
}
