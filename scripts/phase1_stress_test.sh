#!/usr/bin/env bash
# Phase 1.1 stress test — two-phone cluster validation.
#
# Workflow:
#   1. Install the slim debug APK on the server phone.
#   2. Start the FGS foreground service.
#   3. Send 10 sequential prompts from the cluster.
#   4. Mid-job: kill the server's wifi to simulate outage.
#   5. Assert the client detects the failure within ~15 s.
#   6. Re-enable wifi; assert recovery reconnects.

set -euo pipefail

# ============================================================
# Configuration (override via env vars)
# ============================================================
SERVER_DEVICE="${SERVER_DEVICE:?SERVER_DEVICE must be set (adb device serial of the phone running the FGS)}"
CLIENT_DEVICE="${CLIENT_DEVICE:?CLIENT_DEVICE must be set (adb device serial of the cluster client)}"
SERVER_IP="${SERVER_IP:?SERVER_IP must be set (phone IP, discoverable via the Devices screen)}"
APK_PATH="${APK_PATH:-app/build/outputs/apk/debug/app-arm64-v8a-debug.apk}"
NUM_PROMPTS="${NUM_PROMPTS:-10}"
PROMPT="${PROMPT:-What is 2 + 2?}"

# ============================================================
# Preflight
# ============================================================
log() { printf '[phase1-stress] %s\n' "$*" >&2; }

assert_adb_devices() {
  local attached
  attached="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | wc -l | tr -d ' ')"
  if [[ "${attached}" -lt 2 ]]; then
    log "FAIL — expected at least 2 attached adb devices; found ${attached}."
    log "       adb devices:"
    adb devices | sed 's/^/         /' >&2
    exit 1
  fi
}

install_apk() {
  log "Installing ${APK_PATH} on ${SERVER_DEVICE}..."
  adb -s "${SERVER_DEVICE}" install -r "${APK_PATH}"
}

start_fgs() {
  log "Starting Meshlit FGS on ${SERVER_DEVICE}..."
  adb -s "${SERVER_DEVICE}" shell am start-foreground-service \
    -n com.meshlit.debug/com.meshlit.inference.InferenceForegroundService
  sleep 5  # FGS boot + JNI init
}

ping_health() {
  local url="http://${SERVER_IP}:8080/v1/health"
  curl --silent --max-time 5 -o /dev/null -w '%{http_code}\n' "${url}" || echo "000"
}

send_prompts() {
  local prompt_count="$1"
  local i=0
  while [[ $i -lt $prompt_count ]]; do
    local result
    result="$(curl --silent --max-time 30 \
      -X POST -H 'Content-Type: application/json' \
      -d "{\"prompt\":\"${PROMPT}\",\"maxTokens\":16}" \
      "http://${SERVER_IP}:8080/v1/infer")" || {
      log "FAIL — /v1/infer (prompt #$((i+1))) returned an error."
      return 1
    }
    if echo "${result}" | grep -q '"finishReason"'; then
      log "OK — prompt #$((i+1)) / ${prompt_count} completed."
    else
      log "FAIL — prompt #$((i+1)) returned non-done event: ${result}"
      return 1
    fi
    i=$((i+1))
  done
}

simulate_outage() {
  log "Simulating outage: disabling wifi + data on ${SERVER_DEVICE}..."
  adb -s "${SERVER_DEVICE}" shell svc wifi disable
  adb -s "${SERVER_DEVICE}" shell svc data disable
}

restore_network() {
  log "Re-enabling wifi + data on ${SERVER_DEVICE}..."
  adb -s "${SERVER_DEVICE}" shell svc wifi enable
  adb -s "${SERVER_DEVICE}" shell svc data enable
  sleep 10  # DHCP / link re-establish
}

assert_client_detected_outage() {
  local deadline=$((SECONDS + 15))
  log "Waiting for client ${CLIENT_DEVICE} to detect the outage..."
  while [[ $SECONDS -lt $deadline ]]; do
    if adb -s "${CLIENT_DEVICE}" logcat -d -s MeshlitClusterDispatch:* MeshlitInferenceCoordinator:* 2>/dev/null \
        | tail -100 | grep -qE "peer-unreachable|peer-fail|failover|outage"; then
      log "OK — client ${CLIENT_DEVICE} detected the outage within $((SECONDS - (deadline - 15)))s."
      return 0
    fi
    sleep 2
  done
  log "FAIL — client did not detect the outage within 15s."
  return 1
}

assert_client_recovered() {
  local deadline=$((SECONDS + 30))
  log "Waiting for client ${CLIENT_DEVICE} to recover / re-pair..."
  while [[ $SECONDS -lt $deadline ]]; do
    if ping_health | grep -q "200"; then
      log "OK — server /v1/health returned 200; cluster recovered."
      return 0
    fi
    sleep 2
  done
  log "FAIL — server /v1/health did not return 200 within 30s of wifi re-enable."
  return 1
}

# ============================================================
# Run
# ============================================================
assert_adb_devices
install_apk
start_fgs

initial_code="$(ping_health)"
if [[ "${initial_code}" != "200" ]]; then
  log "FAIL — initial /v1/health returned ${initial_code}, expected 200."
  exit 1
fi
log "OK — initial /v1/health=200."

send_prompts "${NUM_PROMPTS}"

simulate_outage
assert_client_detected_outage
restore_network
assert_client_recovered

send_prompts "${NUM_PROMPTS}"

log "PASS — Phase 1.1 stress test cleared all gates."
