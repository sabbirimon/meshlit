import json
from pathlib import Path
import tempfile
import unittest

from runtime_config import load_runtime
from prepare_workbench import safe_tree


class RuntimeConfigTest(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory(prefix='meshlit-runtime-test-')
        self.addCleanup(self.temporary.cleanup)
        self.root=Path(self.temporary.name)/'Original App'
        self.root.mkdir()
        for relative in ('runtime/python/bin/python3','runtime/java/bin/java','runtime/agent_host.py','meshlit-backend.jar'):
            file=self.root/relative;file.parent.mkdir(parents=True,exist_ok=True);file.write_text('fixture')
        for relative in ('runtime/resources','runtime/jars'):
            (self.root/relative).mkdir(parents=True,exist_ok=True)
        self.config=dict(schema=2,kind='portable',python='runtime/python/bin/python3',agentHost='runtime/agent_host.py',
            backend=dict(java='runtime/java/bin/java',resources='runtime/resources',classpath=['meshlit-backend.jar','runtime/jars/*'],main='com.meshlit.desktop.MainKt'))

    def write(self,config=None):
        file=self.root/'runtime.json';file.write_text(json.dumps(self.config if config is None else config));return file

    def test_relocated_runtime(self):
        self.write();moved=self.root.with_name('Moved App');self.root.rename(moved)
        config=load_runtime(moved/'runtime.json')
        self.assertEqual(config['python'],str(moved.resolve()/'runtime/python/bin/python3'))
        self.assertEqual(config['pythonArgs'],['-B','-s'])
        self.assertNotIn('Original App',json.dumps(config))
        self.assertEqual(config['backend'][4],'-Djava.library.path='+str(moved.resolve()/'runtime/resources/local'))
        self.assertEqual(config['backend'][-1],'--ide-backend')

    def test_invalid_paths_and_future_versions(self):
        for value in ('../outside','/usr/bin/python3','runtime/../outside','runtime\\python','runtime//python'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                load_runtime(self.write(dict(self.config,python=value)))
        with self.assertRaises(ValueError):load_runtime(self.write(dict(self.config,schema=3)))

    def test_external_symlinks_and_development_compatibility(self):
        target=Path(self.temporary.name)/'outside';target.write_text('outside')
        try:(self.root/'escape').symlink_to(target)
        except OSError:self.skipTest('Symlink creation needs an OS grant')
        with self.assertRaises(ValueError):load_runtime(self.write(dict(self.config,python='escape')))
        with self.assertRaises(ValueError):safe_tree(self.root)
        config=dict(python='/existing/python',agentHost='/existing/agent.py',backend=['/existing/java'])
        self.assertEqual(load_runtime(self.write(config)),config)


if __name__=='__main__':unittest.main()
