"""Verify native relocation using real Mach-O libraries."""
import subprocess
import sys
import tempfile
import unittest
import base64
import hashlib
import json
from pathlib import Path
from prepare_workbench import bundle_openssl, qualify_native_runtime, refresh_workbench_checksums


class WorkbenchIntegrityTest(unittest.TestCase):
    def test_modified_build_is_resealed_and_later_tampering_is_detectable(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'out/vs').mkdir(parents=True)
            target = root / 'out/vs/workbench.js'
            target.write_bytes(b'original workbench')
            untouched = root / 'out/vs/other.js'
            untouched.write_bytes(b'unchanged')
            checksum = lambda path: base64.b64encode(hashlib.sha256(path.read_bytes()).digest()).decode().rstrip('=')
            original = {'nameShort': 'Meshlit', 'checksums': {'vs/workbench.js': checksum(target), 'vs/other.js': checksum(untouched)}}
            product = root / 'product.json'
            product.write_text(json.dumps(original))
            target.write_bytes(b'branded startup workbench')
            refresh_workbench_checksums(root)
            updated = json.loads(product.read_text())
            self.assertEqual('Meshlit', updated['nameShort'])
            self.assertEqual(set(original['checksums']), set(updated['checksums']))
            self.assertEqual(original['checksums']['vs/other.js'], updated['checksums']['vs/other.js'])
            self.assertEqual(checksum(target), updated['checksums']['vs/workbench.js'])
            self.assertNotEqual(original['checksums']['vs/workbench.js'], updated['checksums']['vs/workbench.js'])
            target.write_bytes(b'modified after packaging')
            self.assertNotEqual(checksum(target), updated['checksums']['vs/workbench.js'])

    def test_missing_integrity_file_does_not_partially_reseal(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'out').mkdir()
            product = root / 'product.json'
            original = json.dumps({'checksums': {'missing.js': 'old'}})
            product.write_text(original)
            with self.assertRaises(FileNotFoundError):
                refresh_workbench_checksums(root)
            self.assertEqual(original, product.read_text())

    def test_empty_manifest_and_escaping_paths_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'out').mkdir()
            outside = root / 'outside.js'
            outside.write_text('outside')
            product = root / 'product.json'
            for checksums in ({}, {'../outside.js': 'old'}, {str(outside): 'old'}):
                with self.subTest(checksums=checksums):
                    product.write_text(json.dumps({'checksums': checksums}))
                    with self.assertRaises(ValueError):
                        refresh_workbench_checksums(root)


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
