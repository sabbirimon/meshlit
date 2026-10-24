import importlib.util, json, os, shutil, tempfile, unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('meshlit_tf',Path(__file__).parents[1]/'meshlit_terraform.py');tf=importlib.util.module_from_spec(spec);spec.loader.exec_module(tf)
@unittest.skipUnless(shutil.which('terraform'),'real Terraform executable not installed')
class TerraformTests(unittest.TestCase):
    def setUp(self):
        os.environ['CHECKPOINT_DISABLE']='1';os.environ['TF_IN_AUTOMATION']='1'
        self.temp=tempfile.TemporaryDirectory();root=Path(self.temp.name)
        self.workspace=root/'workspace';self.workspace.mkdir();self.store=root/'plans'
        (self.workspace/'main.tf').write_text('resource "terraform_data" "owned_local" { input = "local acceptance only" }\n')
        self.binary=Path(shutil.which('terraform')).resolve();self.sha=tf.digest(self.binary)
    def tearDown(self):self.temp.cleanup()
    def plan(self):return tf.plan(self.workspace,self.binary,self.sha,self.store,initialize=True)
    def test_real_local_plan_apply_and_duplicate_refusal(self):
        record=self.plan();self.assertEqual({'create':1},record['actions'])
        result=tf.apply(self.workspace,self.binary,self.sha,self.store,record['id'],record['plan_sha256']);self.assertEqual('apply_completed',result['status'])
        with self.assertRaises(ValueError):tf.apply(self.workspace,self.binary,self.sha,self.store,record['id'],record['plan_sha256'])
    def test_wrong_approval_and_changed_workspace_block_before_mutation(self):
        record=self.plan()
        with self.assertRaises(ValueError):tf.apply(self.workspace,self.binary,self.sha,self.store,record['id'],'0'*64)
        (self.workspace/'main.tf').write_text('resource "terraform_data" "changed" { input = "changed" }\n')
        with self.assertRaises(ValueError):tf.apply(self.workspace,self.binary,self.sha,self.store,record['id'],record['plan_sha256'])
        self.assertFalse((self.workspace/'terraform.tfstate').exists())
    def test_corrupted_plan_blocks(self):
        record=self.plan();(self.store/(record['id']+'.tfplan')).write_bytes(b'corrupt')
        with self.assertRaises(ValueError):tf.apply(self.workspace,self.binary,self.sha,self.store,record['id'],record['plan_sha256'])
