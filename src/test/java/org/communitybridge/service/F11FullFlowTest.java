package org.communitybridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.communitybridge.service.Fixtures.*;

import org.communitybridge.domain.Outcome;
import org.communitybridge.domain.RequestStatus;
import org.communitybridge.service.input.ApproveRequestInput;
import org.communitybridge.service.input.CreateRequestInput;
import org.communitybridge.service.input.FundingSummaryInput;
import org.communitybridge.service.input.GetRequestInput;
import org.communitybridge.service.input.RecordPaymentInput;
import org.communitybridge.service.input.RejectRequestInput;
import org.communitybridge.service.result.FundSummary;
import org.communitybridge.service.result.FundingSummaryResult;
import org.communitybridge.service.result.RequestView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Flow stage 11: the whole workflow end to end at service level, as the three roles would do it,
 * with the audit trail it leaves. The same flow is repeated over MCP in step 5 (AC-20).
 * Requirements: FR-03 to FR-06, FR-10, spec Example.
 */
@DisplayName("11 · Full flow and audit trail")
class F11FullFlowTest extends ServiceTestBase {

    @Test
    @DisplayName("Spec example · Priya submits $300.00 rent for Kavya, Arjun approves, Neha pays; anyone reads the summary: $0.00 committed, $300.00 paid, $9,700.00 available")
    void submitApprovePay() {
        RequestView created = service.createRequest(PRIYA,
                new CreateRequestInput(KAVYA, HOUSING, "RENT", "March rent shortfall", "300.00", "kavya-2026-03")).request();
        assertThat(service.approveRequest(ARJUN, new ApproveRequestInput(created.id())).outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(service.recordPayment(NEHA, new RecordPaymentInput(created.id())).outcome()).isEqualTo(Outcome.APPLIED);

        for (String reader : new String[] {PRIYA, ARJUN, NEHA}) {
            FundSummary f = fund(reader, HOUSING);
            assertThat(f.committed()).isEqualTo("0.00");
            assertThat(f.paid()).isEqualTo("300.00");
            assertThat(f.available()).isEqualTo("9700.00");
        }
        RequestView fin = service.getRequest(PRIYA, new GetRequestInput(created.id())).request();
        assertThat(fin.status()).isEqualTo(RequestStatus.PAID);
        assertThat(fin.createdBy()).isEqualTo(PRIYA);
        assertThat(fin.decidedBy()).isEqualTo(ARJUN);
    }

    @Test
    @DisplayName("FR-10 · the audit trail of a paid request is exactly CREATED by Priya, APPROVED by Arjun, PAID by Neha; a rejected one is CREATED then REJECTED")
    void auditTrail() {
        RequestView paidOne = paid(HOUSING, "300.00");
        RequestView rejectedOne = pending(HOUSING, "20.00");
        service.rejectRequest(ARJUN, new RejectRequestInput(rejectedOne.id(), "Duplicate"));

        assertThat(auditEvents(paidOne.id())).containsExactly(
                event("CREATED", null, "PENDING", PRIYA),
                event("APPROVED", "PENDING", "APPROVED", ARJUN),
                event("PAID", "APPROVED", "PAID", NEHA));
        assertThat(auditEvents(rejectedOne.id())).containsExactly(
                event("CREATED", null, "PENDING", PRIYA),
                event("REJECTED", "PENDING", "REJECTED", ARJUN));
    }

    @Test
    @DisplayName("NFR-06 · both nonprofits run the same flow side by side; each sees only its own figures")
    void twoNonprofitsSideBySide() {
        paid(HOUSING, "300.00");
        String hr = service.createRequest(ANANYA, input(ADITYA, HR_UTILITY, "125.00")).request().id();
        service.approveRequest(VIKRAM, new ApproveRequestInput(hr));
        service.recordPayment(ISHA, new RecordPaymentInput(hr));

        FundingSummaryResult cb = service.getFundingSummary(PRIYA, new FundingSummaryInput(null));
        FundingSummaryResult hrSummary = service.getFundingSummary(ANANYA, new FundingSummaryInput(null));

        assertThat(cb.totals().paidCents()).isEqualTo(30_000);
        assertThat(hrSummary.totals().paidCents()).isEqualTo(12_500);
        assertThat(hrSummary.funds()).extracting(FundSummary::id).containsExactly(HR_UTILITY);
    }
}
