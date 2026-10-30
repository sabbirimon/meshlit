#!/usr/bin/env python3
"""Qualify a real pinned Claude-Mem worker with isolated local manual memories."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import signal
import socket
import subprocess
import tempfile
import time
import urllib.request

REVISION = '70aa40042cfb8157a0d84954b9c0d2b30b50af80'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--bun', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    source, bun, output = args.source.resolve(), args.bun.resolve(), args.output.resolve()
    if subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip() != REVISION:
        parser.error('Source revision differs from the reviewed pin')
    worker = source / 'plugin/scripts/worker-service.cjs'
    if not worker.is_file() or not bun.is_file():
        parser.error('Build the worker and provide Bun first')
    # Native qualification is deliberately local-only even if an upstream
    # startup path unexpectedly attempts telemetry or provider discovery.
    if platform.system() != 'Darwin' or not Path('/usr/bin/sandbox-exec').is_file():
        parser.error('This qualification currently requires macOS loopback-only sandbox-exec; no unrestricted fallback')
    output.mkdir(parents=True, exist_ok=True)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]
    origin = f'http://127.0.0.1:{port}'
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def request(route, body=None, method=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(origin + route, data=data, method=method,
                                     headers={'Content-Type': 'application/json'} if data else {})
        with opener.open(req, timeout=5) as response:
            raw = response.read(4 * 1024 * 1024 + 1)
            if len(raw) > 4 * 1024 * 1024: raise RuntimeError('Qualification response exceeds limit')
            return json.loads(raw) if 'application/json' in response.headers.get('content-type', '') else raw.decode()

    settings = {
        'CLAUDE_MEM_WORKER_HOST': '127.0.0.1', 'CLAUDE_MEM_WORKER_PORT': str(port),
        'CLAUDE_MEM_PROVIDER': 'openai-compatible', 'CLAUDE_MEM_OPENAI_COMPAT_PRESET': 'custom',
        'CLAUDE_MEM_OPENAI_COMPAT_BASE_URL': 'http://127.0.0.1:9/v1',
        'CLAUDE_MEM_OPENAI_COMPAT_MODEL': 'unconfigured-local',
        'CLAUDE_MEM_QUOTA_FALLBACK_PROVIDER': 'off', 'CLAUDE_MEM_QUOTA_FALLBACK_AUTH_METHOD': '',
        'CLAUDE_MEM_CHROMA_ENABLED': 'false', 'CLAUDE_MEM_TRANSCRIPTS_ENABLED': 'false',
        'CLAUDE_MEM_MEMORY_WATCH_ROOTS': '', 'CLAUDE_MEM_CLOUD_SYNC_HUB_URL': '',
        'CLAUDE_MEM_CLOUD_SYNC_TOKEN': '', 'CLAUDE_MEM_CLOUD_SYNC_USER_ID': '',
        'CLAUDE_MEM_MEDIA_CAPTURE_ENABLED': 'false', 'CLAUDE_MEM_MEDIA_INFERENCE_ENABLED': 'false',
        'CLAUDE_MEM_WORK_VIEW_AUTO_REFRESH': 'false', 'CLAUDE_MEM_WORK_VIEW_COPY_PROVIDER': 'off',
        'CLAUDE_MEM_TELEGRAM_ENABLED': 'false', 'CLAUDE_MEM_NOTION_ENABLED': 'false',
        'CLAUDE_MEM_GROK_BOT_AWARENESS_ENABLED': 'false', 'CLAUDE_MEM_BACKUP_ENABLED': 'false',
    }
    with tempfile.TemporaryDirectory(prefix='claude-mem-qualification-', dir=output) as temporary:
        state = Path(temporary); os.chmod(state, 0o700)
        (state / 'settings.json').write_text(json.dumps(settings)); (state / 'settings.json').chmod(0o600)
        config_dir = state / 'claude-config'; config_dir.mkdir()
        # No inherited provider credentials; standard HOME is not changed.
        environment = {key: os.environ[key] for key in ('PATH', 'TMPDIR', 'LANG', 'LC_ALL', 'SYSTEMROOT') if key in os.environ}
        environment.update(settings)
        environment.update(CLAUDE_MEM_DATA_DIR=str(state), CLAUDE_CONFIG_DIR=str(config_dir),
                           CLAUDE_MEM_TELEMETRY='0', CLAUDE_MEM_TELEMETRY_ERRORS='0',
                           DO_NOT_TRACK='1', CLAUDE_MEM_ONLINE_OPTIN='false')
        policy = '(version 1)(allow default)(deny network*)(allow network-inbound (local ip "localhost:*"))(allow network-outbound (remote ip "localhost:*"))'
        process = None
        log = (output / 'claude-mem-worker.log').open('wb')

        def stop():
            nonlocal process
            if process is not None:
                if process.poll() is None:
                    os.killpg(process.pid, signal.SIGTERM)
                    try: process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL); process.wait(timeout=5)
                process = None

        def start():
            nonlocal process
            process = subprocess.Popen(['/usr/bin/sandbox-exec', '-p', policy, str(bun), str(worker), '--daemon'],
                                       cwd=source, env=environment, stdout=log, stderr=log, start_new_session=True)
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                if process.poll() is not None: raise RuntimeError('Worker exited; inspect the qualification log')
                try:
                    health = request('/api/health')
                    if health.get('pid') == process.pid and health.get('initialized') is True:
                        return health
                except (OSError, ValueError): pass
                time.sleep(.25)
            raise RuntimeError('Worker did not become ready within 60 seconds')

        try:
            first = start()
            viewer = request('/viewer')
            if not isinstance(viewer, str) or '<html' not in viewer.lower(): raise RuntimeError('Worker viewer did not return HTML')
            note = 'meshlit-local-qualification-memory-20261031'
            saved = request('/api/memory/save', {'text': note, 'title': 'Meshlit qualification', 'project': 'meshlit-qualification'})
            if saved.get('success') is not True or type(saved.get('id')) is not int: raise RuntimeError('Manual memory save failed')
            rows = request('/api/observations/batch', {'ids': [saved['id']], 'project': 'meshlit-qualification'})
            if note not in json.dumps(rows): raise RuntimeError('Manual memory could not be retrieved')
            stop(); second = start()
            rows = request('/api/observations/batch', {'ids': [saved['id']], 'project': 'meshlit-qualification'})
            if note not in json.dumps(rows): raise RuntimeError('Memory did not survive worker restart')
            request('/api/observation/' + str(saved['id']), method='DELETE')
            after = request('/api/observations/batch', {'ids': [saved['id']], 'project': 'meshlit-qualification'})
            if note in json.dumps(after): raise RuntimeError('Memory forget failed')
            report = {'sourceRevision': REVISION, 'workerBundleSha256': hashlib.sha256(worker.read_bytes()).hexdigest(),
                      'bunVersion': subprocess.check_output([str(bun), '--version'], text=True).strip(),
                      'workerVersion': second.get('version'), 'health': {'initialized': second.get('initialized'), 'mcpReady': second.get('mcpReady')},
                      'viewerHtml': True, 'manualSaveRead': True, 'persistsAcrossRestart': True, 'forget': True,
                      'networkBoundary': 'macOS sandbox-exec, loopback only',
                      'transcriptCapture': False, 'telemetry': False, 'cloudSync': False, 'providerFallback': False,
                      'llmGenerationRequested': False, 'semanticSearchQualified': False, 'dataDirectory': 'temporary, removed after qualification'}
            (output / 'claude-mem-runtime.json').write_text(json.dumps(report, indent=2) + '\n')
            print(json.dumps(report, indent=2))
        finally:
            stop(); log.close()


if __name__ == '__main__': main()
