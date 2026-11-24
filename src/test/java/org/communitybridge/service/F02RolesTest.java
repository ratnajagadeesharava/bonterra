package org.communitybridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.communitybridge.service.Fixtures.*;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.communitybridge.domain.ErrorCode;
import org.communitybridge.domain.Role;
import org.communitybridge.service.error.ServiceException;
import org.communitybridge.service.input.ApproveRequestInput;
import org.communitybridge.service.input.CreateRequestInput;
import org.communitybridge.service.input.RecordPaymentInput;
import org.communitybridge.service.input.RejectRequestInput;
import org.communitybridge.service.result.RequestView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Flow stage 2: what each role may do. Case workers create, program managers approve or reject,
 * finance pays, and all three can read. Role is checked before input and before any lookup.
 * Requirements: Roles section, AC-13, NFR-03.
 */
@DisplayName("2 · Roles")
class F02RolesTest extends ServiceTestBase {

    enum Write { CREATE, APPROVE, REJECT, PAY }

    /** Every write a role is NOT allowed to do, with the role that would be required. */
    static Stream<Arguments> forbiddenWrites() {
        return Stream.of(
                Arguments.of(PRIYA, "case worker", Write.APPROVE, Role.PROGRAM_MANAGER),
                Arguments.of(PRIYA, "case worker", Write.REJECT, Role.PROGRAM_MANAGER),
                Arguments.of(PRIYA, "case worker", Write.PAY, Role.FINANCE),
                Arguments.of(ARJUN, "program manager", Write.CREATE, Role.CASE_WORKER),
                Arguments.of(ARJUN, "program manager", Write.PAY, Role.FINANCE),
                Arguments.of(NEHA, "finance", Write.CREATE, Role.CASE_WORKER),
                Arguments.of(NEHA, "finance", Write.APPROVE, Role.PROGRAM_MANAGER),
                Arguments.of(NEHA, "finance", Write.REJECT, Role.PROGRAM_MANAGER),
                Arguments.of(VIKRAM, "Harbor Relief program manager", Write.CREATE, Role.CASE_WORKER),
                Arguments.of(ANANYA, "Harbor Relief case worker", Write.APPROVE, Role.PROGRAM_MANAGER),
                Arguments.of(ISHA, "Harbor Relief finance", Write.REJECT, Role.PROGRAM_MANAGER));
    }

    private Runnable write(Write w, String staffId, String requestId) {
        return switch (w) {
            case CREATE -> () -> service.createRequest(staffId, input(KAVYA, HOUSING, "100.00"));
            case APPROVE -> () -> service.approveRequest(staffId, new ApproveRequestInput(requestId));
            case REJECT -> () -> service.rejectRequest(staffId, new RejectRequestInput(requestId, null));
            case PAY -> () -> service.recordPayment(staffId, new RecordPaymentInput(requestId));
        };
    }

    @ParameterizedTest(name = "{1} tries {2} -> FORBIDDEN, requires {3}")
    @MethodSource("forbiddenWrites")
    @DisplayName("AC-13 · a role calling a write it does not own gets FORBIDDEN with requiredRole, on a real request, and nothing changes")
    void wrongRoleIsForbidden(String staffId, String who, Write w, Role required) {
        RequestView pending = pending(HOUSING, "100.00");
        RequestView approved = approved(HOUSING, "50.00");
        String target = w == Write.PAY ? approved.id() : pending.id();

        ServiceException e = expectErrorAndNoChange(ErrorCode.FORBIDDEN, write(w, staffId, target));

        assertThat(e.details()).isEqualTo(Map.of("requiredRole", required.name()));
    }

    @ParameterizedTest(name = "{1} tries {2} on a random ID -> same FORBIDDEN")
    @MethodSource("forbiddenWrites")
    @DisplayName("AC-13 · FORBIDDEN is identical for a real and a non-existent request ID, so a wrong role cannot probe which IDs exist")
    void forbiddenDoesNotRevealExistence(String staffId, String who, Write w, Role required) {
        RequestView real = pending(HOUSING, "100.00");

        ServiceException forReal = expectError(ErrorCode.FORBIDDEN, write(w, staffId, real.id()));
        ServiceException forRandom = expectError(ErrorCode.FORBIDDEN, write(w, staffId, NO_SUCH_REQUEST));

        assertThat(forReal.toBody()).isEqualTo(forRandom.toBody());
    }

    @Test
    @DisplayName("Check order · role is checked before input: Neha submitting an invalid request gets FORBIDDEN, not VALIDATION_ERROR")
    void roleIsCheckedBeforeInput() {
        expectErrorAndNoChange(ErrorCode.FORBIDDEN, () -> service.createRequest(NEHA,
                new CreateRequestInput(null, "", "FOOD", "", "abc", "bad id!")));
    }

    @Test
    @DisplayName("Check order · role is checked before lookup: Priya approving another nonprofit's request gets FORBIDDEN, not NOT_FOUND")
    void roleIsCheckedBeforeLookup() {
        String hrRequest = service.createRequest(ANANYA, input(ADITYA, HR_UTILITY, "10.00")).request().id();

        expectErrorAndNoChange(ErrorCode.FORBIDDEN, () -> service.approveRequest(PRIYA, new ApproveRequestInput(hrRequest)));
    }

    static Stream<Arguments> everyRoleEveryRead() {
        Stream.Builder<Arguments> all = Stream.builder();
        for (String staffId : new String[] {PRIYA, ARJUN, NEHA}) {
            for (Op op : F01AuthenticationTest.everyOperation()) {
                if (!op.name().matches("create_request|approve_request|reject_request|record_payment|get_request")) {
                    all.add(Arguments.of(staffId, op));
                }
            }
        }
        return all.build();
    }

    @ParameterizedTest(name = "{0} calls {1}")
    @MethodSource("everyRoleEveryRead")
    @DisplayName("Roles · all three roles can call every read operation in their own nonprofit")
    void everyRoleCanRead(String staffId, Op read) {
        read.call().accept(service, staffId); // must not throw
    }

    @Test
    @DisplayName("Roles · all three roles can read a request by ID (get_request) in their own nonprofit")
    void everyRoleCanGetRequest() {
        RequestView r = pending(HOUSING, "100.00");
        Function<String, RequestView> get =
                staff -> service.getRequest(staff, new org.communitybridge.service.input.GetRequestInput(r.id())).request();

        assertThat(get.apply(PRIYA)).isEqualTo(r);
        assertThat(get.apply(ARJUN)).isEqualTo(r);
        assertThat(get.apply(NEHA)).isEqualTo(r);
    }

    @Test
    @DisplayName("Roles · each role can do its own write: Priya creates, Arjun approves, Neha pays; Arjun can also reject")
    void eachRoleCanDoItsOwnWrite() {
        RequestView created = service.createRequest(PRIYA, input(KAVYA, HOUSING, "100.00")).request();
        service.approveRequest(ARJUN, new ApproveRequestInput(created.id()));
        service.recordPayment(NEHA, new RecordPaymentInput(created.id()));
        RequestView other = pending(HOUSING, "20.00");
        service.rejectRequest(ARJUN, new RejectRequestInput(other.id(), null));

        assertThat(count("payments")).isEqualTo(1);
    }
}
