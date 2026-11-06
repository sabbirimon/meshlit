#!/usr/bin/env python3
"""Prepare both pinned Studio models without changing Android or Compose defaults."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'companions/studio-runtime'))
from studio_models import load_catalog, verify_model


def prepare(model, output, source_dir=None, run=subprocess.run):
    output = Path(output); output.mkdir(parents=True, exist_ok=True)
    destination = output / model['filename']
    if destination.is_symlink():
        raise ValueError('Destination must not be a symbolic link')
    if destination.exists():
        try:
            verify_model(destination, model)
            return destination
        except ValueError:
            pass  # Replace only after a complete new artifact has been verified.
    # Own names are deliberately different from operator downloads named *.partial.
    stem = '.meshlit-' + model['id'] + '-' + model['sha256'][:16]
    part, lock = output / (stem + '.part'), output / (stem + '.lock')
    descriptor = os.open(lock, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    os.close(descriptor)
    try:
        if part.is_symlink() or part.exists() and not part.is_file():
            raise ValueError('Download staging must be a regular private file')
        if part.exists() and part.stat().st_size >= model['sizeBytes']:
            try:
                verify_model(part, model)
                os.replace(part, destination)
                return destination
            except ValueError:
                part.unlink()
        if source_dir is not None:
            source = Path(source_dir) / model['filename']
            verify_model(source, model)
            with part.open('wb') as target, source.open('rb') as original:
                os.chmod(part, 0o600)
                shutil.copyfileobj(original, target, 1024 * 1024)
        else:
            if not part.exists():
                descriptor = os.open(part, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                os.close(descriptor)
            else:
                os.chmod(part, 0o600)
            run(['curl', '--fail', '--location', '--proto', '=https', '--proto-redir', '=https',
                 '--connect-timeout', '30', '--max-time', '3600', '--retry', '3',
                 '--max-filesize', str(model['sizeBytes']), '--continue-at', '-',
                 '--output', str(part), model['url']], check=True)
        try:
            verify_model(part, model)
        except ValueError:
            part.unlink(missing_ok=True)
            raise
        os.replace(part, destination)
        return destination
    finally:
        lock.unlink(missing_ok=True)
        # An interrupted bounded partial remains for safe resume from the same
        # immutable URL; complete size and SHA are required before publication.
        if part.exists() and not part.is_symlink() and part.stat().st_size > model['sizeBytes']:
            part.unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True, help='Studio resources/models directory')
    parser.add_argument('--source-dir', type=Path, help='Import verified existing files instead of downloading')
    args = parser.parse_args()
    catalog = load_catalog(ROOT / 'desktopApp/distribution/studio-models.json')
    for model in catalog['models']:
        print('Verified:', prepare(model, args.output, args.source_dir))
    manifest = args.output / 'studio-models.json'
    if manifest.is_symlink():
        raise ValueError('Catalog destination must not be a symbolic link')
    import tempfile
    descriptor, temporary = tempfile.mkstemp(prefix='.studio-models-', dir=args.output)
    try:
        with os.fdopen(descriptor, 'w') as target:
            json.dump(catalog, target, indent=2); target.write('\n')
        os.replace(temporary, manifest)
    finally:
        Path(temporary).unlink(missing_ok=True)


if __name__ == '__main__':
    main()
