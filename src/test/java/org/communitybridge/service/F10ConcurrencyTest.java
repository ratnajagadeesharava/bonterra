package org.communitybridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.communitybridge.service.Fixtures.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.communitybridge.db.Database;
import org.communitybridge.domain.Outcome;
import org.communitybridge.service.error.ServiceException;
import org.communitybridge.service.input.ApproveRequestInput;
import org.communitybridge.service.input.CreateRequestInput;
import org.communitybridge.service.input.RecordPaymentInput;
import org.communitybridge.service.result.FundSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Flow stage 10: competing calls. Each worker opens its own {@link Database} on the same file, so it has
 * its own connections, exactly like separate server processes. All workers are released at once by a latch.
 *
 * <p>Manual check, done once and recorded in the time log (not part of the suite): remove the
 * "available >= amount" guard from the approval UPDATE, run {@link #competingApprovalsNeverOverspend()},
 * and confirm it fails (the database CHECK then refuses the overspend, so calls fail with INTERNAL instead
 * of INSUFFICIENT_FUNDS). That proves the test can see a broken guard.
 *
 * <p>Requirements: NFR-02, AC-08 (thread gate), FR-09, FR-12.
 */
@DisplayName("10 · Concurrency")
class F10ConcurrencyTest extends ServiceTestBase {

    private static final int WORKERS = 20;

    /** Runs one task per worker, all started together, each with its own Database on the shared file. */
    private <T> List<String> race(int workers, Function<AssistanceService, T> task, Function<T, String> describe)
            throws Exception {
        List<Database> dbs = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            CountDownLatch ready = new CountDownLatch(workers);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                Database own = Database.open(dbFile);
                dbs.add(own);
                AssistanceService s = newService(own);
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return describe.apply(task.apply(s));
                    } catch (ServiceException e) {
                        return e.code().name();
                    }
                }));
            }
            ready.await();
            go.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : results) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
            dbs.forEach(Database::close);
        }
    }

    private static Map<String, Long> tally(List<String> outcomes) {
        return outcomes.stream().collect(Collectors.groupingBy(o -> o, Collectors.counting()));
    }

    @RepeatedTest(value = 10, name = "run {currentRepetition} of {totalRepetitions}")
    @Timeout(60)
    @DisplayName("AC-08 · 20 workers approve 20 different $100.00 requests on the $500.00 fund at once: exactly 5 APPLIED, 15 INSUFFICIENT_FUNDS; committed exactly $500.00; counters equal request sums")
    void competingApprovalsNeverOverspend() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < WORKERS; i++) {
            ids.add(pending(SMALL, "100.00").id());
        }
        java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();

        List<String> outcomes = race(WORKERS,
                s -> s.approveRequest(ARJUN, new ApproveRequestInput(ids.get(next.getAndIncrement()))),
                r -> r.outcome().name());

        assertThat(tally(outcomes)).isEqualTo(Map.of("APPLIED", 5L, "INSUFFICIENT_FUNDS", 15L));
        FundSummary f = fund(SMALL);
        assertThat(f.committedCents()).isEqualTo(50_000);
        assertThat(f.availableCents()).isZero();
        assertThat(count("requests WHERE status = 'APPROVED'")).isEqualTo(5);
        assertThat(count("audit_events WHERE action = 'APPROVED'")).isEqualTo(5);
    }

    @Test
    @Timeout(60)
    @DisplayName("FR-09 · 10 workers approve the SAME request at once: exactly 1 APPLIED, 9 ALREADY_APPLIED; committed $100.00 once; one APPROVED audit event")
    void sameApprovalRacedIsAppliedOnce() throws Exception {
        String id = pending(SMALL, "100.00").id();

        List<String> outcomes = race(10, s -> s.approveRequest(ARJUN, new ApproveRequestInput(id)), r -> r.outcome().name());

        assertThat(tally(outcomes)).isEqualTo(Map.of(Outcome.APPLIED.name(), 1L, Outcome.ALREADY_APPLIED.name(), 9L));
        assertThat(fund(SMALL).committedCents()).isEqualTo(10_000);
        assertThat(auditEvents(id)).filteredOn(e -> "APPROVED".equals(e.get("action"))).hasSize(1);
    }

    @Test
    @Timeout(60)
    @DisplayName("FR-09 · 10 workers pay the SAME approved request at once: exactly 1 APPLIED, 9 ALREADY_APPLIED, all with the same payment ID; one payment row; paid $100.00")
    void samePaymentRacedIsRecordedOnce() throws Exception {
        String id = approved(SMALL, "100.00").id();

        List<String> outcomes = race(10, s -> s.recordPayment(NEHA, new RecordPaymentInput(id)),
                r -> r.outcome().name() + ":" + r.payment().id());

        assertThat(outcomes.stream().map(o -> o.split(":")[1]).distinct()).hasSize(1);
        assertThat(tally(outcomes.stream().map(o -> o.split(":")[0]).toList()))
                .isEqualTo(Map.of("APPLIED", 1L, "ALREADY_APPLIED", 9L));
        assertThat(count("payments")).isEqualTo(1);
        FundSummary f = fund(SMALL);
        assertThat(f.paidCents()).isEqualTo(10_000);
        assertThat(f.committedCents()).isZero();
    }

    @Test
    @Timeout(60)
    @DisplayName("FR-12 · 10 workers send the same keyed create at once: exactly 1 APPLIED, 9 ALREADY_APPLIED, all the same request; one request row")
    void sameKeyedCreateRacedCreatesOne() throws Exception {
        CreateRequestInput in = new CreateRequestInput(KAVYA, HOUSING, "RENT", "March rent", "300.00", "race-key");

        List<String> outcomes = race(10, s -> s.createRequest(PRIYA, in), r -> r.outcome().name() + ":" + r.request().id());

        assertThat(outcomes.stream().map(o -> o.split(":")[1]).distinct()).hasSize(1);
        assertThat(tally(outcomes.stream().map(o -> o.split(":")[0]).toList()))
                .isEqualTo(Map.of("APPLIED", 1L, "ALREADY_APPLIED", 9L));
        assertThat(count("requests")).isEqualTo(1);
    }

    @Test
    @Timeout(60)
    @DisplayName("NFR-02 · 20 workers each submit one $1.00 request at once: all 20 APPLIED with distinct IDs; no lock errors (writers wait, they do not fail)")
    void parallelWritesWaitInsteadOfFailing() throws Exception {
        List<String> outcomes = race(WORKERS, s -> s.createRequest(PRIYA, input(KAVYA, HOUSING, "1.00")),
                r -> r.outcome().name() + ":" + r.request().id());

        assertThat(outcomes).allMatch(o -> o.startsWith("APPLIED:"));
        assertThat(outcomes.stream().distinct()).hasSize(WORKERS);
        assertThat(count("requests")).isEqualTo(WORKERS);
    }
}
