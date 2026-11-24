package org.communitybridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.communitybridge.service.Fixtures.*;

import java.util.List;
import java.util.Map;
import org.communitybridge.db.Database;
import org.communitybridge.domain.RequestStatus;
import org.communitybridge.service.input.GetRequestInput;
import org.communitybridge.service.result.RequestView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Flow stage 12: data lives in the SQLite file and survives restarts; the separate reset command restores the
 * seed in place; the database itself refuses invalid rows; connection settings are what the spec requires.
 * Requirements: NFR-07, NFR-08, NFR-06 (database layer), NFR-02 (settings).
 */
@DisplayName("12 · Persistence, reset and database guarantees")
class F12PersistenceAndResetTest extends ServiceTestBase {

    @Test
    @DisplayName("NFR-07 · an approved request is still there, unchanged, after closing and reopening the database file (a restart)")
    void dataSurvivesRestart() {
        RequestView r = approved(HOUSING, "300.00");
        db.close();

        db = Database.open(dbFile);
        service = newService(db);

        assertThat(service.getRequest(NEHA, new GetRequestInput(r.id())).request()).isEqualTo(r);
        assertThat(fund(HOUSING).committedCents()).isEqualTo(30_000);
    }

    @Test
    @DisplayName("NFR-08 · normal open never resets or seeds: a brand-new file has the tables but no rows")
    void openDoesNotSeed() {
        try (Database fresh = Database.open(tempDir.resolve("empty.sqlite"))) {
            assertThat(countIn(fresh, "nonprofits")).isZero();
            assertThat(countIn(fresh, "staff")).isZero();
        }
    }

    @Test
    @DisplayName("NFR-08 · the seed contains exactly 2 nonprofits, 7 staff, 5 participants, 4 funds and no requests, payments or audit events")
    void seedContents() {
        assertThat(count("nonprofits")).isEqualTo(2);
        assertThat(count("staff")).isEqualTo(7);
        assertThat(count("participants")).isEqualTo(5);
        assertThat(count("funds")).isEqualTo(4);
        assertThat(count("requests")).isZero();
        assertThat(count("payments")).isZero();
        assertThat(count("audit_events")).isZero();
        assertThat(query("SELECT id, nonprofit_id, role, active FROM staff ORDER BY id")).containsExactly(
                row("stf_cb_case", NP_CB, "CASE_WORKER", 1),
                row("stf_cb_fin", NP_CB, "FINANCE", 1),
                row("stf_cb_former", NP_CB, "CASE_WORKER", 0),
                row("stf_cb_pm", NP_CB, "PROGRAM_MANAGER", 1),
                row("stf_hr_case", NP_HR, "CASE_WORKER", 1),
                row("stf_hr_fin", NP_HR, "FINANCE", 1),
                row("stf_hr_pm", NP_HR, "PROGRAM_MANAGER", 1));
    }

    @Test
    @DisplayName("NFR-08 · reset after activity restores exactly the seed, and running reset twice gives identical data")
    void resetIsRepeatable() {
        Map<String, List<Map<String, Object>>> seed = snapshot();
        paid(HOUSING, "300.00");
        rejected(SMALL, "10.00");

        db.reset();
        assertThat(snapshot()).isEqualTo(seed);
        db.reset();
        assertThat(snapshot()).isEqualTo(seed);
    }

    @Test
    @DisplayName("NFR-08 · reset is in place: a second open instance on the same file keeps working and sees the fresh seed")
    void resetInPlaceKeepsOtherInstancesWorking() {
        try (Database other = Database.open(dbFile)) {
            AssistanceService otherService = newService(other);
            otherService.createRequest(PRIYA, input(KAVYA, HOUSING, "10.00"));

            db.reset();

            assertThat(otherService.listRequests(PRIYA,
                    new org.communitybridge.service.input.ListRequestsInput(null, null, null, null, null)).total()).isZero();
            otherService.createRequest(PRIYA, input(KAVYA, HOUSING, "20.00"));
            assertThat(count("requests")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Connection settings · every connection has foreign_keys ON, WAL journal mode and a 5000 ms busy timeout")
    void connectionSettings() {
        assertThat(query("PRAGMA foreign_keys").get(0).values()).containsExactly(1);
        assertThat(query("PRAGMA journal_mode").get(0).values()).containsExactly("wal");
        assertThat(query("PRAGMA busy_timeout").get(0).values()).containsExactly(5000);
    }

    @Test
    @DisplayName("NFR-06 (database) · inserting a request that links a Harbor Relief participant to a Community Bridge request is refused by the database itself")
    void databaseRefusesCrossTenantRow() {
        assertThatThrownBy(() -> execute("""
                INSERT INTO requests (id, nonprofit_id, participant_id, fund_id, category, purpose, amount_cents,
                                      status, created_by, created_at)
                VALUES ('req_x', 'np_cb', 'par_hr_001', 'fnd_cb_housing', 'RENT', 'x', 100, 'PENDING',
                        'stf_cb_case', '2026-11-24T09:30:00Z')"""))
                .hasMessageContaining("FOREIGN KEY");
    }

    @Test
    @DisplayName("NFR-02 (database) · committing more than a fund's allocation is refused by the database CHECK, even bypassing the service")
    void databaseRefusesOverspend() {
        assertThatThrownBy(() -> execute("UPDATE funds SET committed_cents = 50001 WHERE id = ?", SMALL))
                .hasMessageContaining("CHECK");
    }

    @Test
    @DisplayName("FR-08 (database) · editing a submitted request's amount is refused by the database, even bypassing the service")
    void databaseRefusesEdit() {
        RequestView r = pending(HOUSING, "300.00");

        assertThatThrownBy(() -> execute("UPDATE requests SET amount_cents = 1 WHERE id = ?", r.id()))
                .hasMessageContaining("immutable");
        assertThat(service.getRequest(PRIYA, new GetRequestInput(r.id())).request().status()).isEqualTo(RequestStatus.PENDING);
    }

    private static Map<String, Object> row(String id, String np, String role, int active) {
        return Map.of("id", id, "nonprofit_id", np, "role", role, "active", active);
    }

    private static long countIn(Database database, String table) {
        try (var c = database.dataSource().getConnection();
                var rs = c.createStatement().executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
