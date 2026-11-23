'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {newIdentity}=require('./pairing.cjs');
const {Devices}=require('./device-service.cjs');
const {deviceId}=require('./device-tls.cjs');
const {Relay}=require('./device-relay.cjs');
function peer(t,approve=true) {
    const identity=newIdentity(),saved=new Map();
    const service=new Devices(identity,{
        peers:()=>[...saved.values()], accepting:()=>true,approve:async()=>approve,
        save:async p=>saved.set(p.key,p),inventory:()=>[{id:'test',name:'test model'}],
        generate:async(_key,_msg,_signal,delta)=>{await delta('one');await delta(' two');return {count:2};}
    });
    t.after(()=>service.stop());return {service,identity,saved};
}
async function exchange(t,relay) {
    const a=peer(t),b=peer(t);
    for(const p of [a,b])await p.service.start({host:'127.0.0.1',advertise:false});
    const d={id:deviceId(b.identity.key)};
    if(relay){
        const {broker}=require('../../studio-relay/server.cjs');
        const token='test-'.repeat(8),r=broker(token);r.server.listen(0,'127.0.0.1');await require('node:events').once(r.server,'listening');t.after(()=>r.stop());
        for(const p of [a,b]){
            p.service.relay=new Relay('http://127.0.0.1:'+r.server.address().port,token,p.identity,p.service.port);
            await p.service.relay.start();
        }
    }else{d.host='127.0.0.1';d.port=b.service.port;}
    const call=(dest,msg,delta)=>a.service.call(dest,msg,AbortSignal.timeout(6000),delta);
    await assert.rejects(call(d,{op:'models'}),/Pair this/);
    if(!relay)await assert.rejects(call({...d,id:'wrong'},{op:'pair'}),/identity changed/);
    const res=await call(d,{op:'pair',node:a.identity.node,name:'Test A'});
    assert.equal(res.key,b.identity.key);assert.equal(b.saved.get(a.identity.key).name,'Test A');
    assert.equal((await call(d,{op:'models'})).models.length,1);
    let text='';assert.equal((await call(d,{op:'chat'},s=>text+=s)).count,2);assert.equal(text,'one two');
}
test('pinned TLS pairs directly and rejects unknown identities',t=>exchange(t,false));
test('relay carries pinned end-to-end TLS and streamed model replies',{skip:!process.env.MESHLIT_RELAY_TEST},t=>exchange(t,true));
test('decline stores no trust and does not allow model access',async t=>{
    const a=peer(t),b=peer(t,false);
    await b.service.start({host:'127.0.0.1',advertise:false});
    const target={id:b.service.id,host:'127.0.0.1',port:b.service.port};
    await assert.rejects(a.service.call(target,{op:'pair',name:'Mac',node:a.identity.node},AbortSignal.timeout(5000)),/declined/);
    assert.equal(b.saved.size,0);
});
test('cancelling a paired stream stops the host request and allows another',async t=>{
    const a=peer(t),b=peer(t);let stopped;
    const end=new Promise(r=>stopped=r);
    b.saved.set(a.identity.key,{key:a.identity.key});
    b.service.hooks.generate=async(_key,_msg,signal,delta)=>{
        await delta('first');
        if(!signal.aborted)await require('node:events').once(signal,'abort');
        stopped();signal.throwIfAborted();
    };
    await b.service.start({host:'127.0.0.1',advertise:false});
    const target={id:b.service.id,host:'127.0.0.1',port:b.service.port};
    const ctl=new AbortController();
    await assert.rejects(a.service.call(target,{op:'chat'},ctl.signal,()=>ctl.abort()));
    await Promise.race([end,require('node:timers/promises').setTimeout(3000,undefined,{ref:false}).then(()=>{throw Error('Host kept generating');})]);
    assert.ok((await a.service.call(target,{op:'models'},AbortSignal.timeout(3000))).models.length);
});
