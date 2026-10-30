"""Prepare an Intel Mac workbench in a new staging location; never replace the installed Studio."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile
import zipfile
from qualify import verify
from prepare_app_access import build as build_app_access


def digest(file):
    h=hashlib.sha256()
    with Path(file).open('rb') as source:
        for block in iter(lambda:source.read(1024*1024),b''): h.update(block)
    return h.hexdigest()


def safe_tree(directory):
    directory=Path(directory).resolve(strict=True)
    if not directory.is_dir(): raise ValueError('Runtime input must be a directory')
    for file in directory.rglob('*'):
        if file.is_symlink() and directory not in file.resolve(strict=True).parents:
            raise ValueError(f'External runtime symbolic link: {file.relative_to(directory)}')
    return directory


def check_python(directory,version):
    directory=safe_tree(directory); executable=directory/'bin/python3'
    if (directory/'pyvenv.cfg').exists() or not executable.is_file():
        raise ValueError('Portable Python must be a standalone distribution, not a virtual environment')
    if not list(directory.glob('lib/python*/LICENSE*')):
        raise ValueError('Preserve the standalone Python license')
    environment={k:os.environ[k] for k in ('PATH','TMPDIR','LANG') if k in os.environ}
    result=subprocess.run([str(executable),'-B','-s','-c',
        'import importlib.metadata, google.antigravity; print(importlib.metadata.version("google-antigravity"))'],
        check=True,capture_output=True,text=True,timeout=60,env=environment,cwd='/')
    if result.stdout.strip()!=version: raise ValueError('Portable Python must contain the pinned Antigravity SDK')


def add_portable_runtime(args,extension,here,repo,dependencies,lock):
    check_python(args.portable_python,lock['antigravity']['version'])
    sdk=next(item for item in lock['antigravity']['wheels'] if 'macosx_11_0_x86_64' in item['name'])
    wheel=args.cache/sdk['name'];verify(wheel,sdk)
    sites=list(args.portable_python.glob('lib/python*/site-packages'))
    if len(sites)!=1: raise ValueError('Expected one standalone Python site-packages directory')
    with zipfile.ZipFile(wheel) as archive:
        for name in archive.namelist():
            if name.endswith('/') or name.endswith('.dist-info/RECORD'): continue
            target=sites[0]/name
            if not target.is_file() or digest(target)!=hashlib.sha256(archive.read(name)).hexdigest():
                raise ValueError(f'Installed SDK differs from the pinned wheel: {name}')
    java=safe_tree(args.portable_java); resources=safe_tree(args.resources)
    if not (java/'bin/java').is_file() or not (java/'legal').is_dir():
        raise ValueError('Portable Java needs bin/java and its complete legal notices')
    model=json.loads((repo/'desktopApp/distribution/bundled-model.json').read_text())
    weights=resources/'models'/model['filename']
    if not weights.is_file() or weights.stat().st_size!=model['sizeBytes'] or digest(weights)!=model['sha256']:
        raise ValueError('Portable package requires the pinned real bundled model')
    native=json.loads((resources/'local/engine.json').read_text())
    if digest(resources/'local/llama-server')!=native['sha256']:
        raise ValueError('Source local engine does not match its integrity manifest')
    runtime=extension/'runtime'; runtime.mkdir()
    shutil.copytree(args.portable_python,runtime/'python',symlinks=True)
    shutil.copytree(java,runtime/'java',symlinks=True)
    shutil.copytree(resources,runtime/'resources',symlinks=True)
    shutil.copy2(repo/'desktopApp/distribution/bundled-model.json',runtime/'resources/models/bundled-model.json')
    jars=runtime/'jars'; jars.mkdir()
    copied=0
    for jar in sorted(dependencies.glob('*.jar')):
        if jar.name=='desktopApp.jar': continue
        shutil.copy2(jar,jars/jar.name);copied+=1
    if not copied: raise ValueError('Missing prepared Studio dependency jars')
    for name in ('agent_host.py','runtime_config.py'):
        shutil.copy2(here/name,runtime/name)
    notices=runtime/'notices'; notices.mkdir()
    for name in ('LICENSE','NOTICE','THIRD_PARTY_NOTICES.md'):
        shutil.copy2(repo/name,notices/name)
    shutil.copytree(repo/'desktopApp/distribution/notices',notices/'desktop')
    shutil.copy2(here/'upstreams.lock.json',notices/'upstreams.lock.json')
    # The SDK's own .dist-info, Python LICENSE and JDK legal tree are retained in full.
    check_python(runtime/'python',lock['antigravity']['version'])
    return dict(schema=2,kind='portable',python='runtime/python/bin/python3',agentHost='runtime/agent_host.py',
        backend=dict(java='runtime/java/bin/java',resources='runtime/resources',
            classpath=['meshlit-backend.jar','runtime/jars/*'],main='com.meshlit.desktop.MainKt'))


def qualify_native_runtime(runtime,identity):
    native=[]
    magics={b'\xfe\xed\xfa\xce',b'\xce\xfa\xed\xfe',b'\xfe\xed\xfa\xcf',b'\xcf\xfa\xed\xfe',b'\xca\xfe\xba\xbe',b'\xbe\xba\xfe\xca'}
    for file in sorted(runtime.rglob('*')):
        if not file.is_file() or file.is_symlink(): continue
        with file.open('rb') as source:
            if source.read(4) not in magics: continue
        kind=subprocess.run(['/usr/bin/file','-b',str(file)],check=True,capture_output=True,text=True).stdout
        if 'Mach-O' not in kind: continue  # Java class files share one fat-binary magic.
        if 'x86_64' not in kind: raise ValueError(f'Non-Intel native runtime: {file.relative_to(runtime)}')
        output=subprocess.run(['/usr/bin/otool','-L',str(file)],check=True,capture_output=True,text=True).stdout
        for line in output.splitlines()[1:]:
            if line.rstrip().endswith(':'): continue
            dependency=line.strip().split(' (',1)[0]
            if dependency.startswith('/') and not dependency.startswith(('/usr/lib/','/System/Library/')):
                raise ValueError(f'External native runtime dependency: {dependency}')
        commands=subprocess.run(['/usr/bin/otool','-l',str(file)],check=True,capture_output=True,text=True).stdout.splitlines()
        rpath=False
        for line in commands:
            if line.strip()=='cmd LC_RPATH': rpath=True
            elif rpath and line.strip().startswith('path '):
                location=line.strip()[5:].split(' (offset ',1)[0];rpath=False
                if location.startswith('/') and not location.startswith(('/usr/lib/','/System/Library/')):
                    raise ValueError(f'External native runtime search path: {location}')
        subprocess.run(['/usr/bin/codesign','--force','--sign',identity,str(file)],check=True,capture_output=True)
        native.append(str(file.relative_to(runtime)))
    return native


def reseal_engines(resources):
    engine=resources/'local/engine.json'
    metadata=json.loads(engine.read_text());metadata['sha256']=digest(resources/'local/llama-server')
    if 'avx2' in metadata: metadata['avx2']['sha256']=digest(resources/'local/llama-server-avx2')
    if 'cpuProbeSha256' in metadata: metadata['cpuProbeSha256']=digest(resources/'local/cpu-features')
    engine.write_text(json.dumps(metadata,indent=2)+'\n')
    hyperl=resources/'local/hyperl-engine.json'
    if hyperl.is_file():
        metadata=json.loads(hyperl.read_text());metadata['sha256']=digest(resources/'local/libmeshlit_hyperl.dylib')
        hyperl.write_text(json.dumps(metadata,indent=2)+'\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--python',type=Path,help='Development Python executable (not used with --portable)')
    parser.add_argument('--java',type=Path,help='Development Java executable (not used with --portable)')
    parser.add_argument('--resources',type=Path,required=True)
    parser.add_argument('--portable',action='store_true',help='Bundle explicit standalone runtime trees; no development path dependencies')
    parser.add_argument('--portable-python',type=Path,help='Standalone Python tree with the pinned SDK and dependencies installed')
    parser.add_argument('--portable-java',type=Path,help='Redistributable Java runtime Home containing bin/java and legal/')
    parser.add_argument('--gateways',type=Path,help='Optional prepare_gateways.py manifest for reviewed development gateway builds')
    parser.add_argument('--sign-identity',default='-',help='Installed signing identity; default is ad-hoc development signing, not a trusted publisher.')
    args=parser.parse_args()
    if args.portable:
        if not args.portable_python or not args.portable_java: parser.error('--portable requires --portable-python and --portable-java')
        if args.gateways: parser.error('Development gateway paths cannot be embedded in a portable package; configure companions after installation')
    elif not args.python or not args.java: parser.error('Development builds require --python and --java')
    here=Path(__file__).resolve().parent; repo=here.parent.parent
    lock=json.loads((here/'upstreams.lock.json').read_text())
    artifact=lock['workbench']['artifacts'][0]; archive=args.cache/artifact['name']
    verify(archive,artifact)
    if args.output.exists(): raise SystemExit('Output already exists; choose a new staging path.')
    jar=repo/'desktopApp/build/libs/desktopApp.jar'
    dependencies=repo/'desktopApp/build/compose/binaries/main/app/Meshlit.app/Contents/app'
    for file in ([jar,args.resources/'local/engine.json']+([] if args.portable else [args.python,args.java])):
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
        shutil.copy2(repo/'docs/USER_MANUAL.md',extension/'USER_MANUAL.md')
        build_app_access(extension,args.sign_identity)
        # Snapshot our jar; reused dependency jars/resources stay read-only. This is a dev build.
        shutil.copy2(jar,extension/'meshlit-backend.jar')
        if args.portable:
            config=add_portable_runtime(args,extension,here,repo,dependencies,lock)
        else:
            # Source and dependency references are explicit development-only inputs.
            config=dict(python=str(args.python.absolute()),agentHost=str(here/'agent_host.py'),
                backend=[str(args.java.resolve()),f'-Dmeshlit.dev.resources={args.resources.resolve()}',
                         '-cp',str(args.output.resolve()/'Contents/Resources/app/extensions/meshlit-workbench/meshlit-backend.jar')+':'+str(dependencies)+'/*',
                         'com.meshlit.desktop.MainKt','--ide-backend'])
        if args.gateways:
            config['gateways']=json.loads(args.gateways.read_text())
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
    extension=args.output/'Contents/Resources/app/extensions/meshlit-workbench'
    if args.portable:
        native=qualify_native_runtime(extension/'runtime',args.sign_identity)
    subprocess.run(['codesign','--force','--deep','--sign',args.sign_identity,str(args.output)],check=True)
    if args.portable:
        reseal_engines(extension/'runtime/resources')
        check_python(extension/'runtime/python',lock['antigravity']['version'])
        from runtime_config import load_runtime
        resolved=load_runtime(extension/'runtime.json')
        subprocess.run([resolved['backend'][0],'-version'],check=True,capture_output=True,timeout=30)
        report=dict(kind='portable candidate',architecture='x86_64',sdk=lock['antigravity']['version'],
            workbench=lock['workbench']['version'],nativeFilesChecked=native,externalNativeDependencies=[],
            absoluteDevelopmentPaths=False,pythonImportAfterRelocation=True,javaLaunchAfterRelocation=True,
            installedAppQualified=False,notarized=False,upstreamServiceRuntimesBundled=False)
        (extension/'PACKAGING.json').write_text(json.dumps(report,indent=2)+'\n')
        subprocess.run(['codesign','--force','--sign',args.sign_identity,str(args.output)],check=True)
    subprocess.run(['codesign','--verify','--deep','--strict',str(args.output)],check=True)
    print(json.dumps({'app':str(args.output),'kind':'portable candidate' if args.portable else 'development workbench','upstream':lock['workbench']['version'],
        'dependencies':'Bundled Python/SDK, Java, Studio jars and local resources; optional services configured separately' if args.portable else 'Existing local SDK environment, JDK, Studio jars and bundled model resources required',
        'signing':'ad-hoc development; Keychain may ask again after rebuilds' if args.sign_identity=='-' else 'configured identity; notarization not performed',
        'launched':False},indent=2))


if __name__=='__main__': main()
