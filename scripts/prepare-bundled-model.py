#!/usr/bin/env python3
"""Prepare the pinned real starter asset. Never commit the binary.
Requires Python 3 and curl (HTTPS certificates remain verified).
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/models'

def validate(path, manifest):
    if path.stat().st_size != manifest['sizeBytes']:
        raise ValueError('Bundled model has the wrong size')
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        if stream.read(4) != b'GGUF':
            raise ValueError('Bundled model is not GGUF')
        stream.seek(0)
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    if digest.hexdigest() != manifest['sha256']:
        raise ValueError('Bundled model SHA-256 mismatch')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, help='Use an existing real GGUF instead of downloading')
    args = parser.parse_args()
    manifest = json.loads((ASSETS / 'bundled-model.json').read_text())
    dest = ASSETS / manifest['filename']
    if dest.exists():
        try:
            validate(dest, manifest)
            print(f'Verified: {dest} ({manifest["sizeBytes"]} bytes)')
            return
        except ValueError:
            pass
    fd, temporary = tempfile.mkstemp(prefix='bundled-', suffix='.part', dir=ASSETS)
    os.close(fd)
    part = Path(temporary)
    try:
        if args.source:
            validate(args.source, manifest)
            shutil.copyfile(args.source, part)
        else:
            subprocess.run(['curl', '--fail', '--location', '--proto', '=https', '--proto-redir', '=https',
                            '--connect-timeout', '30', '--max-time', '1800', '--retry', '3',
                            '--output', str(part), manifest['url']], check=True)
        validate(part, manifest)
        os.replace(part, dest)
        print(f'Prepared: {dest} ({manifest["sizeBytes"]} bytes; SHA-256 verified)')
    finally:
        part.unlink(missing_ok=True)

if __name__ == '__main__':
    main()
