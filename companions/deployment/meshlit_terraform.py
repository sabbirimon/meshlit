#!/usr/bin/env python3
"""Human-controlled Terraform saved-plan companion, suitable for a pinned SSH host.
No credentials are accepted/exported here; use the host's own provider credential setup.
Terraform/providers execute real code. Select an owned, reviewed workspace explicitly.
"""
import argparse, hashlib, json, os, sys, uuid
from pathlib import Path
from importlib.util import spec_from_file_location, module_from_spec

_spec=spec_from_file_location('process_bounds',Path(__file__).parents[1]/'cyber/meshlit_cyber.py')
_bounds=module_from_spec(_spec);_spec.loader.exec_module(_bounds)
run=_bounds.bounded_process
MAX_PLAN=64*1024*1024

def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()

def fingerprint(workspace):
    h=hashlib.sha256();count=total=0
    for path in sorted(workspace.rglob('*')):
        relative=path.relative_to(workspace)
        if '.terraform' in relative.parts or '.git' in relative.parts:continue
        if path.is_symlink():raise ValueError('Workspace symlinks are unsupported')
        if not path.is_file():continue
        count+=1;total+=path.stat().st_size
        if count>2000 or total>128*1024*1024:raise ValueError('Workspace fingerprint budget exceeded')
        h.update(str(relative).encode());h.update(b'\x00');h.update(digest(path).encode())
    return h.hexdigest()

def validate(workspace,binary,expected,store):
    workspace=Path(workspace);binary=Path(binary);store=Path(store)
    if not workspace.is_absolute() or not workspace.is_dir() or workspace.is_symlink():raise ValueError('Owned absolute workspace required')
    if not binary.is_absolute() or not binary.is_file() or binary.is_symlink() or digest(binary)!=expected:raise ValueError('Verified Terraform executable required')
    if not store.is_absolute() or store.is_symlink() or store.resolve().is_relative_to(workspace.resolve()):raise ValueError('Plan storage must be separate from workspace')
    store.mkdir(mode=0o700,parents=True,exist_ok=True)
    if os.name!='nt' and store.stat().st_mode&0o077:raise ValueError('Plan directory must be private (0700)')
    return workspace,binary,store

def plan(workspace,binary,expected,store,initialize=False):
    workspace,binary,store=validate(workspace,binary,expected,store)
    before=fingerprint(workspace);identity=uuid.uuid4().hex;target=store/(identity+'.tfplan');manifest=store/(identity+'.json')
    argv=[str(binary),'-chdir='+str(workspace)]
    try:
        if initialize:
            run(argv+['init','-input=false','-no-color'],limit=32768,timeout=120)
            # init may legitimately update the lock file; bind the reviewed plan to the resulting workspace.
            before=fingerprint(workspace)
        run(argv+['plan','-input=false','-no-color','-out='+str(target)],limit=32768,timeout=180)
        if not target.is_file() or target.is_symlink() or not 0<target.stat().st_size<=MAX_PLAN:raise ValueError('Plan missing or exceeds budget')
        os.chmod(target,0o600)
        details=json.loads(run(argv+['show','-json',str(target)],limit=4*1024*1024,timeout=40))
        changes=details.get('resource_changes',[])
        if len(changes)>10000:raise ValueError('Plan resource budget exceeded')
        counts={}
        for change in changes:
            actions=change['change']['actions'];key=','.join(actions);counts[key]=counts.get(key,0)+1
        if fingerprint(workspace)!=before or digest(binary)!=expected:raise ValueError('Workspace or executable changed during planning')
        record={'schema':1,'id':identity,'workspace_sha256':before,'terraform_sha256':expected,'plan_sha256':digest(target),'plan_bytes':target.stat().st_size,'actions':counts,'applied':False}
        with manifest.open('x') as stream:
            os.chmod(manifest,0o600);json.dump(record,stream);stream.flush();os.fsync(stream.fileno())
        return record
    except BaseException:
        target.unlink(missing_ok=True);manifest.unlink(missing_ok=True);raise

def _apply(workspace,binary,expected,store,identity,approval):
    workspace,binary,store=validate(workspace,binary,expected,store)
    if len(identity)!=32 or any(c not in '0123456789abcdef' for c in identity):raise ValueError('Saved plan ID required')
    manifest=store/(identity+'.json');target=store/(identity+'.tfplan')
    if manifest.is_symlink() or target.is_symlink() or manifest.stat().st_size>8192:raise ValueError('Unsafe saved plan')
    record=json.loads(manifest.read_text())
    if record['applied'] or record['plan_sha256']!=approval or digest(target)!=approval or record['terraform_sha256']!=expected or record['workspace_sha256']!=fingerprint(workspace):raise ValueError('Plan approval/identity/state mismatch')
    # Mark uncertain BEFORE real provider mutation. Never automatically retry an interrupted apply.
    record['applied']='outcome_uncertain'
    with manifest.open('w') as stream:json.dump(record,stream);stream.flush();os.fsync(stream.fileno())
    run([str(binary),'-chdir='+str(workspace),'apply','-input=false','-no-color',str(target)],limit=32768,timeout=180)
    record['applied']=True
    with manifest.open('w') as stream:json.dump(record,stream);stream.flush();os.fsync(stream.fileno())
    return {'schema':1,'id':identity,'status':'apply_completed','plan_sha256':approval}

def apply(workspace,binary,expected,store,identity,approval):
    workspace,binary,store=validate(workspace,binary,expected,store)
    if len(identity)!=32 or any(c not in '0123456789abcdef' for c in identity):raise ValueError('Saved plan ID required')
    lock=store/(identity+'.apply-lock')
    descriptor=os.open(lock,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
    os.close(descriptor)
    try:return _apply(workspace,binary,expected,store,identity,approval)
    finally:lock.unlink(missing_ok=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['plan','apply'])
    parser.add_argument('--workspace',required=True);parser.add_argument('--terraform',required=True);parser.add_argument('--terraform-sha256',required=True);parser.add_argument('--plan-store',required=True)
    parser.add_argument('--initialize',action='store_true');parser.add_argument('--plan-id');parser.add_argument('--approve-sha256')
    args=parser.parse_args()
    try:
        value=plan(args.workspace,args.terraform,args.terraform_sha256,args.plan_store,args.initialize) if args.action=='plan' else apply(args.workspace,args.terraform,args.terraform_sha256,args.plan_store,args.plan_id or '',args.approve_sha256 or '')
        print(json.dumps(value));return 0
    except Exception:print(json.dumps({'status':'failed_or_uncertain','error':'Inspect the owned workspace and saved plan state before explicit retry; provider output withheld'}));return 1
if __name__=='__main__':sys.exit(main())
