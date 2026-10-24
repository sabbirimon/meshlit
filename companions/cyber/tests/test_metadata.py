import importlib.util, sqlite3, struct, tempfile, unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('metadata_cyber',Path(__file__).parents[1]/'meshlit_cyber.py');cyber=importlib.util.module_from_spec(spec);spec.loader.exec_module(cyber)
class MetadataTests(unittest.TestCase):
    def test_real_sqlite_read_does_not_change_file_or_expose_rows(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'owned.db'
            with sqlite3.connect(path) as db:db.execute('CREATE TABLE observations(secret TEXT)');db.execute("INSERT INTO observations VALUES ('must-not-export')")
            before=path.read_bytes();result=cyber.analyze('sqlite_metadata',path,cyber.digest(path))
            self.assertEqual(before,path.read_bytes());self.assertNotIn('must-not-export',str(result))
            self.assertEqual(result['findings']['objects'],[{'type':'table','name':'observations'}])
    def test_corrupt_sqlite_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'bad.db';path.write_bytes(b'SQLite format 3\x00'+b'bad')
            with self.assertRaises(Exception):cyber.analyze('sqlite_metadata',path,cyber.digest(path))
    def test_elf_and_out_of_bounds_table(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'owned.elf';header=b'\x7fELF\x02\x01\x01'+bytes(9)+struct.pack('<HHIQQQIHHHHHH',2,183,1,0,0,0,0,64,0,0,0,0,0)
            path.write_bytes(header);self.assertEqual(cyber.analyze('elf_inventory',path,cyber.digest(path))['findings']['machine'],183)
            path.write_bytes(header[:32]+struct.pack('<Q',999999)+header[40:54]+struct.pack('<H',56)+struct.pack('<H',1)+header[58:])
            with self.assertRaises(ValueError):cyber.analyze('elf_inventory',path,cyber.digest(path))
