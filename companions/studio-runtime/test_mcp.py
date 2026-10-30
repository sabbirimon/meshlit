import asyncio
from pathlib import Path
import sys
import tempfile
import unittest
import uuid
from unittest.mock import patch
import agent_host

class ToolBackend:
    def __init__(self):self.count=0;self.bodies=[]
    async def call(self,op,**fields):
        body=fields['body'];self.count+=1;self.bodies.append(body)
        message={'role':'assistant','content':'MCP fixture turn complete'};reason='stop'
        if self.count==1:
            names=[t['function']['name'] for t in body.get('tools',[])]
            name=next(n for n in names if 'echo' in n)
            message={'role':'assistant','content':None,'tool_calls':[{'id':'fixture_call','type':'function','function':{'name':name,'arguments':'{"text":"fixture"}'}}]};reason='tool_calls'
        return {'response':{'id':'fixture','object':'chat.completion','model':'fixture','choices':[{'index':0,'message':message,'finish_reason':reason}],'usage':{'prompt_tokens':12,'completion_tokens':8}}}

class McpRuntimeTest(unittest.IsolatedAsyncioTestCase):
    async def scenario(self,approve,paired=False):
        with tempfile.TemporaryDirectory() as directory:
            marker=Path(directory)/'executed';backend=ToolBackend();host=agent_host.Host(backend);host.start_proxy();events=[]
            session=str(uuid.uuid4())
            def event(e):
                events.append(e)
                if e['type']=='tool':
                    asyncio.create_task(host.command(dict(op='tool_result',session=session,id=e['id'],result='approved' if approve else 'denied')))
            config={'name':'fixture','type':'stdio','command':sys.executable,'args':[str(Path(__file__).parent/'fixtures/mcp_server.py'),str(marker)]}
            token='omb_sess_fixture_000000000000000000000000'
            if paired:
                wrapper=Path(directory)/'openmausbot-fixture'
                wrapper.write_text(f'#!{sys.executable}\nimport os,sys,runpy\nassert sys.argv[1:]==["mcp"]\nassert os.environ["OPENMAUSBOT_TOKEN"]=={token!r}\nassert os.environ["OMB_PORT"]=="8799"\nassert os.environ["OPENMAUSBOT_URL"]=="http://127.0.0.1:8799"\nsys.argv=["fixture",{str(marker)!r}]\nrunpy.run_path({str(Path(__file__).parent/"fixtures/mcp_server.py")!r},run_name="__main__")\n')
                wrapper.chmod(0o700)
                config={'name':'paired-fixture','type':'stdio','command':str(wrapper),'args':['mcp'],
                        'auth':{'kind':'openmausbot-session','port':8799},
                        'env':{'OMB_PORT':'8799','OPENMAUSBOT_URL':'http://127.0.0.1:8799','OPENMAUSBOT_TOKEN':token}}
            try:
                with patch.object(agent_host,'emit',event):
                    await host.command(dict(op='send',session=session,mode='autopilot',prompt='Run fixture echo.',connectors=[config]))
                    async def complete():
                        while not any(e['type'] in ('done','error') for e in events):await asyncio.sleep(.03)
                    await asyncio.wait_for(complete(),35)
                    self.assertFalse([e for e in events if e['type']=='error'],events)
                    self.assertTrue(any(e['type']=='tool' and e['name']=='mcp_request' for e in events),events)
                    self.assertEqual(marker.exists(),approve)
                    self.assertNotIn(token,str(backend.bodies));self.assertNotIn(token,str(events))
            finally:
                await host.command(dict(op='close',session=session))
                await asyncio.to_thread(host.server.shutdown);host.server.server_close()
    async def test_approved_mcp_call_executes(self):await self.scenario(True)
    async def test_denied_mcp_call_does_not_execute(self):await self.scenario(False)
    @unittest.skipIf(sys.platform=='win32','POSIX executable fixture')
    async def test_paired_environment_reaches_only_selected_mcp_process(self):await self.scenario(True,paired=True)

if __name__=='__main__':unittest.main()
