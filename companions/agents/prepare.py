#!/usr/bin/env python3
"""Fetch reviewed optional source companions, without running their code/installers."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

MANIFEST = Path(__file__).resolve().parents[1] / 'studio-runtime/extension/companion-sources.lock.json'


def git(directory, *args):
    environment = {key: value for key, value in os.environ.items()
                   if key not in {'GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'GIT_OBJECT_DIRECTORY', 'GIT_ALTERNATE_OBJECT_DIRECTORIES'}}
    environment['GIT_TERMINAL_PROMPT'] = '0'
    command = ['git', '-c', 'core.hooksPath=' + os.devnull, '-c', 'protocol.file.allow=never',
               '-c', 'filter.lfs.smudge=', '-c', 'filter.lfs.process=', '-c', 'filter.lfs.required=false',
               '-C', str(directory), *args]
    result = subprocess.run(command, capture_output=True, text=True, timeout=600, env=environment)
    if result.returncode:
        raise RuntimeError(f'Git {args[0]} failed: {result.stderr[-1500:].strip()}')
    return result.stdout.strip()


def verify(directory, source):
    if git(directory, 'rev-parse', 'HEAD') != source['revision']:
        raise RuntimeError('Checkout revision differs from the reviewed pin; existing files were preserved')
    if git(directory, 'remote', 'get-url', 'origin').rstrip('/') != source['repository']:
        raise RuntimeError('Checkout origin differs from the reviewed repository')
    if git(directory, 'status', '--porcelain', '--untracked-files=no'):
        raise RuntimeError('Checkout contains tracked edits; existing files were preserved')
    license_path = directory / 'LICENSE'
    if not license_path.is_file():
        raise RuntimeError('The reviewed root LICENSE is missing')
    license_bytes = license_path.read_bytes()
    header = license_bytes.decode('utf-8', errors='strict').lstrip()
    expected = 'Apache License' if source['license'] == 'Apache-2.0' else 'MIT License'
    if not header.startswith(expected):
        raise RuntimeError('The root license differs from the reviewed license family')
    return {'id': source['id'], 'directory': str(directory), 'revision': source['revision'],
            'repository': source['repository'], 'license': source['license'],
            'licenseSha256': hashlib.sha256(license_bytes).hexdigest(),
            'sourceVerified': True, 'dependenciesVerified': False, 'runtimeVerified': False}


def prepare(cache, source, verify_only=False):
    directory = cache / source['id']
    if directory.exists():
        if directory.is_symlink() or not (directory / '.git').is_dir():
            raise RuntimeError('Destination is not an ordinary Git checkout; existing files were preserved')
        return verify(directory, source)
    if verify_only:
        raise RuntimeError('Source checkout is not prepared')
    stage = Path(tempfile.mkdtemp(prefix='.' + source['id'] + '-preparing-', dir=cache))
    try:
        git(stage, 'init', '--quiet')
        git(stage, 'remote', 'add', 'origin', source['repository'])
        git(stage, 'fetch', '--depth=1', '--no-tags', 'origin', source['revision'])
        git(stage, 'checkout', '--quiet', '--detach', source['revision'])
        verify(stage, source)
        if directory.exists():
            raise RuntimeError('Destination appeared during preparation; existing files were preserved')
        stage.rename(directory)
        return verify(directory, source)
    finally:
        if stage.exists():
            shutil.rmtree(stage)


def main():
    sources = json.loads(MANIFEST.read_text())['sources']
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', required=True, type=Path, help='External source cache; no dependencies are installed')
    parser.add_argument('--source', choices=['all'] + [item['id'] for item in sources], default='all')
    parser.add_argument('--verify-only', action='store_true')
    parser.add_argument('--report', type=Path, help='Optional JSON source-provenance report')
    options = parser.parse_args()
    cache = options.cache.expanduser().resolve()
    repository = Path(__file__).resolve().parents[2]
    if cache == repository or repository in cache.parents:
        parser.error('Use an external cache outside the Meshlit source tree')
    if not shutil.which('git'):
        parser.error('Install Git first')
    cache.mkdir(parents=True, exist_ok=True)
    results = []
    for source in sources:
        if options.source not in ('all', source['id']):
            continue
        print(f'Verifying {source["id"]} at {source["revision"]}', file=sys.stderr, flush=True)
        results.append(prepare(cache, source, options.verify_only))
    report = {'schemaVersion': 1, 'sources': results,
              'note': 'Source only. No hooks, dependency installers, providers, models or services were started.'}
    if options.report:
        options.report.parent.mkdir(parents=True, exist_ok=True)
        options.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, subprocess.TimeoutExpired, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
