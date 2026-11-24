const test=require('node:test'),assert=require('node:assert/strict');
const {messageText,transcript,handle}=require('./chat-actions.cjs');
test('copy retains exact unicode text and acknowledges only completed clipboard writes',async()=>{
 let copied;const events=[];
 await handle({op:'copyMessage',text:'hello বাংলা\n```js\n1<2\n```'},{vscode:{env:{clipboard:{writeText:async t=>{assert.equal(events.length,0);copied=t;}}}},post:e=>events.push(e)});
 assert.equal(copied,'hello বাংলা\n```js\n1<2\n```');assert.equal(events[0].error,undefined);
});
test('transcript rejects malformed, oversized and unbounded payloads',()=>{
 assert.throws(()=>messageText('é'.repeat(70000)));assert.throws(()=>transcript([{role:'system',text:'hidden'}]));
 assert.throws(()=>transcript(Array(201).fill({role:'user',text:'hi'})));assert.throws(()=>transcript(Array(10).fill({role:'user',text:'a'.repeat(120000)})));
 assert.match(transcript([{role:'assistant',text:'answer'}]),/^## Meshlit\n\nanswer\n$/);
});
test('export cancellation and closed owner never write; actual export uses chosen destination',async()=>{
 let writes=0;const uri={scheme:'file'};let selection,live=true;const events=[];
 const config={vscode:{window:{showSaveDialog:async()=>selection},workspace:{fs:{writeFile:async(u,bytes)=>{assert.equal(u,uri);assert.match(bytes.toString(),/You/);writes++;}}}},active:()=>live,post:e=>events.push(e)};
 const request={op:'exportChat',messages:[{role:'user',text:'hello'}]};
 await handle(request,config);assert.equal(writes,0);selection=uri;live=false;await handle(request,config);assert.equal(writes,0);live=true;await handle(request,config);assert.equal(writes,1);
});
test('clipboard errors are visible and speech is bound to requesting owner',async()=>{
 const events=[];await handle({op:'copyMessage',text:'hi'},{vscode:{env:{clipboard:{writeText:async()=>{throw Error('denied');}}}},post:e=>events.push(e)});assert.match(events[0].error,/did not complete/);
 let spoken,stopped;const config={voice:{speak:async(t,id)=>{spoken=[t,id];},stop:id=>{stopped=id;}},owner:'chat-1',post:e=>events.push(e)};
 await handle({op:'readAloud',text:'hi'},config);await handle({op:'stopSpeech'},config);assert.deepEqual(spoken,['hi','chat-1']);assert.equal(stopped,'chat-1');assert.equal(events.at(-1).speaking,false);
});
test('an older playback completion cannot disable Stop for replacement speech',async()=>{
 const events=[],pending=[];const config={voice:{speak:()=>new Promise(resolve=>pending.push(resolve))},owner:'one',speechState:{},post:e=>events.push(e)};
 const first=handle({op:'readAloud',text:'first'},config),second=handle({op:'readAloud',text:'second'},config);
 pending[0]();await first;assert.equal(events.at(-1).speaking,true);
 pending[1]();await second;assert.equal(events.at(-1).speaking,false);
});
