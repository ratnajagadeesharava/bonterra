package org.communitybridge.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.communitybridge.db.AuditRepository;
import org.communitybridge.db.Database;
import org.communitybridge.db.FundRepository;
import org.communitybridge.db.ParticipantRepository;
import org.communitybridge.db.PaymentRepository;
import org.communitybridge.db.RequestRepository;
import org.communitybridge.db.Times;
import org.communitybridge.domain.Action;
import org.communitybridge.domain.AssistanceRequest;
import org.communitybridge.domain.AuditAction;
import org.communitybridge.domain.AuditEvent;
import org.communitybridge.domain.CallerContext;
import org.communitybridge.domain.Category;
import org.communitybridge.domain.Fund;
import org.communitybridge.domain.FundStatus;
import org.communitybridge.domain.Outcome;
import org.communitybridge.domain.Payment;
import org.communitybridge.domain.RequestStatus;
import org.communitybridge.identity.StaffIdentityResolver;
import org.communitybridge.service.error.ServiceException;
import org.communitybridge.service.input.ApproveRequestInput;
import org.communitybridge.service.input.CreateRequestInput;
import org.communitybridge.service.input.FundingSummaryInput;
import org.communitybridge.service.input.GetParticipantInput;
import org.communitybridge.service.input.GetRequestInput;
import org.communitybridge.service.input.Inputs;
import org.communitybridge.service.input.ListParticipantsInput;
import org.communitybridge.service.input.ListRequestsInput;
import org.communitybridge.service.input.RecordPaymentInput;
import org.communitybridge.service.input.RejectRequestInput;
import org.communitybridge.service.result.ApproveRequestResult;
import org.communitybridge.service.result.CreateRequestResult;
import org.communitybridge.service.result.FundSummary;
import org.communitybridge.service.result.FundingSummaryResult;
import org.communitybridge.service.result.GetParticipantResult;
import org.communitybridge.service.result.GetRequestResult;
import org.communitybridge.service.result.ListFundsResult;
import org.communitybridge.service.result.ListParticipantsResult;
import org.communitybridge.service.result.ListRequestsResult;
import org.communitybridge.service.result.RecordPaymentResult;
import org.communitybridge.service.result.RejectRequestResult;
import org.communitybridge.service.result.WhoAmIResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The application service: one method per MCP tool. Authentication is part of every call:
 * the first argument is the caller's trusted staff ID (from launch config, never from tool input),
 * resolved on each call to a CallerContext. Then, in this order (spec: Validation, check order):
 * identity -> role -> input -> record lookup in own nonprofit -> state and funds.
 *
 * <p>Every failure is a {@link ServiceException} and leaves business state unchanged. Every write runs in one
 * BEGIN IMMEDIATE transaction ({@link Database#write}); anything unexpected is logged and becomes INTERNAL.
 */
public class AssistanceService {

    private static final Logger log = LoggerFactory.getLogger(AssistanceService.class);
    private static final Pattern CLIENT_REQUEST_ID = Pattern.compile(Inputs.CLIENT_REQUEST_ID_PATTERN);

    private final Database db;
    private final Clock clock;
    private final StaffIdentityResolver identities;

    public AssistanceService(Database database, Clock clock) {
        this.db = database;
        this.clock = clock;
        this.identities = new StaffIdentityResolver(database);
    }

    /**
     * Identity, then role, without doing anything else. The MCP layer calls this first so that UNAUTHENTICATED and
     * FORBIDDEN come before any JSON-shape VALIDATION_ERROR (spec check order). Each operation checks again itself.
     */
    public void checkAccess(String callerStaffId, Action action) {
        authenticate(callerStaffId, action);
    }

    // =================================================================== reads (any role)

    /** FR-11. */
    public WhoAmIResult whoami(String callerStaffId) {
        CallerContext me = authenticate(callerStaffId, Action.READ);
        return new WhoAmIResult(me.staffId(), me.name(), me.role(), me.nonprofitId(), me.nonprofitName());
    }

    /** FR-01. */
    public ListParticipantsResult listParticipants(String callerStaffId, ListParticipantsInput input) {
        CallerContext me = authenticate(callerStaffId, Action.READ);
        ListParticipantsInput in = input != null ? input : new ListParticipantsInput(null, null);
        InputValidator v = new InputValidator();
        String search = v.optionalText("search", in.search(), Inputs.SEARCH_MAX);
        int limit = v.intInRange("limit", in.limit(), Inputs.LIMIT_DEFAULT, Inputs.LIMIT_MIN, Inputs.LIMIT_MAX);
        v.throwIfInvalid();

        return read(c -> new ListParticipantsResult(ParticipantRepository.list(c, me.nonprofitId(), search, limit)
                .stream().map(Views::participant).toList()));
    }

    /** FR-01. */
    public GetParticipantResult getParticipant(String callerStaffId, GetParticipantInput input) {
        CallerContext me = authenticate(callerStaffId, Action.READ);
        InputValidator v = new InputValidator();
        String id = v.requiredId("participantId", input == null ? null : input.participantId());
        v.throwIfInvalid();

        return read(c -> new GetParticipantResult(Views.participant(ParticipantRepository.find(c, me.nonprofitId(), id)
                .orElseThrow(() -> ServiceException.notFound("Participant")))));
    }

    /** FR-02. */
    public ListFundsResult listFunds(String callerStaffId) {
        CallerContext me = authenticate(callerStaffId, Action.READ);
        return read(c -> new ListFundsResult(fundSummaries(c, me.nonprofitId(), null)));
    }

    /** FR-02. */
    public FundingSummaryResult getFundingSummary(String callerStaffId, FundingSummaryInput input) {
        CallerContext me = authenticate(callerStaffId, Action.READ);
        InputValidator v = new InputValidator();
        String fundId = v.optionalId("fundId", input == null ? null : input.fundId());
        v.throwIfInvalid();

        return read(c -> {
            List<FundSummary> funds = fundSummaries(c, me.nonprofitId(), fundId);
            if (fundId != null && funds.isEmpty()) {
                throw ServiceException.notFound("Fund");
            }
            return new FundingSummaryResult(funds, Views.totals(funds));
        });
    }

    /** FR-07. */
    public GetRequestResult getRequest(String callerStaffId, GetRequestInput input) {
        CallerContext me = authenticate(callerStaffId, Action.READ);
        String id = requestId(input == null ? null : input.requestId());

        return read(c -> new GetRequestResult(Views.request(findRequest(c, me.nonprofitId(), id))));
    }

    /** FR-07. */
    public ListRequestsResult listRequests(String callerStaffId, ListRequestsInput input) {
        CallerContext me = authenticate(callerStaffId, Action.READ);
        ListRequestsInput in = input != null ? input : new ListRequestsInput(null, null, null, null, null);
        InputValidator v = new InputValidator();
        RequestStatus status = v.optionalEnum("status", in.status(), RequestStatus.class);
        String fundId = v.optionalId("fundId", in.fundId());
        String participantId = v.optionalId("participantId", in.participantId());
        int limit = v.intInRange("limit", in.limit(), Inputs.LIMIT_DEFAULT, Inputs.LIMIT_MIN, Inputs.LIMIT_MAX);
        int offset = v.intInRange("offset", in.offset(), 0, 0, Integer.MAX_VALUE);
        v.throwIfInvalid();

        String np = me.nonprofitId();
        return read(c -> new ListRequestsResult(
                RequestRepository.list(c, np, status, fundId, participantId, limit, offset)
                        .stream().map(Views::request).toList(),
                Math.toIntExact(RequestRepository.count(c, np, status, fundId, participantId))));
    }

    // =================================================================== writes (one role each)

    /** FR-03, FR-12. Case worker. Reserves no money. */
    public CreateRequestResult createRequest(String callerStaffId, CreateRequestInput input) {
        CallerContext me = authenticate(callerStaffId, Action.CREATE_REQUEST);
        CreateRequestInput in = input != null ? input : new CreateRequestInput(null, null, null, null, null, null);
        InputValidator v = new InputValidator();
        String participantId = v.requiredId("participantId", in.participantId());
        String fundId = v.requiredId("fundId", in.fundId());
        Category category = v.requiredEnum("category", in.category(), Category.class);
        String purpose = v.requiredText("purpose", in.purpose(), Inputs.PURPOSE_MAX);
        long cents = v.amount(in.amount());
        String key = v.optionalPattern("clientRequestId", in.clientRequestId(), CLIENT_REQUEST_ID,
                "must be 1 to 64 characters, each a letter, digit, '-' or '_'");
        v.throwIfInvalid();

        String np = me.nonprofitId();
        return write(c -> {
            // A keyed retry is answered before any other check, so it succeeds even if the fund closed since.
            if (key != null) {
                Optional<AssistanceRequest> existing = RequestRepository.findByClientKey(c, np, me.staffId(), key);
                if (existing.isPresent()) {
                    AssistanceRequest e = existing.get();
                    boolean sameInput = e.participantId().equals(participantId) && e.fundId().equals(fundId)
                            && e.category() == category && e.purpose().equals(purpose) && e.amountCents() == cents;
                    if (!sameInput) {
                        throw ServiceException.idempotencyConflict(e.id());
                    }
                    return new CreateRequestResult(Outcome.ALREADY_APPLIED, Views.request(e));
                }
            }
            ParticipantRepository.find(c, np, participantId).orElseThrow(() -> ServiceException.notFound("Participant"));
            Fund fund = FundRepository.find(c, np, fundId).orElseThrow(() -> ServiceException.notFound("Fund"));
            if (fund.status() == FundStatus.CLOSED) {
                throw ServiceException.fundInactive();
            }

            Instant now = now();
            AssistanceRequest created = new AssistanceRequest(Ids.next("req"), np, participantId, fundId, category,
                    purpose, cents, RequestStatus.PENDING, key, me.staffId(), now, null, null, null);
            RequestRepository.insert(c, created);
            audit(c, me, created.id(), AuditAction.CREATED, null, RequestStatus.PENDING, now);
            return new CreateRequestResult(Outcome.APPLIED, Views.request(created));
        });
    }

    /** FR-04, FR-09. Program manager. Reserves the full amount atomically. */
    public ApproveRequestResult approveRequest(String callerStaffId, ApproveRequestInput input) {
        CallerContext me = authenticate(callerStaffId, Action.APPROVE_REQUEST);
        String id = requestId(input == null ? null : input.requestId());

        String np = me.nonprofitId();
        return write(c -> {
            AssistanceRequest r = findRequest(c, np, id);
            if (r.status() == RequestStatus.APPROVED || r.status() == RequestStatus.PAID) {
                return new ApproveRequestResult(Outcome.ALREADY_APPLIED, Views.request(r), fundSummary(c, np, r.fundId()));
            }
            if (r.status() != RequestStatus.PENDING) {
                throw ServiceException.invalidTransition(r.status());
            }
            Fund fund = FundRepository.find(c, np, r.fundId())
                    .orElseThrow(() -> new IllegalStateException("Fund of request " + id + " is missing"));
            if (fund.status() == FundStatus.CLOSED) {
                throw ServiceException.fundInactive();
            }
            if (FundRepository.reserve(c, np, fund.id(), r.amountCents()) == 0) {
                throw ServiceException.insufficientFunds(Views.available(fund), r.amountCents());
            }
            Instant now = now();
            if (RequestRepository.approve(c, np, id, me.staffId(), now) == 0) {
                throw new IllegalStateException("Request " + id + " left PENDING while the write lock was held");
            }
            audit(c, me, id, AuditAction.APPROVED, RequestStatus.PENDING, RequestStatus.APPROVED, now);
            return new ApproveRequestResult(Outcome.APPLIED, Views.request(findRequest(c, np, id)),
                    fundSummary(c, np, fund.id()));
        });
    }

    /** FR-05, FR-09. Program manager. Funding unchanged. */
    public RejectRequestResult rejectRequest(String callerStaffId, RejectRequestInput input) {
        CallerContext me = authenticate(callerStaffId, Action.REJECT_REQUEST);
        InputValidator v = new InputValidator();
        String id = v.requiredId("requestId", input == null ? null : input.requestId());
        String reason = v.optionalText("reason", input == null ? null : input.reason(), Inputs.REASON_MAX);
        v.throwIfInvalid();

        String np = me.nonprofitId();
        return write(c -> {
            AssistanceRequest r = findRequest(c, np, id);
            if (r.status() == RequestStatus.REJECTED) {
                return new RejectRequestResult(Outcome.ALREADY_APPLIED, Views.request(r));
            }
            if (r.status() != RequestStatus.PENDING) {
                throw ServiceException.invalidTransition(r.status());
            }
            Instant now = now();
            if (RequestRepository.reject(c, np, id, me.staffId(), now, reason) == 0) {
                throw new IllegalStateException("Request " + id + " left PENDING while the write lock was held");
            }
            audit(c, me, id, AuditAction.REJECTED, RequestStatus.PENDING, RequestStatus.REJECTED, now);
            return new RejectRequestResult(Outcome.APPLIED, Views.request(findRequest(c, np, id)));
        });
    }

    /** FR-06, FR-09. Finance. Moves the amount from committed to paid, once. */
    public RecordPaymentResult recordPayment(String callerStaffId, RecordPaymentInput input) {
        CallerContext me = authenticate(callerStaffId, Action.RECORD_PAYMENT);
        String id = requestId(input == null ? null : input.requestId());

        String np = me.nonprofitId();
        return write(c -> {
            AssistanceRequest r = findRequest(c, np, id);
            if (r.status() == RequestStatus.PAID) {
                Payment existing = PaymentRepository.findByRequest(c, np, id)
                        .orElseThrow(() -> new IllegalStateException("PAID request " + id + " has no payment"));
                return new RecordPaymentResult(Outcome.ALREADY_APPLIED, Views.payment(existing), Views.request(r),
                        fundSummary(c, np, r.fundId()));
            }
            if (r.status() != RequestStatus.APPROVED) {
                throw ServiceException.invalidTransition(r.status());
            }
            Instant now = now();
            if (RequestRepository.markPaid(c, np, id) == 0) {
                throw new IllegalStateException("Request " + id + " left APPROVED while the write lock was held");
            }
            Payment payment = new Payment(Ids.next("pay"), np, id, r.amountCents(), me.staffId(), now);
            PaymentRepository.insert(c, payment);
            if (FundRepository.settle(c, np, r.fundId(), r.amountCents()) == 0) {
                throw new IllegalStateException("Fund " + r.fundId() + " committed total does not cover request " + id);
            }
            audit(c, me, id, AuditAction.PAID, RequestStatus.APPROVED, RequestStatus.PAID, now);
            return new RecordPaymentResult(Outcome.APPLIED, Views.payment(payment), Views.request(findRequest(c, np, id)),
                    fundSummary(c, np, r.fundId()));
        });
    }

    // =================================================================== shared steps

    /** Identity, then role. Both before any input is read. */
    private CallerContext authenticate(String staffId, Action action) {
        CallerContext me = guard(() -> identities.resolve(staffId));
        Authorizer.authorize(me, action);
        return me;
    }

    private static String requestId(String value) {
        InputValidator v = new InputValidator();
        String id = v.requiredId("requestId", value);
        v.throwIfInvalid();
        return id;
    }

    private static AssistanceRequest findRequest(Connection c, String np, String id) throws SQLException {
        return RequestRepository.find(c, np, id).orElseThrow(() -> ServiceException.notFound("Request"));
    }

    private static List<FundSummary> fundSummaries(Connection c, String np, String fundId) throws SQLException {
        return FundRepository.figures(c, np, fundId).stream().map(Views::fund).toList();
    }

    private static FundSummary fundSummary(Connection c, String np, String fundId) throws SQLException {
        return fundSummaries(c, np, fundId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Fund " + fundId + " is missing"));
    }

    private void audit(Connection c, CallerContext me, String requestId, AuditAction action,
            RequestStatus from, RequestStatus to, Instant at) throws SQLException {
        AuditRepository.insert(c, new AuditEvent(Ids.next("evt"), me.nonprofitId(), requestId, me.staffId(),
                action, from, to, at));
    }

    private Instant now() {
        return Times.truncate(clock.instant());
    }

    private <T> T read(Database.Work<T> work) {
        return guard(() -> db.read(work));
    }

    private <T> T write(Database.Work<T> work) {
        return guard(() -> db.write(work));
    }

    /** Business failures pass through unchanged; anything else is logged (stderr only) and becomes INTERNAL. */
    private static <T> T guard(Supplier<T> call) {
        try {
            return call.get();
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Unexpected failure; transaction rolled back", e);
            throw ServiceException.internal();
        }
    }
}
