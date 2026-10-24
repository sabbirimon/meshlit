#!/usr/bin/env python3
"""Build the pinned portable Intel Mac CPU engine without changing Android."""
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
if platform.system() != "Darwin" or platform.machine() != "x86_64" or not 1 <= args.jobs <= 8:
    parser.error("Requires Intel Mac and 1..8 build jobs")
source = root / "vendored/runanywhere-llama"
pin = json.loads((root / "vendored/sources.lock.json").read_text())["sources"]["runanywhere-llama"]["revision"]
revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
if revision != pin:
    raise SystemExit("Native source differs from reviewed pin")
vector = "ON" if args.variant == "avx2" else "OFF"
subprocess.run(["cmake", "-S", str(source), "-B", str(args.output),
    "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0",
    "-DLLAMA_BUILD_SERVER=ON", "-DLLAMA_BUILD_TESTS=OFF", "-DLLAMA_BUILD_EXAMPLES=OFF",
    "-DLLAMA_CURL=OFF", "-DLLAMA_OPENSSL=OFF", "-DGGML_NATIVE=OFF", "-DGGML_METAL=OFF",
    "-DGGML_OPENMP=OFF", f"-DGGML_AVX={vector}", f"-DGGML_AVX2={vector}", f"-DGGML_FMA={vector}",
    f"-DGGML_F16C={vector}", "-DGGML_BMI2=OFF", "-DGGML_AVX512=OFF", "-DGGML_AVX_VNNI=OFF",
    "-DGGML_BLAS=ON", "-DGGML_BLAS_VENDOR=Apple", "-DBUILD_SHARED_LIBS=OFF"], check=True)
subprocess.run(["cmake", "--build", str(args.output), "--target", "llama-server",
                "--parallel", str(args.jobs)], check=True)
