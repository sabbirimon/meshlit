#!/usr/bin/env python3
"""Build the pinned standalone llama-server without modifying Android JNI."""
import argparse
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jobs", type=int, default=2)
    args = parser.parse_args()
    if not 1 <= args.jobs <= 16:
        parser.error("jobs must be in 1..16")
    source = ROOT / "vendored/runanywhere-llama"
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
    expected = json.loads((ROOT / "vendored/sources.lock.json").read_text())["sources"]["runanywhere-llama"]["revision"]
    if revision != expected:
        raise SystemExit("llama source differs from the reviewed pin")
    build = ROOT / "build/optional-llama"
    subprocess.run(["cmake", "-S", str(source), "-B", str(build),
        "-DCMAKE_BUILD_TYPE=Release", "-DLLAMA_BUILD_SERVER=ON", "-DLLAMA_BUILD_TESTS=OFF",
        "-DLLAMA_BUILD_EXAMPLES=OFF", "-DGGML_NATIVE=OFF"], check=True)
    subprocess.run(["cmake", "--build", str(build), "--target", "llama-server", "--parallel", str(args.jobs)], check=True)
    print(f"Standalone server: {build / 'bin/llama-server'}")


if __name__ == "__main__":
    main()
