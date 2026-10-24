#!/usr/bin/env python3
"""Package lifecycle inside a dedicated owner-provisioned Linux lab guest.
No downloads/install happen on import. Package install executes package scripts.
"""
import argparse, hashlib, json, os, re, shutil, signal, subprocess, sys, tempfile, urllib.request
from pathlib import Path

def execute(argv,timeout=120):
    with tempfile.TemporaryFile() as stream:
        proc=subprocess.Popen(argv,stdout=stream,stderr=stream,start_new_session=True)
        try: code=proc.wait(timeout=timeout)
        except BaseException:
            os.killpg(proc.pid,signal.SIGKILL);proc.wait();raise
        stream.seek(0);data=stream.read(65537)
        # Do not expose package-manager output which can contain credentialed URLs.
        if code: raise ValueError('Package manager returned failure')
        return {'exit_code':code,'output_bytes':len(data),'truncated':len(data)>65536}

def file_digest(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()

def package_name(value):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.+-]{0,99}',value):raise ValueError('Invalid package name')
    return value

def manager(name):
    binaries={'apt':'apt-get','apk':'apk','dnf':'dnf','pacman':'pacman','pip':'python3'}
    if name not in binaries or not shutil.which(binaries[name]):raise ValueError('Manager not installed')
    return binaries[name]

def perform(args):
    # Install this marker only while provisioning a dedicated guest. App additionally
    # requires its own VM_SSH/SSH_READY gate; no normal host SSH path is accepted.
    if not Path('/etc/meshlit-lab-owned').is_file():raise ValueError('Dedicated lab marker missing')
    binary=manager(args.manager)
    root=os.geteuid()==0
    if args.manager!='pip' and (not root or not args.allow_guest_root):raise ValueError('Distro management needs explicit guest-root approval')
    venv=Path.home()/'.local/share/meshlit-lab/venv'
    if args.manager=='pip':
        if root:raise ValueError('Use a non-root guest user for the Python environment')
        if not venv.exists():execute([binary,'-m','venv',str(venv)])
        binary=str(venv/'bin/python')
    if args.action=='list':
        commands={'apt':['dpkg-query','-W','-f=${Package} ${Version}\n'],'apk':[binary,'info'],'dnf':[binary,'list','installed'],'pacman':[binary,'-Q'],'pip':[binary,'-m','pip','list','--format=json']}
        # List output is package metadata, unlike installation logs; bounded capture.
        with tempfile.TemporaryFile() as f:
            proc=subprocess.Popen(commands[args.manager],stdout=f,stderr=subprocess.DEVNULL,start_new_session=True)
            try: code=proc.wait(timeout=10)
            except BaseException:os.killpg(proc.pid,signal.SIGKILL);proc.wait();raise
            if code:raise ValueError('Inventory failed')
            f.seek(0);data=f.read(65537)
        return {'status':'completed','manager':args.manager,'inventory':data[:65536].decode(errors='replace'),'truncated':len(data)>65536}
    if args.action=='uninstall':
        package=package_name(args.source)
        commands={'apt':[binary,'remove','-y',package],'apk':[binary,'del',package],'dnf':[binary,'remove','-y',package],'pacman':[binary,'-R','--noconfirm',package],'pip':[binary,'-m','pip','uninstall','-y',package]}
        return {'status':'completed','action':'uninstall','manager':args.manager,**execute(commands[args.manager])}
    with tempfile.TemporaryDirectory(prefix='meshlit-package-') as staging:
        if args.kind=='repo':source=package_name(args.source)
        else:
            if not re.fullmatch(r'[a-fA-F0-9]{64}',args.sha256):raise ValueError('SHA256 required')
            if args.kind=='web':
                parsed=__import__('urllib.parse',fromlist=['urlsplit']).urlsplit(args.source)
                if parsed.scheme!='https' or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:raise ValueError('HTTPS artifact URL without credentials/query required')
                class NoRedirect(urllib.request.HTTPRedirectHandler):
                    def redirect_request(self,*a,**k):raise ValueError('Redirect requires a new approved URL')
                name=Path(parsed.path).name
                source=str(Path(staging)/name)
                response=urllib.request.build_opener(NoRedirect()).open(args.source,timeout=15)
                with response,open(source,'wb') as out:
                    count=0
                    while True:
                        chunk=response.read(65536)
                        if not chunk:break
                        count+=len(chunk)
                        if count>128*1024*1024:raise ValueError('Artifact exceeds 128 MiB')
                        out.write(chunk)
            else:source=str(Path(args.source).resolve(strict=True))
            path=Path(source)
            if path.stat().st_size>128*1024*1024 or file_digest(path).lower()!=args.sha256.lower():raise ValueError('Artifact verification failed')
            suffix=path.suffix.lower()
            if suffix not in {'pip':{'.whl'},'apt':{'.deb'},'apk':{'.apk'},'dnf':{'.rpm'},'pacman':{'.zst'}}[args.manager]:raise ValueError('Artifact format does not match manager')
        commands={'apt':[binary,'install','-y',source],'apk':[binary,'add',source],'dnf':[binary,'install','-y',source],'pacman':[binary,'-S' if args.kind=='repo' else '-U','--noconfirm',source],'pip':[binary,'-m','pip','install',source]}
        return {'status':'completed','action':'install','manager':args.manager,**execute(commands[args.manager])}

def main():
    p=argparse.ArgumentParser();p.add_argument('action',choices=['list','install','uninstall']);p.add_argument('--manager',choices=['pip','apt','apk','dnf','pacman'],required=True);p.add_argument('--kind',choices=['repo','file','web'],default='repo');p.add_argument('--source',default='');p.add_argument('--sha256',default='');p.add_argument('--allow-guest-root',action='store_true');args=p.parse_args()
    try: print(json.dumps(perform(args)));return 0
    except Exception:print(json.dumps({'status':'failed','error':'Guest marker, privilege, source, signature policy or package-manager operation failed'}));return 1
if __name__=='__main__':sys.exit(main())
