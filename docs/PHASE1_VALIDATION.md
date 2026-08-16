# Phase 1 Hardware Validation

This document is the entry point for the Phase 1.1 hardware validation
gate. Until 10 / 10 prompts return non-empty text on a real phone,
Phase 1 is **not** shipped. This is the only criterion that requires
the user to run scripts on hardware — every other phase ships in CI.

## Pre-flight

1. **Two Android phones** connected to the same Wi-Fi network.
   Both should have USB debugging enabled (`adb devices` lists them).
2. **`adb` installed** on the developer's machine.
3. The **slim debug APK** built locally:
   ```bash
   ./gradlew :app:assembleDebug
   ```
   The :app ABI splits (Phase 1.0) produce
   `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (~82 MB).
   Use whichever variant matches the server phone (arm64-v8a for
   modern phones, x86_64 for emulators).
4. The **stable Meshlit release** installed on the server phone so
   the FGS infra is known-good. First-run onboarding (unlock +
   grant permissions) finishes on the server phone before the
   cluster test runs.

## The smoke test (`phase1_validation.py`)

The fastest way to confirm the on-device LLM runtime is producing
text. Single phone, single prompt, 60-second timeout.

```bash
python3 scripts/phase1_validation.py --server <phone_ip>:8080
```

Replace `<phone_ip>` with the IP the Devices screen shows on the
server phone. The script:

1. Polls `GET /v1/health` — fails fast if the FGS isn't reachable.
2. POSTs `/v1/infer` with `maxTokens=64`.
3. Parses the SSE stream:
   - `event: token` chunks → concatenated to stdout.
   - `event: done` → finish reason, token count, tokens/sec.
   - `event: error` → tagged exit-2.
4. Exit codes:
   - **0** — health + non-empty inference in < 60 s.
   - **1** — network unreachable.
   - **2** — engine returned an error event.
   - **3** — timeout.

A 30-token response in < 30 s is the smoke test bar.

## The stress test (`phase1_stress_test.sh`)

The full Phase 1 acceptance gate. Two-phone scenario, 10 prompts,
mid-job outage, recovery.

```bash
SERVER_DEVICE="<server adb serial>" \
CLIENT_DEVICE="<client adb serial>" \
SERVER_IP="<server phone ip>" \
APK_PATH="app/build/outputs/apk/debug/app-arm64-v8a-debug.apk" \
bash scripts/phase1_stress_test.sh
```

The script:

1. Installs the slim debug APK on the server phone.
2. Starts the FGS foreground service.
3. Sends 10 sequential prompts from the localhost client.
4. Disables the server's Wi-Fi + data (`svc wifi disable`,
   `svc data disable`).
5. Asserts the client detects the outage within 15 s (via logcat
   on the client).
6. Re-enables Wi-Fi + data.
7. Asserts the server's `/v1/health` returns 200 within 30 s.
8. Sends another 10 prompts.

Pass criteria (Phase 1 acceptance):

- **10 / 10 prompts** return non-empty text before the outage.
- **Client detects outage** within 15 s.
- **Server recovers** within 30 s of Wi-Fi re-enable.
- **Second 10 / 10 prompts** after recovery.

## What we are NOT testing yet

- Real-mesh GPU offload (`/v1/infer` with `needsGpu` hint).
- Shard hosting + cross-device layer range allocation.
- HTTP/2 multiplexing, complex proxy chains.
- Multi-modal requests (image / audio).
- Concurrent client throughput (10 / 10 is sequential).

These are Phase 1.5 / Phase 2 work; the gate is smoke-test +
sequential-cluster-stress at the threshold of "the FGS works on a
phone".
