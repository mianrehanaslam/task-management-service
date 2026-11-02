# DS-6622: Implement Task Management CRUD APIs using H2

Jira: https://servicemarket.atlassian.net/browse/DS-6622 (Task, Major, Sprint "SM - Sprint 14", due 2026-11-03)

## Problem and context

DS-6622 asks for REST APIs to create, list, read, update and delete tasks. Tasks are stored in an
H2 in-memory database and must follow layered Spring conventions: validation, a consistent error
body, and no leaked internals. Authentication, ownership, pagination, UI and production deployment
are out of scope.

## Solution summary

There was no existing project in the target folder (`Office Work/Java/Rehan` was empty), so this is
a new Maven service. It follows the conventions of the sibling `communication-service` project
(Spring Boot 3.x, Java 17, Maven, an exception-advice package).

| Layer | Class |
| --- | --- |
| Controller | `TaskController`: `/api/tasks`, produces JSON, `@Valid` request bodies, `201` + `Location` on create, `204` on delete |
| Service | `TaskService`: transactional; sets timestamps from an injected `Clock`, truncated to µs to match `TIMESTAMP(6)` |
| Repository | `TaskRepository extends JpaRepository<Task, Long>` |
| Entity | `Task` (`IDENTITY` id, `@Enumerated(STRING)` status, `@Version` optimistic lock), `TaskStatus` enum |
| DTOs | `TaskRequest` (record with bean validation), `TaskResponse` (record; the entity is never exposed) |
| Errors | `GlobalExceptionHandler extends ResponseEntityExceptionHandler`, `ApiErrorResponse`, `TaskNotFoundException` |
| Config | `application.properties` (safe defaults), `application-local.properties` (H2 console + credentials), `schema.sql` |

### Key decisions

1. **Schema is owned by `schema.sql` (`CREATE TABLE IF NOT EXISTS`), and Hibernate uses
   `ddl-auto=validate`.** No configuration drops or recreates tables (ticket requirement). The
   mapping is checked at startup.
2. **`status` is bound as a String and validated with `@Pattern`.** In the first version, an invalid
   enum failed in Jackson before bean validation ran, so other invalid fields were never reported
   (found in review, R1). A unit test keeps the pattern in sync with the enum.
3. **Every error body is forced to `application/json`, and Spring's own messages are replaced with
   fixed text.** Without this, a browser `Accept` header turned a 404 into an HTML 500, and unknown
   routes echoed internal resource names.
4. **`@Version` optimistic locking.** Concurrent writes produce `409` instead of a silent lost update
   or an `updatedAt` that goes backwards. The version is internal and not exposed in the API.
5. **No new dependencies beyond the ticket's stack** (web, data-jpa, validation, h2, test). No Lombok
   or MapStruct; Java records cover the DTOs.
6. **Spring Boot 3.2.12** is the last 3.2.x patch. 3.2.0 was available locally but has published
   CVEs. Moving to a supported line (3.5.x or 4.x) is a team decision (see risk log).

## Files changed (all new)

```
pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties, .gitignore, README.md, PR_DESCRIPTION.md
src/main/java/com/servicemarket/taskmanagement/
  TaskManagementApplication.java, config/ClockConfig.java, controller/TaskController.java,
  dto/TaskRequest.java, dto/TaskResponse.java, entity/Task.java, entity/TaskStatus.java,
  exception/ApiErrorResponse.java, exception/GlobalExceptionHandler.java, exception/TaskNotFoundException.java,
  repository/TaskRepository.java, service/TaskService.java
src/main/resources/application.properties, application-local.properties, schema.sql
src/test/java/com/servicemarket/taskmanagement/
  TaskManagementApplicationTest.java, controller/TaskControllerIntegrationTest.java,
  dto/TaskRequestTest.java, exception/GlobalExceptionHandlerTest.java,
  repository/TaskRepositoryTest.java, service/TaskServiceTest.java
```

## Acceptance criteria and traceability

