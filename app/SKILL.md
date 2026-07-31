---
name: android-foreground-services
description: Rules and gotchas for running long-lived background work on Android — foreground service types, the Android 15+ 6-hour/24-hour runtime cap on dataSync/mediaProcessing services, wakelocks, and Low Memory Killer behavior. Use this any time you're writing, reviewing, or debugging a worker-node service, an inference service, an MCP server service, or anything that needs to survive the app being backgrounded — even if the task looks like "just add a service," check this first, because the naive approach silently breaks on modern Android.
---

# Android Foreground Services for Long-Running Cluster Nodes

Every cluster node (inference host, MCP tool server, monitor) needs to keep
running while the app is backgrounded or the screen is off. On modern
Android this is a real constraint, not a formality — read this before adding
any background work.

## The core rule

A foreground service is **a controlled execution channel, not a background
freedom pass.** It requires a persistent user-visible notification, and
even then the OS can still throttle or time it out depending on service type
and OS version.

## Foreground service types and their limits

Declare an explicit type in the manifest (`android:foregroundServiceType`).
For this project, the relevant types are typically `dataSync` (job
queue/relay work) and possibly `mediaProcessing` (if framing inference as
media processing) — but note the constraint below applies to both:

- **On apps targeting Android 15+ (API 35), `dataSync` and
  `mediaProcessing` foreground services are capped at 6 hours of runtime
  within any 24-hour rolling window.** When the cap is hit, the system calls
  `Service.onTimeout(int, int)`. You must implement this callback and either
  gracefully checkpoint/stop or restart cleanly — do not assume "foreground
  service" means indefinite runtime.
- `shortService` type has even tighter limits — don't use it for anything
  that needs to outlive a single short burst of work.
- Background jobs launched *from* a foreground service (via `JobScheduler`,
  `WorkManager`, `DownloadManager`) still consume their own runtime quotas —
  starting a foreground service doesn't exempt scheduled jobs from their
  normal budgets. For genuine user-initiated transfers, use a
  user-initiated data transfer job, which is exempt from ordinary quotas.

**Design implication for this project:** treat the inference/MCP service as
restart-resilient from Phase 1, not Phase 4. Persist enough state (current
job, queue position) that `onTimeout()` → clean stop → restart loses no work
beyond the in-flight job, which the router should already be retrying per
the store-and-forward queue design.

## Starting a foreground service from the background

You generally **cannot** start a new foreground service while your app has
no visible UI, with specific exceptions:
- Transitioning from a visible Activity.
- A high-priority FCM message (the OS can silently downgrade priority if
  it decides the message isn't time-sensitive — don't rely on this as a
  guaranteed wake mechanism for cluster coordination).
- A few narrow system broadcasts (timezone/locale change, NFC transaction).
- Apps with device-owner/profile-owner status, or using Companion Device
  Manager with the relevant background-start permissions.

**Design implication:** a node that's fully backgrounded (not just screen
off, but the app process itself killed) generally cannot be woken remotely
to rejoin the cluster without one of the above triggers. Don't design a
feature that assumes silent remote wake of a fully-killed app — the
heartbeat/staleness eviction logic (Phase 2) needs to treat "app process
killed" the same as "network unreachable": mark unavailable, evict, move on.

## BOOT_COMPLETED restrictions

Foreground services started from a `BOOT_COMPLETED` receiver cannot use
several service types (`dataSync`, `camera`, `mediaPlayback`, `phoneCall`,
`mediaProjection`, etc.) — attempting to throws an exception. If you want a
node to auto-rejoin the cluster on device boot, start a lightweight service
type first and transition to the real worker service once the app has a
visible/foreground context.

## Wakelocks

- Acquire the minimum wakelock level needed (`PARTIAL_WAKE_LOCK` is usually
  sufficient for CPU-bound inference; you don't need the screen on).
- Always pair `acquire()`/`release()` with a timeout as a safety net
  (`acquire(timeoutMs)`), even if you also release explicitly — a crash
  between acquire and release otherwise drains the battery indefinitely.
- A held wakelock does not exempt you from the foreground-service runtime
  caps above — it only prevents CPU sleep, it doesn't grant extra service
  runtime.

## Low Memory Killer (LMK) and RAM headroom

There's no single documented universal RAM percentage before Android kills
a background/low-priority process — it varies by OEM, Android version, and
what else is running. Don't hardcode a threshold like "never exceed 75% of
RAM."

Instead:
- Query actual headroom at model-load time via
  `ActivityManager.getMemoryInfo()` (check `availMem`, `lowMemory`,
  `threshold`).
- Treat the foreground-service notification and active state as your best
  defense against LMK, not a RAM percentage guess.
- Log every LMK-style kill (service `onDestroy` without a corresponding
  clean stop) — this is exactly the kind of per-device failure pattern the
  Phase 5 adaptive scheduler should learn from ("this node's OS kills the
  service around 85% RAM utilization reliably").

## Testing requirement

Emulators do not reliably reproduce OEM-specific battery/thermal/background
policies (this is explicitly worse on some manufacturers' custom Android
skins than on stock/Pixel builds). Anything built against this skill must be
verified on a **physical device**, ideally more than one OEM if available in
the device pool, before being considered done.
