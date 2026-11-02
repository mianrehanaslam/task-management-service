# Step 3: Implement with TDD (red → green → refactor)

1. Create the branch: `git checkout -b feature/<TICKET-ID>`.
2. **Red:** write or update tests first. Run them and save the failing output to the scratchpad. A
   compile failure only counts as the first red. Aim for at least one *behavioural* red: build the
   layers but leave out the piece under test, then show the tests fail for the expected reason.
3. **Green:** implement the smallest maintainable change, then run the tests again and record the
   passing output.
4. **Refactor:** improve without changing behaviour, then re-run all relevant tests.

## Rules

- Follow the project's architecture, style, naming, error handling, logging and dependency practices
  (see the project `CLAUDE.md`).
- No placeholders, stubs, TODOs or pseudocode in the final code.
- No unnecessary new dependencies.
- Never expose secrets, tokens, credentials, personal data or stack traces in responses or logs.

## Cover where applicable

- Happy paths
- Validation and malformed input. Report every invalid field together; an enum or type error must
  not hide the other errors.
- Failure and recovery
- Boundaries: exact limits and limit + 1
- Backward compatibility with existing API contracts
- Concurrency and idempotency, e.g. `@Version` optimistic locking mapped to `409`
- Permissions and authorization
- Performance implications
- Observability and useful error reporting

## Lessons from DS-6622 (check these every time)

- Force error bodies to `application/json`, otherwise a browser `Accept` header turns a 404 into an
  HTML 500.
- Use fixed error messages. Spring's own detail text echoes client input.
- Truncate timestamps to the database's precision, so a create/update response matches a later read.
- Ignore client-supplied `id` and timestamps (no mass assignment): keep them out of request DTOs.
- Production config must never use destructive schema recreation.
