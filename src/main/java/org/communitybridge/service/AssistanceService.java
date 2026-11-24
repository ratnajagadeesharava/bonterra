package org.communitybridge.service;

import java.time.Clock;
import org.communitybridge.db.Database;
import org.communitybridge.service.input.ApproveRequestInput;
import org.communitybridge.service.input.CreateRequestInput;
import org.communitybridge.service.input.FundingSummaryInput;
import org.communitybridge.service.input.GetParticipantInput;
import org.communitybridge.service.input.GetRequestInput;
import org.communitybridge.service.input.ListParticipantsInput;
import org.communitybridge.service.input.ListRequestsInput;
import org.communitybridge.service.input.RecordPaymentInput;
import org.communitybridge.service.input.RejectRequestInput;
import org.communitybridge.service.result.ApproveRequestResult;
import org.communitybridge.service.result.CreateRequestResult;
import org.communitybridge.service.result.FundingSummaryResult;
import org.communitybridge.service.result.GetParticipantResult;
import org.communitybridge.service.result.GetRequestResult;
import org.communitybridge.service.result.ListFundsResult;
import org.communitybridge.service.result.ListParticipantsResult;
import org.communitybridge.service.result.ListRequestsResult;
import org.communitybridge.service.result.RecordPaymentResult;
import org.communitybridge.service.result.RejectRequestResult;
import org.communitybridge.service.result.WhoAmIResult;

/**
 * The application service: one method per MCP tool. Authentication is part of every call:
 * the first argument is the caller's trusted staff ID (from launch config, never from tool input),
 * resolved on each call to a CallerContext. Then, in this order (spec: Validation, check order):
 * identity -> role -> input -> record lookup in own nonprofit -> state and funds.
 *
 * <p>Every failure is a {@link org.communitybridge.service.error.ServiceException} and leaves
 * business state unchanged. Every write runs in one BEGIN IMMEDIATE transaction.
 *
 * <p>Stub: implemented in step 3.
 */
public class AssistanceService {

    public AssistanceService(Database database, Clock clock) {
    }

    /** FR-11. Any role. */
    public WhoAmIResult whoami(String callerStaffId) {
        throw notYet();
    }

    /** FR-01. Any role. */
    public ListParticipantsResult listParticipants(String callerStaffId, ListParticipantsInput input) {
        throw notYet();
    }

    /** FR-01. Any role. */
    public GetParticipantResult getParticipant(String callerStaffId, GetParticipantInput input) {
        throw notYet();
    }

    /** FR-02. Any role. */
    public ListFundsResult listFunds(String callerStaffId) {
        throw notYet();
    }

    /** FR-02. Any role. */
    public FundingSummaryResult getFundingSummary(String callerStaffId, FundingSummaryInput input) {
        throw notYet();
    }

    /** FR-03, FR-12. Case worker. Reserves no money. */
    public CreateRequestResult createRequest(String callerStaffId, CreateRequestInput input) {
        throw notYet();
    }

    /** FR-04, FR-09. Program manager. Reserves the full amount atomically. */
    public ApproveRequestResult approveRequest(String callerStaffId, ApproveRequestInput input) {
        throw notYet();
    }

    /** FR-05, FR-09. Program manager. Funding unchanged. */
    public RejectRequestResult rejectRequest(String callerStaffId, RejectRequestInput input) {
        throw notYet();
    }

    /** FR-06, FR-09. Finance. Moves the amount from committed to paid, once. */
    public RecordPaymentResult recordPayment(String callerStaffId, RecordPaymentInput input) {
        throw notYet();
    }

    /** FR-07. Any role. */
    public GetRequestResult getRequest(String callerStaffId, GetRequestInput input) {
        throw notYet();
    }

    /** FR-07. Any role. */
    public ListRequestsResult listRequests(String callerStaffId, ListRequestsInput input) {
        throw notYet();
    }

    private static UnsupportedOperationException notYet() {
        return new UnsupportedOperationException("Implemented in step 3");
    }
}
