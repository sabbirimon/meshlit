"""Prepare an Intel Mac development workbench; never replace the installed Studio."""
import argparse
import json
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile
from qualify import verify
from prepare_app_access import build as build_app_access


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--python',type=Path,required=True)
    parser.add_argument('--java',type=Path,required=True)
    parser.add_argument('--resources',type=Path,required=True)
    parser.add_argument('--sign-identity',default='-',help='Installed signing identity; default is ad-hoc development signing, not a trusted publisher.')
    args=parser.parse_args()
    here=Path(__file__).resolve().parent; repo=here.parent.parent
    lock=json.loads((here/'upstreams.lock.json').read_text())
    artifact=lock['workbench']['artifacts'][0]; archive=args.cache/artifact['name']
    verify(archive,artifact)
    if args.output.exists(): raise SystemExit('Output already exists; choose a new staging path.')
    jar=repo/'desktopApp/build/libs/desktopApp.jar'
    dependencies=repo/'desktopApp/build/compose/binaries/main/app/Meshlit.app/Contents/app'
    for file in [args.python,args.java,jar,args.resources/'local/engine.json']:
        if not file.is_file(): raise SystemExit(f'Missing input: {file}')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=args.output.parent,prefix='.meshlit-workbench-') as temp:
        subprocess.run(['ditto','-x','-k',str(archive),temp],check=True)
        app=Path(temp)/'VSCodium.app'; content=app/'Contents'
        app_resources=content/'Resources/app'
        product_path=app_resources/'product.json'; product=json.loads(product_path.read_text())
        product.update(nameShort='Meshlit',nameLong='Meshlit Studio',applicationName='meshlit-studio',
            dataFolderName='.meshlit-workbench',urlProtocol='meshlit',darwinBundleIdentifier='com.meshlit.desktop',
            reportIssueUrl='https://github.com/sabbirimon/meshlit/issues',enableTelemetry=False)
        for key in ('updateUrl','darwinProfileUUID','darwinProfilePayloadUUID'):
            product.pop(key,None)
        # Electron uses its package productName for its Keychain service name.
        # New builds use Meshlit's own item; do not delete/migrate the owner's old item silently.
        electron_package=app_resources/'package.json'
        electron_metadata=json.loads(electron_package.read_text())
        electron_metadata.update(name='meshlit-studio',productName='Meshlit')
        electron_package.write_text(json.dumps(electron_metadata,indent=2)+'\n')
        # Retain upstream extension gallery and all licences/notices.
        product_path.write_text(json.dumps(product,indent=2)+'\n')
        plist_path=content/'Info.plist'; plist=plistlib.loads(plist_path.read_bytes())
        plist.update(CFBundleName='Meshlit',CFBundleDisplayName='Meshlit',CFBundleIdentifier='com.meshlit.desktop')
        icon=repo/'desktopApp/distribution/icons/meshlit.icns'
        shutil.copy2(icon,content/'Resources/meshlit.icns')
        plist.update(CFBundleIconFile='meshlit.icns',CFBundleIconName='meshlit',
            CFBundleURLTypes=[dict(CFBundleTypeRole='Viewer',CFBundleURLName='Meshlit',CFBundleURLSchemes=['meshlit'])])
        plist_path.write_bytes(plistlib.dumps(plist))
        extension=app_resources/'extensions/meshlit-workbench'
        shutil.copytree(here/'extension',extension,ignore=shutil.ignore_patterns('*.test.cjs'))
        build_app_access(extension,args.sign_identity)
        # Snapshot our jar; reused dependency jars/resources stay read-only. This is a dev build.
        shutil.copy2(jar,extension/'meshlit-backend.jar')
        # agent_host remains a source development path so fixes do not require another large archive extraction.
        config=dict(python=str(args.python.absolute()),agentHost=str(here/'agent_host.py'),
            backend=[str(args.java.resolve()),f'-Dmeshlit.dev.resources={args.resources.resolve()}',
                     '-cp',str(args.output.resolve()/'Contents/Resources/app/extensions/meshlit-workbench/meshlit-backend.jar')+':'+str(dependencies)+'/*',
                     'com.meshlit.desktop.MainKt','--ide-backend'])
        (extension/'runtime.json').write_text(json.dumps(config,indent=2)+'\n')
        for helper in list((content/'Frameworks').glob('VSCodium Helper*.app')):
            old=helper.stem; name=old.replace('VSCodium','Meshlit',1)
            helper_plist=helper/'Contents/Info.plist'
            data=plistlib.loads(helper_plist.read_bytes())
            executable=helper/'Contents/MacOS'/old
            executable.rename(executable.with_name(name))
            data.update(CFBundleDisplayName=name,CFBundleName=name,CFBundleExecutable=name)
            suffix=old.removeprefix('VSCodium Helper').strip().strip('()')
            data['CFBundleIdentifier']='com.meshlit.desktop.helper'+('.'+suffix if suffix else '')
            helper_plist.write_bytes(plistlib.dumps(data))
            helper.rename(helper.with_name(name+'.app'))
        shutil.move(str(app),args.output)
    subprocess.run(['codesign','--force','--deep','--sign',args.sign_identity,str(args.output)],check=True)
    subprocess.run(['codesign','--verify','--deep','--strict',str(args.output)],check=True)
    print(json.dumps({'app':str(args.output),'kind':'development workbench','upstream':lock['workbench']['version'],
        'dependencies':'Existing local SDK environment, JDK, Studio jars and bundled model resources required',
        'signing':'ad-hoc development; Keychain may ask again after rebuilds' if args.sign_identity=='-' else 'configured identity; notarization not performed',
        'launched':False},indent=2))


if __name__=='__main__': main()
