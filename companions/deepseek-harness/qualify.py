#!/usr/bin/env python3
"""Optional real-runtime smoke check. Empty workspace, no model requests, no raw host logs."""
import argparse
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import queue
import re
import signal
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
from launch import VERSION, build_launch


def qualify(runtime, port):
    with tempfile.TemporaryDirectory(prefix="meshlit-harness-smoke-") as directory:
        root = Path(directory)
        workspace = root / "workspace"
        workspace.mkdir()
        command, environment, cwd = build_launch(runtime, root / "home", workspace, port=port)
        dump = subprocess.run(command[:command.index("--host")] + ["--dump-config"], env=environment,
                              cwd=cwd, capture_output=True, timeout=60, check=True).stdout.decode()
        for expression in [r'id: session-log-deepseek\n[^#]*enabled: false',
                           r'id: session-telemetry-otel\n[^#]*mode: DISABLED',
                           r'id: desktop-product-telemetry\n[^#]*disabled: true']:
            if not re.search(expression, dump):
                raise RuntimeError("Required privacy policy missing from effective runtime config")
        child = subprocess.Popen(command, env=environment, cwd=cwd, stdout=subprocess.PIPE,
                                 stderr=subprocess.STDOUT, start_new_session=os.name != "nt")
        lines = queue.Queue(maxsize=512)
        def consume():
            for line in child.stdout:
                try: lines.put_nowait(line[:16384].decode(errors="replace"))
                except queue.Full: pass
        reader = threading.Thread(target=consume, daemon=True)
        reader.start()
        try:
            deadline, login = time.monotonic() + 60, None
            while time.monotonic() < deadline and child.poll() is None:
                try: line = lines.get(timeout=0.25)
                except queue.Empty: continue
                match = re.search(r'http://127\.0\.0\.1:' + str(port) + r'/\?token=[A-Za-z0-9_-]+', line)
                if match: login = match.group(0); break
            if not login:
                raise RuntimeError("Harness did not announce readiness within 60 seconds (raw logs withheld)")
            base = f"http://127.0.0.1:{port}/"
            # Ignore system proxy variables even on machines using a corporate proxy.
            unauth = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            try:
                unauth.open(base, timeout=10)
                raise RuntimeError("Harness root unexpectedly accepted an unauthenticated client")
            except urllib.error.HTTPError as e:
                if e.code != 401: raise RuntimeError("Unexpected unauthenticated HTTP status") from None
            cookies = http.cookiejar.CookieJar()
            authenticated = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(cookies))
            with authenticated.open(login, timeout=10) as response:
                if response.status != 200 or response.geturl() != base:
                    raise RuntimeError("Token exchange did not redirect to the clean host root")
                html = response.read(2_000_000)
                if b'<html' not in html.lower(): raise RuntimeError("Host did not serve its real HTML application")
            with authenticated.open(base, timeout=10) as response:
                if response.status != 200: raise RuntimeError("Authenticated cookie was not retained")
            if not list(cookies): raise RuntimeError("No browser session cookie was issued")
            with_authenticated = authenticated
            request = urllib.request.Request(base + "api/", headers={"Origin": "https://unapproved.example"})
            try:
                with_authenticated.open(request, timeout=10)
                raise RuntimeError("Harness accepted a foreign Origin")
            except urllib.error.HTTPError as e:
                if e.code != 403: raise RuntimeError("Unexpected Origin rejection status") from None
            return {"version": VERSION, "runtimeStarted": True, "unauthenticatedStatus": 401,
                    "authenticatedStatus": 200, "foreignOriginApiStatus": 403, "tokenRemovedByRedirect": True,
                    "sessionLogUpload": False, "sessionTelemetry": "DISABLED", "modelRequests": 0,
                    "uiHtmlSha256": hashlib.sha256(html).hexdigest(),
                    "limits": ["HTTP loopback only", "No provider call or agent tool execution", "Not an Android WebView or Studio UI qualification"]}
        finally:
            if child.poll() is None:
                if os.name != "nt": os.killpg(child.pid, signal.SIGTERM)
                else: child.terminate()
                try: child.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    if os.name != "nt": os.killpg(child.pid, signal.SIGKILL)
                    else: child.kill()
                    child.wait()
            child.stdout.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", required=True)
    parser.add_argument("--port", type=int, default=19381)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        evidence = qualify(args.runtime, args.port)
    except Exception as error:
        # Exceptions from HTTP libraries can embed the login URL. Never print them.
        raise SystemExit("Harness qualification failed (" + type(error).__name__ + "). No provider requests were made.") from None
    args.output.write_text(json.dumps(evidence, indent=2) + "\n")
    print("Real Harness runtime, login exchange and origin checks passed. No provider requests.")
