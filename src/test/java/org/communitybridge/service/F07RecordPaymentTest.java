package org.communitybridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.communitybridge.service.Fixtures.*;

import org.communitybridge.domain.ErrorCode;
import org.communitybridge.domain.Outcome;
import org.communitybridge.domain.RequestStatus;
import org.communitybridge.service.input.RecordPaymentInput;
import org.communitybridge.service.result.FundSummary;
import org.communitybridge.service.result.PaymentView;
import org.communitybridge.service.result.RecordPaymentResult;
import org.communitybridge.service.result.RequestView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Flow stage 7: finance records the simulated payment for an APPROVED request, for the full amount, once.
 * The amount moves from committed to paid; available does not drop a second time.
 * Requirements: FR-06, FR-10, AC-03.
 */
@DisplayName("7 · Record payment")
class F07RecordPaymentTest extends ServiceTestBase {

    private RecordPaymentResult pay(String requestId) {
        return service.recordPayment(NEHA, new RecordPaymentInput(requestId));
    }

    @Test
    @DisplayName("AC-03 · Neha pays the approved $300.00: APPLIED; payment for the full $300.00 recorded by Neha now; request PAID")
    void payRecordsFullPayment() {
        RequestView r = approved(HOUSING, "300.00");

        RecordPaymentResult result = pay(r.id());

        assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
        PaymentView p = result.payment();
        assertThat(p.id()).matches("pay_[A-Za-z0-9]+");
        assertThat(p).isEqualTo(new PaymentView(p.id(), r.id(), 30_000, "300.00", "USD", NEHA, NOW_TEXT));
        assertThat(result.request().status()).isEqualTo(RequestStatus.PAID);
        assertThat(result.request().decidedBy()).isEqualTo(ARJUN);
    }

    @Test
    @DisplayName("AC-03 · after payment the fund shows $0.00 committed, $300.00 paid, and available stays $9,700.00 (not reduced twice)")
    void payMovesCommittedToPaid() {
        RequestView r = approved(HOUSING, "300.00");
        assertThat(fund(HOUSING).availableCents()).isEqualTo(970_000);

        FundSummary f = pay(r.id()).fund();

        assertThat(f.committedCents()).isZero();
        assertThat(f.paidCents()).isEqualTo(30_000);
        assertThat(f.availableCents()).isEqualTo(970_000);
        assertThat(f.paid()).isEqualTo("300.00");
        assertThat(f.available()).isEqualTo("9700.00");
        assertThat(f).isEqualTo(fund(HOUSING));
    }

    @Test
    @DisplayName("FR-06 · paying one of two approved requests moves only its amount: $100 and $200 approved, $100 paid -> $200 committed, $100 paid")
    void payMovesOnlyItsAmount() {
        RequestView a = approved(SMALL, "100.00");
        approved(SMALL, "200.00");

        FundSummary f = pay(a.id()).fund();

        assertThat(f.committedCents()).isEqualTo(20_000);
        assertThat(f.paidCents()).isEqualTo(10_000);
        assertThat(f.availableCents()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("FR-10 · payment adds one PAID audit event (APPROVED to PAID) by Neha, after CREATED and APPROVED")
    void payWritesAuditEvent() {
        RequestView r = approved(HOUSING, "300.00");
        pay(r.id());

        assertThat(auditEvents(r.id())).containsExactly(
                event("CREATED", null, "PENDING", PRIYA),
                event("APPROVED", "PENDING", "APPROVED", ARJUN),
                event("PAID", "APPROVED", "PAID", NEHA));
        assertThat(count("payments")).isEqualTo(1);
    }

    @Test
    @DisplayName("Assumption Q5 · an already-approved request can still be paid after its fund is closed (the money was committed)")
    void payAfterFundClosed() {
        RequestView r = approved(SMALL, "100.00");
        execute("UPDATE funds SET status = 'CLOSED' WHERE id = ?", SMALL);

        RecordPaymentResult result = pay(r.id());

        assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(result.fund().paidCents()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("FR-06 · paying a request ID that does not exist is NOT_FOUND and nothing changes")
    void payUnknownRequest() {
        expectErrorAndNoChange(ErrorCode.NOT_FOUND, () -> pay(NO_SUCH_REQUEST));
    }
}
