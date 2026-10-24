#!/usr/bin/env python3
"""Download pinned official Linux gateway to a requested staging directory; never execute it."""
import argparse, hashlib, json, subprocess, platform, os
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--architecture',choices=['amd64','arm64'],required=True);p.add_argument('--output',required=True);args=p.parse_args()
lock=json.loads((Path(__file__).parent/'upstream.lock.json').read_text());asset=lock['binaries']['agentgateway-linux-'+args.architecture]
destination=Path(args.output).resolve();destination.parent.mkdir(parents=True,exist_ok=True);temporary=destination.with_name(destination.name+'.part')
try:
 subprocess.run(['curl','--fail','--location','--proto','=https','--proto-redir','=https','--max-time','120','--output',str(temporary),asset['url']],check=True)
 actual=hashlib.sha256(temporary.read_bytes()).hexdigest()
 if actual!=asset['sha256']:raise ValueError('Upstream artifact hash mismatch')
 temporary.chmod(0o700);os.replace(temporary,destination)
 print(json.dumps({'release':lock['release'],'sha256':actual,'path':str(destination),'executed':False}))
finally:
 temporary.unlink(missing_ok=True)
