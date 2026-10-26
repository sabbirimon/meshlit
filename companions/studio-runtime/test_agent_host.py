"""Real SDK/native-harness transport tests against an explicitly synthetic local model."""
import asyncio
import unittest
import uuid
from unittest.mock import patch
import agent_host


class FixtureBackend:
    def __init__(self): self.calls=[]
    async def call(self, op, **fields):
        if op != 'complete': raise AssertionError(op)
        body=fields['body']; self.calls.append(body)
        user=next(m['content'] for m in reversed(body['messages']) if m['role']=='user')
        # Delay one session to verify another can finish independently.
        if 'slow-fixture' in str(user): await asyncio.sleep(2)
        return {'response':{'id':'fixture','object':'chat.completion','model':'fixture',
            'choices':[{'index':0,'message':{'role':'assistant','content':str(user)},'finish_reason':'stop'}],
            'usage':{'prompt_tokens':11,'completion_tokens':5,'total_tokens':16}}}


class NativeSdkTest(unittest.IsolatedAsyncioTestCase):
    async def test_independent_chats_and_read_only_plan_tools(self):
        events=[]; backend=FixtureBackend(); host=agent_host.Host(backend); host.start_proxy()
        slow,fast=str(uuid.uuid4()),str(uuid.uuid4())
        try:
            with patch.object(agent_host,'emit',events.append):
                await host.command(dict(op='send',session=slow,mode='plan',prompt='slow-fixture'))
                await host.command(dict(op='send',session=fast,mode='chat',prompt='fast-fixture'))
                async def complete():
                    while len([e for e in events if e['type'] in ('done','error')])<2: await asyncio.sleep(.05)
                await asyncio.wait_for(complete(),45)
                self.assertFalse([e for e in events if e['type']=='error'],events)
                answers={s:''.join(e['text'] for e in events if e['type']=='delta' and e['session']==s) for s in (slow,fast)}
                self.assertIn('fast-fixture',answers[fast]); self.assertNotIn('slow-fixture',answers[fast])
                self.assertIn('slow-fixture',answers[slow]); self.assertNotIn('fast-fixture',answers[slow])
                self.assertEqual(len(backend.calls),2)
                self.assertEqual([e['session'] for e in events if e['type']=='done'][0],fast)
                for body in backend.calls:
                    self.assertLess(len(str(body)),15000,'SDK default prompt unexpectedly restored')
                    names=[t['function']['name'] for t in body.get('tools',[])]
                    self.assertNotIn('propose_edit',names)
                    self.assertNotIn('run_command',names)
                for s in (slow,fast): await host.command(dict(op='close',session=s))
        finally:
            for s in list(host.sessions): await host.command(dict(op='close',session=s))
            await asyncio.to_thread(host.server.shutdown); host.server.server_close()

    async def test_stop_cancels_only_its_session(self):
        events=[]; backend=FixtureBackend(); host=agent_host.Host(backend); host.start_proxy()
        slow,fast=str(uuid.uuid4()),str(uuid.uuid4())
        try:
            with patch.object(agent_host,'emit',events.append):
                await host.command(dict(op='send',session=slow,mode='chat',prompt='slow-fixture'))
                async def started():
                    while not backend.calls: await asyncio.sleep(.02)
                await asyncio.wait_for(started(),20)
                await host.command(dict(op='stop',session=slow))
                await host.command(dict(op='send',session=fast,mode='chat',prompt='fast-fixture'))
                async def done():
                    while not any(e['type']=='done' and e['session']==fast for e in events): await asyncio.sleep(.02)
                await asyncio.wait_for(done(),20)
                self.assertFalse(any(e['type']=='done' and e['session']==slow for e in events))
                self.assertTrue(host.sessions[slow]['closed'])
        finally:
            for s in list(host.sessions): await host.command(dict(op='close',session=s))
            await asyncio.to_thread(host.server.shutdown); host.server.server_close()


if __name__=='__main__': unittest.main()
