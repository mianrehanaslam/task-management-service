# Step 2: Inspect the repository and map requirements

## Inspect

- Find the target project. It is usually the working directory. If the ticket names a service,
  check sibling folders under `Office Work/Java/` (read-only unless that is the target).
- Read `CLAUDE.md`, the README, `pom.xml`, the package layout, existing controllers, services,
  exception handling, config/profiles, tests, and `git log` for related history.
- Check the toolchain: `java -version`, `./mvnw -v`, and the Spring Boot version in use.
- If the folder is empty or has no project, build a new one that follows team conventions (see
  `task-management-service` as the reference). State this as an assumption.

## Engineering notes (put them in the report and the PR description)

- Understanding of the problem
- Questions considered, and the answers found
- Relevant repository findings
- Proposed behaviour and technical approach
- Alternatives considered, and why they were rejected
- Risks, assumptions, compatibility concerns and rollback considerations
- Feature scope (as defined by the ticket) and traceable implementation tasks

## Traceability checklist

Build a table with one row per requirement and acceptance criterion:

| Req/AC | Planned code change | Test(s) | Verification evidence |

Keep it up to date through steps 3–5.

## Ambiguity

Ask **one** concise, specific question only when an ambiguity genuinely blocks the work. Otherwise
state a reasonable assumption and continue. Stay in the ticket's scope: no unrelated refactoring.
