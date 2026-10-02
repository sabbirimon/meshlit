#!/usr/bin/env python3
"""
Native-engine benchmark skeleton (Slice 1).

Purpose
-------
This script exercises the inference path that's actually shipping today
(``engineTag == "runanywhere"`` — the RunAnywhere SDK wrapping libllama.so)
and records:

  - load_ms           — wall-clock from "Load model" tap to /v1/health reporting Ready
  - first_token_ms    — wall-clock from infer() to first SSE token
  - tokens_per_sec    — sustained decode throughput over the run
  - peak_mem_mb       — high-water RSS via /proc/self/status (the FGS process)
  - engine_tag        — must be "runanywhere"; we assert this is NOT "llama.cpp"

What this script does NOT pretend to do
----------------------------------------
- It does NOT exercise an owned Meshlit llama.cpp JNI build. ``libmeshlit_inference.so``
  does not exist; the Kotlin declarations in ``LlamaCppInferenceEngine`` are stubs
  whose ``loadNativeLibrary()`` call fails at runtime, so ``pickEngine()`` silently
  falls back to the RunAnywhere path. Slice 2 (owned llama.cpp engine) is the
  milestone that will let this script also target ``engineTag == "llama.cpp"``.
- It does NOT swap engines at runtime. Both engines are exercised sequentially
  via separate app installs in Slice 2; for now, only the RunAnywhere path is
  real.
- It does NOT touch the Devices-screen UI. It uses the wire-level
  ``/v1/health`` + ``/v1/infer`` (SSE) endpoints on :8080 only.

Usage
-----
    SERVER_IP=10.0.0.42 PROMPT="What is 2 + 2?" ./scripts/native_benchmark.py

Environment
-----------
    SERVER_IP        required — IP of the phone running the FGS
    ADB_SERIAL       optional — adb device serial (default: first attached)
    PROMPT           optional — prompt to send (default: "Write a haiku about latency.")
    NUM_TOKENS       optional — soft target; inference runs until NATURAL_STOP or MAX_TOKENS (default: 128)
    WARMUP           optional — run a warmup infer first to amortise JNI init (default: 1)
    OUTPUT_JSON      optional — path to write a structured report (default: stdout only)

Exit codes
----------
    0   benchmark ran; engine is the expected RunAnywhere path
    2   engine tag mismatch (someone shipped a real llama.cpp path — update this script!)
    3   server unreachable / health endpoint did not return Ready
    4   SSE stream did not deliver a single token within the timeout
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from typing import Optional

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

SERVER_IP: str = os.environ.get("SERVER_IP", "").strip()
ADB_SERIAL: Optional[str] = os.environ.get("ADB_SERIAL", "").strip() or None
PROMPT: str = os.environ.get("PROMPT", "Write a haiku about latency.")
NUM_TOKENS: int = int(os.environ.get("NUM_TOKENS", "128"))
WARMUP: int = int(os.environ.get("WARMUP", "1"))
OUTPUT_JSON: Optional[str] = os.environ.get("OUTPUT_JSON", "").strip() or None
HEALTH_TIMEOUT_S: float = 30.0
FIRST_TOKEN_TIMEOUT_S: float = 60.0

# Slice 1 invariant: we are benchmarking the RunAnywhere path, NOT an owned
# Meshlit llama.cpp JNI build. When Slice 2 lands and the owned engine becomes
# selectable, this constant becomes the "known-good" baseline to compare against.
EXPECTED_ENGINE_TAG: str = "runanywhere"
FORBIDDEN_ENGINE_TAG: str = "llama.cpp"  # would indicate the stub silently started working


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def log(*args: object) -> None:
    print("[native-bench]", *args, file=sys.stderr, flush=True)


def adb_run(args: list[str], timeout: float = 30.0) -> str:
    cmd = (["adb", "-s", ADB_SERIAL] if ADB_SERIAL else ["adb"]) + args
    log("$", " ".join(cmd))
    out = subprocess.run(
        cmd,
        check=True,
        capture_output=True,
        text=True,
        timeout=timeout,
    )
    return out.stdout


def http_get_json(url: str, timeout: float) -> dict:
    with urllib.request.urlopen(url, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


def wait_until_ready(base: str, timeout_s: float) -> tuple[float, dict]:
    """Poll /v1/health until ``modelLoaded == true`` or timeout. Returns (elapsed_s, payload)."""
    deadline = time.monotonic() + timeout_s
    started = time.monotonic()
    last_payload: dict = {}
    while time.monotonic() < deadline:
        try:
            payload = http_get_json(f"{base}/v1/health", timeout=2.0)
            last_payload = payload
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            log(f"health probe failed: {exc!r} — retrying")
            time.sleep(1.0)
            continue
        if payload.get("modelLoaded"):
            return (time.monotonic() - started), payload
        time.sleep(1.0)
    raise TimeoutError(f"server did not report Ready within {timeout_s}s; last={last_payload}")


def stream_infer(base: str, prompt: str, max_tokens: int) -> tuple[float, float, int, float, str]:
    """
    POST /v1/infer (SSE) and collect tokens. Returns:
        (first_token_ms, total_ms, tokens_count, tokens_per_sec, finish_reason)
    """
    body = json.dumps({"prompt": prompt, "max_tokens": max_tokens}).encode("utf-8")
    req = urllib.request.Request(
        f"{base}/v1/infer",
        data=body,
        headers={"Content-Type": "application/json", "Accept": "text/event-stream"},
        method="POST",
    )
    started = time.monotonic()
    first_token_at: Optional[float] = None
    token_count = 0
    finish_reason = "UNKNOWN"
    with urllib.request.urlopen(req, timeout=FIRST_TOKEN_TIMEOUT_S + 60) as resp:
        # Naive SSE line parser — assumes the wire format documented in
        # core-inference/net/InferenceWire.kt. Sufficient for a benchmark.
        for raw in resp:
            line = raw.decode("utf-8", errors="replace").rstrip("\n")
            if not line.startswith("data:"):
                continue
            payload = line[len("data:"):].strip()
            if payload == "[DONE]":
                finish_reason = "DONE"
                break
            try:
                evt = json.loads(payload)
            except json.JSONDecodeError:
                continue
            kind = evt.get("event")
            if kind == "token":
                if first_token_at is None:
                    first_token_at = time.monotonic()
                token_count += 1
            elif kind == "done":
                finish_reason = evt.get("finishReason", "DONE")
                break
            elif kind == "error":
                finish_reason = "ERROR:" + str(evt.get("message", ""))
                break
    total_ms = (time.monotonic() - started) * 1000.0
    first_token_ms = ((first_token_at - started) * 1000.0) if first_token_at else float("nan")
    tps = (token_count / max(total_ms / 1000.0, 1e-6)) if token_count else 0.0
    return first_token_ms, total_ms, token_count, tps, finish_reason


def peak_mem_mb() -> Optional[float]:
    """Read /proc/<pid>/status VmHWM via adb shell. None if not available."""
    if not ADB_SERIAL and not _has_attached_device():
        return None
    try:
        pid_out = adb_run(["shell", "pidof", "com.meshlit"], timeout=5.0).strip()
    except subprocess.CalledProcessError:
        return None
    if not pid_out:
        return None
    try:
        status = adb_run(["shell", "cat", f"/proc/{pid_out}/status"], timeout=5.0)
    except subprocess.CalledProcessError:
        return None
    for ln in status.splitlines():
        if ln.startswith("VmHWM:"):
            kb = int(ln.split()[1])
            return kb / 1024.0
    return None


def _has_attached_device() -> bool:
    out = subprocess.run(
        ["adb", "devices"],
        capture_output=True,
        text=True,
        timeout=5.0,
    )
    return any(line.endswith("\tdevice") for line in out.stdout.splitlines()[1:])


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def main() -> int:
    if not SERVER_IP:
        log("ERROR — SERVER_IP env var is required (IP of the phone running the FGS).")
        return 3

    base = f"http://{SERVER_IP}:8080"
    log(f"target = {base}")
    log(f"prompt = {PROMPT!r}")
    log(f"max_tokens = {NUM_TOKENS}  warmup_runs = {WARMUP}")

    # Health probe — confirm server up + model loaded.
    log("probing /v1/health ...")
    try:
        load_ms, health = wait_until_ready(base, HEALTH_TIMEOUT_S)
    except TimeoutError as exc:
        log(f"FAIL — {exc}")
        return 3
    load_ms *= 1000.0  # to ms

    engine_tag = str(health.get("engineTag", ""))
    log(f"health.engineTag = {engine_tag!r} (load_ms = {load_ms:.1f})")

    if engine_tag == FORBIDDEN_ENGINE_TAG:
        log(
            f"FAIL — engineTag == 'llama.cpp' but the owned JNI build does not ship. "
            f"This script's invariant is broken; either the stub started working "
            f"(great — update EXPECTED_ENGINE_TAG) or someone rigged the wire."
        )
        return 2
    if engine_tag != EXPECTED_ENGINE_TAG:
        log(
            f"FAIL — engineTag == {engine_tag!r} (expected {EXPECTED_ENGINE_TAG!r}). "
            f"Update EXPECTED_ENGINE_TAG in this script if a new engine is now canonical."
        )
        return 2

    # Warmup — amortises JNI init, quant kernel warmup, etc.
    for i in range(WARMUP):
        log(f"warmup {i + 1}/{WARMUP}")
        try:
            _, _, _, _, fr = stream_infer(base, "ping", max_tokens=8)
        except (urllib.error.URLError, TimeoutError) as exc:
            log(f"WARN — warmup failed: {exc!r}")
        log(f"warmup {i + 1} finish = {fr}")

    # Real run.
    log("benchmark run ...")
    first_token_ms, total_ms, n, tps, finish = stream_infer(base, PROMPT, NUM_TOKENS)
    mem = peak_mem_mb()
    log(f"first_token = {first_token_ms:.1f} ms")
    log(f"total       = {total_ms:.1f} ms")
    log(f"tokens      = {n}")
    log(f"tps         = {tps:.2f}")
    log(f"peak_mem    = {mem if mem is not None else 'n/a'} MB")
    log(f"finish      = {finish}")

    report = {
        "slice": 1,
        "engineTag": engine_tag,
        "loadMs": load_ms,
        "firstTokenMs": first_token_ms,
        "totalMs": total_ms,
        "tokens": n,
        "tokensPerSec": tps,
        "peakMemMb": mem,
        "finishReason": finish,
        "prompt": PROMPT,
        "maxTokens": NUM_TOKENS,
        "note": (
            "Owned Meshlit llama.cpp JNI is not yet implemented — Slice 2 milestone. "
            "Engine selection still falls back to the RunAnywhere SDK."
        ),
    }
    out = json.dumps(report, indent=2)
    if OUTPUT_JSON:
        with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
            f.write(out)
        log(f"wrote report → {OUTPUT_JSON}")
    else:
        print(out)
    return 0 if n > 0 else 4


if __name__ == "__main__":
    sys.exit(main())
