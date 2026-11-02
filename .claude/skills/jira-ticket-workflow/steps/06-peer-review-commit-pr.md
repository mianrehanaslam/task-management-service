# Step 6: Peer review, commit and PR

This is a final gate after step 5. An independent **peer reviewer** reviews the complete change
before it is committed. The step 4 review checks code while it is being written; this one checks the
finished branch: code, tests, config, docs and `PR_DESCRIPTION.md` together.

## 6a. Peer review of the full diff

1. Collect the full change: `git status`, then `git diff <base>...HEAD` plus the uncommitted changes
   (`git diff`, untracked files). `<base>` is the default branch; on a new repo with no base,
   review every file.
2. Launch a **fresh peer-review subagent** (general-purpose, read-only, told not to edit files and
   not to run git write commands). Give it:
   - the ticket ID with a summary and the acceptance criteria;
   - the list of changed files;
   - the project `CLAUDE.md` conventions;
   - what to check:
     - correctness against each AC;
     - bugs and edge cases;
     - missing or weak tests;
     - error handling;
     - security (secrets, injection, leakage, authorization);
     - concurrency;
     - config safety (no destructive DDL, no dev credentials in base config);
     - leftover debug code or TODOs;
     - files that must not be committed (`target/`, IDE files, logs, `.env`, keys);
     - whether the README and `PR_DESCRIPTION.md` match the code.

   It returns findings ranked Critical/High/Medium/Low, each with file:line and a fix.
3. Fix every valid Critical, High and Medium finding, and every Low finding that is in scope. Add a
   regression test for each code fix. Re-run `./mvnw -B clean verify`; it must pass.
4. Repeat the peer review on the fixes until there are **no unresolved Critical/High findings**.
5. Add a "Peer review" section to `PR_DESCRIPTION.md`: each finding, its severity, and its
   resolution (Fixed / Accepted and documented).

## 6b. Commit (automatic)

1. Make sure the branch is `feature/<TICKET-ID>`.
2. Run `git status` and stage only intended files. Never stage `target/`, IDE folders, logs,
   scratch files, `.env`, `*.pem` or other secrets.
3. Commit with the message `<TICKET-ID>: <summary>`, a short body (what and why), and end it with
   `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
4. Review fixes made after an earlier commit go in a follow-up commit, e.g.
   `<TICKET-ID>: Address peer review findings`. Never amend or rewrite commits that were pushed.

## 6c. Push and draft PR: ask the user first

1. Check `git remote -v` and `gh auth status`.
   - **No remote or no `gh` access:** don't create a remote yourself. Report it as a handoff
     blocker; `PR_DESCRIPTION.md` stays ready to paste. Skip the rest of 6c.
2. **Ask the user once in chat** before pushing, and wait for a clear yes. Name the branch, the
   remote, the base branch and the PR title, e.g.:
   "Push `feature/DS-6700` to `origin` and open a draft PR into `main` titled
   `DS-6700: <summary>`?"
3. Only after the user says yes:
   - run `git push -u origin feature/<TICKET-ID>`;
   - run `gh pr create --draft --base <default-branch> --title "<TICKET-ID>: <summary>" --body-file PR_DESCRIPTION.md`;
   - check the PR with the ccd_pr tools (`get_status`, and `bind_pr` if it isn't reported), read
     the CI results, and share the PR link.
4. If the user says no, stop after the commit and report that the branch is local only.
- **Never** merge, force-push, push to the default branch, enable auto-merge, or deploy.
- Jira access is read-only: put the PR link in the proposed Jira update text; don't post it.

## Done when

- Peer review has no unresolved Critical/High findings, and the build passes after the fixes.
- The work is committed on `feature/<TICKET-ID>`.
- The draft PR is open with the link reported, or the user declined, or the exact blocker is stated.
