package org.communitybridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.communitybridge.service.Fixtures.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.communitybridge.domain.ErrorCode;
import org.communitybridge.domain.Outcome;
import org.communitybridge.domain.RequestStatus;
import org.communitybridge.service.error.ServiceException;
import org.communitybridge.service.input.ApproveRequestInput;
import org.communitybridge.service.input.CreateRequestInput;
import org.communitybridge.service.input.RecordPaymentInput;
import org.communitybridge.service.input.RejectRequestInput;
import org.communitybridge.service.result.ApproveRequestResult;
import org.communitybridge.service.result.CreateRequestResult;
import org.communitybridge.service.result.RecordPaymentResult;
import org.communitybridge.service.result.RejectRequestResult;
import org.communitybridge.service.result.RequestView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Flow stage 8: repeating an action that already happened is safe (no second financial effect),
 * and every move outside the three permitted transitions is refused without changing anything.
 * Requirements: FR-09, FR-12, AC-10, AC-11, AC-12, AC-23, AC-24, NFR-03.
 */
@DisplayName("8 · Retries and invalid transitions")
class F08RetriesAndTransitionsTest extends ServiceTestBase {

    private static final String KEY = "kavya-march-rent";

    private ApproveRequestResult approve(String id) {
        return service.approveRequest(ARJUN, new ApproveRequestInput(id));
    }

    private RejectRequestResult reject(String id, String reason) {
        return service.rejectRequest(ARJUN, new RejectRequestInput(id, reason));
    }

    private RecordPaymentResult pay(String id) {
        return service.recordPayment(NEHA, new RecordPaymentInput(id));
    }

    // ------------------------------------------------------------ safe repeats (FR-09)

    @Test
    @DisplayName("AC-10 · Arjun approves the same request twice: second is ALREADY_APPLIED with the same request; committed still $300.00; one APPROVED audit event; the repeat writes nothing")
    void approveTwice() {
        RequestView r = pending(HOUSING, "300.00");
        ApproveRequestResult first = approve(r.id());
        Map<String, List<Map<String, Object>>> before = snapshot();

        ApproveRequestResult second = approve(r.id());

        assertThat(second.outcome()).isEqualTo(Outcome.ALREADY_APPLIED);
        assertThat(second.request()).isEqualTo(first.request());
        assertThat(second.fund().committedCents()).isEqualTo(30_000);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(auditEvents(r.id())).filteredOn(e -> "APPROVED".equals(e.get("action"))).hasSize(1);
    }

