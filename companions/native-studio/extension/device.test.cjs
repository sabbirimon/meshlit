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
test('MeshCoin verifies signed blocks, retry-safe keeper allocations and individual serials',async t=>{
    const {Wallet,walletId,coinSerial}=require('./meshcoin.cjs'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
    const host=peer(t),keeper=peer(t),root=fs.mkdtempSync(path.join(os.tmpdir(),'studio-meshcoin-'));
    const w=new Wallet(path.join(root,'host'),host.identity),k=new Wallet(path.join(root,'keeper'),keeper.identity);
    t.after(()=>{w.close();k.close();fs.rmSync(root,{recursive:true,force:true})});
    const target={id:host.service.id,host:'127.0.0.1'};
    host.service.hooks.wallet=async()=>w;
    await host.service.start({host:'127.0.0.1',advertise:false});target.port=host.service.port;
    await keeper.service.start({host:'127.0.0.1',advertise:false});
    const call=msg=>keeper.service.call(target,msg,AbortSignal.timeout(4000));
    await assert.rejects(call({op:'identity'}),/Pair/);
    await call({op:'pair',name:'Keeper',node:keeper.identity.node});
    const card=(await call({op:'identity'})).identity;assert.equal(card.contributor,w.owner);assert.notEqual(card.session,keeper.service.session);
    assert.equal(w.record('off',50),undefined);w.set('record',true);
    const b=w.record('job1',10,{keeper:k.owner,bps:2000});
    assert.equal(w.record('job1',10,{}),undefined);assert.equal(w.record('no-usage',undefined,{}),undefined);
    assert.equal(coinSerial(b,1).length,128);assert.notEqual(coinSerial(b,1),coinSerial(b,2));assert.throws(()=>coinSerial(b,11));
    const p={key:host.identity.key,node:host.identity.node};
    await assert.rejects(k.sync(p,call),/enable receipt sharing/);
    w.set('share:'+k.owner,true);await k.sync(p,call);await k.sync(p,call);
    assert.equal(w.totals().earned,8);assert.equal(w.totals().paid,2);assert.equal(k.totals().kept,2);
    // Simulate a contributor restoring the same chain from before the keeper acknowledged it.
    w.db.prepare('DELETE FROM acks WHERE hash=?').run(b.hash);
    assert.equal(w.totals().pending,2);
    await k.sync(p,call);
    assert.equal(w.totals().paid,2);
    assert.equal(k.totals().kept,2);
    const bad=structuredClone(b);bad.receipt.generatedTokens=500;
    k.db.prepare('UPDATE blocks SET body=? WHERE owner=?').run(JSON.stringify(bad),w.owner);
    assert.throws(()=>k.totals(),/verify/);
    const restored=new Wallet(path.join(root,'restore'),host.identity);
    try{
        const backup=w.backup();
        restored.restore(backup);restored.restore(backup);
        assert.deepEqual(restored.totals(),w.totals());
        assert.throws(()=>restored.restore({...backup,owner:k.owner}));
        assert.throws(()=>restored.restore({...backup,acks:[{...backup.acks[0],signature:'00'}]}));
    }finally{restored.close();}

});