| AC | Implementation | Automated tests | Status |
| --- | --- | --- | --- |
| AC1 Create: saved in H2, generated id, timestamps, `201` | `TaskController.create`, `TaskService.create`, `Task` (`IDENTITY`) | `createReturns201WithGeneratedIdAndTimestampsAndPersistsInH2`, `createIgnoresClientSuppliedIdAndTimestamps`, `TaskServiceTest.createSetsTimestamps…` | ✅ |
| AC2 Invalid (missing/blank title, invalid status, over-limit) → `400` + field errors, on create **and** update | `TaskRequest` constraints, `handleMethodArgumentNotValid`, `handleHttpMessageNotReadable` | `createWithBlankTitle…`, `…MissingTitle…`, `…MissingStatus…`, `…InvalidStatus…`, `…LowercaseStatus…`, `…OverLimitTitle…`, `…OverLimitDescription…`, `createReportsEveryInvalidField`, `createWithBlankTitleAndInvalidStatusReportsBothFields`, `createWithWrongJsonType…`, `updateWithInvalidDataReturns400AndLeavesTaskUnchanged`, boundary `createAcceptsFieldsExactlyAtMaximumLength` | ✅ |
| AC3 List all → `200` | `findAll` (ordered by id) | `listReturnsAllStoredTasks`, `TaskServiceTest.findAllReturnsTasksOrderedById` | ✅ |
| AC4 Empty list → `[]` + `200` | same | `listReturnsEmptyArrayWhenNoTasksExist`, `TaskServiceTest.findAllReturnsEmptyList…` | ✅ |
| AC5 Get existing → `200` | `findById` | `getByIdReturnsMatchingTask`, `TaskServiceTest.findByIdReturnsMatchingTask` | ✅ |
| AC6 Missing on get/update/delete → `404` + consistent body | `TaskNotFoundException` → `handleTaskNotFound` | `getByIdReturns404WithConsistentErrorResponse`, `updateReturns404…`, `deleteReturns404…`, service unit tests | ✅ |
| AC7 Update: editable fields + `updatedAt` change; `id`, `createdAt` unchanged | `TaskService.update`; `createdAt` is `updatable=false` | `updateReplacesEditableFieldsAndPreservesIdAndCreatedAt` (integration + unit) | ✅ |
| AC8 Delete → `204`, removed | `TaskService.delete` | `deleteRemovesTaskAndReturns204`, `TaskRepositoryTest.deletesTask` | ✅ |
| AC9 Latest values read back from H2 in the same session | JPA + H2 | `updateReplaces…` (GET after PUT), `TaskRepositoryTest.persistsTaskInH2…` (cleared persistence context) | ✅ |
| Error structure `{timestamp,status,error,message,path}`, no stack traces | `ApiErrorResponse`, `server.error.include-*=never` | `getByIdReturns404WithConsistentErrorResponse`, `GlobalExceptionHandlerTest.unexpectedErrorReturns500WithoutLeaking…`, `createWithMalformedJson…WithoutInternalDetails` | ✅ |
| H2 console only for local; production config non-destructive | `application-local.properties`; base `ddl-auto=validate` | `TaskManagementApplicationTest$LocalProfile` (console 200), `$DefaultProfile` (console 404) | ✅ |
| App starts with the documented local profile | README | `LocalProfile` test + manual `java -jar … --spring.profiles.active=local` smoke (below) | ✅ |
| API usage and startup docs | `README.md` | n/a | ✅ |

## Red–green–refactor evidence

