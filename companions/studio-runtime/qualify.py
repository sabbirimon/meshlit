"""Offline upstream preflight. Does not start an agent or open a model connection."""
import argparse
import hashlib
import importlib.metadata
import json
from pathlib import Path
import platform
import zipfile


def verify(path, artifact):
    if path.stat().st_size != artifact['size']:
        raise ValueError(f"Size mismatch: {path.name}")
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    if digest.hexdigest() != artifact['sha256']:
        raise ValueError(f"SHA-256 mismatch: {path.name}")
    return {'name': path.name, 'size': path.stat().st_size,
            'sha256': digest.hexdigest(), 'verified': True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', required=True, type=Path)
    args = parser.parse_args()
    lock = json.loads(Path(__file__).with_name('upstreams.lock.json').read_text())
    if (platform.system(), platform.machine()) != ('Darwin', 'x86_64'):
        raise SystemExit('This initial workbench preflight is qualified for Intel macOS only.')
    wheel = next(a for a in lock['antigravity']['wheels']
                 if 'macosx_11_0_x86_64' in a['name'])
    workbench = lock['workbench']['artifacts'][0]
    artifacts = [verify(args.cache / a['name'], a) for a in (wheel, workbench)]
    if importlib.metadata.version('google-antigravity') != lock['antigravity']['version']:
        raise ValueError('Installed SDK version differs from lock')
    from google.antigravity import (CapabilitiesConfig, LocalOpenAIAgentConfig,
                                   ModelAPIRetryConfig, ModelOutputRetryConfig,
                                   RetryConfig)
    from google.antigravity.hooks.policy import deny
    # Explicit capability configuration: never inherit autonomous SDK defaults.
    # No Agent is created. Port 1 is only a configuration fixture, never contacted.
    config = LocalOpenAIAgentConfig(
        model='qualification-only', base_url='http://127.0.0.1:1/v1',
        capabilities=CapabilitiesConfig(enabled_tools=[], enable_subagents=False),
        policies=[deny('*')], tools=[], mcp_servers=[], subagents=[],
        triggers=[], skills_paths=[], inline_skills=[], workspaces=[],
        retry_config=RetryConfig(api_retry=ModelAPIRetryConfig(max_retries=0),
            model_output_retry=ModelOutputRetryConfig(max_retries=0)),
    )
    assert config.capabilities.enabled_tools == []
    assert config.capabilities.enable_subagents is False
    assert config.retry_config.api_retry.max_retries == 0
    with zipfile.ZipFile(args.cache / workbench['name']) as archive:
        names = archive.namelist()
        required = ['Contents/Resources/app/product.json',
                    'Contents/Resources/app/out/vs/workbench/workbench.desktop.main.js']
        for suffix in required:
            if not any(name.endswith(suffix) for name in names):
                raise ValueError(f'Missing workbench component: {suffix}')
    print(json.dumps({'platform': platform.system(), 'architecture': platform.machine(),
        'sdk_version': lock['antigravity']['version'], 'artifacts': artifacts,
        'sdk_import_and_explicit_config': 'passed', 'workbench_archive': 'passed',
        'agent_started': False, 'model_requests': 0,
        'integrated_ide_qualified': False, 'device_control_qualified': False}, indent=2))


if __name__ == '__main__':
    main()
