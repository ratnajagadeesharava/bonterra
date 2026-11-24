package org.communitybridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.communitybridge.service.Fixtures.*;

import java.util.List;
import org.communitybridge.domain.ErrorCode;
import org.communitybridge.domain.FundStatus;
import org.communitybridge.service.error.FieldError;
import org.communitybridge.service.error.ServiceException;
import org.communitybridge.service.input.ApproveRequestInput;
import org.communitybridge.service.input.FundingSummaryInput;
import org.communitybridge.service.input.GetParticipantInput;
import org.communitybridge.service.input.GetRequestInput;
import org.communitybridge.service.input.ListParticipantsInput;
import org.communitybridge.service.input.ListRequestsInput;
import org.communitybridge.service.input.RecordPaymentInput;
import org.communitybridge.service.input.RejectRequestInput;
import org.communitybridge.service.result.FundSummary;
import org.communitybridge.service.result.FundingSummaryResult;
import org.communitybridge.service.result.FundingTotals;
import org.communitybridge.service.result.ListRequestsResult;
import org.communitybridge.service.result.ParticipantView;
import org.communitybridge.service.result.RequestView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Flow stage 3: looking things up, and never seeing or touching another nonprofit's records.
 * Another nonprofit's record behaves exactly like one that does not exist.
 * Requirements: FR-01, FR-02, FR-07, NFR-06, AC-14, AC-15.
 */
@DisplayName("3 · Reads and tenant isolation")
class F03ReadsAndTenantIsolationTest extends ServiceTestBase {

    // ------------------------------------------------------------ participants (FR-01)

    @Test
    @DisplayName("FR-01 · Priya lists exactly Community Bridge's 3 participants, ordered by name; no Harbor Relief participant appears")
    void listParticipantsShowsOnlyOwnNonprofit() {
        List<ParticipantView> list = service.listParticipants(PRIYA, new ListParticipantsInput(null, null)).participants();

        assertThat(list).extracting(ParticipantView::name).containsExactly("Kavya Reddy", "Rohan Mehta", "Sneha Iyer");
    }

    @Test
    @DisplayName("FR-01 · Ananya (Harbor Relief) lists exactly her nonprofit's 2 participants")
    void listParticipantsOtherNonprofit() {
        List<ParticipantView> list = service.listParticipants(ANANYA, new ListParticipantsInput(null, null)).participants();

        assertThat(list).extracting(ParticipantView::id).containsExactly(ADITYA, MEGHANA);
    }

    @Test
    @DisplayName("FR-01 · search is a case-insensitive name match: 'kAv' finds only Kavya; 'zzz' finds nobody")
    void listParticipantsSearch() {
        assertThat(service.listParticipants(PRIYA, new ListParticipantsInput("kAv", null)).participants())
                .extracting(ParticipantView::id).containsExactly(KAVYA);
        assertThat(service.listParticipants(PRIYA, new ListParticipantsInput("zzz", null)).participants()).isEmpty();
    }

    @Test
    @DisplayName("NFR-06 · searching for another nonprofit's participant by name ('Aditya') returns nothing")
    void searchDoesNotCrossNonprofits() {
        assertThat(service.listParticipants(PRIYA, new ListParticipantsInput("Aditya", null)).participants()).isEmpty();
    }

    @Test
    @DisplayName("FR-01 · limit 2 returns the first 2 participants by name")
    void listParticipantsLimit() {
        assertThat(service.listParticipants(PRIYA, new ListParticipantsInput(null, 2)).participants())
                .extracting(ParticipantView::id).containsExactly(KAVYA, ROHAN);
    }

    @ParameterizedTest(name = "limit {0} is refused")
    @ValueSource(ints = {0, -1, 101})
    @DisplayName("Validation · list_participants limit outside 1..100 is VALIDATION_ERROR on field 'limit'")
    void listParticipantsBadLimit(int limit) {
        ServiceException e = expectError(ErrorCode.VALIDATION_ERROR,
                () -> service.listParticipants(PRIYA, new ListParticipantsInput(null, limit)));
        assertThat(fields(e)).containsExactly("limit");
    }

    @Test
    @DisplayName("Validation · a search longer than 100 characters is VALIDATION_ERROR on field 'search'")
    void listParticipantsSearchTooLong() {
        ServiceException e = expectError(ErrorCode.VALIDATION_ERROR,
                () -> service.listParticipants(PRIYA, new ListParticipantsInput("a".repeat(101), null)));
        assertThat(fields(e)).containsExactly("search");
    }

    @Test
    @DisplayName("FR-01 · get_participant returns Kavya's details: Kavya Reddy, household of 3, Hyderabad (no nonprofit ID exposed)")
    void getParticipantOwn() {
        assertThat(service.getParticipant(ARJUN, new GetParticipantInput(KAVYA)).participant())
                .isEqualTo(new ParticipantView(KAVYA, "Kavya Reddy", 3, "Hyderabad"));
    }

