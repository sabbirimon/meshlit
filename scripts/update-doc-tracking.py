#!/usr/bin/env python3
"""Build or verify the public documentation index and content hashes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
INDEX = "docs/DOCUMENTATION_STATUS.md"
MANIFEST = "docs/documentation-status.json"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--check", action="store_true")
    args = parser.parse_args()
    names = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", "*.md"], cwd=ROOT
    ).decode().split("\0")
    documents = []
    lines = ["# Public documentation", "", "[User guide](USER_GUIDE.md) · [Build instructions](../BUILD.md) · [Security](../SECURITY.md)", ""]
    for name in sorted(set(filter(None, names))):
        path = ROOT / name
        if name == INDEX or not path.is_file():
            continue
        documents.append({"path": name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
        link = os.path.relpath(path, ROOT / "docs").replace(os.sep, "/")
        lines.append(f"- [{name}](<{link}>)")
    outputs = {
        INDEX: "\n".join(lines) + "\n",
        MANIFEST: json.dumps({"schemaVersion": 2, "documents": documents}, indent=2) + "\n",
    }
    for name, content in outputs.items():
        path = ROOT / name
        if args.write:
            path.write_text(content, encoding="utf-8")
        elif not path.is_file() or path.read_text(encoding="utf-8") != content:
            raise SystemExit(f"Outdated public document index: {name}")
    print(f"Indexed {len(documents)} public documents")


if __name__ == "__main__":
    main()
