-- Community Bridge emergency assistance: SQLite schema (spec: Data model).
--
-- Every connection must run PRAGMA foreign_keys = ON (set in DataSourceConfig); without it
-- SQLite ignores every FOREIGN KEY below, including the composite same-tenant keys.
--
-- Money: INTEGER cents only, never REAL. Times: UTC ISO-8601 TEXT, e.g. 2026-11-24T09:30:00Z.
-- IDs: server-made prefixed TEXT (np_, stf_, par_, fnd_, req_, pay_, evt_).
--
-- Each table is (id, nonprofit_id) UNIQUE so child tables can reference both columns at once.
-- That makes it impossible to store a row that links records of two different nonprofits (NFR-06).

CREATE TABLE IF NOT EXISTS nonprofits (
    id   TEXT PRIMARY KEY CHECK (id LIKE 'np\_%' ESCAPE '\'),
    name TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 200)
) STRICT;

CREATE TABLE IF NOT EXISTS staff (
    id           TEXT PRIMARY KEY CHECK (id LIKE 'stf\_%' ESCAPE '\'),
    nonprofit_id TEXT NOT NULL REFERENCES nonprofits (id),
    name         TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 200),
    role         TEXT NOT NULL CHECK (role IN ('CASE_WORKER', 'PROGRAM_MANAGER', 'FINANCE')),
    active       INTEGER NOT NULL CHECK (active IN (0, 1)),
    UNIQUE (id, nonprofit_id)
) STRICT;

CREATE TABLE IF NOT EXISTS participants (
    id             TEXT PRIMARY KEY CHECK (id LIKE 'par\_%' ESCAPE '\'),
    nonprofit_id   TEXT NOT NULL REFERENCES nonprofits (id),
    name           TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 200),
    household_size INTEGER NOT NULL CHECK (household_size BETWEEN 1 AND 50),
    city           TEXT NOT NULL CHECK (length(city) BETWEEN 1 AND 200),
    UNIQUE (id, nonprofit_id)
) STRICT;

CREATE TABLE IF NOT EXISTS funds (
    id               TEXT PRIMARY KEY CHECK (id LIKE 'fnd\_%' ESCAPE '\'),
    nonprofit_id     TEXT NOT NULL REFERENCES nonprofits (id),
    name             TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 200),
    status           TEXT NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    -- at most $10,000,000.00
    allocation_cents INTEGER NOT NULL CHECK (allocation_cents BETWEEN 0 AND 1000000000),
    committed_cents  INTEGER NOT NULL DEFAULT 0 CHECK (committed_cents >= 0),
    paid_cents       INTEGER NOT NULL DEFAULT 0 CHECK (paid_cents >= 0),
    -- Database backstop against overspending (NFR-02), even if the code were wrong.
    CHECK (committed_cents + paid_cents <= allocation_cents),
    UNIQUE (id, nonprofit_id)
) STRICT;

