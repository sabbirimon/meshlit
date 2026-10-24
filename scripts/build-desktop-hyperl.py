#!/usr/bin/env python3
"""Compile the unmodified HyperL CPU/JNI sources for the Intel desktop adapter.

HyperL retains its separate licence and earlier grants. No sources are relabelled
or modified; output is a relocatable host library, not an Android/GPU backend.
"""
import argparse
import platform
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--jdk-home", required=True, type=Path)
parser.add_argument("--output", required=True, type=Path)
args = parser.parse_args()
if platform.system() != "Darwin" or platform.machine() != "x86_64":
    parser.error("Requires Intel Mac")
if args.output.exists() or not (args.jdk_home / "include/jni.h").is_file():
    parser.error("Requires a new output file and the configured JDK JNI headers")
root = Path(__file__).resolve().parents[1]
source = root / "core-hyperl/src/main/cpp"
args.output.parent.mkdir(parents=True, exist_ok=True)
subprocess.run(["clang", "-dynamiclib", "-arch", "x86_64", "-mmacosx-version-min=11.0",
    "-std=c99", "-O2", "-Wall", "-Wextra", "-Werror", "-ffp-contract=off", "-fvisibility=hidden",
    "-I" + str(source / "include"), "-I" + str(args.jdk_home / "include"),
    "-I" + str(args.jdk_home / "include/darwin"), str(source / "cpu.c"), str(source / "android_jni.c"),
    "-Wl,-install_name,@rpath/libmeshlit_hyperl.dylib", "-o", str(args.output)], check=True)
print("Built relocatable Intel CPU/JNI library; native recipe qualification remains separate.")
