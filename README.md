# Community Bridge · Emergency Assistance Funding MCP Server

A headless MCP server that lets case workers submit assistance requests, program managers approve or reject them,
and finance staff record simulated payments against fixed grant funds. Built with Java 21 (compiled with
`--release 21`), Spring Boot 4.1.3, Spring AI 2.0.3 (MCP Java SDK 2.1.0) and SQLite, over local stdio.

| Document | What it holds |
|---|---|
| [SETUP.md](SETUP.md) | Step-by-step setup and run guide (Linux and Windows): prerequisites, tests, database, demo, running the server, connecting MCP Inspector, Claude Code, Claude Desktop and other clients, troubleshooting |
| [docs/SPEC.md](docs/SPEC.md) | The specification: requirements, business rules, data model, MCP interface, permissions, acceptance criteria, production security, traceability with results, and a revision log of every change since v1 |
| [docs/spec-v1.docx](docs/spec-v1.docx) | The initial specification, unchanged, saved before any code |
| [docs/TEST-CASES.md](docs/TEST-CASES.md) | Every test by description, in flow order, with its last result (generated from the code) |
| [docs/VERIFICATION.md](docs/VERIFICATION.md) | Real command output for each step, including failures and how they were resolved |
| [docs/demo-output.txt](docs/demo-output.txt) | Output of the MCP client demonstration |

## Setup, run and test

Requirements: a JDK 21 or newer. Maven is downloaded by the wrapper (pinned to 3.9.16), and every dependency
version is pinned in [pom.xml](pom.xml) through the Spring Boot and Spring AI BOMs. The full guide, including
Windows commands and troubleshooting, is [SETUP.md](SETUP.md). On Linux or macOS, from the repository root:

```bash
./scripts/test.sh                    # clean build + 307 business cases + 71 MCP cases, with a summary
./scripts/reset-db.sh                # create or reset data/cb.sqlite with the seed (separate from startup)
./scripts/demo.sh                    # MCP client demo: discover, create, approve, pay, summary
./scripts/run-server.sh stf_cb_case  # run the stdio server as Priya (normally an MCP client launches it)
./scripts/inspector.sh stf_cb_case   # MCP Inspector in the browser (needs Node.js)
./scripts/mcp-config.sh              # client config (Claude Desktop, Cursor, ...) with this machine's paths
./scripts/claude-code-add.sh         # register Priya, Arjun and Neha with Claude Code
```

On Windows PowerShell: `.\mvnw.cmd clean verify`, `.\scripts\reset-db.ps1`, `.\scripts\demo.ps1`.

**Fixture identities** (seeded by the reset command; full data in [seed.sql](src/main/resources/db/seed.sql)):

| Nonprofit | Case worker | Program manager | Finance | Funds |
|---|---|---|---|---|
| Community Bridge `np_cb` | `stf_cb_case` Priya | `stf_cb_pm` Arjun | `stf_cb_fin` Neha (`stf_cb_former` is inactive) | Housing Stability $10,000, Small Emergencies $500, Winter Relief $1,000 (closed) |
| Harbor Relief `np_hr` | `stf_hr_case` Ananya | `stf_hr_pm` Vikram | `stf_hr_fin` Isha | Utility Relief $5,000 |

## How it works

**Tools.** `whoami`, `list_participants`, `get_participant`, `list_funds`, `get_funding_summary`, `get_request` and
`list_requests` work for every role; `create_request` is for case workers, `approve_request` and `reject_request`
for program managers, and `record_payment` for finance. Each tool has a hand-written strict input schema, an
output schema, and a description that starts with who may call it. Results are `structuredContent` plus a
one-line summary; failures are tool results with `isError: true` and `{ error: { code, message, details } }`
using nine fixed codes (spec: Error behavior).

**Every call runs the same checks in the same order**, and any failure leaves the database exactly as it was:

1. **Identity.** `CB_STAFF_ID` comes from the launch environment only. It is checked before the server starts
   (a bad one exits with code 2, before `initialize`) and re-read on every call, so deactivating someone takes
   effect at once.
2. **Role.** One role matrix ([Authorizer](src/main/java/org/communitybridge/service/Authorizer.java)) decides before
   any input is read, so a wrong role cannot probe which IDs exist.
