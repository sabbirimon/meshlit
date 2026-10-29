#!/usr/bin/env python3
"""Launch an explicitly installed, pinned Harness. Never downloads or installs it."""
import argparse
import ipaddress
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

VERSION = "0.2.2-alpha.2"


def build_launch(runtime, home, workspace, port=3080, host="127.0.0.1", cert=None, key=None, writable=False):
    runtime, home, workspace = (Path(p).expanduser().resolve() for p in (runtime, home, workspace))
    if not workspace.is_dir():
        raise ValueError("Workspace directory does not exist")
    if not 1 <= port <= 65535:
        raise ValueError("Invalid port")
    address = ipaddress.ip_address(host)
    if address.is_unspecified or address.is_multicast:
        raise ValueError("Use one concrete host address")
    if bool(cert) != bool(key) or (str(address) not in {"127.0.0.1", "::1"} and not (cert and key)):
        raise ValueError("A network listener requires both a trusted TLS certificate and key")
    package = runtime / "node_modules/@deepseek-ai/dsh"
    metadata = json.loads((package / "package.json").read_text())
    if metadata.get("name") != "@deepseek-ai/dsh" or metadata.get("version") != VERSION:
        raise ValueError("Installed Harness version differs from the reviewed pin")
    entry = package / "lib/bin.js"
    if not entry.is_file():
        raise ValueError("Installed Harness launcher is missing")
    home.mkdir(mode=0o700, parents=True, exist_ok=True)
    if os.name != "nt":
        if home.stat().st_uid != os.getuid():
            raise ValueError("Harness home must belong to this user")
        home.chmod(0o700)
    # Command-line overlays have higher precedence than saved profile patches.
    patch = home / "meshlit-privacy.patch.yml"
    if patch.is_symlink():
        raise ValueError("Refusing a symlinked policy file")
    content = """- id: session-log-deepseek
  config:
    enabled: false
- id: session-telemetry-otel
  config:
    mode: DISABLED
- id: desktop-product-telemetry
  disabled: true
"""
    # Atomic policy replacement; never modify upstream package files or owner profiles.
    import tempfile
    fd, temporary = tempfile.mkstemp(prefix=".meshlit-policy-", dir=home)
    try:
        with os.fdopen(fd, "w") as stream:
            stream.write(content)
        os.replace(temporary, patch)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    node = shutil.which("node")
    if not node:
        raise ValueError("Node.js >=24.9 is required")
    version = subprocess.check_output([node, "--version"], text=True, timeout=5).strip().lstrip("v").split(".")
    if tuple(map(int, version[:2])) < (24, 9):
        raise ValueError("Node.js >=24.9 is required")
    command = [node, str(entry), "--profile", "web", "--patch", str(patch), "--host", str(address), "--port", str(port), "--no-open"]
    if cert:
        for flag, value in (("--tls-cert", cert), ("--tls-key", key)):
            file = Path(value).expanduser().resolve()
            if not file.is_file():
                raise ValueError("TLS material is missing")
            command += [flag, str(file)]
    # Provider keys, npm hooks, proxy variables, NODE_OPTIONS and home overrides are not inherited.
    environment = {k: os.environ[k] for k in ("PATH", "HOME", "TMPDIR", "LANG", "LC_ALL", "SYSTEMROOT", "USERPROFILE") if k in os.environ}
    environment.update(DSH_HOME=str(home), DSH_TELEMETRY_MODE="DISABLED",
                       DSH_PERMISSION_MODE="workspace-write" if writable else "read-only")
    return command, environment, workspace


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", required=True, help="Directory containing the reviewed npm installation")
    parser.add_argument("--home", required=True, help="Dedicated private Harness home, separate from ~/.dsh")
    parser.add_argument("--workspace", required=True)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3080)
    parser.add_argument("--tls-cert")
    parser.add_argument("--tls-key")
    parser.add_argument("--allow-workspace-write", action="store_true")
    args = parser.parse_args()
    try:
        command, environment, cwd = build_launch(args.runtime, args.home, args.workspace, args.port, args.host,
                                                 args.tls_cert, args.tls_key, args.allow_workspace_write)
    except (ValueError, OSError, subprocess.SubprocessError):
        parser.exit(2, "Harness setup failed. Check the pinned installation, Node version, workspace, private home and TLS paths.\n")
    print("Starting Harness. Its printed login URL is private; do not put it in logs or Git. Ctrl-C stops this host.", flush=True)
    child = subprocess.Popen(command, env=environment, cwd=cwd)
    try:
        return child.wait()
    except KeyboardInterrupt:
        child.terminate()
        try:
            return child.wait(timeout=10)
        except subprocess.TimeoutExpired:
            child.kill()
            return child.wait()


if __name__ == "__main__":
    sys.exit(main())
