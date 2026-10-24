#!/usr/bin/env python3
"""Measure a running Meshlit v1 HTTP/SSE endpoint; never count chunks as tokens.

Forward the phone's existing server explicitly (this does not start or load it):
    adb -s DEVICE forward tcp:18080 tcp:8080
    python3 scripts/native_benchmark.py --engine runanywhere --output report.json

HTTP is restricted to loopback. Remote endpoints require HTTPS with system trust.
An endpoint must already have a loaded model. Readiness wait is not model-load
latency. Output includes neither prompt nor generated text. App-UID engine/load/
memory qualification lives in DeviceCoreBenchmarkTest, not this wire client.
"""
from __future__ import annotations

import argparse
import ipaddress
import json
import math
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

MAX_EVENT_BYTES = 65536
MAX_STREAM_BYTES = 2 * 1024 * 1024


class BenchmarkError(ValueError):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise BenchmarkError("Redirect refused: benchmark content must stay on the selected endpoint")


OPENER = urllib.request.build_opener(NoRedirect())


def validate_base(base: str) -> str:
    parsed = urllib.parse.urlsplit(base)
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        raise BenchmarkError("Use an HTTP loopback or HTTPS endpoint")
    if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
        raise BenchmarkError("Endpoint must be an origin without credentials, path, query or fragment")
    try:
        parsed.port
        loopback = ipaddress.ip_address(parsed.hostname).is_loopback
    except ValueError:
        loopback = parsed.hostname == "localhost"
    if parsed.scheme == "http" and not loopback:
        raise BenchmarkError("Plain HTTP requires loopback; use an explicit adb forward or trusted HTTPS")
    return base.rstrip("/")


def get_json(base: str, route: str) -> dict:
    with OPENER.open(base + route, timeout=5) as response:
        raw = response.read(MAX_EVENT_BYTES + 1)
    if len(raw) > MAX_EVENT_BYTES:
        raise BenchmarkError("Endpoint metadata exceeds limit")
    value = json.loads(raw)
    if not isinstance(value, dict):
        raise BenchmarkError("Endpoint metadata is not an object")
    return value


def wait_ready(base: str, engine: str, timeout_s: float = 30) -> tuple[float, dict]:
    start = time.monotonic()
    while time.monotonic() - start < timeout_s:
        try:
            health = get_json(base, "/v1/health")
            model = get_json(base, "/v1/model")
        except (urllib.error.URLError, TimeoutError):
            time.sleep(0.25)
            continue
        if health.get("status") == "ok" and model.get("loaded") is True:
            if health.get("engineTag") != engine:
                raise BenchmarkError("Loaded engine differs from the explicitly selected engine")
            return (time.monotonic() - start) * 1000, model
        time.sleep(0.25)
    raise BenchmarkError("No loaded model reported before readiness deadline")


def events(lines, deadline: float):
    """Parse bounded SSE event/data fields, including comments and multiple data lines."""
    kind = "message"
    data = []
    event_bytes = stream_bytes = 0
    for raw in lines:
        if time.monotonic() > deadline:
            raise BenchmarkError("Inference stream deadline exceeded")
        stream_bytes += len(raw)
        event_bytes += len(raw)
        if event_bytes > MAX_EVENT_BYTES or stream_bytes > MAX_STREAM_BYTES:
            raise BenchmarkError("Inference stream exceeds size limit")
        line = raw.decode("utf-8", errors="strict").rstrip("\r\n")
        if not line:
            if data:
                yield kind, json.loads("\n".join(data))
            kind, data, event_bytes = "message", [], 0
        elif line.startswith(":"):
            continue
        else:
            field, _, value = line.partition(":")
            value = value.removeprefix(" ")
            if field == "event":
                kind = value
            elif field == "data":
                data.append(value)
    # Meshlit's contract requires an event delimiter and explicit done event.
    if data:
        raise BenchmarkError("Truncated SSE event")


def finite_nonnegative(value, field: str, nullable: bool = True):
    if value is None and nullable:
        return None
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
        raise BenchmarkError(f"Invalid engine usage field: {field}")
    return value


