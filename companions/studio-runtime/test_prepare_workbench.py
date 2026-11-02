"""Verify native relocation using real Mach-O libraries."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from prepare_workbench import bundle_openssl, qualify_native_runtime


@unittest.skipUnless(sys.platform == 'darwin', 'Mach-O packaging requires macOS')
class NativeRelocationTest(unittest.TestCase):
    def test_explicit_ssl_dependencies_survive_relocation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            prefix = root / 'explicit-openssl'
            (prefix / 'lib').mkdir(parents=True)
            (prefix / 'LICENSE.txt').write_text('Test-only library license\n')
            (root / 'crypto.c').write_text('int crypto_probe(void) { return 7; }\n')
            (root / 'ssl.c').write_text('extern int crypto_probe(void); int ssl_probe(void) { return crypto_probe(); }\n')
            crypto = prefix / 'lib/libcrypto.3.dylib'
            ssl = prefix / 'lib/libssl.3.dylib'
            subprocess.run(['clang', '-dynamiclib', str(root / 'crypto.c'), '-Wl,-install_name,' + str(crypto), '-o', str(crypto)], check=True)
            subprocess.run(['clang', '-dynamiclib', str(root / 'ssl.c'), str(crypto), '-Wl,-install_name,' + str(ssl), '-o', str(ssl)], check=True)
            runtime = root / 'runtime'
            bundle_openssl(runtime, prefix)
            qualify_native_runtime(runtime, '-')
            relocated = root / 'relocated runtime'
            runtime.rename(relocated)
            prefix.rename(root / 'unavailable-build-input')
            library = relocated / 'python/lib/meshlit-openssl/libssl.3.dylib'
            subprocess.run([sys.executable, '-c', 'import ctypes,sys; assert ctypes.CDLL(sys.argv[1]).ssl_probe() == 7', str(library)], check=True)
            self.assertEqual('Test-only library license\n', (relocated / 'notices/openssl/LICENSE.txt').read_text())

    def test_library_identity_is_relocated_and_signed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'probe.c'
            library = root / 'libprobe.dylib'
            source.write_text('int probe(void) { return 1; }\n')
            subprocess.run(['clang', '-dynamiclib', str(source), '-Wl,-install_name,/development/libprobe.dylib', '-o', str(library)], check=True)
            self.assertEqual(['libprobe.dylib'], qualify_native_runtime(root, '-'))
            self.assertIn('@rpath/libprobe.dylib', subprocess.check_output(['otool', '-D', str(library)], text=True))
            subprocess.run(['codesign', '--verify', '--strict', str(library)], check=True)

    def test_external_dependency_is_still_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'dep.c').write_text('int dependency(void) { return 1; }\n')
            (root / 'client.c').write_text('extern int dependency(void); int client(void) { return dependency(); }\n')
            dependency = root / 'libdep.dylib'
            subprocess.run(['clang', '-dynamiclib', str(root / 'dep.c'), '-Wl,-install_name,/development/libdep.dylib', '-o', str(dependency)], check=True)
            subprocess.run(['clang', '-dynamiclib', str(root / 'client.c'), str(dependency), '-o', str(root / 'libclient.dylib')], check=True)
            with self.assertRaisesRegex(ValueError, 'External native runtime dependency'):
                qualify_native_runtime(root, '-')


if __name__ == '__main__':
    unittest.main()
