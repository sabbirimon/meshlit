#!/usr/bin/env python3
"""Opt-in JSch/OpenSSH test on this host, using disposable loopback keys only.
Run from a normal terminal if macOS denies nested OpenSSH sandbox initialization.
No system SSH configuration, credentials or login service are changed.
"""
import argparse
import getpass
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import tempfile
import time

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence',type=Path,default=ROOT/'build/reports/live-ssh/proof.json')
    args=parser.parse_args()
    sshd=shutil.which('sshd') or ('/usr/sbin/sshd' if Path('/usr/sbin/sshd').is_file() else None)
    if not sshd or not shutil.which('ssh-keygen') or not shutil.which('ssh'):
        raise SystemExit('An installed OpenSSH server/client/key generator is required; no automatic installation')
    with tempfile.TemporaryDirectory(prefix='meshlit-live-ssh-') as directory:
        root=Path(directory);root.chmod(0o700)
        for key in ['host','client']:
            subprocess.run(['ssh-keygen','-q','-t','ed25519','-N','','-f',str(root/key)],check=True)
        authorized=root/'authorized_keys';authorized.write_text((root/'client.pub').read_text());authorized.chmod(0o600)
        with socket.socket() as connection:
            connection.bind(('127.0.0.1',0));port=connection.getsockname()[1]
        (root/'port').write_text(str(port))
        public=(root/'host.pub').read_text().split()[:2]
        known=root/'known_hosts';known.write_text(f'[127.0.0.1]:{port} '+ ' '.join(public)+'\n')
        config=root/'sshd_config'
        config.write_text(f'''Port {port}
ListenAddress 127.0.0.1
HostKey {root/'host'}
PidFile {root/'sshd.pid'}
AuthorizedKeysFile {authorized}
UsePAM no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
StrictModes yes
AllowUsers {getpass.getuser()}
PermitRootLogin no
AllowTcpForwarding no
X11Forwarding no
PermitTunnel no
LogLevel ERROR
''')
        subprocess.run([sshd,'-t','-f',str(config)],check=True)
        server_log=root/'server.log'
        with server_log.open('w') as output:
            process=subprocess.Popen([sshd,'-D','-e','-f',str(config)],stdout=output,stderr=subprocess.STDOUT,start_new_session=True)
            try:
                time.sleep(0.5)
                if process.poll() is not None:raise RuntimeError('Local OpenSSH test server could not start')
                probe=subprocess.run(['ssh','-F','/dev/null','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes',
                    '-o',f'UserKnownHostsFile={known}','-i',str(root/'client'),'-p',str(port),f'{getpass.getuser()}@127.0.0.1',
                    'printf meshlit-ssh-preflight'],capture_output=True,text=True,timeout=15)
                if probe.returncode or probe.stdout!='meshlit-ssh-preflight':
                    diagnostic=server_log.read_text()
                    if 'sandbox initialization failed' in diagnostic or 'sandbox_init' in diagnostic:
                        raise RuntimeError('OpenSSH sandbox initialization was denied by this environment; run the script in a normal terminal')
                    raise RuntimeError('Pinned local OpenSSH preflight failed; no successful integration evidence')
                environment=os.environ.copy();environment['MESHLIT_LIVE_SSH_DIR']=directory
                subprocess.run([str(ROOT/'gradlew'),':core-ssh:testDebugUnitTest','--tests','com.meshlit.core.ssh.LiveSshHostTest',
                    '--rerun-tasks','--offline','--console=plain','--max-workers=2','-Pkotlin.compiler.execution.strategy=in-process'],
                    cwd=ROOT,env=environment,check=True)
                args.evidence.parent.mkdir(parents=True,exist_ok=True)
                args.evidence.write_text(json.dumps({'passed':True,'transport':'JSch 2.28.7 → real OpenSSH',
                    'host':os.uname().sysname,'loopback':True,'disposableKeys':True,
                    'checks':['encrypted exec','host pin rejection','exit status','stdout/stderr','output bound','cancellation'],
                    'androidDeviceProof':False},indent=2)+'\n')
                print('Live SSH evidence:',args.evidence)
            finally:
                if process.poll() is None:
                    os.killpg(process.pid,signal.SIGTERM)
                    try:process.wait(timeout=5)
                    except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()

if __name__=='__main__':main()
