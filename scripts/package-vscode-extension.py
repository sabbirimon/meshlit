#!/usr/bin/env python3
"""Package the dependency-free Meshlit VS Code handoff extension as a local VSIX."""
from pathlib import Path
import json, sys, zipfile
from xml.sax.saxutils import escape
root = Path(__file__).resolve().parents[1]
source = root / 'companions/vscode'
meta = json.loads((source / 'package.json').read_text())
out = Path(sys.argv[1]).resolve()
assert out.suffix == '.vsix' and not out.exists(), 'Use a new .vsix output path'
manifest = f'''<?xml version="1.0" encoding="utf-8"?>
<PackageManifest Version="2.0.0" xmlns="http://schemas.microsoft.com/developer/vsx-schema/2011">
<Metadata><Identity Language="en-US" Id="{meta['name']}" Version="{meta['version']}" Publisher="{meta['publisher']}"/><DisplayName>{escape(meta['displayName'])}</DisplayName><Description xml:space="preserve">{escape(meta['description'])}</Description><Tags>meshlit</Tags><Categories>Other</Categories><GalleryFlags>Preview</GalleryFlags><Properties><Property Id="Microsoft.VisualStudio.Code.Engine" Value="{meta['engines']['vscode']}"/><Property Id="Microsoft.VisualStudio.Code.ExtensionDependencies" Value=""/><Property Id="Microsoft.VisualStudio.Code.ExtensionPack" Value=""/></Properties></Metadata>
<Installation><InstallationTarget Id="Microsoft.VisualStudio.Code"/></Installation><Dependencies/>
<Assets><Asset Type="Microsoft.VisualStudio.Code.Manifest" Path="extension/package.json" Addressable="true"/><Asset Type="Microsoft.VisualStudio.Services.Content.Details" Path="extension/README.md" Addressable="true"/><Asset Type="Microsoft.VisualStudio.Services.Content.License" Path="extension/LICENSE" Addressable="true"/></Assets></PackageManifest>'''
content_types = '''<?xml version="1.0" encoding="utf-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="json" ContentType="application/json"/><Default Extension="cjs" ContentType="application/javascript"/><Default Extension="md" ContentType="text/markdown"/><Default Extension="vsixmanifest" ContentType="text/xml"/><Default Extension="" ContentType="text/plain"/></Types>'''
with zipfile.ZipFile(out, 'x', zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('extension.vsixmanifest', manifest)
    archive.writestr('[Content_Types].xml', content_types)
    for name in ('package.json','extension.cjs','launcher.cjs','README.md'):
        archive.write(source / name, 'extension/' + name)
    archive.write(root / 'LICENSE', 'extension/LICENSE')
print(out)
