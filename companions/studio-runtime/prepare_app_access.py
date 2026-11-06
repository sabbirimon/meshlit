"""Build the small native Accessibility helper inside a prepared Studio extension."""
import argparse
import os
from pathlib import Path
import plistlib
import signal
import subprocess
import tempfile

def build(extension, identity='-'):
    bundle=Path(extension)/'native'/'Meshlit App Access.app'
    binary=bundle/'Contents/MacOS/Meshlit App Access'
    binary.parent.mkdir(parents=True,exist_ok=True)
    source=Path(__file__).with_name('native')/'AppAccess.swift'
    # Keep compiler caches outside the signed application and user-global cache.
    with tempfile.TemporaryDirectory(prefix='meshlit-swift-') as cache:
        command=['xcrun','swiftc','-module-cache-path',cache,'-O',str(source),'-o',str(binary)]
        # Cold SDK modules on Intel can take several minutes. Own the compiler
        # process group so timeout or cancellation also stops swift-frontend.
        with subprocess.Popen(command,start_new_session=True) as compiler:
            try:
                status=compiler.wait(timeout=900)
            except BaseException:
                try: os.killpg(compiler.pid,signal.SIGKILL)
                except ProcessLookupError: pass
                compiler.wait()
                raise
            if status: raise subprocess.CalledProcessError(status,command)
    (bundle/'Contents/Info.plist').write_bytes(plistlib.dumps(dict(
        CFBundleIdentifier='com.meshlit.desktop.appaccess',CFBundleName='Meshlit App Access',
        CFBundleDisplayName='Meshlit App Access',CFBundleExecutable=binary.name,
        CFBundlePackageType='APPL',CFBundleVersion='1',CFBundleShortVersionString='1.0',LSUIElement=True)))
    subprocess.run(['codesign','--force','--sign',identity,str(bundle)],check=True)
    subprocess.run(['codesign','--verify','--strict',str(bundle)],check=True)
    return bundle

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--extension',type=Path,required=True);p.add_argument('--sign-identity',default='-')
    args=p.parse_args();print(build(args.extension,args.sign_identity))
