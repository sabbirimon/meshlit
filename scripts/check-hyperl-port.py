#!/usr/bin/env python3
"""Check owned source-port provenance. Does not qualify a device or download code."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--upstream', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'docs/hyperl/PORT_PROVENANCE.json').read_text())
assert manifest['format'] == 'hyperl-port-provenance/1'
for entry in manifest['files']:
    name = entry['file']
    assert Path(name).name == name and name.endswith('.kt')
    port = (root / 'core-gpu/src/main/kotlin/com/meshlit/core/gpu' / name).read_text()
    normalized = port.replace('package com.meshlit.core.gpu', 'package org.hyperl')
    assert hashlib.sha256(normalized.encode()).hexdigest() == entry['normalizedSha256'], f'Port changed: {name}'
    if args.upstream:
        original = (args.upstream / 'src/main/kotlin/org/hyperl' / name).read_text()
        assert original == normalized, f'Upstream mismatch: {name}'
print(f"HyperL port provenance matches {len(manifest['files'])} portable files; no hardware claim")