CREATE TABLE IF NOT EXISTS requests (
    id                TEXT PRIMARY KEY CHECK (id LIKE 'req\_%' ESCAPE '\'),
    nonprofit_id      TEXT NOT NULL REFERENCES nonprofits (id),
    participant_id    TEXT NOT NULL,
    fund_id           TEXT NOT NULL,
    category          TEXT NOT NULL CHECK (category IN ('RENT', 'UTILITIES', 'TRANSPORTATION', 'OTHER')),
    purpose           TEXT NOT NULL CHECK (length(purpose) BETWEEN 1 AND 500),
    -- 1 cent to $1,000,000.00
    amount_cents      INTEGER NOT NULL CHECK (amount_cents BETWEEN 1 AND 100000000),
    status            TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID')),
    client_request_id TEXT CHECK (client_request_id IS NULL OR length(client_request_id) BETWEEN 1 AND 64),
    created_by        TEXT NOT NULL,
    created_at        TEXT NOT NULL,
    decided_by        TEXT,
    decided_at        TEXT,
    rejection_reason  TEXT CHECK (rejection_reason IS NULL OR length(rejection_reason) <= 500),

    -- A PENDING request has no decision; every other status has one.
    CHECK ((status = 'PENDING') = (decided_by IS NULL)),
    CHECK ((decided_by IS NULL) = (decided_at IS NULL)),
    -- Only a rejected request can carry a rejection reason.
    CHECK (rejection_reason IS NULL OR status = 'REJECTED'),

    -- Same-tenant links (a NULL decided_by skips its key, as SQLite does for any NULL column).
    FOREIGN KEY (participant_id, nonprofit_id) REFERENCES participants (id, nonprofit_id),
    FOREIGN KEY (fund_id, nonprofit_id)        REFERENCES funds (id, nonprofit_id),
    FOREIGN KEY (created_by, nonprofit_id)     REFERENCES staff (id, nonprofit_id),
    FOREIGN KEY (decided_by, nonprofit_id)     REFERENCES staff (id, nonprofit_id),

    UNIQUE (id, nonprofit_id),
    -- Idempotent create (FR-12). SQLite treats NULLs as distinct, so requests without a key never clash.
    UNIQUE (nonprofit_id, created_by, client_request_id)
) STRICT;

CREATE INDEX IF NOT EXISTS ix_requests_status      ON requests (nonprofit_id, status);
CREATE INDEX IF NOT EXISTS ix_requests_fund        ON requests (nonprofit_id, fund_id, status);
CREATE INDEX IF NOT EXISTS ix_requests_participant ON requests (nonprofit_id, participant_id);

-- Requests cannot be edited after submission (FR-08): only status and decision columns may change.
CREATE TRIGGER IF NOT EXISTS trg_requests_immutable
BEFORE UPDATE OF id, nonprofit_id, participant_id, fund_id, category, purpose, amount_cents,
                 client_request_id, created_by, created_at
ON requests
BEGIN
    SELECT RAISE(ABORT, 'request fields are immutable after submission');
END;

-- Only the permitted transitions: PENDING -> APPROVED | REJECTED, APPROVED -> PAID.
CREATE TRIGGER IF NOT EXISTS trg_requests_transition
BEFORE UPDATE OF status ON requests
WHEN NOT (
       (OLD.status = 'PENDING'  AND NEW.status IN ('APPROVED', 'REJECTED'))
    OR (OLD.status = 'APPROVED' AND NEW.status = 'PAID')
)
BEGIN
    SELECT RAISE(ABORT, 'invalid request status transition');
END;

CREATE TABLE IF NOT EXISTS payments (
    id           TEXT PRIMARY KEY CHECK (id LIKE 'pay\_%' ESCAPE '\'),
    nonprofit_id TEXT NOT NULL REFERENCES nonprofits (id),
    -- One payment per request, even if two payment calls race (FR-06, FR-09).
    request_id   TEXT NOT NULL UNIQUE,
    amount_cents INTEGER NOT NULL CHECK (amount_cents BETWEEN 1 AND 100000000),
    recorded_by  TEXT NOT NULL,
    recorded_at  TEXT NOT NULL,
    FOREIGN KEY (request_id, nonprofit_id)  REFERENCES requests (id, nonprofit_id),
    FOREIGN KEY (recorded_by, nonprofit_id) REFERENCES staff (id, nonprofit_id)
) STRICT;

-- A payment is always for the full request amount (no partial payments).
CREATE TRIGGER IF NOT EXISTS trg_payments_full_amount
BEFORE INSERT ON payments
WHEN NEW.amount_cents IS NOT (SELECT amount_cents FROM requests WHERE id = NEW.request_id)
BEGIN
    SELECT RAISE(ABORT, 'payment amount must equal the request amount');
END;

-- Payments are never changed after insert (no refunds or edits).
CREATE TRIGGER IF NOT EXISTS trg_payments_immutable
BEFORE UPDATE ON payments
BEGIN
    SELECT RAISE(ABORT, 'payments are immutable');
END;

CREATE TABLE IF NOT EXISTS audit_events (
    id             TEXT PRIMARY KEY CHECK (id LIKE 'evt\_%' ESCAPE '\'),
    nonprofit_id   TEXT NOT NULL REFERENCES nonprofits (id),
    request_id     TEXT NOT NULL,
    actor_staff_id TEXT NOT NULL,
    action         TEXT NOT NULL CHECK (action IN ('CREATED', 'APPROVED', 'REJECTED', 'PAID')),
    from_status    TEXT CHECK (from_status IS NULL OR from_status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID')),
    to_status      TEXT NOT NULL CHECK (to_status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID')),
    at             TEXT NOT NULL,
    -- Each action has exactly one matching from/to pair.
    CHECK (
           (action = 'CREATED'  AND from_status IS NULL      AND to_status = 'PENDING')
        OR (action = 'APPROVED' AND from_status = 'PENDING'  AND to_status = 'APPROVED')
        OR (action = 'REJECTED' AND from_status = 'PENDING'  AND to_status = 'REJECTED')
        OR (action = 'PAID'     AND from_status = 'APPROVED' AND to_status = 'PAID')
    ),
    FOREIGN KEY (request_id, nonprofit_id)     REFERENCES requests (id, nonprofit_id),
    FOREIGN KEY (actor_staff_id, nonprofit_id) REFERENCES staff (id, nonprofit_id)
) STRICT;

CREATE INDEX IF NOT EXISTS ix_audit_request ON audit_events (nonprofit_id, request_id);

-- Audit events are append-only. (The reset command clears tables with DELETE, which stays allowed.)
CREATE TRIGGER IF NOT EXISTS trg_audit_append_only
BEFORE UPDATE ON audit_events
BEGIN
    SELECT RAISE(ABORT, 'audit events are append-only');
END;
