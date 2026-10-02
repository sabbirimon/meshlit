# Slice 1 — Pre-existing dirty-tree inventory

> **Snapshot date:** 2026-09-30
> **Branch:** `redesign/drawer-gemini` (HEAD `b4a0762`)
> **Author:** baseline / Slice 1 audit
> **Do not modify any file in this list without an explicit Phase B / Phase C / Phase G commit entry in `PLAN.md`.**

---

## Inventory (7 dirty entries, 3 modified tracked + 4 untracked)

| File | State | Lines | Purpose | Preserve? |
|---|---|---|---|---|
| `app/src/main/kotlin/com/meshlit/ui/screens/CatalogScreen.kt` | modified | +599 / −78 | Gemini-style catalog re-skin (Phase C from `PLAN.md`). Uses `RaListCard` + `RaGetButton` for download rows. | **Yes.** |
| `app/src/main/kotlin/com/meshlit/ui/theme/Color.kt` | modified | +23 | Adds `Ra*` theme tokens (`RaOrange`, `RaOutline`, `RaSurface`, `RaSurfaceVariant`, `RaTextPrimary`, `RaTextSecondary`) used by the new `Ra*` components. | **Yes.** |
| `app/src/main/res/values/strings.xml` | modified | +17 | New `ra_*` string keys for the Gemini-style catalog badges, Get/Retry buttons, top-pick header. | **Yes.** |
| `app/src/main/kotlin/com/meshlit/ui/components/RaChip.kt` | untracked | 121 | Material 3 chip primitive with violet / amber / emerald / error tints. | **Yes.** |
| `app/src/main/kotlin/com/meshlit/ui/components/RaGetButton.kt` | untracked | 76 | Get / Retry button with download icon + label. Replaces the inline `Button(...)` block in `CatalogScreen`. | **Yes.** |
| `app/src/main/kotlin/com/meshlit/ui/components/RaListCard.kt` | untracked | 238 | Generic list-row card with leading icon + title + subtitle + trailing affordance. Used by `CatalogScreen` rows. | **Yes.** |
| `app/src/main/kotlin/com/meshlit/ui/components/TopPickCard.kt` | untracked | 286 | Larger hero card for the "Top pick" recommendation slot in the catalog. Mirrors the RunAnywhere AI sample reference (per `Screenshots/UI suggestion/`). | **Yes.** |

Also discovered (not in `git status` but present on disk and referenced): `RaHeroIcon.kt` (62 lines, untracked). Likely either an in-flight import or recently committed-clean.

## What this branch is doing

This is the `redesign/drawer-gemini` work described in `PLAN.md` Phase B (Agent redesign — already shipped on `dev`) and `PLAN.md` "RA-style UI components wired into Agent + Catalog screens" (commit `ca7a613`, ahead on `dev` but not yet merged here). The dirty tree is the same set of files, ported onto `redesign/drawer-gemini` as the integration branch for the visual re-skin.

## Branch policy for Slice 1+

**Do not:**
- ❌ Touch any of the 7 files above.
- ❌ Revert, stash, reset, or rebase.
- ❌ Modify `Color.kt` — the `Ra*` tokens are owned by this branch.
- ❌ Modify `strings.xml` — the `ra_*` keys are owned by this branch.
- ❌ Modify `CatalogScreen.kt` — references the untracked `Ra*` components.

**Do:**
- ✅ Add **new files only** for Slice 1 deliverables.
- ✅ If a Slice 1 deliverable conflicts with the existing dirty tree (e.g. a shared theme token), stop and ask the user.
- ✅ If a Slice 1 test exercises the redesigned catalog, gate it behind `@Ignore` or move it to a future slice so the current branch stays compilable.

## Carry-over items not in scope for Slice 1

These are observations from the inventory; they are explicitly out of scope:

- `RaHeroIcon.kt` exists on disk but is not in `git status` and not referenced from `CatalogScreen.kt`. Verify whether it's used elsewhere before deleting.
- The `Meshlit*` color tokens added in `Color.kt` are not in the original `DynamicTheme.kt` palette structure. Treat them as `redesign/drawer-gemini`'s concern.
- The dirty tree implies the in-progress Gemini-style redesign is being polished alongside Phase 2.x — these are different tracks and should not be conflated.

## Risk for Slice 1

The only realistic risk: any new code that imports `com.meshlit.ui.theme.Ra*` or `com.meshlit.ui.components.Ra*` would force the build to succeed against an uncommitted, un-tracked-by-HEAD state. Slice 1 deliberately does not import any of those, and the `CapabilityMatrix` renderer it adds is colocated in `core-common` (no Compose dependency in `core-common` for the data class itself; the renderer goes under `app/.../ui/screens/settings/` but is gated to compile only on the unchanged `MeshlitCapabilityMatrixCard` reference path).
