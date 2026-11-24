# Verification log

Real results from running the checks, newest last. Commands are run from the repository root on Windows
(`.\mvnw.cmd`); JDK 25 compiling with `--release 21`.

## 2026-11-24 · Step 1 · schema constraints

A throwaway JDBC script loaded `schema.sql` into in-memory SQLite 3.53.4 and tried 21 good and bad rows
(mixed-tenant participant and fund, zero and REAL amounts, unknown category, overspent fund, edited amount,
PENDING→PAID, approval without `decided_by`, APPROVED→REJECTED, partial and second payment, wrong audit
from/to, edited audit row, duplicate `clientRequestId`, and the valid counterparts).
Result: **21 of 21** accepted or refused as intended.

## 2026-11-24 · Step 3 · Money unit tests

`.\mvnw.cmd test -Dtest=MoneyTest` → `Tests run: 39, Failures: 0, Errors: 0, Skipped: 0`.

## 2026-11-24 · Step 4 · full business suite

`.\mvnw.cmd test` → `Tests run: 307, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`). Run twice;
both runs green. Per-test results: [TEST-CASES.md](TEST-CASES.md).

## 2026-11-24 · Step 4 · the race test can see a broken guard (AC-08)

To show that `F10ConcurrencyTest.competingApprovalsNeverOverspend` would catch overspending, the
`allocation_cents - committed_cents - paid_cents >= ?` condition in `FundRepository.reserve` was temporarily
replaced with an always-true condition, and only that test was run:

```
.\mvnw.cmd test "-Dtest=F10ConcurrencyTest#competingApprovalsNeverOverspend"
ran=10 failed=10
expected: {"APPLIED"=5L, "INSUFFICIENT_FUNDS"=15L}
 but was: {"APPLIED"=5L, "INTERNAL"=15L}
```

All 10 repetitions failed. Exactly 5 approvals still applied because the database `CHECK
(committed_cents + paid_cents <= allocation_cents)` refused the rest, which surfaced as `INTERNAL`. This shows
two things: the test detects a missing guard, and the database backstop alone still prevents overspending.
The guard was then restored (`git diff` on the file empty) and the full suite re-run: 307 of 307 passed.