    @Test
    @DisplayName("NFR-06 · Priya getting a Harbor Relief participant gets NOT_FOUND, identical to an ID that does not exist")
    void getParticipantOtherNonprofitLooksMissing() {
        ServiceException other = expectError(ErrorCode.NOT_FOUND,
                () -> service.getParticipant(PRIYA, new GetParticipantInput(ADITYA)));
        ServiceException missing = expectError(ErrorCode.NOT_FOUND,
                () -> service.getParticipant(PRIYA, new GetParticipantInput("par_does_not_exist")));

        assertThat(other.toBody()).isEqualTo(missing.toBody());
    }

    @Test
    @DisplayName("Validation · get_participant with no participantId is VALIDATION_ERROR on field 'participantId'")
    void getParticipantMissingId() {
        ServiceException e = expectError(ErrorCode.VALIDATION_ERROR,
                () -> service.getParticipant(PRIYA, new GetParticipantInput(null)));
        assertThat(fields(e)).containsExactly("participantId");
    }

    // ------------------------------------------------------------ funds (FR-02)

    @Test
    @DisplayName("FR-02 · list_funds shows Community Bridge's 3 funds (ordered by id) with their seed balances; no Harbor Relief fund")
    void listFundsShowsOwnFundsWithBalances() {
        List<FundSummary> funds = service.listFunds(NEHA).funds();

        assertThat(funds).extracting(FundSummary::id).containsExactly(CLOSED, HOUSING, SMALL);
        assertThat(funds).extracting(FundSummary::allocationCents).containsExactly(CLOSED_CENTS, HOUSING_CENTS, SMALL_CENTS);
        assertThat(funds).extracting(FundSummary::status)
                .containsExactly(FundStatus.CLOSED, FundStatus.ACTIVE, FundStatus.ACTIVE);
    }

    @Test
    @DisplayName("FR-02 · a fresh Housing Stability summary is exact in cents and as text: $10,000.00 allocated, $0.00 committed/paid/pending, $10,000.00 available, USD")
    void fundSummaryShape() {
        assertThat(fund(HOUSING)).isEqualTo(new FundSummary(
                HOUSING, "Housing Stability", FundStatus.ACTIVE, "USD",
                1_000_000, 0, 0, 1_000_000, 0, 0,
                "10000.00", "0.00", "0.00", "10000.00", "0.00"));
    }

    @Test
    @DisplayName("FR-02 · get_funding_summary with no fundId returns every own fund plus totals across them ($11,500.00 allocated, all available)")
    void fundingSummaryAllFundsWithTotals() {
        FundingSummaryResult all = service.getFundingSummary(ARJUN, new FundingSummaryInput(null));

        assertThat(all.funds()).hasSize(3);
        assertThat(all.totals()).isEqualTo(new FundingTotals("USD",
                1_150_000, 0, 0, 1_150_000, 0,
                "11500.00", "0.00", "0.00", "11500.00", "0.00"));
    }

    @Test
    @DisplayName("FR-02 · totals add up after activity: approve $300 on Housing, pay $100 on Small, $50 pending on Small")
    void fundingTotalsAfterActivity() {
        approved(HOUSING, "300.00");
        paid(SMALL, "100.00");
        pending(SMALL, "50.00");

        FundingTotals t = service.getFundingSummary(ARJUN, new FundingSummaryInput(null)).totals();

        assertThat(t.committedCents()).isEqualTo(30_000);
        assertThat(t.paidCents()).isEqualTo(10_000);
        assertThat(t.pendingCents()).isEqualTo(5_000);
        assertThat(t.availableCents()).isEqualTo(1_150_000 - 30_000 - 10_000);
        assertThat(t.available()).isEqualTo("11100.00");
    }

    @Test
    @DisplayName("NFR-06 · Priya asking for the Harbor Relief fund's summary gets NOT_FOUND, identical to a fund that does not exist")
    void fundSummaryOtherNonprofitLooksMissing() {
        ServiceException other = expectError(ErrorCode.NOT_FOUND,
                () -> service.getFundingSummary(PRIYA, new FundingSummaryInput(HR_UTILITY)));
        ServiceException missing = expectError(ErrorCode.NOT_FOUND,
                () -> service.getFundingSummary(PRIYA, new FundingSummaryInput("fnd_does_not_exist")));

        assertThat(other.toBody()).isEqualTo(missing.toBody());
    }

    // ------------------------------------------------------------ requests (FR-07, NFR-06)

    @Test
    @DisplayName("AC-14 · Ananya (Harbor Relief) getting a Community Bridge request gets NOT_FOUND, identical to a random ID")
    void getRequestOtherNonprofitLooksMissing() {
        RequestView cb = pending(HOUSING, "100.00");

        ServiceException other = expectError(ErrorCode.NOT_FOUND,
                () -> service.getRequest(ANANYA, new GetRequestInput(cb.id())));
        ServiceException missing = expectError(ErrorCode.NOT_FOUND,
                () -> service.getRequest(ANANYA, new GetRequestInput(NO_SUCH_REQUEST)));

        assertThat(other.toBody()).isEqualTo(missing.toBody());
    }