3. **JSON shape.** Unknown fields such as `role` or `nonprofitId`, and wrong JSON types, are rejected by
   [Args](src/main/java/org/communitybridge/mcp/Args.java). The SDK's own validation is turned off because it
   returns text-only errors and runs before the role check (spike result S2).
4. **Values.** [InputValidator](src/main/java/org/communitybridge/service/InputValidator.java) reports every bad
   field at once. Amounts are decimal strings parsed into integer cents with no floating point anywhere
   ([Money](src/main/java/org/communitybridge/domain/Money.java)).
5. **Own nonprofit only.** Every query filters on the caller's `nonprofit_id`. Another nonprofit's record looks
   exactly like a missing one (`NOT_FOUND`). Composite foreign keys make the database itself refuse a row that
   mixes nonprofits.
6. **State and money.** Approve reserves the full amount; pay moves it from committed to paid; available is
   `allocation - committed - paid`. Repeating an approve, reject or pay returns `ALREADY_APPLIED` and changes
   nothing; `create_request` accepts an optional `clientRequestId` so retried submissions are safe too.

**Competing approvals.** Each staff identity is its own server process, so competing approvals are separate
processes writing one SQLite file. Every write runs inside one explicit `BEGIN IMMEDIATE` transaction
([Database.write](src/main/java/org/communitybridge/db/Database.java)), which takes the database write lock
*before* reading anything. In WAL mode with a 5 s busy timeout, a second writer waits instead of failing. The
reservation itself is a guarded update (`... WHERE allocation_cents - committed_cents - paid_cents >= ?`), and a
`CHECK (committed_cents + paid_cents <= allocation_cents)` refuses an overspend even if the code were wrong.

*How it was verified:* 20 workers, each with its own database connection, approve 20 different $100 requests on
the $500 fund at the same instant. Exactly 5 succeed and 15 get `INSUFFICIENT_FUNDS`, in 10 of 10 repetitions.
The same scenario passes across **4 separate server processes over MCP**. To show the test can actually catch a
bug, I removed the guard once: all 10 repetitions failed, with only the database `CHECK` stopping the overspend.
Racing the same approval, payment or keyed create from 10 workers applies it exactly once
([F10ConcurrencyTest](src/test/java/org/communitybridge/service/F10ConcurrencyTest.java),
[VERIFICATION.md](docs/VERIFICATION.md)).

**Code layout** (`src/main/java/org/communitybridge/`): `domain` (types, Money), `service` (AssistanceService,
validation, role matrix), `identity`, `db` (Database, repositories; [schema.sql](src/main/resources/db/schema.sql)
with CHECKs and triggers for allowed transitions, immutable requests and append-only audit), `mcp` (tools,
schemas, argument checks).

## Requirements, code and tests

