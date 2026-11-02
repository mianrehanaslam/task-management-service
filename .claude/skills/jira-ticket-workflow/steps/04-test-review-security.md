# Step 4: Test, review and security audit

## 4a. Tests and checks

- Create or update tests at each applicable level:
  - unit (business logic);
  - integration (controller, persistence, validation, API);
  - API/contract;
  - end-to-end where supported;
  - regression;
  - security-focused (no leakage, mass assignment, authorization).
- Run the narrowest tests first, e.g. `./mvnw -B test -Dtest=ClassName`. Then run the full suite:
  `./mvnw -B clean verify` (compile with `-Xlint:all -Werror`, all tests, package). Save the logs to
  the scratchpad.
- **Run the real app:**
  1. Start the packaged jar on a spare port (local profile if one exists).
  2. Smoke-test each endpoint with curl.
  3. For write APIs, run a parallel burst, e.g. `xargs -P 20` curl PUT/DELETE on the same ids, and
     confirm there are no 500s or ERROR log lines.
  4. Stop the process when done.
- Mark every check **passed, failed, skipped, unavailable or not applicable**. Never claim a check
  passed unless it ran.

## 4b. AI code review

Review as a critical senior reviewer, **and** launch an independent read-only reviewer subagent
(general-purpose, run in the background, told not to edit files). Check:

- correctness against every acceptance criterion;
- missing behaviour or tests;
- regressions and backward compatibility;
- error handling and data integrity;
- maintainability and unnecessary complexity;
- race conditions and state management;
- performance and resources;
- security and privacy;
- accessibility and usability where applicable.

Rank findings **Critical / High / Medium / Low**. Fix every valid in-scope finding, add a regression
test for each, re-run the full suite, and repeat the review. Record every finding and its resolution
(Fixed / Mitigated / Accepted and documented). Never present unresolved Critical/High findings as
done.

## 4c. Security audit

For each finding give severity, location, impact, evidence and remediation. Check:

- authentication and authorization
- input validation and output encoding
- injection (SQL/JPQL, command, log)
- sensitive-data exposure and logging
- secrets and configuration (no credentials in base or production config)
- dependency and supply-chain risk: list key runtime versions
  (`./mvnw dependency:list -DincludeScope=runtime`) and check them for known CVEs and EOL status
- file, network and command execution
- SSRF, XSS, CSRF, insecure deserialization, path traversal
- privilege boundaries and tenant isolation
- abuse cases, rate limiting, DoS
- cryptographic usage

Fix all Critical/High issues in scope. If one can't be fixed safely, **stop and escalate** to the
user; do not mark the ticket done.
