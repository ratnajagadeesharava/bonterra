package org.communitybridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.communitybridge.service.Fixtures.*;

import java.util.Map;
import org.communitybridge.domain.Outcome;
import org.communitybridge.service.input.ApproveRequestInput;
import org.communitybridge.service.input.RecordPaymentInput;
import org.communitybridge.service.result.FundSummary;
import org.communitybridge.service.result.RequestView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Flow stage 9: fund figures are always exact and explainable. available = allocation - committed - paid,
 * and the stored counters always equal the sums of the request amounts behind them.
 * Requirements: NFR-01, AC-05, G3.
 */
@DisplayName("9 · Exact money and fund figures")
class F09ExactMoneyTest extends ServiceTestBase {

    @Test
    @DisplayName("AC-05 · on the $500.00 fund approve $499.70, then $0.10 and $0.20: both APPLIED; available is exactly $0.00 and committed exactly $500.00")
    void centsAddUpExactly() {
        approved(SMALL, "499.70");
        assertThat(fund(SMALL).availableCents()).isEqualTo(30);

        RequestView ten = pending(SMALL, "0.10");
        RequestView twenty = pending(SMALL, "0.20");
        assertThat(service.approveRequest(ARJUN, new ApproveRequestInput(ten.id())).outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(service.approveRequest(ARJUN, new ApproveRequestInput(twenty.id())).outcome()).isEqualTo(Outcome.APPLIED);

        FundSummary f = fund(SMALL);
        assertThat(f.availableCents()).isZero();
        assertThat(f.committedCents()).isEqualTo(50_000);
        assertThat(f.available()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("Spec example · Housing $10,000.00: submit $300 -> 10000.00 available; approve -> 300.00 committed, 9700.00 available; pay -> 0.00 committed, 300.00 paid, 9700.00 available")
    void specWorkedExample() {
        RequestView r = pending(HOUSING, "300.00");
        assertThat(figures(HOUSING)).isEqualTo(new String[] {"10000.00", "0.00", "0.00", "10000.00"});

        service.approveRequest(ARJUN, new ApproveRequestInput(r.id()));
        assertThat(figures(HOUSING)).isEqualTo(new String[] {"10000.00", "300.00", "0.00", "9700.00"});

        service.recordPayment(NEHA, new RecordPaymentInput(r.id()));
        assertThat(figures(HOUSING)).isEqualTo(new String[] {"10000.00", "0.00", "300.00", "9700.00"});
    }

    @Test
    @DisplayName("G3 · after a mixed scenario every fund's committed and paid counters equal the sums of its APPROVED and PAID request amounts, and available = allocation - committed - paid")
    void countersEqualRequestSums() {
        approved(HOUSING, "123.45");
        paid(HOUSING, "1000.01");
        rejected(HOUSING, "50.00");
        pending(HOUSING, "75.00");
        paid(SMALL, "0.99");
        approved(SMALL, "250.00");
        rejected(SMALL, "9.99");

        for (String fundId : new String[] {HOUSING, SMALL, CLOSED}) {
            FundSummary f = fund(fundId);
            Map<String, Object> sums = query("""
                    SELECT coalesce(sum(CASE WHEN status = 'APPROVED' THEN amount_cents END), 0) AS committed,
                           coalesce(sum(CASE WHEN status = 'PAID'     THEN amount_cents END), 0) AS paid,
                           coalesce(sum(CASE WHEN status = 'PENDING'  THEN amount_cents END), 0) AS pending
                    FROM requests WHERE fund_id = ?""", fundId).get(0);

            assertThat(f.committedCents()).as(fundId + " committed").isEqualTo(((Number) sums.get("committed")).longValue());
            assertThat(f.paidCents()).as(fundId + " paid").isEqualTo(((Number) sums.get("paid")).longValue());
            assertThat(f.pendingCents()).as(fundId + " pending").isEqualTo(((Number) sums.get("pending")).longValue());
            assertThat(f.availableCents()).as(fundId + " available")
                    .isEqualTo(f.allocationCents() - f.committedCents() - f.paidCents());
        }
        assertThat(fund(HOUSING).available()).isEqualTo("8876.54");
    }

    private String[] figures(String fundId) {
        FundSummary f = fund(fundId);
        return new String[] {f.allocation(), f.committed(), f.paid(), f.available()};
    }
}
