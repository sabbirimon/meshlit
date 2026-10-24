import importlib.util, tempfile, unittest, zipfile, os
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('packages',Path(__file__).parents[1]/'meshlit_packages.py');packages=importlib.util.module_from_spec(spec);spec.loader.exec_module(packages)
class PackageTests(unittest.TestCase):
    def test_package_names_reject_shell_arguments(self):
        for name in ['-y','a;id','x\nrm','a/b','$(id)']:
            with self.assertRaises(ValueError):packages.package_name(name)
        self.assertEqual(packages.package_name('test-package'),'test-package')
    def test_missing_lab_marker_blocks(self):
        class Args:manager='pip';action='list'
        with patch.object(Path,'is_file',return_value=False):
            with self.assertRaises(ValueError):packages.perform(Args())
    def test_real_offline_wheel_install_list_uninstall(self):
        with tempfile.TemporaryDirectory() as temp:
            home=Path(temp);wheel=home/'meshlit_fixture-1.0-py3-none-any.whl'
            with zipfile.ZipFile(wheel,'w') as z:
                z.writestr('meshlit_fixture.py','VALUE = 1\n')
                z.writestr('meshlit_fixture-1.0.dist-info/METADATA','Metadata-Version: 2.1\nName: meshlit-fixture\nVersion: 1.0\n')
                z.writestr('meshlit_fixture-1.0.dist-info/WHEEL','Wheel-Version: 1.0\nGenerator: Meshlit test\nRoot-Is-Purelib: true\nTag: py3-none-any\n')
                z.writestr('meshlit_fixture-1.0.dist-info/RECORD','meshlit_fixture.py,,\nmeshlit_fixture-1.0.dist-info/METADATA,,\nmeshlit_fixture-1.0.dist-info/WHEEL,,\nmeshlit_fixture-1.0.dist-info/RECORD,,\n')
            class Args:manager='pip';action='install';kind='file';source=str(wheel);sha256=packages.file_digest(wheel);allow_guest_root=False
            real_is_file=Path.is_file
            def marker(p):return True if str(p)=='/etc/meshlit-lab-owned' else real_is_file(p)
            with patch.object(Path,'home',return_value=home),patch.object(Path,'is_file',marker),patch('os.geteuid',return_value=1000),patch.dict(os.environ,{'PIP_NO_INDEX':'1'}):
                self.assertEqual(packages.perform(Args())['status'],'completed')
                args=Args();args.action='list';self.assertIn('meshlit-fixture',packages.perform(args)['inventory'])
                args.action='uninstall';args.source='meshlit-fixture';self.assertEqual(packages.perform(args)['status'],'completed')
                args.action='list';self.assertNotIn('meshlit-fixture',packages.perform(args)['inventory'])
