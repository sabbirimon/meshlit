# Architecture notes

This directory holds architecture-level documentation for Meshlit:
diagrams, design rationale, cross-cutting concerns. Phase 0+ will fill
this in.

**To be written:**
- High-level diagram (ASCII or generated PNG) showing the multi-module
  dependency graph.
- Bootstrap sequence: how a node advertises itself, gets discovered,
  gets a role, and starts receiving jobs.
- Trust-tier handshake: what happens between NSD service discovery and
  the first job dispatch. Which `core-*` module owns each step.
- Foreground service lifecycle: start, onTimeout, restart, why the
  6h/24h cap matters here, where the checkpoint state lives.
- Job dispatch path: from the UI's "send prompt" tap to the
  inference node's response streaming back. Sequence diagram.
- Per-phase architecture diagrams as each phase ships.

**Constraint reference:** all diagrams must respect the §0 principles
in `app/BUILD_GUIDE.md`. Especially: no cross-device tensor splits;
trust-tier boundaries; data-parallel only.
