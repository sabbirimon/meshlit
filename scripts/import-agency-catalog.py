"""Import pinned role documents as data, without running upstream installers or granting tools."""
import argparse,hashlib,json,re,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('source',type=Path);args=p.parse_args()
source=args.source.resolve();root=Path(__file__).resolve().parents[1]
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=source,text=True).strip()
license=(source/'LICENSE').read_text();assert license.startswith('MIT License')
divisions=json.loads((source/'divisions.json').read_text())['divisions'];roles=[]
def field(header,name):
 m=re.search(r'^'+re.escape(name)+r':\s*(.+)$',header,re.M)
 if not m:return ''
 v=m.group(1).strip()
 if v.startswith('"'):
  try:return json.loads(v)
  except ValueError:pass
 return v.strip("'\"")
for division in divisions:
 for path in sorted((source/division).glob('*.md')):
  if source not in path.resolve().parents:raise ValueError('External role file')
  raw=path.read_text();parts=raw.split('---',2)
  if len(parts)!=3 or parts[0].strip():continue
  name=field(parts[1],'name')
  if not name:continue
  body=parts[2].strip();assert len(body)<=100000
  mission=next((m.start() for m in re.finditer(r'^##.*Core Mission',body,re.M)),0)
  excerpt=body[mission:mission+1000]
  roles.append(dict(id=division+'/'+path.stem,name=name[:100],division=division,description=field(parts[1],'description')[:1000],
                    excerpt=excerpt,sourcePath=str(path.relative_to(source)),sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
assert roles and len({x['id'] for x in roles})==len(roles)
destination=root/'shared-workspace/src/jvmMain/resources/agency'
destination.mkdir(parents=True,exist_ok=True)
(destination/'catalog.json').write_text(json.dumps(dict(schemaVersion=2,source='https://github.com/msitarzewski/agency-agents',commit=commit,roles=roles),ensure_ascii=False,indent=2)+'\n')
(destination/'LICENSE').write_text(license)
(destination/'NOTICE.txt').write_text('Agency Agents role templates\nhttps://github.com/msitarzewski/agency-agents\nPinned source: '+commit+'\nMIT License; copyright retained in LICENSE.\nMeshlit adaptation: searchable data catalog and bounded instruction excerpts; no installer scripts, autonomous grants, or third-party runtime are imported.\n')
print(f'Imported {len(roles)} role templates from {commit}; no tools granted')
