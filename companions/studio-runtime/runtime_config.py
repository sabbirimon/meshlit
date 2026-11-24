"""Resolve packaged runtime paths after relocation; development manifests stay compatible."""
import json
import os
from pathlib import Path


def load_runtime(file):
    file = Path(file).resolve()
    if file.stat().st_size > 65536:
        raise ValueError('Runtime manifest is too large')
    config = json.loads(file.read_text())
    if config.get('schema') in (None, 1):
        return config
    if config['schema'] != 2:
        raise ValueError('Unsupported runtime manifest version')
    backend = config.get('backend')
    if config.get('kind') != 'portable' or not isinstance(backend, dict) or not isinstance(backend.get('classpath'), list) or not 1 <= len(backend['classpath']) <= 8:
        raise ValueError('Invalid portable runtime manifest')
    root = file.parent

    def local(relative, glob=False):
        if not isinstance(relative, str) or len(relative) > 300 or '\\' in relative or relative.startswith('/') or any(p in ('', '.', '..') for p in relative.split('/')) or any(ord(c) < 32 for c in relative):
            raise ValueError('Runtime paths must stay inside the application')
        wildcard = glob and relative.endswith('/*')
        base = relative[:-2] if wildcard else relative
        if '*' in base:
            raise ValueError('Invalid runtime wildcard')
        resolved = (root / base).resolve(strict=True)
        if root not in resolved.parents:
            raise ValueError('Runtime path escapes the application')
        return str(resolved / '*') if wildcard else str(resolved)

    if backend.get('main') != 'com.meshlit.desktop.MainKt':
        raise ValueError('Unexpected portable backend')
    return dict(config, python=local(config['python']), pythonArgs=['-B', '-s'], agentHost=local(config['agentHost']),
                backend=[local(backend['java']), '-Dmeshlit.dev.resources=' + local(backend['resources']),
                         '-cp', os.pathsep.join(local(p, True) for p in backend['classpath']),
                         '-Djava.library.path=' + str(Path(local(backend['resources'])) / 'local'),
                         backend['main'], '--ide-backend'])
