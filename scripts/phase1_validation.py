#!/usr/bin/env python3
"""
Phase 1.1 hardware-validation entry point.

Drives a single on-device inference session against a phone running
the Meshlit FGS. Polls `/v1/health`, posts `/v1/infer`, and parses
the SSE stream (events: token, done, error) to confirm the on-device
LLM runtime produces text.

Usage:
  python3 scripts/phase1_validation.py --server <phone_ip>:8080 \\
      [--prompt "What is 2 + 2?"] [--max-tokens 64] [--timeout 60]

Exit code:
  0 on full success (non-empty text within `--timeout`).
  1 on health-check failure or network error.
  2 on inference failure (engine returned an `error` event).
  3 on timeout.

This script is the canonical Phase 1 hardware validation entry
point. Land it as a one-commit, run it on real hardware before
treating Phase 1 as shipped.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.request
import urllib.error
from typing import Iterator

USER_AGENT = "meshlit-phase1-validation/1.1"


def fetch_health(server: str, timeout_s: float = 5.0) -> dict:
    """GET /v1/health. Returns parsed JSON. Raises on network error."""
    url = f"http://{server}/v1/health"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=timeout_s) as resp:
        body = resp.read().decode("utf-8")
        return json.loads(body)


def post_infer(
    server: str,
    prompt: str,
    max_tokens: int = 64,
    timeout_s: float = 60.0,
) -> Iterator[dict]:
    """POST /v1/infer. Yields decoded SSE event payloads."""
    url = f"http://{server}/v1/infer"
    payload = {
        "prompt": prompt,
        "maxTokens": max_tokens,
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Content-Type": "application/json",
            "User-Agent": USER_AGENT,
            "Accept": "text/event-stream",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout_s) as resp:
        # SSE shape: each event is
        #   event: <name>\n
        #   data: <json>\n\n
        #
        # We yield the parsed JSON for each data line.
        event = ""
        for raw in resp:
            line = raw.decode("utf-8").rstrip("\r\n")
            if not line:
                if event:
                    yield event
                    event = ""
                continue
            if line.startswith("event:"):
                continue
            if line.startswith("data:"):
                payload_str = line[len("data:"):].strip()
                if payload_str:
                    event = json.loads(payload_str)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--server", required=True, help="Server host:port (e.g. 192.168.1.42:8080)")
    ap.add_argument("--prompt", default="What is 2 + 2?", help="LLM prompt")
    ap.add_argument("--max-tokens", type=int, default=64, help="Max tokens to generate")
    ap.add_argument("--timeout", type=float, default=60.0, help="Total timeout (s)")
    args = ap.parse_args()

    print(f"[phase1] target = http://{args.server}")
    started = time.monotonic()

    try:
        health = fetch_health(args.server, timeout_s=5.0)
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        print(f"[phase1] /v1/health unreachable: {e}", file=sys.stderr)
        return 1

    print(
        f"[phase1] /v1/health = status={health.get('status')} "
        f"engine={health.get('engine')} tier={health.get('capabilityTier')}"
    )
    if health.get("status") != "ok":
        print(f"[phase1] /v1/health returned non-ok status", file=sys.stderr)
        return 1

    collected = []
    deadline = started + args.timeout
    exit_status = 0
    try:
        for ev in post_infer(args.server, args.prompt, args.max_tokens):
            kind = ev.get("kind") or ev.get("type")
            if kind == "token":
                text = ev.get("text") or ev.get("delta") or ""
                if text:
                    sys.stdout.write(text)
                    sys.stdout.flush()
                    collected.append(text)
            elif kind == "done":
                finish = ev.get("finishReason") or ev.get("finish_reason") or "stop"
                tps = ev.get("tokensPerSecond") or ev.get("tokens_per_second") or 0.0
                gen = ev.get("generatedTokens") or ev.get("generated_tokens") or 0
                print(
                    f"\n[phase1] done: finish={finish} "
                    f"tokens={gen} tps={tps:.1f}"
                )
                break
            elif kind == "error":
                tag = ev.get("tag") or "unknown"
                msg = ev.get("message") or ""
                print(f"\n[phase1] engine error: {tag}: {msg}", file=sys.stderr)
                return 2
            if time.monotonic() > deadline:
                print(f"\n[phase1] timed out after {args.timeout}s", file=sys.stderr)
                return 3
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        print(f"\n[phase1] /v1/infer failed: {e}", file=sys.stderr)
        return 1

    elapsed = time.monotonic() - started
    full = "".join(collected).strip()
    if not full:
        print(f"[phase1] inference returned 0 tokens", file=sys.stderr)
        return 2

    print(f"[phase1] OK — {len(full)} chars in {elapsed:.1f}s")
    return exit_status


if __name__ == "__main__":
    sys.exit(main())
