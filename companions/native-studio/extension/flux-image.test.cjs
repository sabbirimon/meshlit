'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {generate,origin}=require('./flux-image.cjs');
const prompt='A red sailboat';
const signal=()=>AbortSignal.timeout(5000);
const begin=()=>Response.json({event_id:'1234'});
const finish=url=>new Response('event: complete\ndata: '+JSON.stringify([{url}])+'\n\n');
test('free image client sends one bounded prompt and returns image bytes from the same Space',async()=>{
    const url=origin+'/gradio_api/file=/tmp/output.webp';
    const bytes=Buffer.from('RIFF....WEBPsample');
    const replies=[begin(),finish(url),new Response(bytes)];
    const calls=[];
    const result=await generate(prompt,()=>{},signal(),async(address,options)=>{
        calls.push({address,options});return replies.shift();
    });
    assert.deepEqual(calls.map(c=>c.address),[origin+'/gradio_api/call/infer',origin+'/gradio_api/call/infer/1234',url]);
    assert.equal(JSON.parse(calls[0].options.body).data[0],prompt);
    assert.ok(calls.every(c=>c.options.redirect==='error'&&c.options.credentials==='omit'));
    assert.deepEqual(result,{data:bytes,mimeType:'image/webp'});
});
test('a denied or revoked Internet grant prevents the next request',async()=>{
    let calls=0,permitted=true;
    const check=()=>{if(!permitted)throw Error('revoked')};
    await assert.rejects(generate(prompt,check,signal(),async()=>{calls++;permitted=false;return begin();}),/revoked/);
    assert.equal(calls,1);
    await assert.rejects(generate(prompt,check,signal(),async()=>{calls++;return begin();}),/revoked/);
    assert.equal(calls,1);
});
test('cross-origin and unrelated same-origin file references never get fetched',async()=>{
    for(const url of ['https://other.invalid/image.png',origin+'/private',origin.replace('https://','https://user@')+'/gradio_api/file=test']){
        const replies=[begin(),finish(url)];
        await assert.rejects(generate(prompt,()=>{},signal(),async()=>{assert.ok(replies.length);return replies.shift()}),/file URL/);
    }
});
test('queue errors, oversize responses and invalid image bytes produce errors',async()=>{
    for(const [replies,err] of [
        [[new Response('x'.repeat(8193))],/too large/],
        [[begin(),new Response('event: error\ndata: null')],/did not complete/],
        [[begin(),finish(origin+'/gradio_api/file=x'),new Response('<html>')],/supported image/]
    ])await assert.rejects(generate(prompt,()=>{},signal(),async()=>replies.shift()),err);
});
