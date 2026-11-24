"""Measure real packaged SDK first-text latency with a disposable local usage vault."""
import argparse,asyncio,contextlib,hashlib,json,secrets,sys,tempfile,time,uuid
from pathlib import Path
from unittest.mock import patch
from runtime_config import load_runtime
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--runtime',required=True,type=Path)
parser.add_argument('--output',required=True,type=Path)
parser.add_argument('--trials',type=int,choices=range(1,6),default=2)
args=parser.parse_args()
config=load_runtime(args.runtime)
sys.path.insert(0,str(Path(config['agentHost']).parent))
import agent_host
async def main():
 command=[arg for arg in config['backend'] if not arg.startswith('-Duser.home=')]
 with tempfile.TemporaryDirectory(prefix='meshlit-latency-benchmark-') as directory:
  command.insert(1,'-Duser.home='+directory)
  process=await asyncio.create_subprocess_exec(*command,stdin=asyncio.subprocess.PIPE,stdout=asyncio.subprocess.PIPE,stderr=asyncio.subprocess.DEVNULL,limit=3000000)
  backend=agent_host.Backend(process);reader=asyncio.create_task(backend.read());host=agent_host.Host(backend);host.start_proxy()
  rows=[]
  try:
   began=time.perf_counter();await backend.call('load_local');load=(time.perf_counter()-began)*1000
   await backend.call('unlock',password=secrets.token_urlsafe(32))
   for trial in range(args.trials):
    events=[];session=str(uuid.uuid4());began=time.perf_counter()
    def emit(event):events.append((time.perf_counter(),event))
    with patch.object(agent_host,'emit',emit):
     await host.command(dict(op='send',session=session,mode='chat',prompt='In about 60 words, explain how to make reliable backups of important files.'))
     async def done():
      while not any(e['type'] in ('done','error') for _,e in events):await asyncio.sleep(.01)
     await asyncio.wait_for(done(),180)
     assert not [e for _,e in events if e['type']=='error'],events
     deltas=[(t,e) for t,e in events if e['type']=='delta' and e.get('text')]
     assert deltas
     rows.append(dict(trial=trial+1,firstVisibleTextMs=(deltas[0][0]-began)*1000,completionMs=(events[-1][0]-began)*1000,deltaEvents=len(deltas),characters=sum(len(e['text']) for _,e in deltas)))
     await host.command(dict(op='close',session=session))
   result=dict(loadLocalMs=load,trials=rows,usage=await backend.call('usage'),providerRequests=0,ownerVaultAccessed=False)
   result['hostSha256']=hashlib.sha256(Path(agent_host.__file__).read_bytes()).hexdigest()
   result['scope']='Same synthetic prompt, new SDK session each trial, one shared local engine; default sampling, variable output length. First-text latency is not decode throughput.'
   args.output.parent.mkdir(parents=True,exist_ok=True)
   args.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
  finally:
   for session in list(host.sessions):
    with contextlib.suppress(Exception):await host.command(dict(op='close',session=session))
   await asyncio.to_thread(host.server.shutdown);host.server.server_close();process.stdin.close()
   try:await asyncio.wait_for(process.wait(),15)
   except TimeoutError:process.kill();await process.wait()
   reader.cancel()
asyncio.run(main())
