#!/usr/bin/env python3
"""Inspect review artifacts. Static checks are not Play approval or runtime proof."""
import argparse,json,struct,zipfile,xml.etree.ElementTree as E
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--apk',type=Path,required=True);p.add_argument('--manifest',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];android='{http://schemas.android.com/apk/res/android}'
m=E.parse(a.manifest).getroot();permissions={x.get(android+'name') for x in m.findall('uses-permission')}
forbidden={'android.permission.SEND_SMS','android.permission.MANAGE_EXTERNAL_STORAGE','android.permission.READ_EXTERNAL_STORAGE','android.permission.WRITE_EXTERNAL_STORAGE','android.permission.READ_MEDIA_IMAGES','android.permission.READ_MEDIA_VIDEO','android.permission.READ_MEDIA_AUDIO','android.permission.READ_MEDIA_VISUAL_USER_SELECTED','android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS','android.permission.REQUEST_INSTALL_PACKAGES','android.permission.REQUEST_DELETE_PACKAGES','com.termux.permission.RUN_COMMAND'}
services={x.get(android+'name') for x in m.findall('application/service')};disallowed={'com.meshlit.core.cloudmcp.android.MeshlitAccessibilityService','com.meshlit.core.net.capture.MeshlitCaptureVpnService'}
r={'apk':a.apk.name,'package':m.get('package'),'targetSdk':m.find('uses-sdk').get(android+'targetSdkVersion'),'forbiddenPermissionsPresent':sorted(permissions&forbidden),'forbiddenServicesPresent':sorted(services&disallowed),'native':[],'policies':{},'playApproved':False,'runtime16KiBTested':False,'submissionReady':False}
with zipfile.ZipFile(a.apk) as z:
 for key,name in [('privacy','PRIVACY_POLICY.md'),('terms','TERMS_OF_USE.md')]:
  r['policies'][key]=z.read(f'assets/legal/{key}.txt')==(root/'docs'/name).read_bytes()
 for n in z.namelist():
  if not n.endswith('.so'):continue
  b=z.read(n);assert b[:4]==b'\x7fELF',n;end='<' if b[5]==1 else '>';is64=b[4]==2
  phoff=struct.unpack_from(end+('Q' if is64 else 'I'),b,32 if is64 else 28)[0];ents,num=struct.unpack_from(end+'HH',b,54 if is64 else 42)
  alignment=[struct.unpack_from(end+('Q' if is64 else 'I'),b,phoff+i*ents+(48 if is64 else 28))[0] for i in range(num) if struct.unpack_from(end+'I',b,phoff+i*ents)[0]==1]
  r['native'].append({'library':n,'loadAlignments':alignment,'elf16KiBAligned':bool(alignment) and all(x>=16384 for x in alignment)})
r['staticChecksPass']=not(r['forbiddenPermissionsPresent'] or r['forbiddenServicesPresent']) and all(r['policies'].values()) and bool(r['native']) and all(x['elf16KiBAligned'] for x in r['native']) and int(r['targetSdk'])>=36
r['remainingGates']=['Developer-managed AI content reporting and restricted-content prevention','Private developer/privacy contact and Data safety disclosures','Vendor SDK telemetry review/opt-out','Operator production signing and permanent package','Actual 16 KiB device/AAB testing and Play Console review']
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(r,indent=2)+'\n')
print(json.dumps({k:v for k,v in r.items() if k!='native'},indent=2));raise SystemExit(0 if r['staticChecksPass'] else 1)
