"""Real SDK/native-harness transport tests against an explicitly synthetic local model."""
import asyncio
import json
import unittest
import uuid
from unittest.mock import patch
import agent_host


class FixtureBackend:
    def __init__(self): self.calls=[]; self.requests=[]
    async def call(self, op, **fields):
        if op != 'complete': raise AssertionError(op)
        body=fields['body']; self.calls.append(body); self.requests.append(fields)
        user=next(m['content'] for m in reversed(body['messages']) if m['role']=='user')
        # Delay one session to verify another can finish independently.
        if 'slow-fixture' in str(user): await asyncio.sleep(2)
        return {'response':{'id':'fixture','object':'chat.completion','model':'fixture',
            'choices':[{'index':0,'message':{'role':'assistant','content':str(user)},'finish_reason':'stop'}],
            'usage':{'prompt_tokens':11,'completion_tokens':5,'total_tokens':16}}}


class NativeSdkTest(unittest.IsolatedAsyncioTestCase):
    async def test_effort_changes_between_turns_without_changing_connection(self):
        events=[]; backend=FixtureBackend(); host=agent_host.Host(backend); host.start_proxy(); session=str(uuid.uuid4())
        route=dict(kind='compatible',endpoint='https://example.test/v1',model='fixture-model',token='fixture-only')
        try:
            with patch.object(agent_host,'emit',events.append):
                for index, effort in enumerate(('high','low','auto'),1):
                    await host.command(dict(op='send',session=session,mode='chat',route=route,effort=effort,prompt='effort fixture'))
                    async def done():
                        while len([e for e in events if e['type']=='done'])<index: await asyncio.sleep(.02)
                    await asyncio.wait_for(done(),20)
                    self.assertEqual(backend.requests[-1]['effort'],effort)
                    self.assertEqual(backend.requests[-1]['route']['endpoint'],route['endpoint'])
                with self.assertRaises(ValueError):
                    await host.command(dict(op='send',session=session,mode='chat',route={**route,'endpoint':'https://other.test/v1'},effort='high',prompt='denied change'))
                self.assertEqual(len(backend.requests),3)
        finally:
            for s in list(host.sessions): await host.command(dict(op='close',session=s))
            await asyncio.to_thread(host.server.shutdown); host.server.server_close()

    async def test_local_and_unknown_effort_are_rejected_without_starting_session(self):
        host=agent_host.Host(FixtureBackend())
        for effort in ('high','ultra'):
            with self.assertRaises(ValueError):
                await host.command(dict(op='send',session=str(uuid.uuid4()),mode='chat',effort=effort,prompt='blocked'))
        self.assertEqual(host.sessions,{})

    async def test_ipc_chunks_do_not_finish_request_and_failure_cancels_it(self):
        written=[]
        class Input:
            def write(self, value): written.append(json.loads(value))
            async def drain(self): pass
        class Process:
            stdin=Input()
            stdout=asyncio.StreamReader()
        process=Process();backend=agent_host.Backend(process);reader=asyncio.create_task(backend.read())
        chunks=[]
        try:
            call=asyncio.create_task(backend.call('complete',on_chunk=chunks.append,body={}))
            await asyncio.sleep(0)
            request=written[-1]['id']
            process.stdout.feed_data((json.dumps(dict(id=request,chunk={'text':'early'}))+'\n').encode())
            await asyncio.sleep(0)
            self.assertEqual(chunks,[{'text':'early'}]);self.assertFalse(call.done())
            process.stdout.feed_data((json.dumps(dict(id=request,error='failed'))+'\n').encode())
            with self.assertRaises(RuntimeError):await call
            self.assertEqual(written[-1]['op'],'cancel')
            self.assertEqual(backend.pending,{});self.assertEqual(backend.chunk_handlers,{})
        finally:
            process.stdout.feed_eof();await reader

    async def test_stop_after_streamed_text_cancels_the_backend(self):
        events=[];released=asyncio.Event()
        class StreamingBackend:
            async def call(self, op, on_chunk=None, **fields):
                try:
                    on_chunk(dict(id='fixture',object='chat.completion.chunk',model='fixture',
                        choices=[dict(index=0,delta=dict(role='assistant',content='Partial answer'),finish_reason=None)]))
                    await asyncio.Event().wait()
                finally:released.set()
        host=agent_host.Host(StreamingBackend());host.start_proxy();session=str(uuid.uuid4())
        try:
            with patch.object(agent_host,'emit',events.append):
                await host.command(dict(op='send',session=session,mode='chat',prompt='Cancel fixture'))
                async def first_text():
                    while not any(e['type']=='delta' and e.get('text') for e in events):await asyncio.sleep(.02)
                await asyncio.wait_for(first_text(),20)
                await host.command(dict(op='stop',session=session))
                await asyncio.wait_for(released.wait(),3)
                self.assertFalse(any(e['type']=='done' for e in events))
        finally:
            for s in list(host.sessions):await host.command(dict(op='close',session=s))
            await asyncio.to_thread(host.server.shutdown);host.server.server_close()

    async def test_native_sdk_receives_text_before_backend_completion(self):
        events=[]; release=asyncio.Event()
        class StreamingBackend:
            async def call(self, op, on_chunk=None, **fields):
                self_outer.assertTrue(fields['body']['stream'])
                on_chunk(dict(id='fixture',object='chat.completion.chunk',model='fixture',
                    choices=[dict(index=0,delta=dict(role='assistant',content='Early text'),finish_reason=None)]))
                await release.wait()
                terminal=dict(id='fixture',object='chat.completion.chunk',model='fixture',
                    choices=[dict(index=0,delta={},finish_reason='stop')])
                return dict(response=dict(streamed=True,terminalChunks=[terminal]))
        self_outer=self
        host=agent_host.Host(StreamingBackend());host.start_proxy();session=str(uuid.uuid4())
        try:
            with patch.object(agent_host,'emit',events.append):
                await host.command(dict(op='send',session=session,mode='chat',prompt='Streaming fixture'))
                async def first_text():
                    while not any(e['type']=='delta' and e.get('text') for e in events):await asyncio.sleep(.02)
                await asyncio.wait_for(first_text(),20)
                self.assertFalse(any(e['type']=='done' for e in events))
                release.set()
                async def done():
                    while not any(e['type'] in ('done','error') for e in events):await asyncio.sleep(.02)
                await asyncio.wait_for(done(),20)
                self.assertFalse(any(e['type']=='error' for e in events),events)
        finally:
            release.set()
            for s in list(host.sessions):await host.command(dict(op='close',session=s))
            await asyncio.to_thread(host.server.shutdown);host.server.server_close()

    async def test_backend_write_failure_cleans_pending_request(self):
        class BrokenInput:
            def write(self, _): raise BrokenPipeError()
        class Process:
            stdin = BrokenInput()
        backend = agent_host.Backend(Process())
        with self.assertRaises(BrokenPipeError):
            await backend.call('usage')
        self.assertEqual(backend.pending, {})

    async def test_local_load_does_not_unlock_or_generate(self):
        events=[]
        class LocalBackend:
            async def call(self, op, **fields):
                self_outer.assertEqual(op, 'load_local')
                self_outer.assertFalse(fields)
                return dict(label='bundled fixture', context=4096)
        self_outer=self
        host=agent_host.Host(LocalBackend())
        with patch.object(agent_host,'emit',events.append):
            await host.command(dict(op='load_local'))
        self.assertEqual(events,[dict(type='local_model',state='ready',label='bundled fixture',context=4096)])
        self.assertEqual(host.sessions,{})

    async def test_local_load_failure_does_not_fall_back_online(self):
        events=[]
        class MissingBackend:
            async def call(self, op, **fields): raise RuntimeError('private path')
        host=agent_host.Host(MissingBackend())
        with patch.object(agent_host,'emit',events.append):
            await host.command(dict(op='load_local'))
        self.assertEqual(events[0]['state'],'error')
        self.assertNotIn('private path',events[0]['message'])
        self.assertEqual(host.sessions,{})

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
