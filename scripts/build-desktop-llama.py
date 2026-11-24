#!/usr/bin/env python3
"""Build the pinned portable desktop CPU engine without changing Android."""
import argparse
import json
import platform
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", required=True, type=Path)
parser.add_argument("--jobs", type=int, default=2)
parser.add_argument("--variant", choices=("baseline", "avx2"), default="baseline")
args = parser.parse_args()
if not 1 <= args.jobs <= 8 or (args.variant == "avx2" and platform.machine().lower() not in ("amd64", "x86_64")):
    parser.error("Requires 1..8 build jobs and x86-64 for AVX2")
source = root / "vendored/runanywhere-llama"
pin = json.loads((root / "vendored/sources.lock.json").read_text())["sources"]["runanywhere-llama"]["revision"]
revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
if revision != pin:
    raise SystemExit("Native source differs from reviewed pin")
vector = "ON" if args.variant == "avx2" else "OFF"
subprocess.run(["cmake", "-S", str(source), "-B", str(args.output),
    "-DCMAKE_BUILD_TYPE=Release", *(["-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0"] if platform.system() == "Darwin" else []),
    "-DLLAMA_BUILD_SERVER=ON", "-DLLAMA_BUILD_TESTS=OFF", "-DLLAMA_BUILD_EXAMPLES=OFF",
    "-DLLAMA_BUILD_UI=OFF", "-DLLAMA_USE_PREBUILT_UI=OFF", "-DLLAMA_BUILD_APP=OFF", "-DLLAMA_CURL=OFF", "-DLLAMA_OPENSSL=OFF", "-DGGML_NATIVE=OFF", "-DGGML_METAL=OFF",
    "-DGGML_OPENMP=OFF", f"-DGGML_AVX={vector}", f"-DGGML_AVX2={vector}", f"-DGGML_FMA={vector}",
    f"-DGGML_F16C={vector}", "-DGGML_BMI2=OFF", "-DGGML_AVX512=OFF", "-DGGML_AVX_VNNI=OFF",
    *(["-DGGML_BLAS=ON", "-DGGML_BLAS_VENDOR=Apple"] if platform.system() == "Darwin" else ["-DGGML_BLAS=OFF"]), "-DBUILD_SHARED_LIBS=OFF"], check=True)
subprocess.run(["cmake", "--build", str(args.output), "--config", "Release", "--target", "llama-server",
                "--parallel", str(args.jobs)], check=True)
