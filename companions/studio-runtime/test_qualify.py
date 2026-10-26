import hashlib
from pathlib import Path
import tempfile
import unittest

from qualify import verify


class ArtifactIntegrityTest(unittest.TestCase):
    def test_tampered_and_truncated_artifacts_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'artifact'
            original = b'upstream artifact fixture'
            artifact = {'size': len(original),
                        'sha256': hashlib.sha256(original).hexdigest()}
            path.write_bytes(original)
            self.assertTrue(verify(path, artifact)['verified'])
            path.write_bytes(b'X' + original[1:])
            with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
                verify(path, artifact)
            path.write_bytes(original[:-1])
            with self.assertRaisesRegex(ValueError, 'Size mismatch'):
                verify(path, artifact)


if __name__ == '__main__':
    unittest.main()
