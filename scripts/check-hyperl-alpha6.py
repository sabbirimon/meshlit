#!/usr/bin/env python3
"""Verify the separate licence boundary, pinned imports and shared ABI limits."""
import hashlib
import json
import re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'docs/hyperl/ALPHA6_PROVENANCE.json').read_text())
assert manifest['upstreamCommit'] == 'c03a8590d959f5480c54efb4e3c3f87af6532e39'
for item in manifest['files']:
    data = (root / item['path']).read_bytes()
    assert hashlib.sha256(data).hexdigest() == item['sha256'], item['path']
    if item['path'].endswith(('.c','.h')):
        assert item['sha256'] == item['upstreamSha256'], 'Modified pinned native ABI source'
module = root / 'core-hyperl'
for name in ['LICENSE','NOTICE','Apache-2.0.txt','LICENSE_HISTORY.md']:
    assert (module/name).read_bytes() == (module/'src/main/assets/hyperl'/name).read_bytes(), name
assert 'Apache License' in (root/'LICENSE').read_text()
assert 'LicenseRef-HyperL-Community-1.0' in (module/'LICENSE').read_text()
contract = json.loads((module/'contracts/hyperl-1.json').read_text())
kotlin = (module/'src/main/kotlin/com/meshlit/core/hyperl/HyperLContract.kt').read_text()
header = (module/'src/main/cpp/include/hyperl_contract.h').read_text()
symbols = {'maxInputs':'MAX_INPUTS', 'maxSteps':'MAX_STEPS', 'maxVectorElements':'MAX_VECTOR_ELEMENTS',
           'maxRetainedElements':'MAX_RETAINED_ELEMENTS', 'cancellationInterval':'CANCELLATION_INTERVAL',
           'maxProgramChars':'MAX_PROGRAM_CHARS', 'maxInputChars':'MAX_INPUT_CHARS'}
for key, symbol in symbols.items():
    value = contract[key]
    assert re.search(r'const val '+symbol+r' = '+str(value)+r'\b', kotlin)
    assert re.search(r'#define HL_'+symbol+r' '+str(value)+r'u\b', header)
for operation, meta in contract['operations'].items():
    assert re.search(r'#define HL_OPCODE_'+operation.upper()+r' '+str(meta['opcode'])+r'\b', header)
    assert '"'+operation+'" to '+str(meta['operands']) in kotlin
print('HyperL alpha.6 provenance, offline notices and Kotlin/C limits match; no hardware claim')
