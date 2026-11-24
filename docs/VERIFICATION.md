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

## 2026-11-24 · Step 5 · SDK spike

A throwaway Spring AI 2.0.3 / MCP SDK 2.1.0 server and client (scratchpad only, not committed) answered
S1–S4; the answers and their design consequences are spec revision v1.5.

## 2026-11-24 · Step 6 · MCP tests and demo

First `.\mvnw.cmd verify` with the MCP server in place: 307 of 307 business cases and **70 of 71** MCP cases passed.
The failure was `M05EndToEndIT.stdoutIsOnlyJsonRpc` (AC-22):

```
Expecting actual: [1, 2]  to contain exactly in any order: [1, 2, 3]  but could not find: [3]
```

Every stdout line was valid JSON-RPC (the property under test), but the test closed stdin immediately after writing
its requests and the SDK shut down before answering the in-flight `tools/call`. The test was changed to keep stdin
open until all answers arrive (spec v1.7); the server was not changed. Re-run of M5: 5 of 5 passed.

Clean run: `.\mvnw.cmd clean verify` → business `Tests run: 307, Failures: 0, Errors: 0, Skipped: 0`;
MCP `Tests run: 71, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` in about 1 minute.

Demo: `.\scripts\demo.ps1` exit 0. It discovered 11 tools; Priya created a $300.00 request, Arjun approved it,
Neha paid it, and the final summary showed committed 0.00, paid 300.00, available 9700.00 USD. Priya's
approve attempt returned `FORBIDDEN`. Full output: [demo-output.txt](demo-output.txt).
