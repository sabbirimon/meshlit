"""Describe reviewed local gateway build inputs for the development workbench.

No downloads, provider logins, credentials or service starts. Portable installers
must carry the referenced Node runtime, complete dependency trees and notices.
"""
import argparse
import hashlib
import json
from pathlib import Path

PINS = {
    'OmniRoute': ('3.8.52', 'package.json', 'dist/server.js'),
    'FreeLLMAPI': ('0.2.1', 'server/package.json', 'server/dist/index.js'),
}


def describe(service, root, node):
    version, metadata, entry = PINS[service]
    root, node = Path(root).resolve(), Path(node).resolve()
    if json.loads((root / metadata).read_text())['version'] != version:
        raise ValueError('Gateway version differs from the reviewed pin')
    if not node.is_file() or not (root / 'LICENSE').is_file():
        raise ValueError('Missing Node runtime or upstream license')
    if service == 'FreeLLMAPI' and not (root / 'client/dist/index.html').is_file():
        raise ValueError('Build the actual FreeLLMAPI dashboard first')
    return dict(root=str(root), node=str(node), version=version,
                sha256=hashlib.sha256((root / entry).read_bytes()).hexdigest())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--node', required=True)
    parser.add_argument('--omniroute', required=True)
    parser.add_argument('--freellmapi', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    config = {service: describe(service, root, args.node) for service, root in
              [('OmniRoute', args.omniroute), ('FreeLLMAPI', args.freellmapi)]}
    Path(args.output).write_text(json.dumps(config, indent=2) + '\n')
