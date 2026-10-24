#!/usr/bin/env python3
"""Fetch pinned optional sources without resetting an existing checkout."""
import argparse
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCES = json.loads((ROOT / "vendored/sources.lock.json").read_text())["sources"]


def run(*args, cwd=None):
    return subprocess.run(args, cwd=cwd, check=True, text=True, capture_output=True).stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sources", nargs="+", choices=tuple(SOURCES))
    selected = parser.parse_args().sources
    for name in selected:
        source = SOURCES[name]
        destination = ROOT / "vendored" / name
        if destination.exists():
            revision = run("git", "rev-parse", "HEAD", cwd=destination)
            if revision != source["revision"]:
                raise SystemExit(f"{name}: existing checkout differs from the pin; inspect it before updating")
            print(f"{name}: already at {revision}")
            continue
        destination.mkdir(parents=True)
        run("git", "init", str(destination))
        run("git", "remote", "add", "origin", source["url"], cwd=destination)
        run("git", "fetch", "--depth=1", "origin", source["revision"], cwd=destination)
        run("git", "checkout", "--detach", "FETCH_HEAD", cwd=destination)
        print(f"{name}: fetched {source['revision']}")


if __name__ == "__main__":
    main()
