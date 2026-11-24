---
name: journal
description: Update PROGRESS.md with the current session's work.
tools: Read, Edit
---

# /journal — Append a session summary to PROGRESS.md

Captures the day's decisions, what was built, what was skipped, and
what's next. The journal is read by every subsequent session — it's
the only place that records the *why* behind decisions that don't
appear in the code.

## When to invoke

- After closing a phase boundary (every 1-2 weeks).
- After a material design decision (e.g. swapping sqlite-vss for
  a Room-only schema).
- After bumping the version number.
- After deleting or renaming a module.

## Template

```markdown
## Current state — YYYY-MM-DD (Phase <name>)

**This session:** One-paragraph summary of the headline change.
List the new files / modules / screens. State the version bump.

**Architectural notes:** *Why* this change was made. What was
rejected and why. What shortcuts were taken.

**Out of scope (follow-up PRs):** Bullet list of what's deliberately
deferred.

**Verification:** `./gradlew :app:assembleDebug` is green. Unit
tests pass. Manual smoke test on device `R9KN2009CZJ`.

---
```

## Don't

- Don't edit the early `## Current state — YYYY-MM-DD` sections —
  they're historical. Append a new top-of-file section instead.
- Don't mention commit hashes or branch names — those go in the
  commit message.
- Don't duplicate the `BUILD_GUIDE.md` — point to it instead.