Every requirement ID (FR-, NFR-, MCP-) maps to its code and tests in the
[traceability table](docs/SPEC.md#traceability), with the result of the last run. Every test's display name
starts with the requirement or acceptance-criterion ID it proves.

| Suite | Where | Runs against | Result (2026-11-24) |
|---|---|---|---|
| Business tests, stages 1–12 + Money | `src/test/.../service/F01…F12`, `domain/MoneyTest` | Service layer on a fresh seeded SQLite file | 307 of 307 cases pass |
| MCP tests, stages M1–M5 | `src/test/.../mcp/M01…M05*IT` | The packaged jar as real stdio processes, one per identity, via the MCP Java SDK client | 71 of 71 cases pass |
| Demo | `scripts/demo.ps1` | Three server processes | Pass ([output](docs/demo-output.txt)) |

Failure cases also assert that every row of every table is unchanged. The MCP client validates each successful
result against the tool's output schema, so a passing MCP test also proves its output matched the schema.

**Platforms:** Windows 11 with JDK 25 (`--release 21`), and a fresh clone on Debian 13 (WSL2) with OpenJDK
21.0.12 and an empty Maven cache, where `./scripts/test.sh`, `reset-db.sh`, `run-server.sh`, `mcp-config.sh` and
`demo.sh` all ran green. Not tried on macOS.

## How I worked, and how AI was used

The order I chose was: specification first (v1 committed before any code); review of v1; data types (review);
business test cases in flow order (review); service layer; seed and run; SDK spike and MCP test cases (review);
MCP server. I reviewed at each pause. The AI coding agent (Claude Code) worked one step at a time, and every
decision the code needed that the spec did not cover went into the spec's revision log before or with the code
(v1.1–v1.7).

**1 · Requirements and specification.** *Prompt:* "go through these two documents", then "revise it" for the
review findings. *Suggestion:* the AI reviewed spec v1 against the brief and found ten issues. Among them: SQLite
ignores foreign keys unless `PRAGMA foreign_keys=ON`, so the composite-key tenant protection in v1 would silently
not work; approving on a closed fund would have reported `INSUFFICIENT_FUNDS`; a retried `create_request` could
create a duplicate request. *Change:* spec v1.1 (connection settings named, `FUND_INACTIVE` checked separately,
optional `clientRequestId`). *Independent evidence:* a throwaway script loaded the schema into SQLite and tried 21
good and bad rows, including cross-tenant inserts; all were accepted or refused as intended. Tests
`databaseRefusesCrossTenantRow`, `approveOnFundClosedAfterSubmission` and AC-23/24 now pass.

**2 · Implementation planning and coding.** *Context:* spec v1 assumed the SDK would enforce
`additionalProperties: false`, and left open how errors interact with output schemas. *Suggestion:* before writing
MCP tests, run a throwaway spike server and client against the pinned SDK. *Result:* the SDK validates input
before the handler, with text-only errors and no code (which would also put schema errors ahead of `FORBIDDEN`),
and the Java client validates *successful* output against the schema. *Change:* SDK validation turned off and an
own JSON-shape check added after the role check (spec v1.5); every output schema mirrors its record exactly.
*Correction during the spike:* the Spring context failed to start because `spring-boot-starter-jdbc`
auto-configured an unused DataSource, so the dependency was removed. *Evidence:* the spike's printed results
([VERIFICATION.md](docs/VERIFICATION.md)), and tests `unknownFieldRejected`, `amountMustBeAString` and `roleBeforeInput`.

**3 · Debugging and verification.** *Context:* first MCP run, 70 of 71 passing; AC-22 got answers for ids 1 and 2
but not 3. *Diagnosis:* every stdout line was valid JSON-RPC. The test had closed stdin immediately, and the SDK
shuts down at stdin EOF before finishing the in-flight call. *Change:* the test was fixed to behave like a real
client and keep stdin open until answered; the server was not changed just to pass. The limitation is recorded.
*Evidence:* M5 re-run 5 of 5, then a clean full run. *Other corrections caught by independent checks:* a regex in
`Money` lost its escapes when written through a shell heredoc (the compiler rejected it); the first test catalogue
counted 241 cases where Surefire ran 307 (fixed after cross-checking the counts); and the guard-removal run above
confirmed the race test can fail.

## Time, limitations and next steps

**Actual time spent:** _to be filled in by the candidate_ (the agent cannot measure active human time). The
commit history gives each step's timestamps: `git log --format="%ad %s" --date=iso`.

**Known limitations**

- The MCP SDK stops at stdin EOF without finishing in-flight calls; well-behaved clients are unaffected.
- One identity per server process (stdio); a client needing three roles runs three servers.
- `list_requests` counts and pages in two statements, so `total` can be off by in-flight writes.
- Requests created in the same second are ordered by a random ID, not by insertion.
- SQLite allows one writer at a time; fine for one nonprofit's staff, not for large scale.
- The audit trail is stored but not exposed as a tool (Q8); business limits per participant or year are not
  modelled (Q1). See the spec's open questions.
- On this Windows machine, `./mvnw` in Git Bash picks up a broken `wget`; use `.\mvnw.cmd`.

**Next priorities:** run CI on a real JDK 21 from a fresh clone; HTTP transport behind the existing OAuth
provider as designed in the spec (token validation, `iss`+`sub` mapped to a staff row); Postgres with
`SELECT … FOR UPDATE` in place of SQLite for multi-instance hosting; answers to open questions Q1–Q12 from the
business.
