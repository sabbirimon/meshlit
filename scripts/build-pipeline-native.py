#!/usr/bin/env python3
"""Build pinned llama.cpp layer/RPC executables for host proofs or Android.
Android artifacts are install-time native libraries containing PIE executables;
they do not replace the RunAnywhere SDK libraries.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android', action='store_true')
    parser.add_argument('--backend', choices=['cpu','cuda','hip','vulkan','metal','sycl','opencl','musa','cann'], default='cpu')
    parser.add_argument('--abi', choices=['arm64-v8a', 'x86_64'], default='arm64-v8a')
    parser.add_argument('--ndk', type=Path)
    parser.add_argument('--jobs', type=int, default=4)
    args = parser.parse_args()
    if not 1 <= args.jobs <= 16:
        parser.error('jobs must be in 1..16')
    source = ROOT / 'vendored/runanywhere-llama'
    expected = json.loads((ROOT / 'vendored/sources.lock.json').read_text())['sources']['runanywhere-llama']['revision']
    if subprocess.check_output(['git','rev-parse','HEAD'],cwd=source,text=True).strip() != expected:
        raise SystemExit('Source revision differs from reviewed pin')
    if args.android and args.backend not in ['cpu','vulkan','opencl']:
        parser.error('Android builds only expose CPU/Vulkan/OpenCL; vendor desktop backends need their host toolchains')
    if args.backend == 'metal' and os.name == 'nt':
        parser.error('Metal requires an Apple host')
    suffix = '' if args.backend == 'cpu' else '-' + args.backend
    build = ROOT / 'build' / (('pipeline-android-' + args.abi if args.android else 'pipeline-host') + suffix)
    options = ['-DCMAKE_BUILD_TYPE=Release','-DBUILD_SHARED_LIBS=OFF','-DGGML_RPC=ON',
        '-DGGML_NATIVE=OFF','-DGGML_METAL=OFF','-DGGML_BLAS=OFF','-DGGML_OPENMP=OFF',
        '-DLLAMA_BUILD_TESTS=OFF','-DLLAMA_BUILD_EXAMPLES=OFF','-DLLAMA_BUILD_SERVER=ON',
        '-DLLAMA_BUILD_TOOLS=ON','-DLLAMA_BUILD_UI=OFF','-DLLAMA_USE_PREBUILT_UI=OFF','-DLLAMA_CURL=OFF']
    options += ['-DGGML_' + name.upper() + '=' + ('ON' if args.backend == name else 'OFF')
                for name in ['cuda','hip','vulkan','metal','sycl','opencl','musa','cann']]
    if args.android:
        if not args.ndk or not (args.ndk / 'build/cmake/android.toolchain.cmake').is_file():
            parser.error('--android requires --ndk pointing to an installed Android NDK')
        options += ['-DCMAKE_TOOLCHAIN_FILE=' + str(args.ndk / 'build/cmake/android.toolchain.cmake'),
            '-DANDROID_ABI=' + args.abi,'-DANDROID_PLATFORM=android-24','-DANDROID_STL=c++_static']
    subprocess.run(['cmake','-S',str(source),'-B',str(build)] + options,check=True)
    subprocess.run(['cmake','--build',str(build),'--target','llama-server','ggml-rpc-server',
        '--config','Release','--parallel',str(args.jobs)],check=True)
    if args.android:
        out = ROOT / 'app/src/main/jniLibs' / args.abi
        out.mkdir(parents=True,exist_ok=True)
        for src,dst in [('llama-server','libmeshlit_pipeline_server.so'),('ggml-rpc-server','libmeshlit_pipeline_worker.so')]:
            shutil.copy2(build / 'bin' / src,out / dst)
            os.chmod(out / dst,0o755)
            strip = next((args.ndk / 'toolchains/llvm/prebuilt').glob('*/bin/llvm-strip'))
            subprocess.run([str(strip),'--strip-unneeded',str(out / dst)],check=True)
        print('Install-time executables:',out)
    else:
        print('Host proof executables:',build / 'bin')
        print('Verify backend with llama-server --list-devices before advertising it; CPU-only Android is the tested default.')
if __name__ == '__main__':
    main()
