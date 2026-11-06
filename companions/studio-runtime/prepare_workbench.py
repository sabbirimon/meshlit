"""Prepare an Intel Mac workbench in a new staging location; never replace the installed Studio."""
import argparse
import base64
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
from prepare_shell import configure_chat_shell
from studio_models import load_catalog, verify_catalog


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
    catalog=load_catalog(repo/'desktopApp/distribution/studio-models.json')
    verify_catalog(resources/'models',catalog)
    native=json.loads((resources/'local/engine.json').read_text())
    if digest(resources/'local/llama-server')!=native['sha256']:
        raise ValueError('Source local engine does not match its integrity manifest')
    runtime=extension/'runtime'; runtime.mkdir()
    shutil.copytree(args.portable_python,runtime/'python',symlinks=True)
    shutil.copytree(java,runtime/'java',symlinks=True)
    shutil.copytree(resources,runtime/'resources',symlinks=True)
    verify_catalog(runtime/'resources/models',catalog)
    (runtime/'resources/models/studio-models.json').write_text(json.dumps(catalog,indent=2)+'\n')
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
    if args.portable_openssl: bundle_openssl(runtime,args.portable_openssl)
    # The SDK's own .dist-info, Python LICENSE and JDK legal tree are retained in full.
    check_python(runtime/'python',lock['antigravity']['version'])
    return dict(schema=2,kind='portable',python='runtime/python/bin/python3',agentHost='runtime/agent_host.py',
        backend=dict(java='runtime/java/bin/java',resources='runtime/resources',
            classpath=['meshlit-backend.jar','runtime/jars/*'],main='com.meshlit.desktop.MainKt'))


def bundle_openssl(runtime, prefix):
    runtime=Path(runtime); prefix=Path(prefix).resolve(strict=True)
    destination=runtime/'python/lib/meshlit-openssl'; destination.mkdir(parents=True,exist_ok=True)
    sources={}
    for name in ('libssl.3.dylib','libcrypto.3.dylib'):
        source=(prefix/'lib'/name).resolve(strict=True)
        target=destination/name
        if target.exists(): target.chmod(target.stat().st_mode | 0o200)
        shutil.copy2(source,target)
        target.chmod(target.stat().st_mode | 0o200)
        sources[source]=target
    notices=runtime/'notices/openssl';notices.mkdir(parents=True,exist_ok=True)
    shutil.copy2(prefix/'LICENSE.txt',notices/'LICENSE.txt')
    for file in (runtime/'python').rglob('*'):
        if not file.is_file() or file.is_symlink():continue
        with file.open('rb') as stream:
            if stream.read(4) not in {b'\xfe\xed\xfa\xce',b'\xce\xfa\xed\xfe',b'\xfe\xed\xfa\xcf',b'\xcf\xfa\xed\xfe',b'\xca\xfe\xba\xbe',b'\xbe\xba\xfe\xca'}:continue
        output=subprocess.run(['otool','-L',str(file)],check=True,capture_output=True,text=True).stdout
        for line in output.splitlines()[1:]:
            dependency=line.strip().split(' (',1)[0]
            if not dependency.startswith('/'):continue
            source=Path(dependency).resolve()
            if source in sources:
                file.chmod(file.stat().st_mode | 0o200)
                relative=os.path.relpath(sources[source],file.parent)
                subprocess.run(['install_name_tool','-change',dependency,'@loader_path/'+relative,str(file)],check=True,capture_output=True)


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
        identity_output=subprocess.run(['/usr/bin/otool','-D',str(file)],capture_output=True,text=True)
        identity_lines=identity_output.stdout.splitlines()
        library_id=identity_lines[1].strip() if len(identity_lines)==2 else None
        file.chmod(file.stat().st_mode | 0o200)  # Only mutate our copied runtime, including read-only upstream files.
        if library_id and library_id.startswith('/'):
            subprocess.run(['/usr/bin/install_name_tool','-id','@rpath/'+file.name,str(file)],check=True,capture_output=True)
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


