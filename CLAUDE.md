# task-management-service

## Jira tickets: always run the full workflow

When the user sends only a Jira ticket ID (e.g. `DS-6700`) or a ticket URL, **invoke the
`jira-ticket-workflow` skill** (`.claude/skills/jira-ticket-workflow/SKILL.md`). Run all five steps
in order without asking which ones to do. The user will not list the steps.

## Environment

| Item | Value |
| --- | --- |
| Jira / Confluence | Atlassian MCP, site `https://servicemarket.atlassian.net`, cloudId `2dc213b3-4bf4-45b4-bac3-629147063da3`. **Read-only**: never post comments or change status. |
| JDK | 17 (`/Library/Java/JavaVirtualMachines/microsoft-17.jdk`), the default `java` |
| Build | `./mvnw` (Maven 3.9.9). Maven is not on PATH. |
| Git | Branch `feature/<TICKET-ID>`. Commit when the work is done; **never push, merge or deploy** unless the user asks. |

## Commands

```bash
./mvnw -B clean verify                     # compile (-Xlint:all -Werror) + all tests + jar
./mvnw -B test -Dtest=TaskServiceTest      # one test class
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Architecture and conventions

- Spring Boot 3.2.12, Java 17, Spring Web, Spring Data JPA, Bean Validation, H2 (in-memory).
- Package `com.servicemarket.taskmanagement`:
  - `controller`: REST endpoints, `produces = application/json`, `@Valid` bodies.
  - `service`: `@Transactional` business logic. Time comes from the injected `Clock`.
  - `repository`: Spring Data interfaces.
  - `entity`: JPA entities with `@Version` optimistic locking.
  - `dto`: Java records. Entities are never returned from the API.
  - `exception`: `GlobalExceptionHandler` (`@RestControllerAdvice` extending
    `ResponseEntityExceptionHandler`). Every error is an `ApiErrorResponse` with
    `{timestamp,status,error,message,path,fieldErrors?}` and fixed messages: no stack traces and
    no echoed input.
- Schema is owned by `src/main/resources/schema.sql` (`CREATE TABLE IF NOT EXISTS`), with Hibernate
  `ddl-auto=validate`. Never use `create`/`create-drop`/`update` in base config.
- The `local` profile (`application-local.properties`) is the only place for dev credentials and
  the H2 console.
- No Lombok or MapStruct; plain classes and records. Don't add dependencies without a reason.
- Tests:
  - `*Test` unit tests use Mockito and a fixed `Clock`.
  - `*IntegrationTest` uses `@SpringBootTest` + MockMvc against H2.
  - `@DataJpaTest` covers persistence; `@WebMvcTest` covers handler edge cases.
- `PR_DESCRIPTION.md` (DS-6622) is the reference example of a complete handoff package.
