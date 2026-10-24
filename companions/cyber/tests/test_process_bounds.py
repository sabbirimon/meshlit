import importlib.util,sys,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('cyber_bounds',Path(__file__).parents[1]/'meshlit_cyber.py');cyber=importlib.util.module_from_spec(spec);spec.loader.exec_module(cyber)
class BoundsTests(unittest.TestCase):
    def test_real_subprocess_metadata(self):
        self.assertEqual(cyber.bounded_process([sys.executable,'-c','print("metadata")']),b'metadata\n')
    def test_excess_output_is_rejected(self):
        with self.assertRaises(ValueError):cyber.bounded_process([sys.executable,'-c','print("x"*10000)'],limit=100)
    def test_deadline_kills_subprocess(self):
        with self.assertRaises(TimeoutError):cyber.bounded_process([sys.executable,'-c','import time;time.sleep(10)'],timeout=.1)
    def test_nonzero_is_not_success(self):
        with self.assertRaises(ValueError):cyber.bounded_process([sys.executable,'-c','import sys;sys.exit(3)'])
