#!/usr/bin/env python3
"""Validate documentation references; this does not validate runtime behavior."""
import json
import re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
data=json.loads((root/'docs/feature-map.json').read_text())
ids=[entry['id'] for entry in data['features']]
assert len(ids)==len(set(ids)), 'Duplicate feature IDs'
for feature in data['features']:
    for source in feature['sources']:
        assert (root/source).is_file(), f'{feature["id"]}: missing {source}'
source=(root/'core-mcp/src/main/kotlin/com/meshlit/core/mcp/control/AgentCommands.kt').read_text()
operations=re.findall(r'\b[A-Z][A-Z_]+\b',source.split('enum class AgentOperation {',1)[1].split('}',1)[0])
assert data['operations']==operations, 'Update feature-map operation inventory'
assert (root/data['acceptanceLedger']).is_file()
registered=sorted(set(re.findall(r'"(:[\w-]+)"',(root/'settings.gradle.kts').read_text())))
assert data['modules']==[m[1:] for m in registered], 'Update registered Gradle module inventory'
print(f'Feature map valid: {len(ids)} features, {len(operations)} operations, {len(data["modules"])} modules')