    @Test
    @DisplayName("AC-14 · Vikram (Harbor Relief PM) approving a Community Bridge request gets NOT_FOUND; it stays PENDING and no money moves")
    void approveOtherNonprofitRequestIsNotFound() {
        RequestView cb = pending(SMALL, "100.00");

        expectErrorAndNoChange(ErrorCode.NOT_FOUND, () -> service.approveRequest(VIKRAM, new ApproveRequestInput(cb.id())));
    }

    @Test
    @DisplayName("AC-14 · Vikram rejecting a Community Bridge request gets NOT_FOUND and nothing changes")
    void rejectOtherNonprofitRequestIsNotFound() {
        RequestView cb = pending(SMALL, "100.00");

        expectErrorAndNoChange(ErrorCode.NOT_FOUND,
                () -> service.rejectRequest(VIKRAM, new RejectRequestInput(cb.id(), null)));
    }

    @Test
    @DisplayName("AC-14 · Isha (Harbor Relief finance) paying an approved Community Bridge request gets NOT_FOUND and nothing changes")
    void payOtherNonprofitRequestIsNotFound() {
        RequestView cb = approved(SMALL, "100.00");

        expectErrorAndNoChange(ErrorCode.NOT_FOUND, () -> service.recordPayment(ISHA, new RecordPaymentInput(cb.id())));
    }

    @Test
    @DisplayName("FR-07 · get_request returns the full request view; Ananya's list_requests never includes Community Bridge requests")
    void listRequestsShowsOnlyOwnNonprofit() {
        RequestView cb = pending(HOUSING, "100.00");
        RequestView hr = service.createRequest(ANANYA, input(ADITYA, HR_UTILITY, "40.00")).request();

        assertThat(service.getRequest(ARJUN, new GetRequestInput(cb.id())).request()).isEqualTo(cb);
        ListRequestsResult hrList = service.listRequests(ANANYA, new ListRequestsInput(null, null, null, null, null));
        assertThat(hrList.requests()).extracting(RequestView::id).containsExactly(hr.id());
        assertThat(hrList.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-07 · list_requests filters by status, by fund and by participant, combined with AND; total counts matches")
    void listRequestsFilters() {
        RequestView p1 = pending(HOUSING, "10.00");
        RequestView a1 = approved(HOUSING, "20.00");
        RequestView r1 = rejected(SMALL, "30.00");
        RequestView rohan = service.createRequest(PRIYA, input(ROHAN, SMALL, "40.00")).request();

        assertThat(list(new ListRequestsInput("PENDING", null, null, null, null))).containsExactlyInAnyOrder(p1.id(), rohan.id());
        assertThat(list(new ListRequestsInput(null, HOUSING, null, null, null))).containsExactlyInAnyOrder(p1.id(), a1.id());
        assertThat(list(new ListRequestsInput(null, null, ROHAN, null, null))).containsExactly(rohan.id());
        assertThat(list(new ListRequestsInput("REJECTED", SMALL, KAVYA, null, null))).containsExactly(r1.id());
        assertThat(list(new ListRequestsInput("PAID", null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("FR-07 · list_requests pages with limit and offset in (createdAt, id) order, while total counts every match")
    void listRequestsPaging() {
        for (int i = 1; i <= 5; i++) {
            pending(HOUSING, i + ".00");
        }
        List<String> allIds = list(new ListRequestsInput(null, null, null, null, null));

        ListRequestsResult page = service.listRequests(PRIYA, new ListRequestsInput(null, null, null, 2, 2));

        assertThat(allIds).hasSize(5).isSorted(); // same createdAt (fixed clock), so ordered by id
        assertThat(page.requests()).extracting(RequestView::id).containsExactlyElementsOf(allIds.subList(2, 4));
        assertThat(page.total()).isEqualTo(5);
    }

    @Test
    @DisplayName("NFR-06 · filtering list_requests by another nonprofit's fund returns an empty list (not an error that reveals the fund exists)")
    void listRequestsOtherNonprofitFundIsEmpty() {
        service.createRequest(ANANYA, input(ADITYA, HR_UTILITY, "40.00"));

        ListRequestsResult r = service.listRequests(PRIYA, new ListRequestsInput(null, HR_UTILITY, null, null, null));

        assertThat(r.requests()).isEmpty();
        assertThat(r.total()).isZero();
    }

    @Test
    @DisplayName("Validation · list_requests with status 'DONE', limit 0 and offset -1 is VALIDATION_ERROR naming all three fields")
    void listRequestsBadInput() {
        ServiceException e = expectError(ErrorCode.VALIDATION_ERROR,
                () -> service.listRequests(PRIYA, new ListRequestsInput("DONE", null, null, 0, -1)));

        assertThat(fields(e)).containsExactlyInAnyOrder("status", "limit", "offset");
    }

    // ------------------------------------------------------------ helpers

    private List<String> list(ListRequestsInput in) {
        return service.listRequests(PRIYA, in).requests().stream().map(RequestView::id).toList();
    }

    @SuppressWarnings("unchecked")
    static List<String> fields(ServiceException e) {
        return ((List<FieldError>) e.details().get("fields")).stream().map(FieldError::field).toList();
    }
}
