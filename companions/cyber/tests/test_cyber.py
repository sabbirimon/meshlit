import hashlib, importlib.util, struct, tempfile, unittest, zipfile
from pathlib import Path
spec=importlib.util.spec_from_file_location('cyber',Path(__file__).parents[1]/'meshlit_cyber.py');cyber=importlib.util.module_from_spec(spec);spec.loader.exec_module(cyber)
class CyberTests(unittest.TestCase):
    def test_apk_digest_and_inventory(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'test.apk'
            with zipfile.ZipFile(p,'w') as z: z.writestr('AndroidManifest.xml',b'binary fixture');z.writestr('classes.dex',b'dex')
            result=cyber.analyze('apk_inventory',p,cyber.digest(p));self.assertEqual(result['findings']['dex_files'],['classes.dex'])
            with self.assertRaises(ValueError):cyber.analyze('apk_inventory',p,'0'*64)
    def test_unsafe_zip(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'bad.apk'
            with zipfile.ZipFile(p,'w') as z:z.writestr('../escape',b'x');z.writestr('AndroidManifest.xml',b'x')
            with self.assertRaises(ValueError):cyber.analyze('apk_inventory',p,cyber.digest(p))
    def test_pcap_endian_and_truncation(self):
        for endian,magic in [('<',b'\xd4\xc3\xb2\xa1'),('>',b'\xa1\xb2\xc3\xd4')]:
            with tempfile.TemporaryDirectory() as temp:
                p=Path(temp)/'input.pcap';p.write_bytes(magic+struct.pack(endian+'HHiIII',2,4,0,0,65535,1)+struct.pack(endian+'IIII',0,0,3,3)+b'abc')
                self.assertEqual(cyber.analyze('pcap_summary',p,cyber.digest(p))['findings']['packets'],1)
                p.write_bytes(p.read_bytes()[:-1])
                with self.assertRaises(ValueError):cyber.analyze('pcap_summary',p,cyber.digest(p))