def refresh_workbench_checksums(resources):
    """Seal the branded workbench using Code OSS's existing integrity contract.

    The pinned product lists paths relative to out/, with unpadded base64
    SHA-256 digests. Keep every entry so the installed integrity service still
    detects subsequent modifications, instead of suppressing its warning.
    """
    resources = Path(resources)
    product_path = resources / 'product.json'
    product = json.loads(product_path.read_text())
    checksums = product.get('checksums')
    if not isinstance(checksums, dict) or not checksums:
        raise ValueError('Pinned workbench product must retain integrity checksums')
    out = (resources / 'out').resolve(strict=True)
    refreshed = {}
    for name in checksums:
        relative = Path(name)
        target = (out / relative).resolve(strict=True)
        if relative.is_absolute() or '..' in relative.parts or out not in target.parents or not target.is_file():
            raise ValueError('Invalid workbench integrity path: ' + name)
        checksum = hashlib.sha256()
        with target.open('rb') as source:
            for block in iter(lambda: source.read(1024 * 1024), b''):
                checksum.update(block)
        refreshed[name] = base64.b64encode(checksum.digest()).decode('ascii').rstrip('=')
    product['checksums'] = refreshed
    product_path.write_text(json.dumps(product, indent=2) + '\n')


def finalize_workbench(args,lock):
    extension=args.output/'Contents/Resources/app/extensions/meshlit-workbench'
    if args.portable:
        native=qualify_native_runtime(extension/'runtime',args.sign_identity)
    refresh_workbench_checksums(args.output/'Contents/Resources/app')
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



def configure_workbench_defaults(resources, session_secrets=False):
    """Apply explicit product defaults to the locked Code OSS bundle."""
    def replace(path, old, new):
        source = path.read_text()
        if source.count(old) != 1:
            raise ValueError('Unexpected pinned workbench startup code: ' + path.name)
        path.write_text(source.replace(old, new))

    out = resources / 'out'
    restore_setting = 'this.configurationService.inspect("window.restoreWindows")'
    replace(out / 'mainImpl.js', 'this.configurationService.getValue("window")?.restoreWindows||"all"',
            '(' + restore_setting + '.policyValue??' + restore_setting + '.userLocalValue??"none")')
    restore_schema = '"window.restoreWindows":{type:"string",enum:["preserve","all","folders","one","none"],enumDescriptions:[d(26700,null),d(26696,null),d(26697,null),d(26699,null),d(26698,null)],default:'
    for name in ('workbench', 'sessions'):
        replace(out / 'vs' / name / (name + '.desktop.main.js'), restore_schema + '"all"', restore_schema + '"none"')
    if session_secrets:
        entry = 'g1("main");await import("./mainImpl.js");'
        replace(out / 'main.js', entry, 'process.argv.splice(1,0,"--use-inmemory-secretstorage");' + entry)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--python',type=Path,help='Development Python executable (not used with --portable)')
    parser.add_argument('--java',type=Path,help='Development Java executable (not used with --portable)')
    parser.add_argument('--resources',type=Path,required=True)
    parser.add_argument('--portable',action='store_true',help='Bundle explicit standalone runtime trees; no development path dependencies')
    parser.add_argument('--portable-python',type=Path,help='Standalone Python tree with the pinned SDK and dependencies installed')
    parser.add_argument('--portable-openssl',type=Path,help='Explicit OpenSSL prefix with libssl, libcrypto and LICENSE.txt to bundle for Python')
    parser.add_argument('--portable-java',type=Path,help='Redistributable Java runtime Home containing bin/java and legal/')
    parser.add_argument('--gateways',type=Path,help='Optional prepare_gateways.py manifest for reviewed development gateway builds')
    parser.add_argument('--sign-identity',default='-',help='Installed signing identity; default is ad-hoc development signing, not a trusted publisher.')
    parser.add_argument('--session-secrets',action='store_true',help='Use volatile editor secrets in a QA build without Keychain access')
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
        configure_workbench_defaults(app_resources,args.session_secrets)
        configure_chat_shell(app_resources)
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
        shutil.copy2(repo/'docs/OPEN_RESOURCE_NETWORK.md',extension/'OPEN_RESOURCE_NETWORK.md')
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
        config['secretStorage']='session' if args.session_secrets else 'protected'
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
    finalize_workbench(args,lock)


if __name__=='__main__': main()
