#!/usr/bin/env python3
"""Inspect Core packaging; manifest/data separation does not imply device qualification."""
import argparse, hashlib, json, zipfile, xml.etree.ElementTree as E
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--apk',type=Path,required=True);p.add_argument('--manifest',type=Path);p.add_argument('--output',type=Path,required=True);args=p.parse_args()
root=Path(__file__).resolve().parents[1]
manifest=args.manifest
if manifest is None:
 candidates=list((root/'app/build/intermediates').glob('merged_manifests/meshlitV2ProductionCandidate/*/universal/AndroidManifest.xml'))
 if not candidates:candidates=list((root/'app/build/intermediates').glob('merged_manifest/meshlitV2ProductionCandidate/**/AndroidManifest.xml'))
 assert len(candidates)==1, f'Expected one merged Core candidate manifest, found {len(candidates)}'
 manifest=candidates[0]
a='{http://schemas.android.com/apk/res/android}';m=E.parse(manifest).getroot()
services={x.get(a+'name') for x in m.findall('application/service')}
allowed_services={'com.meshlit.models.ModelDownloadService','com.meshlit.chat.ChatInferenceService','androidx.room.MultiInstanceInvalidationService'}
permissions={x.get(a+'name') for x in m.findall('uses-permission')}
forbidden={'android.permission.SEND_SMS','android.permission.REQUEST_INSTALL_PACKAGES','android.permission.REQUEST_DELETE_PACKAGES','android.permission.MANAGE_EXTERNAL_STORAGE','com.termux.permission.RUN_COMMAND'}
r={'package':m.get('package'),'label':m.find('application').get(a+'label'),'debuggable':m.find('application').get(a+'debuggable','false')=='true','missingServices':sorted(allowed_services-services),'unexpectedServices':sorted(services-allowed_services),'forbiddenPermissions':sorted(permissions&forbidden),'productionQualified':False,'physicalDeviceTested':False,'signing':'developer candidate; production signing not asserted','sharedInactiveCodePresent':True}
model=json.loads((root/'app/src/main/assets/models/bundled-model.json').read_text())
with zipfile.ZipFile(args.apk) as z:
 name='assets/models/'+model['filename'];digest=hashlib.sha256()
 with z.open(name) as f:
  while chunk:=f.read(1024*1024):digest.update(chunk)
 r['bundledModelVerified']=z.getinfo(name).file_size==model['sizeBytes'] and digest.hexdigest()==model['sha256']
 r['hyperlNotices']=all(z.read('assets/hyperl/'+name)==(root/'core-hyperl/src/main/assets/hyperl'/name).read_bytes() for name in ('LICENSE','NOTICE','LICENSE_HISTORY.md','Apache-2.0.txt'))
 r['sshNotices']=all(z.read('assets/node-ssh/'+name)==(root/'app/src/main/assets/node-ssh'/name).read_bytes() for name in ('LICENSE.txt','NOTICE.txt'))
 r['policies']=all(z.read('assets/legal/'+key+'.txt')==(root/'docs'/name).read_bytes() for key,name in [('privacy','PRIVACY_POLICY.md'),('terms','TERMS_OF_USE.md')])
r['staticChecksPass']=r['package']=='com.meshlit.v2.production' and r['label']=='Meshlit Core Candidate' and not(r['debuggable'] or r['missingServices'] or r['unexpectedServices'] or r['forbiddenPermissions']) and all(r[x] for x in ('bundledModelVerified','hyperlNotices','sshNotices','policies'))
args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(r,indent=2)+'\n');print(json.dumps(r,indent=2));raise SystemExit(0 if r['staticChecksPass'] else 1)
