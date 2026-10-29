"""Opt-in real bundled-model/SDK check with a disposable encrypted usage ledger.

Uses an existing development runtime.json; never opens the owner's usage vault,
provider credentials, or workspace. No downloaded artifacts or online fallback.
"""
import argparse
import asyncio
import contextlib
import json
from pathlib import Path
import secrets
import tempfile
import uuid
from unittest.mock import patch

import agent_host


async def qualify(runtime):
    config = json.loads(Path(runtime).read_text())
    with tempfile.TemporaryDirectory(prefix='meshlit-local-qualification-') as directory:
        # Java's user.home controls DesktopUsage only; resource paths stay explicit.
        command = [arg for arg in config['backend'] if not arg.startswith('-Duser.home=')]
        command.insert(1, '-Duser.home=' + directory)
        password = secrets.token_urlsafe(32)

        async def start():
            process = await asyncio.create_subprocess_exec(*command, stdin=asyncio.subprocess.PIPE,
                stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL, limit=3_000_000)
            backend = agent_host.Backend(process)
            return process, backend, asyncio.create_task(backend.read())

        async def finish(process, reader):
            process.stdin.close()
            try:
                await asyncio.wait_for(process.wait(), 15)
            except TimeoutError:
                process.kill()
                await process.wait()
            reader.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await reader

        process, backend, reader = await start()
        host = agent_host.Host(backend)
        host.start_proxy()
        events = []
        result = dict(providerRequests=0, ownerVaultAccessed=False)
        try:
            loaded = await backend.call('load_local')
            assert loaded['ready']
            result['model'] = loaded['label']
            assert not (Path(directory) / '.meshlit/usage/history.mul').exists()
            try:
                await backend.call('complete', body={'messages': [{'role': 'user', 'content': 'Hello'}]})
                raise AssertionError('Locked ledger admitted generation')
            except RuntimeError:
                result['lockedRequestRejected'] = True
            await backend.call('unlock', password=password)
            sessions = [str(uuid.uuid4()), str(uuid.uuid4())]
            prompts = ['Say hello in one short sentence.', 'What is 2 plus 2? Answer briefly.']
            with patch.object(agent_host, 'emit', events.append):
                for session, prompt in zip(sessions, prompts):
                    await host.command(dict(op='send', session=session, mode='chat', prompt=prompt))
                async def done():
                    while len([e for e in events if e['type'] in ('done', 'error')]) < 2:
                        await asyncio.sleep(.1)
                await asyncio.wait_for(done(), 300)
                assert not [e for e in events if e['type'] == 'error'], 'SDK chat failed'
                answers = [''.join(e['text'] for e in events if e['type'] == 'delta' and e['session'] == s) for s in sessions]
                assert all(a.strip() for a in answers), 'Empty local reply'
                result['replies'] = answers
                usage = await backend.call('usage')
                assert usage['requests'] == 2 and usage['outputTokens'] > 0
                result['usage'] = {k: v for k, v in usage.items() if k != 'id'}
                result['independentLocalChats'] = 2
                # Exercise the host UI command and persistent budget contract, not provider billing.
                await host.command(dict(op='budget', freeOnly=True, currency='USD', monthly='10', perRequest='1'))
                budget_event = next(e for e in reversed(events) if e['type'] == 'usage')
                assert budget_event['freeOnly'] is True
                assert budget_event['budgets'][0]['monthly'] == '10'
                result['budgetUpdatedThroughHost'] = True
        finally:
            with patch.object(agent_host, 'emit', events.append):
                for session in list(host.sessions):
                    await host.command(dict(op='close', session=session))
            await asyncio.to_thread(host.server.shutdown)
            host.server.server_close()
            await finish(process, reader)
        # Reopen the same disposable ledger in a new process, without loading a model.
        process, backend, reader = await start()
        try:
            try:
                await backend.call('unlock', password=secrets.token_urlsafe(32))
                raise AssertionError('Wrong passphrase accepted')
            except RuntimeError:
                result['wrongPassphraseRejected'] = True
            await backend.call('unlock', password=password)
            usage = await backend.call('usage')
            assert usage['requests'] == 2 and usage['outputTokens'] == result['usage']['outputTokens']
            result['usageSurvivesRestart'] = True
            assert usage['freeOnly'] is True and usage['budgets'][0]['monthly'] == '10'
            result['budgetSurvivesRestart'] = True
        finally:
            await finish(process, reader)
        return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    evidence = asyncio.run(qualify(args.runtime))
    Path(args.output).write_text(json.dumps(evidence, indent=2) + '\n')
    print(json.dumps(evidence, indent=2))