def measure_stream(lines, started: float, max_tokens: int, deadline: float) -> dict:
    first = None
    chunks = characters = 0
    for kind, payload in events(lines, deadline):
        if not isinstance(payload, dict):
            raise BenchmarkError("SSE payload must be an object")
        if kind == "token":
            text = payload.get("text")
            if not isinstance(text, str):
                raise BenchmarkError("Text event must contain a string")
            if text:
                if first is None:
                    first = time.monotonic()
                chunks += 1
                characters += len(text)
        elif kind == "error":
            raise BenchmarkError("Inference endpoint reported an error")
        elif kind == "done":
            finish = payload.get("finishReason")
            if finish not in ("natural", "max_tokens", "stop_sequence"):
                raise BenchmarkError("Inference did not finish successfully")
            count = finite_nonnegative(payload.get("generatedTokens"), "generatedTokens")
            if count is not None and (not isinstance(count, int) or not 1 <= count <= max_tokens):
                raise BenchmarkError("Engine token count exceeds request bound or is not an integer")
            duration = finite_nonnegative(payload.get("totalDurationMs"), "totalDurationMs", nullable=False)
            rate = finite_nonnegative(payload.get("tokensPerSecond"), "tokensPerSecond")
            if not characters:
                raise BenchmarkError("Completed inference produced no streamed text")
            return {
                "firstTextEventMs": None if first is None else (first - started) * 1000,
                "requestWallMs": (time.monotonic() - started) * 1000,
                "textEventCount": chunks, "outputCharacters": characters,
                "generatedTokens": count, "engineDurationMs": duration,
                "engineTokensPerSecond": rate, "finishReason": finish,
            }
        else:
            raise BenchmarkError("Unknown inference event type")
    raise BenchmarkError("Inference stream ended before explicit completion")


def stream_infer(base: str, prompt: str, max_tokens: int) -> dict:
    body = json.dumps({"prompt": prompt, "maxTokens": max_tokens, "temperature": 0, "seed": 42}).encode()
    request = urllib.request.Request(base + "/v1/infer", data=body,
        headers={"Content-Type": "application/json", "Accept": "text/event-stream"}, method="POST")
    started = time.monotonic()
    with OPENER.open(request, timeout=15) as response:
        if response.headers.get_content_type() != "text/event-stream":
            raise BenchmarkError("Endpoint did not return SSE")
        return measure_stream(iter(lambda: response.readline(MAX_EVENT_BYTES + 1), b""), started, max_tokens, started + 120)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="http://127.0.0.1:18080")
    parser.add_argument("--engine", required=True, choices=("runanywhere", "llama-native-local", "onnx-ort", "llama-rpc-layer"))
    parser.add_argument("--prompt", default="Say hello in one short sentence.")
    parser.add_argument("--max-tokens", type=int, default=16)
    parser.add_argument("--samples", type=int, default=3)
    parser.add_argument("--warmups", type=int, default=1)
    parser.add_argument("--output", type=Path, required=True, help="New file; refuses to overwrite existing evidence")
    args = parser.parse_args(argv)
    try:
        base = validate_base(args.base)
        if not (1 <= args.max_tokens <= 256 and 1 <= args.samples <= 10 and 0 <= args.warmups <= 3 and 1 <= len(args.prompt) <= 12000):
            raise BenchmarkError("Request exceeds bounded benchmark limits")
        if args.output.exists():
            raise BenchmarkError("Output already exists; select a new evidence file")
        ready_ms, model = wait_ready(base, args.engine)
        for _ in range(args.warmups):
            stream_infer(base, args.prompt, args.max_tokens)
        samples = [stream_infer(base, args.prompt, args.max_tokens) for _ in range(args.samples)]
        report = {"format": "meshlit-wire-benchmark/2", "engineTag": args.engine,
            "readinessWaitMs": ready_ms, "contextSize": model.get("contextSize"),
            "maxTokens": args.max_tokens, "warmups": args.warmups, "samples": samples,
            "note": "Wire latency includes transport; text events are not tokens; model load and process memory are unmeasured."}
        fd = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as out:
            json.dump(report, out, indent=2, allow_nan=False)
            out.write("\n")
        print(json.dumps({"status": "passed", "report": str(args.output), "sampleCount": len(samples)}))
        return 0
    except (BenchmarkError, OSError, ValueError) as error:
        # Do not echo endpoint replies, prompts, or generated text on failure.
        print(json.dumps({"status": "failed", "errorClass": type(error).__name__}))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
