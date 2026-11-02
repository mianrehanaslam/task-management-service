# Step 5: QA matrix, documentation and PR handoff

`PR_DESCRIPTION.md` from DS-6622 (in `task-management-service`) is the reference example for every
section below.

## QA test-case matrix

| ID | Category | Requirement | Preconditions | Test steps | Expected result | Automated/Manual | Status | Evidence |

- Include positive/happy, negative/invalid input, error/recovery, boundary/edge,
  security/permission, and regression/backward-compatibility cases.
- Run every feasible automated case. For cases that need a person, give precise manual steps and
  mark them **Pending (human)**.

## Documentation

Update the README (and API docs if they exist) with:

- developer- or user-facing behaviour;
- API contract and curl examples;
- configuration, profiles and environment;
- migration and deployment steps;
- compatibility notes;
- monitoring;
- rollback;
- limitations.

## Known-risk log

| ID | Risk | Severity | Likelihood | Impact | Mitigation | Owner / follow-up | Status |

Write "No known residual risks identified" only after the reviews are complete and nothing remains.

## PR package: `PR_DESCRIPTION.md` at the project root

1. Title `<TICKET-ID>: <summary>` and a link to the ticket
2. Problem and business context
3. Solution summary and important implementation decisions
4. Files or components changed
5. Acceptance-criteria checklist plus requirement → code → test traceability
6. Red–green–refactor evidence
7. Test and build results (passed / failed / skipped / unavailable / N/A)
8. AI code-review findings and resolutions
9. Severity-ranked security audit
10. QA matrix
11. API examples, logs or screenshots
12. Deployment, migration, monitoring and rollback notes
13. Known risks and limitations
14. Reviewer focus areas
15. Proposed Jira update text, **not posted** (Jira access is read-only)
16. Ending: `🤖 Generated with [Claude Code](https://claude.com/claude-code)`

## Next

Don't commit yet. Go to [step 6](06-peer-review-commit-pr.md): the peer review, the commit, and
the push and draft PR (after asking the user) all happen there. Write the completion report only
after step 6.
