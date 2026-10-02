#!/usr/bin/env bash
# scripts/audit-ui-wiring.sh
#
# Grep-based audit that fails the build if any v2 screen strays
# from the "sealed UiState + lifecycle-aware collect" pattern.
#
# Invariants:
#   1. Every *Screen.kt file under ui/v2/screens/ that collects
#      a StateFlow must use `collectAsStateWithLifecycle()` (no
#      plain `collectAsState()` calls outside `V2Root.kt`).
#   2. Every screen file (SettingsScreen, DevicesScreen,
#      ClusterScreen, AgentScreen) must reference its `*UiState`
#      sealed type and read `viewModel.uiState`.
#   3. The `collectAsState()` runtime import must not appear
#      in any v2 source file outside `V2Root.kt`.
#
# Note: `BootstrapScreen` is a pure Composable helper (no
# ViewModel) — it owns `phaseProgress` / `currentPhaseLine`
# helpers and is exercised by `BootstrapViewModelTest`. It
# is excluded from Check 2.
#
# Exit codes:
#   0  every check passed
#   1  one or more checks failed
#
# Usage:
#   ./scripts/audit-ui-wiring.sh
#
# Wired into CI per the plan's step 5 audit.

set -u
set -o pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
V2_SCREENS_DIR="${ROOT}/app/src/main/kotlin/com/meshlit/ui/v2/screens"
V2_ROOT_FILE="${ROOT}/app/src/main/kotlin/com/meshlit/ui/v2"

if [[ ! -d "${V2_SCREENS_DIR}" ]]; then
    echo "audit-ui-wiring: cannot find v2 screens dir at ${V2_SCREENS_DIR}" >&2
    exit 1
fi

failures=0

# Strip KDoc block comments + line comments + string literals
# so a doc reference to `collectAsState()` doesn't trip the
# grep. Pure-text replacement — we're not parsing Kotlin, just
# removing anything that looks like a comment or string before
# the audit grep runs.
strip_comments() {
    python3 - "$1" <<'PY'
import re, sys
with open(sys.argv[1]) as fh:
    text = fh.read()
text = re.sub(r'/\*.*?\*/', '', text, flags=re.DOTALL)
text = re.sub(r'//.*', '', text)
text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
text = re.sub(r"'(?:\\.|[^'\\])*'", "''", text)
sys.stdout.write(text)
PY
}

# Check 1: every *Screen.kt in ui/v2/screens/ that uses
# `collectAsState` family must use the lifecycle-aware variant.
# Doc-comments only are OK.
echo "audit-ui-wiring: checking collectAsStateWithLifecycle usage…"
for f in "${V2_SCREENS_DIR}"/*Screen.kt; do
    [[ -f "$f" ]] || continue
    stripped=$(strip_comments "$f")
    if echo "${stripped}" | grep -q "collectAsState(" \
        && ! echo "${stripped}" | grep -q "collectAsStateWithLifecycle"; then
        echo "  FAIL: $(basename "$f") calls collectAsState() without collectAsStateWithLifecycle()"
        failures=$((failures + 1))
    fi
done

# Check 2: every screen file references a matching `*UiState`
# sealed type and reads `viewModel.uiState`. The v2 surface
# should always read from a ViewModel, not raw repository
# flows. `BootstrapScreen` is excluded — it's a pure
# composable helper without a ViewModel.
echo "audit-ui-wiring: checking sealed UiState references…"
expected_settings="SettingsUiState"
expected_devices="DevicesUiState"
expected_cluster="ClusterUiState"
expected_agent="AgentUiState"
for f in "${V2_SCREENS_DIR}"/*Screen.kt; do
    [[ -f "$f" ]] || continue
    name=$(basename "$f")
    case "${name}" in
        SettingsScreen.kt) expect="${expected_settings}" ;;
        DevicesScreen.kt) expect="${expected_devices}" ;;
        ClusterScreen.kt) expect="${expected_cluster}" ;;
        AgentScreen.kt) expect="${expected_agent}" ;;
        *) continue ;;  # BootstrapScreen etc.
    esac
    if ! grep -q "${expect}" "$f"; then
        echo "  FAIL: ${name} does not reference ${expect}"
        failures=$((failures + 1))
    fi
    if ! grep -q "viewModel.uiState" "$f"; then
        echo "  FAIL: ${name} does not read viewModel.uiState"
        failures=$((failures + 1))
    fi
done

# Check 3: no plain `collectAsState()` runtime calls outside
# `V2Root.kt` in the v2 tree. Doc comments are excluded.
echo "audit-ui-wiring: checking for stray collectAsState() outside V2Root.kt…"
while IFS= read -r f; do
    [[ -f "$f" ]] || continue
    stripped=$(strip_comments "$f")
    if echo "${stripped}" | grep -q "collectAsState()"; then
        echo "  FAIL: $(basename "$f") calls collectAsState() outside V2Root.kt"
        failures=$((failures + 1))
    fi
done < <(find "${V2_ROOT_FILE}" -name "*.kt" ! -name "V2Root.kt")

# Report.
echo
if (( failures == 0 )); then
    echo "audit-ui-wiring: 0 v2 screens without collectAsStateWithLifecycle"
    echo "audit-ui-wiring: 0 v2 screens without sealed UiState"
    echo "audit-ui-wiring: 0 occurrences of collectAsState() outside V2Root.kt"
    exit 0
else
    echo "audit-ui-wiring: ${failures} check(s) failed"
    exit 1
fi