    @Test
    @DisplayName("FR-09 · approving a request that is already PAID is ALREADY_APPLIED and changes nothing (the approval did happen)")
    void approveAfterPaid() {
        RequestView r = paid(HOUSING, "300.00");
        Map<String, List<Map<String, Object>>> before = snapshot();

        ApproveRequestResult again = approve(r.id());

        assertThat(again.outcome()).isEqualTo(Outcome.ALREADY_APPLIED);
        assertThat(again.request().status()).isEqualTo(RequestStatus.PAID);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("FR-09 · rejecting twice is ALREADY_APPLIED; the first reason is kept even if the retry sends a different one; nothing changes")
    void rejectTwice() {
        RequestView r = pending(HOUSING, "200.00");
        reject(r.id(), "Duplicate");
        Map<String, List<Map<String, Object>>> before = snapshot();

        RejectRequestResult again = reject(r.id(), "Changed my mind");

        assertThat(again.outcome()).isEqualTo(Outcome.ALREADY_APPLIED);
        assertThat(again.request().rejectionReason()).isEqualTo("Duplicate");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-11 · Neha pays twice: second is ALREADY_APPLIED with the same payment ID; paid still $300.00; one payment row; nothing changes")
    void payTwice() {
        RequestView r = approved(HOUSING, "300.00");
        RecordPaymentResult first = pay(r.id());
        Map<String, List<Map<String, Object>>> before = snapshot();

        RecordPaymentResult second = pay(r.id());

        assertThat(second.outcome()).isEqualTo(Outcome.ALREADY_APPLIED);
        assertThat(second.payment()).isEqualTo(first.payment());
        assertThat(second.fund().paidCents()).isEqualTo(30_000);
        assertThat(count("payments")).isEqualTo(1);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("FR-09 · a repeat still checks the role first: Priya re-approving an approved request is FORBIDDEN, not ALREADY_APPLIED")
    void repeatStillChecksRole() {
        RequestView r = approved(HOUSING, "300.00");

        expectErrorAndNoChange(ErrorCode.FORBIDDEN, () -> service.approveRequest(PRIYA, new ApproveRequestInput(r.id())));
    }

    @Test
    @DisplayName("FR-09 · a repeat still checks the nonprofit: Vikram re-approving Community Bridge's approved request is NOT_FOUND")
    void repeatStillChecksNonprofit() {
        RequestView r = approved(HOUSING, "300.00");

        expectErrorAndNoChange(ErrorCode.NOT_FOUND, () -> service.approveRequest(VIKRAM, new ApproveRequestInput(r.id())));
    }

    // ------------------------------------------------------------ invalid transitions (AC-12)

    enum Move { APPROVE, REJECT, PAY }

    static Stream<Arguments> invalidMoves() {
        return Stream.of(
                Arguments.of(RequestStatus.REJECTED, Move.APPROVE),
                Arguments.of(RequestStatus.APPROVED, Move.REJECT),
                Arguments.of(RequestStatus.PAID, Move.REJECT),
                Arguments.of(RequestStatus.PENDING, Move.PAY),
                Arguments.of(RequestStatus.REJECTED, Move.PAY));
    }

    @ParameterizedTest(name = "{1} on a {0} request -> INVALID_TRANSITION")
    @MethodSource("invalidMoves")
    @DisplayName("AC-12 · every move outside PENDING->APPROVED, PENDING->REJECTED, APPROVED->PAID is INVALID_TRANSITION with currentStatus; nothing changes")
    void invalidTransition(RequestStatus from, Move move) {
        RequestView r = switch (from) {
            case PENDING -> pending(SMALL, "100.00");
            case APPROVED -> approved(SMALL, "100.00");
            case REJECTED -> rejected(SMALL, "100.00");
            case PAID -> paid(SMALL, "100.00");
        };
        Runnable call = switch (move) {
            case APPROVE -> () -> approve(r.id());
            case REJECT -> () -> reject(r.id(), null);
            case PAY -> () -> pay(r.id());
        };

        ServiceException e = expectErrorAndNoChange(ErrorCode.INVALID_TRANSITION, call);

        assertThat(e.details()).isEqualTo(Map.of("currentStatus", from.name()));
    }

    // ------------------------------------------------------------ idempotent create (FR-12)

    private CreateRequestInput keyed(String participant, String fund, String category, String purpose, String amount) {
        return new CreateRequestInput(participant, fund, category, purpose, amount, KEY);
    }

    private CreateRequestInput keyed() {
        return keyed(KAVYA, HOUSING, "RENT", "March rent", "300.00");
    }

    @Test
    @DisplayName("AC-23 · Priya sends the same create twice with clientRequestId 'kavya-march-rent': second is ALREADY_APPLIED with the same request; one request, one CREATED event")
    void keyedCreateTwice() {
        CreateRequestResult first = service.createRequest(PRIYA, keyed());
        Map<String, List<Map<String, Object>>> before = snapshot();

        CreateRequestResult second = service.createRequest(PRIYA, keyed());

        assertThat(first.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(second.outcome()).isEqualTo(Outcome.ALREADY_APPLIED);
        assertThat(second.request()).isEqualTo(first.request());
        assertThat(snapshot()).isEqualTo(before);
        assertThat(count("requests")).isEqualTo(1);
        assertThat(count("audit_events")).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-12 · 'same input' compares meaning, not spelling: purpose '  March rent ' and amount '300' match the original ('March rent', '300.00')")
    void keyedCreateComparesNormalizedInput() {
        CreateRequestResult first = service.createRequest(PRIYA, keyed());

        CreateRequestResult again = service.createRequest(PRIYA, keyed(KAVYA, HOUSING, "RENT", "  March rent ", "300"));

        assertThat(again.outcome()).isEqualTo(Outcome.ALREADY_APPLIED);
        assertThat(again.request().id()).isEqualTo(first.request().id());
    }

    static Stream<Arguments> conflictingInputs() {
        return Stream.of(
                Arguments.of("different amount ($350.00)", new CreateRequestInput(KAVYA, HOUSING, "RENT", "March rent", "350.00", KEY)),
                Arguments.of("different participant (Rohan)", new CreateRequestInput(ROHAN, HOUSING, "RENT", "March rent", "300.00", KEY)),
                Arguments.of("different fund (Small)", new CreateRequestInput(KAVYA, SMALL, "RENT", "March rent", "300.00", KEY)),
                Arguments.of("different category (UTILITIES)", new CreateRequestInput(KAVYA, HOUSING, "UTILITIES", "March rent", "300.00", KEY)),
                Arguments.of("different purpose", new CreateRequestInput(KAVYA, HOUSING, "RENT", "April rent", "300.00", KEY)));
    }

    @ParameterizedTest(name = "reused key with {0} -> IDEMPOTENCY_CONFLICT")
    @MethodSource("conflictingInputs")
    @DisplayName("AC-24 · reusing a clientRequestId with any different field is IDEMPOTENCY_CONFLICT naming existingRequestId; nothing changes")
    void keyedCreateConflict(String what, CreateRequestInput changed) {
        String existing = service.createRequest(PRIYA, keyed()).request().id();

        ServiceException e = expectErrorAndNoChange(ErrorCode.IDEMPOTENCY_CONFLICT, () -> service.createRequest(PRIYA, changed));

        assertThat(e.details()).isEqualTo(Map.of("existingRequestId", existing));
    }

    @Test
    @DisplayName("FR-12 · a keyed retry after the request was approved returns ALREADY_APPLIED with its current state (APPROVED)")
    void keyedRetryReturnsCurrentState() {
        String id = service.createRequest(PRIYA, keyed()).request().id();
        approve(id);

        CreateRequestResult again = service.createRequest(PRIYA, keyed());

        assertThat(again.outcome()).isEqualTo(Outcome.ALREADY_APPLIED);
        assertThat(again.request().status()).isEqualTo(RequestStatus.APPROVED);
    }

    @Test
    @DisplayName("FR-12 · a keyed retry still succeeds as ALREADY_APPLIED after the fund was closed (the original was accepted while it was open)")
    void keyedRetryAfterFundClosed() {
        service.createRequest(PRIYA, keyed());
        execute("UPDATE funds SET status = 'CLOSED' WHERE id = ?", HOUSING);

        assertThat(service.createRequest(PRIYA, keyed()).outcome()).isEqualTo(Outcome.ALREADY_APPLIED);
    }

    @Test
    @DisplayName("FR-12 · keys belong to the caller: Ananya (Harbor Relief) using the same key text creates her own new request")
    void keysAreScopedToCaller() {
        service.createRequest(PRIYA, keyed());

        CreateRequestResult hr = service.createRequest(ANANYA, keyed(ADITYA, HR_UTILITY, "RENT", "March rent", "300.00"));

        assertThat(hr.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(count("requests")).isEqualTo(2);
    }

    @Test
    @DisplayName("Assumption · without a clientRequestId, two identical submissions create two separate PENDING requests")
    void unkeyedDuplicatesAreSeparate() {
        RequestView a = pending(HOUSING, "300.00");
        RequestView b = pending(HOUSING, "300.00");

        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(count("requests")).isEqualTo(2);
    }
}
