# ADR 0001 — Resolve conflicting constraints via user-driven choices

**Date:** 2026-08-01
**Status:** Accepted
**Decided by:** Planning session

## Context

Several user-requested features appeared to conflict with the project's
non-negotiable constraints from `app/CLAUDE.md` and `app/BUILD_GUIDE.md` §0:

- Users want cluster SSH. The build guide says "no public SSH."
- Users want long-distance cluster connectivity. The build guide says
  "no multi-hop mesh routing."
- Users want adaptive tuning. The build guide says "ship static
  thresholds first."
- Users want bypass-charging help. The build guide says "no
  programmatic bypass-charging control."

Two ways to resolve this: **silently relax constraints** (leads to
insecurity), or **surface the choices in the UI** (the user picks, the
app shows the trade-offs, the constraint stays in force but stops being
the user's problem).

## Decision

Adopt principle 9 and 10 in `app/BUILD_GUIDE.md` §0: any feature that
appears to conflict with a non-negotiable is implemented as a
**user-toggled option in the UI** with a clear consent flow. The
constraint itself remains in force — it just stops being the default.

Specific worked examples:

- **Cluster SSH** — bound to LAN + Tailscale interfaces only, gated by
  trust tier, default off. Users who want a public SSH daemon go to
  Termux; Meshlit doesn't compromise.
- **Long-distance transport** — five cards in the Network screen:
  NSD/LAN, Wi-Fi Direct, Tailscale, WireGuard, Relay. Each user-toggled
  with its own consent flow. Nothing on by default.
- **Adaptive vs static thresholds** — per-device pin in Settings.
- **Bypass-charge recommendation** — UI surfaces the manual toggle on
  supported OEMs as a recommendation; never automates.
- **Trust-tier auto-pairing** — explicit pairing is the default; the
  user can opt in to "remember LAN devices for 30 days."

## Consequences

**Positive:**
- Power users get the features they want with the safety they understand.
- Non-technical users never see a flag they'd need to flip.
- The constraints in CLAUDE.md can stay as written — they describe the
  *floor*, not the *ceiling*.

**Negative:**
- More UI surface to design, document, and test.
- Risk of feature creep — every new option needs to be categorized as
  "this is a Meshlit feature" vs "this is the user's option to enable."
- A documented review of which user choices are safe vs require
  warnings is needed. Captured in BUILD_GUIDE §5 validation checklist.

**Mitigation:**
- Every new "user option" must respect the §5 checklist: physical-device
  verified, survives backgrounding, metered-data consent, etc.
- Phase exit criteria explicitly require "no silent privilege changes."

## References

- `app/BUILD_GUIDE.md` §0 principles 9–10
- `app/CLAUDE.md` — newly added "How this project handles conflicting
  scope choices" section
- This session's `PROGRESS.md` decision log