| Step | Command | Result |
| --- | --- | --- |
| RED 1: tests written first, no production code | `mvn -B test` | Compilation failure: `cannot find symbol: class Task / TaskRepository / TaskService / TaskRequest / TaskResponse / TaskNotFoundException` (expected) |
| RED 2: layers implemented, no exception handler | `mvn -B test` | `Tests run: 39, Failures: 12, Errors: 4`. Service (9), repository (3) and happy-path API tests pass. Every error-contract test fails as expected: `No value at JSON path "$.timestamp"` / `"$.fieldErrors"` / `"$.status"`, and `TaskNotFoundException` escapes as a servlet error |
| GREEN | `mvn -B test` | `Tests run: 39, Failures: 0, Errors: 0`, BUILD SUCCESS |
| Review fixes, round 1 (R1–R3) | `mvn -B test` | `Tests run: 44, Failures: 0` |
| Boot 3.2.12 + wrapper | `./mvnw -B clean verify` | `Tests run: 44, Failures: 0`, BUILD SUCCESS, 0 Maven warnings |
| Review fixes, round 2 (M1, M3, L6, L7, test gaps) | `./mvnw -B clean verify` | First run: 1 failure (`htmlOnly` Accept gave 406, not 404; the test's expectation was wrong, not the behaviour). After splitting the test: **`Tests run: 53, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS, 0 warnings** |

## Validation results

| Check | Status | Evidence |
| --- | --- | --- |
| Compile (main + test) with `-Xlint:all,-processing,-serial -Werror` | ✅ Passed | part of `./mvnw clean verify` |
| Unit tests (`TaskServiceTest` 9, `TaskRequestTest` 2) | ✅ Passed | surefire |
| Integration tests (`TaskControllerIntegrationTest` 34, `TaskRepositoryTest` 4, `GlobalExceptionHandlerTest` 2, application/profile tests 2) | ✅ Passed | surefire, 53 total |
| Packaging (`spring-boot:repackage` jar) | ✅ Passed | `target/task-management-service-0.0.1-SNAPSHOT.jar` |
| Manual smoke: real jar, `local` profile, curl | ✅ Passed | see API examples below |
| Concurrency burst: 150 parallel PUT/DELETE on 30 ids | ✅ Passed | `21×200, 21×204, 25×404, 83×409`, **0×500**, 0 ERROR log lines; 9 tasks remain, consistent with 21 deletes |
| Formatter / linter (Checkstyle, Spotless) | ⚪ Not available | no project-level config exists; not added, to avoid new tooling (see risks) |
| Dependency CVE scan (OWASP dependency-check / Snyk) | ⚪ Not run | tooling not configured; reviewed versions manually (see security audit) |
| E2E / UI tests | ⚪ Not applicable | API only |

## AI code review

There were two passes: a self-review, plus an independent reviewer subagent working from a read-only
brief. Both passes include a runtime smoke test.

| ID | Severity | Finding | Resolution |
| --- | --- | --- | --- |
| R1 | Medium | An invalid `status` aborted deserialization, so other invalid fields (e.g. blank title) were not reported | Status bound as a String with `@Pattern`; all errors reported together. Regression test `createWithBlankTitleAndInvalidStatusReportsBothFields`. **Fixed** |
| M1 | Medium | `Accept: text/html` turned a 404 into an HTML 500; `Accept: application/xml` got an empty body | Error bodies forced to JSON; controller `produces=application/json`. Tests `notFoundIsReturnedAsJsonForBrowserAcceptHeader`, `htmlOnlyAcceptReturns406AsJson`, `unsupportedAcceptTypeReturns406AsJson`. **Fixed** |
| M3 | Medium | No optimistic locking: lost updates, and `updatedAt` could go backwards | `@Version` column. Test `rejectsUpdateBasedOnStaleVersion`; burst test confirms 409 and no 500. **Fixed** |
| M2 | Medium | Spring Boot 3.2.0 has published CVEs (Spring 6.1.1, Tomcat 10.1.16) | Upgraded to 3.2.12 (Spring 6.1.15, Tomcat 10.1.33). Moving to a supported line is tracked as risk K1. **Mitigated** |
| R2 | Low | `description: null` omitted from responses, so the response shape varied | Field always present. **Fixed** |
| R3 | Low | A concurrent delete or update race surfaced as 500 | Mapped to `409`. Test `concurrentModificationReturns409`. **Fixed** |
| L6 | Low | Unknown-route 404 echoed Spring's detail (`No static resource api/taskz.`) | Fixed message `Request could not be processed: <reason>`. Test `unknownRouteReturns404WithFixedMessage`. **Fixed** |
| L7 | Low | Status values duplicated in the enum and the regex | Guard test `statusPatternAcceptsExactlyTheTaskStatusValues`. **Fixed (guarded)** |
| Gap | Low | No tests for empty or `null` body | `createWithEmptyBodyReturns400`, `createWithJsonNullBodyReturns400`. **Fixed** |
| L4 | Low | Two simultaneous DELETEs can return 204 + 409 instead of 204 + 404 | Accepted; documented (K4) |
| L5 | Low | URLs rejected by Tomcat itself (e.g. `%00`) get Tomcat's HTML error page | Accepted; documented (K5) |
| L8 | Low | Jackson coerces scalars (`"title":123` → `"123"`); unknown properties are ignored | Accepted by design: ignoring unknown properties prevents setting `id`/`createdAt`. Documented (K6) |

No Critical or High review findings were raised. All in-scope findings are fixed and covered by
tests.

## Security audit

| Area | Result |
| --- | --- |
| AuthN/AuthZ | **None by ticket scope** (explicitly out of scope). Tracked as K2. Do not expose publicly. |
| Input validation | All fields validated (presence, blank, length, enum allow-list). DB `CHECK` constraint and column lengths as a second layer. Ids are typed `Long`, so non-numeric ids return 400. |
| Injection | Spring Data JPA uses parameterized queries only; no string-built SQL or JPQL. No command execution. |
| Output encoding / XSS | JSON-only responses (`produces` + forced content type for errors). User text is never rendered as HTML. Consumers must encode it on display. |
| Mass assignment | DTO has no `id`/`createdAt`/`updatedAt`/`version`. Client-supplied values are ignored (tested). |
| Sensitive data / logging | No PII. Error bodies use fixed messages; stack traces and exception names are suppressed (tested with a secret-bearing exception message). Logs record method + URI only, never bodies. |
| Secrets/config | Production-safe base config has no credentials. Throwaway `sa`/empty credentials exist only in `application-local.properties` for a private in-memory DB. |
| H2 console | Off by default (tested 404). Local profile only, `web-allow-others=false` (localhost only). The console can execute arbitrary SQL/Java, so it must never be enabled on shared hosts (README warns). Medium, accepted for local dev (K3). |
| CSRF | Not applicable: no cookies or session auth. Revisit when auth is added. |
| SSRF / path traversal / deserialization | No outbound calls, no file I/O. Jackson is used without default typing (no polymorphic deserialization). |
| DoS | No pagination (out of scope) and an unbounded in-memory store. Jackson 2.15 stream constraints cap string sizes. Tracked as K7. |
| Dependencies | Boot 3.2.12 → Spring 6.1.15, Tomcat 10.1.33, Hibernate 6.4.10, H2 2.2.224, Jackson 2.15.4. The 3.2.x line is out of OSS support, so later CVEs will not be patched (K1). No automated CVE scan was run. |

**No unresolved Critical or High security findings within the ticket's scope.**

## QA test-case matrix

All "Auto" cases ran in `./mvnw clean verify` on 2026-11-02 and passed. Test class prefixes:
`IT` = `TaskControllerIntegrationTest`, `UT` = `TaskServiceTest`, `RT` = `TaskRepositoryTest`,
`EH` = `GlobalExceptionHandlerTest`, `AT` = `TaskManagementApplicationTest`, `RQ` = `TaskRequestTest`.

| ID | Category | Requirement | Preconditions | Steps | Expected | Type | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| QA-01 | Happy | AC1 | Empty DB | POST valid body | 201, id, equal createdAt/updatedAt, `Location`, stored in DB | Auto | Pass | IT.createReturns201… |
| QA-02 | Happy | AC1 | — | POST without description | 201, `description:null` | Auto | Pass | IT.createWithoutDescription… |
| QA-03 | Boundary | Validation | — | POST title=150, description=1000 chars | 201 | Auto | Pass | IT.createAcceptsFieldsExactlyAtMaximumLength |
| QA-04 | Negative | AC2 | — | POST title `"   "` | 400, fieldErrors[title] | Auto | Pass | IT.createWithBlankTitle… |
| QA-05 | Negative | AC2 | — | POST without title | 400, fieldErrors[title] | Auto | Pass | IT.createWithMissingTitle… |
| QA-06 | Negative | AC2 | — | POST status `DONE` / `pending` | 400, fieldErrors[status] lists allowed values | Auto | Pass | IT.createWithInvalidStatus…, …LowercaseStatus… |
| QA-07 | Negative | AC2 | — | POST without status | 400, fieldErrors[status] | Auto | Pass | IT.createWithMissingStatus… |
| QA-08 | Boundary | AC2 | — | POST title=151 / description=1001 | 400, field error | Auto | Pass | IT.createWithOverLimit… |
| QA-09 | Negative | AC2 | — | POST several invalid fields | 400, every field reported, nothing saved | Auto | Pass | IT.createReportsEveryInvalidField, …BlankTitleAndInvalidStatus… |
| QA-10 | Negative | Errors | — | POST malformed / empty / `null` / wrong-type JSON | 400, no internal details | Auto | Pass | IT.createWithMalformedJson…, …EmptyBody…, …JsonNullBody…, …WrongJsonType… |
| QA-11 | Negative | Errors | — | POST `text/plain` | 415 JSON | Auto | Pass | IT.createWithUnsupportedMediaType… |
| QA-12 | Security | Mass assignment | — | POST with `id`, `createdAt` | Ignored | Auto | Pass | IT.createIgnoresClientSupplied… |
| QA-13 | Happy | AC3 | 2 tasks | GET /api/tasks | 200, both, ordered by id | Auto | Pass | IT.listReturnsAllStoredTasks |
| QA-14 | Edge | AC4 | Empty DB | GET /api/tasks | 200 `[]` | Auto | Pass | IT.listReturnsEmptyArray… |
| QA-15 | Happy | AC5 | Task exists | GET /{id} | 200 task | Auto | Pass | IT.getByIdReturnsMatchingTask |
| QA-16 | Negative | AC6 | — | GET /25 | 404 consistent body | Auto | Pass | IT.getByIdReturns404… |
| QA-17 | Negative | Errors | — | GET /abc | 400 consistent body | Auto | Pass | IT.nonNumericId… |
| QA-18 | Happy | AC7, AC9 | Task exists | PUT valid; then GET | 200; id/createdAt same; updatedAt later; GET shows new values | Auto | Pass | IT.updateReplacesEditableFields…, UT.update… |
| QA-19 | Edge | AC7 | Task with description | PUT without description | description cleared (full replace) | Auto | Pass | IT.updateWithoutDescriptionClearsIt |
| QA-20 | Negative | AC6 | — | PUT /25 | 404 | Auto | Pass | IT.updateReturns404… |
| QA-21 | Negative | AC2 | Task exists | PUT blank title / bad status | 400; task unchanged | Auto | Pass | IT.updateWithInvalidData… |
| QA-22 | Happy | AC8 | Task exists | DELETE /{id}; GET | 204 empty body; then 404 | Auto | Pass | IT.deleteRemovesTask… |
| QA-23 | Negative | AC6 | — | DELETE /25 | 404 | Auto | Pass | IT.deleteReturns404… |
| QA-24 | Negative | Errors | — | DELETE /api/tasks | 405 JSON | Auto | Pass | IT.unsupportedMethod… |
| QA-25 | Regression | Errors | — | GET /25 with browser Accept; GET with `text/html` or XML only | 404 JSON; 406 JSON | Auto | Pass | IT.notFoundIsReturnedAsJson…, …406… |
| QA-26 | Security | No leakage | — | GET /api/taskz | 404, fixed message, no echoed input | Auto | Pass | IT.unknownRoute… |
| QA-27 | Security | No leakage | Service throws with secret in message | GET /api/tasks | 500 generic message, no secret or stack | Auto | Pass | EH.unexpectedError… |
| QA-28 | Recovery | Concurrency | — | Delete/update race | 409 consistent body | Auto | Pass | EH.concurrentModification…, RT.rejectsUpdateBasedOnStaleVersion |
| QA-29 | Persistence | AC9 | — | Save, clear context, reload | Values and µs timestamps identical; generated ids increase; status stored as name | Auto | Pass | RT.* |
| QA-30 | Config | H2 console | — | Start default / local profile; GET /h2-console/ | 404 / 200 | Auto | Pass | AT.* |
| QA-31 | Regression | Status sync | — | Compare regex to enum | Identical | Auto | Pass | RQ.* |
| QA-32 | Manual | Local startup | JDK 17 | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`; run the README curl examples | Same responses as documented | Manual | Pass (run by Claude via jar) | smoke log below |
| QA-33 | Manual | H2 console | Local profile running | Open `http://localhost:8080/h2-console`, JDBC `jdbc:h2:mem:tasksdb`, user `sa`; `SELECT * FROM tasks` | Rows match API data | Manual | **Pending (human)** | — |
| QA-34 | Manual | Concurrency | Local profile running | Fire parallel PUT/DELETE at the same ids (e.g. `xargs -P 20 curl …`) | Only 200/204/404/409, never 500 | Manual | Pass (run by Claude) | 150 requests: 21×200, 21×204, 25×404, 83×409 |

### Smoke-test output (real jar, `local` profile)

```
POST   /api/tasks     → 201  Location: http://localhost:18080/api/tasks/1
GET    /api/tasks     → 200  [{"id":1,…,"status":"PENDING",…}]
PUT    /api/tasks/1   → 200  createdAt unchanged, updatedAt advanced
GET    /api/tasks/1   → 200  latest values
DELETE /api/tasks/1   → 204
GET    /api/tasks/25  → 404  {"timestamp":…,"status":404,"error":"Not Found","message":"Task with ID 25 was not found","path":"/api/tasks/25"}
GET    /h2-console/   → 200 (local profile)
```

## Deployment, monitoring and rollback

- **Deploy:** none requested (production deployment is out of scope). Run with `java -jar` (default
  profile) or `--spring.profiles.active=local` for development.
- **Migration:** none. A fresh in-memory schema is created by `schema.sql` on each start. A
  non-embedded database would need a migration tool such as Flyway; startup then fails fast on
  `validate`.
- **Monitoring:** 500s are logged at ERROR with method and URI; concurrency conflicts at WARN. No
  Actuator was added (not requested).
- **Rollback:** the service is new and standalone with no shared state, so rollback is removing or
  not deploying it. Data is in memory only.

## Known-risk log

| ID | Risk | Severity | Likelihood | Impact | Mitigation | Owner / follow-up | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| K1 | Spring Boot 3.2.x is out of OSS support; future CVEs unpatched | Medium | Medium | Exposure to unpatched framework/Tomcat CVEs | Pinned to the last patch 3.2.12; upgrade to 3.5.x or 4.x and add OWASP dependency-check to CI | Team / tech lead | Open |
| K2 | No authentication or authorization | High if exposed; N/A for scope | Low (local/internal) | Anyone who can reach the port can read and modify all tasks | Out of scope per ticket; README warns not to expose it | Product, future ticket | Accepted |
| K3 | H2 console can run arbitrary SQL/Java | Medium | Low | Code execution on the dev machine if the local profile runs on a shared host | Off by default; localhost-only; README warning | Developers | Accepted |
| K4 | Simultaneous DELETEs may return 409 instead of 404 | Low | Low | Minor client confusion | Documented; client retries a GET | — | Accepted |
| K5 | Tomcat-rejected URLs return Tomcat's HTML error page | Low | Low | Inconsistent error format for malformed URLs only | Add a custom `ErrorController` if needed | Backlog | Accepted |
| K6 | Lenient JSON binding (scalar coercion, unknown fields ignored) | Low | Medium | A misspelled `descripton` is silently dropped | Documented; tighten Jackson settings if desired | Backlog | Accepted |
| K7 | No pagination; unbounded in-memory data | Low | Low | Memory growth or large responses under heavy use | Out of scope; add pagination before real use | Backlog | Accepted |
| K8 | No formatter, linter or CVE-scan tooling in the build | Low | — | Style drift; unscanned dependencies | Compiler `-Xlint -Werror` only; adopt team Checkstyle/Spotless config | Team | Open |
| K9 | Not in version control (target folder is not a git repo) | Medium (process) | — | Cannot open a PR as-is | Push the project into the intended repository, then open the PR using this description | Rehan | Open |

## Reviewer focus areas

1. `GlobalExceptionHandler`: completeness of exception mapping and fixed messages.
2. `TaskRequest.status` as a String with `@Pattern`, versus a custom enum constraint.
3. `@Version` semantics, and whether `PUT` should also support `If-Match`/ETag.
4. Profile split and `schema.sql` + `ddl-auto=validate` approach.
5. Spring Boot line choice (K1).

## Proposed Jira update (not posted: the Jira connection is read-only)

> DS-6622 implemented in `task-management-service` (Spring Boot 3.2.12, Java 17, H2).
>
> - The five CRUD endpoints under `/api/tasks` are done, with validation, a consistent JSON error
>   body, the H2 console in the local profile only, and non-destructive schema handling.
> - 53 automated tests (unit + integration) pass via `./mvnw clean verify`, and a manual smoke and
>   concurrency test was run on the packaged jar. AC1–AC9 are covered.
> - AI review findings were fixed: all-field validation reporting, JSON-only error bodies,
>   optimistic locking, and no echoing of internal details.
> - Open items:
>   - the Boot version line decision (3.2.x is out of OSS support),
>   - adding the project to git and opening the PR,
>   - manual check of QA-33 (H2 console).
> - The automation comment asks for estimates; the due date (2026-11-03) is already set. Original
>   estimate still needs to be filled in by the assignee.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
