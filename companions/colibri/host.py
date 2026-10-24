#!/usr/bin/env python3
"""Explicit host setup/launch. Never called automatically by Meshlit.

Only source is fetched. A human separately builds Colibri and obtains licensed
model weights. The listener is loopback; remote clients need an authenticated
TLS gateway. No credentials on the command line, arbitrary commands or shell.
"""
import argparse
import os
from pathlib import Path
import subprocess

REVISION = "bf2442915d6e3dd4cdfd2eb9c2a3d2aa44a25850"
UPSTREAM = "https://github.com/JustVugg/colibri.git"


def verified_source(path: Path) -> Path:
    root = path.expanduser().resolve(strict=True)
    actual = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    if actual != REVISION:
        raise ValueError("Colibri checkout does not match the reviewed revision")
    # Build products are untracked. Tracked launch and engine source must match.
    subprocess.run(["git", "-C", str(root), "diff", "--exit-code", "HEAD", "--"], check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return root


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    setup = sub.add_parser("fetch", help="Fetch pinned source only; no build or model download")
    setup.add_argument("--source", type=Path, required=True)
    serve = sub.add_parser("serve", help="Run a human-built Colibri host on loopback")
    serve.add_argument("--source", type=Path, required=True)
    serve.add_argument("--model", type=Path, required=True)
    serve.add_argument("--model-id", required=True)
    serve.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    if args.action == "fetch":
        root = args.source.expanduser().absolute()
        if root.exists():
            raise ValueError("Choose a new directory; existing files are preserved")
        root.mkdir(parents=True)
        subprocess.run(["git", "init", str(root)], check=True)
        subprocess.run(["git", "-C", str(root), "remote", "add", "origin", UPSTREAM], check=True)
        subprocess.run(["git", "-C", str(root), "fetch", "--depth=1", "--no-tags", "origin", REVISION], check=True)
        subprocess.run(["git", "-C", str(root), "checkout", "--detach", "FETCH_HEAD"], check=True)
        verified_source(root)
        print("Pinned source ready. No compiler, engine binary or model has been installed.")
        return
    root = verified_source(args.source)
    model = args.model.expanduser().resolve(strict=True)
    key = os.environ.get("COLI_API_KEY", "")
    if len(key) < 32 or len(key) > 4096 or any(not 33 <= ord(c) <= 126 for c in key):
        raise ValueError("Set a private COLI_API_KEY of 32–4096 non-control, non-space characters")
    if not 1024 <= args.port <= 65535 or not args.model_id or len(args.model_id) > 200 or any(c.isspace() or ord(c) < 32 for c in args.model_id):
        raise ValueError("Invalid port or model identifier")
    launcher = root / "c" / "coli"
    if not launcher.is_file() or not os.access(launcher, os.X_OK):
        raise ValueError("Pinned Colibri launcher is not available")
    env = os.environ.copy()
    env["COLI_MODEL"] = str(model)
    os.chdir(launcher.parent)
    os.execve(str(launcher), [str(launcher), "serve", "--host", "127.0.0.1", "--port", str(args.port),
                             "--model-id", args.model_id], env)


if __name__ == "__main__":
    main()
