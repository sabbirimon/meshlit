"""Real small GGUF-shaped fixtures test artifact integrity, never inference quality."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from studio_models import load_catalog, verify_catalog

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('prepare_studio_models', ROOT / 'scripts/prepare-studio-models.py')
prepare_script = importlib.util.module_from_spec(spec); spec.loader.exec_module(prepare_script)


class StudioModelsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.payload = b'GGUFtest only fixture'
        self.model = dict(id='fixture', filename='fixture.gguf', sizeBytes=len(self.payload), sha256=hashlib.sha256(self.payload).hexdigest(), revision='a'*40, url='https://huggingface.co/Qwen/fixture/resolve/'+'a'*40+'/fixture.gguf')
        self.catalog = dict(schema=1, defaultModel='fixture', models=[self.model])

    def test_catalog_retains_existing_quick_default_and_required_second_pin(self):
        catalog = load_catalog(ROOT / 'desktopApp/distribution/studio-models.json')
        self.assertEqual('qwen25-1.5b',catalog['defaultModel'])
        self.assertEqual(['qwen25-1.5b','qwen3-4b'],[m['id'] for m in catalog['models']])
        self.assertEqual(2497280256,catalog['models'][1]['sizeBytes'])
        starter=json.loads((ROOT/'desktopApp/distribution/bundled-model.json').read_text())
        for key in ('filename','sha256','sizeBytes','revision'):
            self.assertEqual(starter[key],catalog['models'][0][key])

    def test_manifest_rejects_escape_duplicates_unpinned_and_unknown_default(self):
        file=self.root/'catalog.json'
        for changes in ({'filename':'../bad.gguf'},{'url':'http://example.test/file'},{'revision':'main'},{'sha256':'a'},{'sizeBytes':True}):
            file.write_text(json.dumps({**self.catalog,'models':[{**self.model,**changes}]}))
            with self.assertRaises(ValueError):load_catalog(file)
        for changes in ({'models':[self.model,self.model]},{'defaultModel':'missing'}):
            file.write_text(json.dumps({**self.catalog,**changes}))
            with self.assertRaises(ValueError):load_catalog(file)

    def test_packaging_verifies_every_model_and_rejects_wrong_bytes_or_symlink(self):
        file=self.root/self.model['filename'];file.write_bytes(self.payload)
        verify_catalog(self.root,self.catalog)
        second={**self.model,'id':'second','filename':'second.gguf'}
        with self.assertRaises(ValueError):verify_catalog(self.root,{**self.catalog,'models':[self.model,second]})
        file.write_bytes(b'GGUF'+b'x'*(len(self.payload)-4))
        with self.assertRaisesRegex(ValueError,'SHA-256'):verify_catalog(self.root,self.catalog)
        file.unlink();target=self.root/'target';target.write_bytes(self.payload);file.symlink_to(target)
        with self.assertRaises(ValueError):verify_catalog(self.root,self.catalog)

    def test_verified_import_publishes_atomically_and_preserves_operator_partial(self):
        source=self.root/'source';source.mkdir();(source/self.model['filename']).write_bytes(self.payload)
        output=self.root/'output';output.mkdir();operator=output/'fixture.gguf.partial';operator.write_bytes(b'operator data')
        result=prepare_script.prepare(self.model,output,source)
        self.assertEqual(self.payload,result.read_bytes());self.assertEqual(b'operator data',operator.read_bytes())
        self.assertFalse(list(output.glob('*.lock')))
        self.assertEqual(result,prepare_script.prepare(self.model,output,run=lambda *a,**kw:self.fail('must not download valid artifact')))

    def test_download_failure_can_resume_without_publishing_or_overwriting_final(self):
        output=self.root/'output';output.mkdir();destination=output/self.model['filename'];destination.write_bytes(b'old invalid artifact')
        def interrupted(args,**kwargs):
            self.assertIn('--continue-at',args);self.assertIn('--max-filesize',args)
            Path(args[args.index('--output')+1]).write_bytes(self.payload[:8])
            raise subprocess.CalledProcessError(18,args)
        with self.assertRaises(subprocess.CalledProcessError):prepare_script.prepare(self.model,output,run=interrupted)
        self.assertEqual(b'old invalid artifact',destination.read_bytes())
        def resume(args,**kwargs):
            part=Path(args[args.index('--output')+1]);self.assertEqual(self.payload[:8],part.read_bytes());part.write_bytes(self.payload)
        prepare_script.prepare(self.model,output,run=resume)
        self.assertEqual(self.payload,destination.read_bytes());self.assertFalse(list(output.glob('*.part')))

    def test_corrupt_download_and_busy_lock_never_publish(self):
        output=self.root/'output'
        def corrupt(args,**kwargs):Path(args[args.index('--output')+1]).write_bytes(b'GGUF'+b'x'*(len(self.payload)-4))
        with self.assertRaisesRegex(ValueError,'SHA-256'):prepare_script.prepare(self.model,output,run=corrupt)
        self.assertFalse((output/self.model['filename']).exists());self.assertFalse(list(output.glob('*.part')))
        lock=output/('.meshlit-'+self.model['id']+'-'+self.model['sha256'][:16]+'.lock');lock.touch()
        with self.assertRaises(FileExistsError):prepare_script.prepare(self.model,output,run=lambda *a,**kw:self.fail('must not download'))
        self.assertTrue(lock.exists())


if __name__=='__main__':unittest.main()
