"""Companion contract tests. Subprocess fixtures below are not model-training evidence."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import uuid

spec_module = importlib.util.spec_from_file_location("meshlit_training", Path(__file__).parents[1] / "meshlit_training.py")
training = importlib.util.module_from_spec(spec_module)
spec_module.loader.exec_module(training)

class TrainingContracts(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.base = self.root / "base"
        self.base.mkdir()
        (self.base / "config.json").write_text('{}')
        (self.base / "model.safetensors").write_bytes(b'test-only-contract-fixture')
        self.data = self.root / "data.jsonl"
        self.data.write_text('\n'.join(json.dumps({"instruction": "fixture", "output": "test"}) for _ in range(10)))
        self.spec = {"jobId": str(uuid.uuid4()), "basePath": str(self.base), "datasetPath": str(self.data),
                     "epochs": 1, "rank": 16, "quantization": "none", "streamLayers": False,
                     "maxLength": 512, "timeoutSeconds": 60}
    def tearDown(self):
        self.temp.cleanup()
    def test_rejects_paths_outside_workspace_and_symlink_escape(self):
        with self.assertRaises(ValueError): training.inside(self.root, str(self.root.parent / "x"), exists=False)
        (self.root / "escape").symlink_to(self.root.parent, target_is_directory=True)
        with self.assertRaises(ValueError): training.inside(self.root, str(self.root / "escape" / "x"), exists=False)
        with self.assertRaises(ValueError): training.job_directory(self.root, "../x")
    def test_spec_rejects_unknown_fields_boolean_epochs_and_bad_quantization(self):
        for changes in ({"other": 1}, {"epochs": True}, {"quantization": "q4_k_m"}, {"rank": 0}):
            with self.assertRaises(ValueError): training.validate_spec(self.root, dict(self.spec, **changes))
        config, _, _ = training.validate_spec(self.root, self.spec)
        self.assertEqual(config['data']['max_length'], 512)
        self.assertFalse(config['training']['stream_layers'])
    def test_dataset_rejects_wrong_format_short_and_binary_input(self):
        self.assertEqual(training.validate_dataset(self.data), 10)
        for content in (b'{}', b'\xff', b'{"instruction":"x","output":"y","secret":"z"}'):
            self.data.write_bytes(content)
            with self.assertRaises((ValueError, UnicodeError)): training.validate_dataset(self.data)
    def test_missing_or_wrong_soup_version_blocks_start(self):
        with patch.object(training.importlib.metadata, 'version', return_value='0.74.0'):
            with self.assertRaises(ValueError): training.start(self.root, self.spec)
        self.assertFalse((self.root / 'meshlit-jobs').exists())
    def test_lost_heartbeat_reports_unknown_without_success(self):
        directory = training.job_directory(self.root, self.spec['jobId']); directory.mkdir()
        training.atomic(directory / 'status.json', {'jobId': self.spec['jobId'], 'state': 'running', 'updatedAt': 1})
        self.assertEqual(training.status(self.root, self.spec['jobId'])['state'], 'unknown')
    def test_idempotent_start_does_not_spawn_duplicate_and_conflict_is_rejected(self):
        with patch.object(training, 'soup_command', return_value=[sys.executable]), patch.object(training.subprocess, 'Popen') as child:
            first = training.start(self.root, self.spec)
            self.assertEqual(training.start(self.root, self.spec)['jobId'], first['jobId'])
            self.assertEqual(child.call_count, 1)
            with self.assertRaises(ValueError): training.start(self.root, dict(self.spec, rank=32))
    def test_cancellation_terminates_owned_process(self):
        directory = training.job_directory(self.root, self.spec['jobId']); directory.mkdir()
        training.atomic(directory / 'cancel.json', {'jobId': self.spec['jobId']})
        code, reason = training.run_logged([sys.executable, '-c', 'import time; time.sleep(20)'], directory, 60, lambda: None)
        self.assertEqual(reason, 'cancelled'); self.assertIsNotNone(code)
    def test_timeout_and_nonzero_are_not_reported_as_success(self):
        directory = training.job_directory(self.root, self.spec['jobId']); directory.mkdir()
        code, reason = training.run_logged([sys.executable, '-c', 'import time; time.sleep(20)'], directory, 0, lambda: None)
        self.assertEqual(reason, 'timed_out'); self.assertIsNotNone(code)
        code, reason = training.run_logged([sys.executable, '-c', 'raise SystemExit(7)'], directory, 60, lambda: None)
        self.assertEqual(code, 7); self.assertIsNone(reason)
    def test_large_logs_are_bounded_and_outputless_exit_is_not_training_completion(self):
        directory = training.job_directory(self.root, self.spec['jobId']); directory.mkdir()
        with patch.object(training, 'MAX_LOG', 8192):
            code, _ = training.run_logged([sys.executable, '-c', 'print("x"*100000)'], directory, 60, lambda: None)
            self.assertEqual(code, 0); self.assertLessEqual((directory / 'train.log').stat().st_size, 8192)
        with self.assertRaises(FileNotFoundError): training.artifacts(directory)
    def test_symlink_adapter_cannot_be_exported_as_verified(self):
        directory = training.job_directory(self.root, self.spec['jobId']); directory.mkdir()
        output = directory / 'output'; output.mkdir()
        (output / 'adapter_config.json').write_text('{}')
        (output / 'adapter_model.safetensors').symlink_to(self.base / 'model.safetensors')
        with self.assertRaises(ValueError): training.artifacts(directory)

if __name__ == '__main__':
    unittest.main()
