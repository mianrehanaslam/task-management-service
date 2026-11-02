# DS-6623: Add task filtering by status

Jira: https://servicemarket.atlassian.net/browse/DS-6623 (Sub-task of
[DS-6622](https://servicemarket.atlassian.net/browse/DS-6622), Major, To Do, Sprint "SM - Sprint 14").
No comments, links or attachments.

> The DS-6622 handoff package that this file replaced is in commit `78fc9ca`.

## Problem and context

Users need to list only the tasks in a given state. `GET /api/tasks` gets an optional `status`
query parameter, filtered in the database, without changing the existing list behaviour or the
other CRUD APIs.

## Solution summary

| Layer | Change |
| --- | --- |
| Controller | `GET /api/tasks` reads the optional `status` from the raw query map and matches it exactly against `TaskStatus` names. Unknown, blank, padded, lowercase or repeated values throw `InvalidTaskStatusException`. |
| Service | `findAll(TaskStatus status)`: `null` → `findAll(Sort.by("id"))`; otherwise `findByStatus(status, Sort.by("id"))`. |
| Repository | Derived query `List<Task> findByStatus(TaskStatus, Sort)` → `select … where status=? order by id` (parameter-bound). |
| Errors | `GlobalExceptionHandler.handleInvalidTaskStatus` → `400` in the standard format, `message: "Validation failed"`, `fieldErrors[{field:"status", message:…}]`. The rejected value is never stored or echoed. |
| Schema | `CREATE INDEX IF NOT EXISTS idx_tasks_status_id ON tasks (status, id)`: non-destructive; serves the filter and the ordering. |
| Docs | README "Filtering by status" section, API table, curl examples, 400 example, limitations. |

### Key decisions

1. **Exact, case-sensitive matching, with blank values rejected.** This matches the request-body
   rule from DS-6622. Binding the enum directly would make Spring silently treat `?status=` as "no
   filter".
2. **The raw `MultiValueMap` query map instead of a `String`/`List` binding.** Spring joins or
   splits repeated values on commas, which hides `?status=A&status=B`. Repeated parameters now get a
   precise `"must be specified only once"` error.
3. **Filtering in the repository query, with a composite `(status, id)` index.** This satisfies the
   ticket's "database, not in memory" requirement. The SQL is verified in the smoke-test log.
4. **No API shape change.** No new dependencies (`pom.xml` unchanged), no migration beyond an
   additive index.

## Files changed

```
README.md
src/main/java/com/servicemarket/taskmanagement/controller/TaskController.java
src/main/java/com/servicemarket/taskmanagement/exception/GlobalExceptionHandler.java
src/main/java/com/servicemarket/taskmanagement/exception/InvalidTaskStatusException.java   (new)
src/main/java/com/servicemarket/taskmanagement/repository/TaskRepository.java
src/main/java/com/servicemarket/taskmanagement/service/TaskService.java
src/main/resources/schema.sql
src/test/java/.../controller/TaskControllerIntegrationTest.java
src/test/java/.../exception/GlobalExceptionHandlerTest.java
src/test/java/.../repository/TaskRepositoryTest.java
src/test/java/.../service/TaskServiceTest.java
PR_DESCRIPTION.md
```

## Acceptance criteria and traceability

| AC / requirement | Implementation | Automated tests | Status |
| --- | --- | --- | --- |
| AC1 `?status=PENDING` → only PENDING, 200 | controller parse → service → `findByStatus` | `listFilteredByStatusReturnsOnlyMatchingTasks[PENDING]`, `TaskServiceTest.findAllWithStatusQueriesRepositoryByStatus`, `TaskRepositoryTest.findByStatusReturnsOnlyMatchingTasksOrderedById` | ✅ |
| AC2 `?status=IN_PROGRESS` | same | `listFilteredByStatusReturnsOnlyMatchingTasks[IN_PROGRESS]`, repository test | ✅ |
| AC3 `?status=COMPLETED` | same | `listFilteredByStatusReturnsOnlyMatchingTasks[COMPLETED]`, repository test | ✅ |
| AC4 No match → `[]`, 200 | same | `listFilteredByStatusReturnsEmptyArrayWhenNothingMatches`, `TaskServiceTest.findAllWithStatusReturnsEmptyList…`, `TaskRepositoryTest.findByStatusReturnsEmptyListWhenNothingMatches` | ✅ |
| AC5 Unsupported status → 400 + clear validation error, standard format | `InvalidTaskStatusException`, `handleInvalidTaskStatus` | `listWithUnsupportedStatusReturns400WithFieldError` ×6 (`INVALID`, `pending`, blank, space, padded, comma list), `listWithRepeatedStatusParameterReturns400` ×2, `invalidStatusValueIsNotEchoedInErrorResponse` | ✅ |
| AC6 No param → existing behaviour | `status == null` → `findAll(Sort.by("id"))` | `listWithoutStatusStillReturnsAllTasksAcrossStatuses`, existing `listReturnsAllStoredTasks`, `listReturnsEmptyArrayWhenNoTasksExist`, `TaskServiceTest.findAllReturnsTasksOrderedById` (verifies `findByStatus` is never called) | ✅ |
| Filtering in the DB, not in memory | derived query + index | `TaskServiceTest.findAllWithStatusQueriesRepositoryByStatus` (verifies `findAll` is never called), `TaskRepositoryTest.statusFilterIndexExists`, smoke SQL log | ✅ |
| Existing CRUD unaffected | — | all 53 pre-existing DS-6622 tests still pass (53 + 19 new = 72) | ✅ |
| API docs updated | README | — | ✅ |

## Red–green–refactor evidence

| Step | Command | Result |
| --- | --- | --- |
| RED 1: tests first | `./mvnw -B test` | Compile failure: `cannot find symbol: method findByStatus(TaskStatus, Sort)` and `findAll(TaskStatus)` |
| RED 2: repository + service done, controller ignores `status` | `./mvnw -B test` | `Tests run: 68, Failures: 11`. Service (11) and repository (5) tests pass. Every new API test fails for the expected reason: `Status expected:<400> but was:<200>`, `JSON path "$"` size mismatch, `Expected 0 values but got 1` |
| GREEN | `./mvnw -B test` | `Tests run: 68, Failures: 0` |
| Refactor (status index) | `./mvnw -B clean verify` | `68, 0 failures`, BUILD SUCCESS, 0 warnings |
| Review fixes: RED | `./mvnw -B test` | `72, Failures: 3`: repeated param got `must be one of…` instead of `must be specified only once` (×2); `statusFilterIndexExists` found no `IDX_TASKS_STATUS_ID` |
| Review fixes: GREEN | `./mvnw -B clean verify` | **`Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`**, BUILD SUCCESS, 0 warnings |

## Validation results

| Check | Status | Evidence |
| --- | --- | --- |
| Compile `-Xlint:all -Werror` | ✅ Passed | `./mvnw -B clean verify` |
| Unit tests: `TaskServiceTest` 11, `TaskRequestTest` 2 | ✅ Passed | surefire |
| Integration tests: `TaskControllerIntegrationTest` 48, `TaskRepositoryTest` 7, `GlobalExceptionHandlerTest` 2, application tests 2 | ✅ Passed | surefire, 72 total |
| Smoke test: packaged jar, `local` profile | ✅ Passed | below |
| SQL proof of DB filtering | ✅ Passed | `Hibernate: select … from tasks t1_0 where t1_0.status=? order by t1_0.id` |
| Mixed parallel burst (40 POST, 80 filtered GET, 40 DELETE; 20 at a time) | ✅ Passed | `40×201, 80×200, 19×204, 21×404`, **0×500**, 0 ERROR lines |
| Formatter / linter | ⚪ Unavailable | no project tooling (risk K8 from DS-6622) |
| Dependency CVE scan | ⚪ Not applicable | `pom.xml` unchanged; no new dependencies |

### Smoke-test output

```
GET /api/tasks                                   → 200 [ids 1 (PENDING), 2 (IN_PROGRESS), 3 (PENDING)]
GET /api/tasks?status=PENDING                    → 200 [ids 1, 3]
GET /api/tasks?status=IN_PROGRESS                → 200 [id 2]
GET /api/tasks?status=COMPLETED                  → 200 []
GET /api/tasks?status=INVALID | pending | (blank)→ 400 {"message":"Validation failed","path":"/api/tasks",
                                                        "fieldErrors":[{"field":"status","message":"must be one of: PENDING, IN_PROGRESS, COMPLETED"}]}
GET /api/tasks?status=PENDING&status=PENDING     → 400 {"fieldErrors":[{"field":"status","message":"must be specified only once"}]}
```

## AI code review

There were two passes: a self-review, plus an independent read-only reviewer subagent that ran the
tests and curl checks. The reviewer's verdict on the code was "merge-ready".

| ID | Severity | Finding | Resolution |
| --- | --- | --- | --- |
| A1 | High | README not updated with the `status` parameter (the reviewer saw the tree before the docs commit) | README "Filtering by status" section, API table, examples, 400 body. **Fixed** |
| A2 | Low | A repeated parameter gets a misleading `must be one of…` even when each value is valid | Raw query map, separate `"must be specified only once"` error; tests ×2. **Fixed** |
| A3 | Low | Single-column `status` index (3 distinct values) doesn't help `ORDER BY id` | Composite `idx_tasks_status_id (status, id)`; test `statusFilterIndexExists`. **Fixed** |
| A4 | Low | Repository test never checked a non-empty IN_PROGRESS result; no-match not covered at repository level | Test strengthened, `findByStatusReturnsEmptyListWhenNothingMatches` added. **Fixed** |
| A5 | Low | Misspelled parameter names (`?STATUS=`, `?state=`) are silently ignored and return all tasks | Standard Spring behaviour, outside the ticket. Documented in README. **Accepted** |
| A6 | Low | `PR_DESCRIPTION.md` still described DS-6622 | Rewritten for DS-6623. **Fixed** |

No Critical, High or Medium code findings remain.

## Peer review (step 6, full diff before commit)

A fresh read-only peer reviewer re-ran the suite (72 tests, 0 failures) and reviewed the whole
change set. Its verdict was "OK to commit once the PR_DESCRIPTION.md counts are fixed".

| ID | Severity | Finding | Resolution |
| --- | --- | --- | --- |
| P1 | Medium | `PR_DESCRIPTION.md` gave `TaskControllerIntegrationTest` 50 tests; surefire shows 48 | Corrected to 48. **Fixed** |
| P2 | Medium | Said "46 pre-existing tests"; the DS-6622 baseline is 53 (53 + 19 new = 72) | Corrected in traceability and QA F-16. **Fixed** |
| P3 | Low | README showed the 400 JSON directly under the valid `?status=PENDING` curl | Example split into a valid request and a labelled `?status=INVALID` response. **Fixed** |
| P4 | Low | Project `CLAUDE.md` still calls `PR_DESCRIPTION.md` the DS-6622 reference, but the file now holds DS-6623 | Not changed here: `CLAUDE.md` is Claude config and needs the user's say-so. The DS-6622 package is available via `git show 78fc9ca:PR_DESCRIPTION.md`. **Tracked** |
| P5 | Low | `@RequestParam MultiValueMap` binds all request parameters, not only the query string | Harmless on GET (no form body is parsed); the reason is documented in the Javadoc. **No change** |

Other checks found nothing:
- security: fixed messages, no echo, no string-built SQL;
- config: additive index only;
- debug code and TODOs: none;
- files: `target/` and `.idea/` are ignored; no secrets.

## Security audit

| Area | Result |
| --- | --- |
| Injection | Derived JPA query with a bound parameter (`status=?`). The value only reaches the DB after it is matched against enum names. **None** |
| Output encoding / XSS / leakage | The rejected value is never stored in the exception or the response (test with `<script>`). Fixed messages only. **None** |
| Input validation | Allow-list of enum names; case-sensitive; blank, padded and repeated values rejected. **OK** |
| AuthN/AuthZ | Unchanged: no auth, out of scope (DS-6622 risk K2). Filtering exposes nothing the unfiltered list didn't. |
| DoS | Filtering reduces the result size; indexed. No pagination (out of scope, DS-6622 K7). |
| Dependencies / secrets / config | No dependency or config change; the schema change is additive and non-destructive. |
| CSRF / SSRF / deserialization / files | Not applicable (GET, no outbound calls, no body). |

**No Critical or High security findings.**

## QA test-case matrix

All "Auto" cases ran in `./mvnw -B clean verify` on 2026-11-02 and passed. `IT` =
`TaskControllerIntegrationTest`, `UT` = `TaskServiceTest`, `RT` = `TaskRepositoryTest`.

| ID | Category | Requirement | Preconditions | Steps | Expected | Type | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | Happy | AC1 | 2 PENDING, 1 IN_PROGRESS, 1 COMPLETED | `GET ?status=PENDING` | 200, the 2 PENDING ids in id order | Auto | Pass | IT.listFiltered…[PENDING] |
| F-02 | Happy | AC2 | same | `GET ?status=IN_PROGRESS` | 200, 1 task | Auto | Pass | IT.listFiltered…[IN_PROGRESS] |
| F-03 | Happy | AC3 | same | `GET ?status=COMPLETED` | 200, 1 task | Auto | Pass | IT.listFiltered…[COMPLETED] |
| F-04 | Edge | AC4 | Only PENDING tasks | `GET ?status=COMPLETED` | 200 `[]` | Auto | Pass | IT.…EmptyArrayWhenNothingMatches |
| F-05 | Negative | AC5 | — | `GET ?status=INVALID` | 400, `fieldErrors[status]` lists the allowed values | Auto | Pass | IT.listWithUnsupportedStatus…[INVALID] |
| F-06 | Negative | AC5 | — | `?status=pending` | 400 (case-sensitive) | Auto | Pass | IT …[pending] |
| F-07 | Boundary | AC5 | — | `?status=`, `?status=%20`, `?status=%20PENDING` | 400 | Auto | Pass | IT …["", " ", " PENDING"] |
| F-08 | Negative | AC5 | — | `?status=PENDING,COMPLETED` | 400 `must be one of…` | Auto | Pass | IT …[PENDING,COMPLETED] |
| F-09 | Negative | AC5 | — | `?status=PENDING&status=COMPLETED` / `…&status=PENDING` | 400 `must be specified only once` | Auto | Pass | IT.listWithRepeatedStatusParameter… |
| F-10 | Security | No echo | — | `?status=<script>alert(1)</script>` | 400; body contains no `script` | Auto | Pass | IT.invalidStatusValueIsNotEchoed… |
| F-11 | Regression | AC6 | Mixed statuses | `GET /api/tasks` | 200, all tasks, id order | Auto | Pass | IT.listWithoutStatusStillReturnsAll…, IT.listReturnsAllStoredTasks |
| F-12 | Regression | AC6 | Empty DB | `GET /api/tasks` | 200 `[]` | Auto | Pass | IT.listReturnsEmptyArray… |
| F-13 | Persistence | DB filtering | H2 | `findByStatus` per status, no-match | Correct rows in id order; empty list | Auto | Pass | RT.findByStatus… |
| F-14 | Performance | DB filtering | — | Service with status | `findByStatus` called; `findAll` never called | Auto | Pass | UT.findAllWithStatusQueriesRepositoryByStatus |
| F-15 | Performance | Index | H2 schema | Inspect `INFORMATION_SCHEMA` | `IDX_TASKS_STATUS_ID` on `(STATUS, ID)` | Auto | Pass | RT.statusFilterIndexExists |
| F-16 | Regression | CRUD | — | Full DS-6622 suite | All pass | Auto | Pass | 53 pre-existing tests |
| F-17 | Recovery | Concurrency | Running jar | Parallel POST, filtered GET, DELETE | No 500s | Manual (run by Claude) | Pass | burst counts above |
| F-18 | Manual | Docs | Local profile running | Run the README "Filtering by status" curl examples | Responses match the README | Manual | **Pending (human)** | — |

## Deployment, migration, monitoring and rollback

- **Deploy:** none requested. Same jar and profiles as DS-6622.
- **Migration:** additive `CREATE INDEX IF NOT EXISTS` in `schema.sql`, run on startup. No data
  change.
- **Monitoring:** unchanged. 400s are client errors and are not logged at ERROR.
- **Rollback:** revert this branch's commit. The index is harmless if left in place.
- **Compatibility:** `GET /api/tasks` without `status` behaves exactly as before. Clients that were
  already sending an unrelated `status` parameter (previously ignored) would now get filtered
  results or a 400. No such clients are known; the service has not been released.

## Known-risk log

| ID | Risk | Severity | Likelihood | Impact | Mitigation | Owner / follow-up | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| D1 | Misspelled parameter names are ignored and return all tasks | Low | Low | Client may assume the filter applied | Documented in README | — | Accepted |
| D2 | Case-sensitive values (`pending` → 400) may surprise clients | Low | Medium | Client sees 400 | Consistent with the request-body rule; clear field error lists the allowed values | Product, if case-insensitive is wanted | Accepted |
| D3 | `feature/DS-6623` is branched from unmerged `feature/DS-6622` | Low (process) | — | PR diff includes DS-6622 if opened against a base without it | Merge DS-6622 first, or open this PR against `feature/DS-6622` | Rehan | Open |
| D4 | No git remote, so a PR can't be opened yet | Medium (process) | — | Handoff only via this file | Add the team remote, then open a draft PR | Rehan | Open |
| — | DS-6622 risks K1 (Boot 3.2 EOL line), K2 (no auth), K7 (no pagination), K8 (no linter) | — | — | — | Unchanged | see DS-6622 | Open |

## Reviewer focus areas

1. `TaskController.findAll`: raw `MultiValueMap` versus a bound `@RequestParam`, and strict
   matching rules.
2. Whether case-sensitive `status` matching is the desired product behaviour (D2).
3. Index choice `(status, id)`.

## Proposed Jira update (not posted: the Jira connection is read-only)

> DS-6623 implemented on `feature/DS-6623`.
>
> - `GET /api/tasks?status={PENDING|IN_PROGRESS|COMPLETED}` filters in the database (derived JPA
>   query plus an index). Omitting `status` keeps the existing behaviour.
> - An unsupported, blank, lowercase or repeated value returns 400 in the standard error format with
>   a `status` field error.
> - 72 automated tests pass (`./mvnw clean verify`), including all existing CRUD tests. AC1–AC6 are
>   covered, and the README is updated.
> - Open items: add the git remote and open the PR (branched from DS-6622), and the manual docs
>   check F-18.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
