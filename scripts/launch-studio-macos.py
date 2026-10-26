#!/usr/bin/env python3
"""Build and launch the named native bundle, never a raw Java GUI on macOS."""
import argparse
import fcntl
import os
from pathlib import Path
import subprocess
import time
import uuid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--workspace', type=Path, required=True)
    args = parser.parse_args()
    workspace = args.workspace.resolve(strict=True)
    if not workspace.is_dir():
        parser.error('workspace must be a directory')
    repo = Path(__file__).resolve().parent.parent
    profile = Path.home() / '.meshlit' / 'studio'
    profile.mkdir(parents=True, exist_ok=True, mode=0o700)
    if profile.is_symlink():
        raise RuntimeError('Studio profile must not be a symbolic link')

    def active():
        fd = os.open(profile / 'instance.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        try:
            try:
                fcntl.lockf(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
                return False
            except BlockingIOError:
                return True
        finally:
            os.close(fd)

    # Serialize builds and launches, including the short JVM startup interval.
    guard = os.open(profile / 'build.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        fcntl.lockf(guard, fcntl.LOCK_EX)
        if active():
            fd = os.open(profile / 'activate', os.O_CREAT | os.O_WRONLY | os.O_TRUNC | os.O_NOFOLLOW, 0o600)
            with os.fdopen(fd, 'w') as stream:
                stream.write(str(uuid.uuid4()))
            print('Activated the running Meshlit. Use Open folder there to change projects.')
            return
        command = ['./gradlew', ':desktopApp:createDistributable', '--no-configuration-cache',
                   '--no-daemon', '--no-parallel', '--max-workers=1',
                   '-Pkotlin.compiler.execution.strategy=in-process']
        if os.environ.get('MESHLIT_PACKAGING_JDK'):
            command.append('-Pmeshlit.packagingJdk=' + os.environ['MESHLIT_PACKAGING_JDK'])
        if os.environ.get('MESHLIT_DEV_RESOURCES'):
            command.append('-Pmeshlit.devResources=' + str(Path(os.environ['MESHLIT_DEV_RESOURCES']).resolve(strict=True)))
        subprocess.run(command, cwd=repo, check=True)
        bundle = repo / 'desktopApp/build/compose/binaries/main/app/Meshlit.app'
        if not bundle.is_dir():
            raise RuntimeError('Native Meshlit bundle was not produced')
        # Local integrity identity only; this is not Developer ID signing or notarization.
        subprocess.run(['codesign', '--force', '--deep', '--sign', '-', str(bundle)], check=True)
        subprocess.run(['codesign', '--verify', '--deep', '--strict', str(bundle)], check=True)
        subprocess.run(['open', str(bundle), '--args', '--workspace', str(workspace)], check=True)
        for _ in range(100):
            if active():
                print('Meshlit native bundle is running.')
                return
            time.sleep(.2)
        raise RuntimeError('Meshlit did not acquire its startup lock within 20 seconds')
    finally:
        os.close(guard)


if __name__ == '__main__':
    main()
