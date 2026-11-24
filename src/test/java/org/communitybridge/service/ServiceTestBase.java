package org.communitybridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.communitybridge.service.Fixtures.ARJUN;
import static org.communitybridge.service.Fixtures.KAVYA;
import static org.communitybridge.service.Fixtures.NEHA;
import static org.communitybridge.service.Fixtures.PRIYA;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.communitybridge.db.Database;
import org.communitybridge.domain.ErrorCode;
import org.communitybridge.service.error.ServiceException;
import org.communitybridge.service.input.ApproveRequestInput;
import org.communitybridge.service.input.CreateRequestInput;
import org.communitybridge.service.input.FundingSummaryInput;
import org.communitybridge.service.input.RecordPaymentInput;
import org.communitybridge.service.input.RejectRequestInput;
import org.communitybridge.service.result.FundSummary;
import org.communitybridge.service.result.RequestView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every business test gets a fresh database file, reset to the seed, and a service whose clock is
 * fixed at {@link #NOW} so timestamps are predictable.
 */
abstract class ServiceTestBase {

    static final Instant NOW = Instant.parse("2026-11-24T09:30:00Z");
    static final String NOW_TEXT = "2026-11-24T09:30:00Z";

    /** Every business table, in the order they are snapshotted. */
    static final List<String> TABLES =
            List.of("nonprofits", "staff", "participants", "funds", "requests", "payments", "audit_events");

    @TempDir
    Path tempDir;

    Path dbFile;
    Database db;
    AssistanceService service;

    @BeforeEach
    void openFreshDatabase() {
        dbFile = tempDir.resolve("cb.sqlite");
        db = Database.open(dbFile);
        db.reset();
        service = newService(db);
    }

    @AfterEach
    void closeDatabase() {
        if (db != null) {
            db.close();
        }
    }

    static AssistanceService newService(Database database) {
        return new AssistanceService(database, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---------------------------------------------------------------- inputs

    static CreateRequestInput input(String participantId, String fundId, String amount) {
        return new CreateRequestInput(participantId, fundId, "RENT", "Monthly rent shortfall", amount, null);
    }

    // ------------------------------------------------- flow shortcuts (happy path only)

    /** Priya submits a RENT request for Kavya. */
    RequestView pending(String fundId, String amount) {
        return service.createRequest(PRIYA, input(KAVYA, fundId, amount)).request();
    }

    /** Priya submits, Arjun approves. */
    RequestView approved(String fundId, String amount) {
        RequestView r = pending(fundId, amount);
        return service.approveRequest(ARJUN, new ApproveRequestInput(r.id())).request();
    }

    /** Priya submits, Arjun rejects. */
    RequestView rejected(String fundId, String amount) {
        RequestView r = pending(fundId, amount);
        return service.rejectRequest(ARJUN, new RejectRequestInput(r.id(), null)).request();
    }

    /** Priya submits, Arjun approves, Neha pays. */
    RequestView paid(String fundId, String amount) {
        RequestView r = approved(fundId, amount);
        return service.recordPayment(NEHA, new RecordPaymentInput(r.id())).request();
    }

    /** The funding summary of one fund, as seen by the given staff member. */
    FundSummary fund(String staffId, String fundId) {
        return service.getFundingSummary(staffId, new FundingSummaryInput(fundId)).funds().get(0);
    }

    FundSummary fund(String fundId) {
        return fund(PRIYA, fundId);
    }

    // ---------------------------------------------------------------- errors

    /** Runs the call, expects a ServiceException with the code, and returns it for further checks. */
    static ServiceException expectError(ErrorCode code, Runnable call) {
        try {
            call.run();
        } catch (ServiceException e) {
            assertThat(e.code()).as("error code (message: %s)", e.getMessage()).isEqualTo(code);
            return e;
        }
        return fail("Expected ServiceException " + code + " but the call succeeded");
    }

    /**
     * Expects the error AND that every row of every business table is identical before and after
     * (NFR-03: an invalid, unauthorized or failed call leaves state exactly as it was).
     */
    ServiceException expectErrorAndNoChange(ErrorCode code, Runnable call) {
        Map<String, List<Map<String, Object>>> before = snapshot();
        ServiceException e = expectError(code, call);
        assertThat(snapshot()).as("database state after a failed call").isEqualTo(before);
        return e;
    }

    // ---------------------------------------------------------------- direct database access

    /** Every row of every business table, ordered by id. */
    Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> all = new LinkedHashMap<>();
        for (String table : TABLES) {
            all.put(table, query("SELECT * FROM " + table + " ORDER BY id"));
        }
        return all;
    }

    List<Map<String, Object>> query(String sql, Object... args) {
        try (Connection c = db.dataSource().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int col = 1; col <= md.getColumnCount(); col++) {
                        row.put(md.getColumnLabel(col), rs.getObject(col));
                    }
                    rows.add(row);
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    long count(String table) {
        return ((Number) query("SELECT count(*) AS n FROM " + table).get(0).get("n")).longValue();
    }

    /** For test setup only, e.g. closing a fund or deactivating staff. Bypasses the service on purpose. */
    void execute(String sql, Object... args) {
        try (Connection c = db.dataSource().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The audit trail of one request, oldest first, as {action, from_status, to_status, actor_staff_id, at}. */
    List<Map<String, Object>> auditEvents(String requestId) {
        return query("SELECT action, from_status, to_status, actor_staff_id, at FROM audit_events "
                + "WHERE request_id = ? ORDER BY at, rowid", requestId);
    }

    /** One expected audit row, timestamped {@link #NOW}. fromStatus may be null. */
    static Map<String, Object> event(String action, String fromStatus, String toStatus, String actor) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("action", action);
        row.put("from_status", fromStatus);
        row.put("to_status", toStatus);
        row.put("actor_staff_id", actor);
        row.put("at", NOW_TEXT);
        return row;
    }

    // ---------------------------------------------------------------- every operation, for matrices

    /** One service operation with arguments fixed, so tests can run "every tool" against one caller. */
    record Op(String name, BiConsumer<AssistanceService, String> call) {
        @Override
        public String toString() {
            return name;
        }
    }
}
