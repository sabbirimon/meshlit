const test=require('node:test');
const assert=require('node:assert/strict');
const {create}=require('./usage-unlock.cjs');
const password='fixture passphrase';

function fixture() {
  let stored;
  const sent=[];
  const secrets={get:async()=>stored,store:async(_key,value)=>{stored=value;},delete:async()=>{stored=undefined;}};
  const make=()=>create({secrets,send:message=>sent.push(message),report:message=>assert.fail(message)});
  return {secrets,make,sent,saved:()=>stored};
}

test('remembering requires an opted-in successful unlock and restores once per backend',async()=>{
  const f=fixture();const first=f.make();
  assert.equal(first.request(password,true),true);
  assert.equal(first.request(password,true),false);
  assert.equal(f.saved(),undefined);
  first.reset();
  await first.accepted();
  assert.equal(f.saved(),undefined);
  first.request(password,false);
  await first.accepted();
  assert.equal(f.saved(),undefined);
  first.request(password,true);
  await first.accepted();
  assert.equal(f.saved(),password);
  const restarted=f.make();
  await restarted.restore(()=>true);
  await restarted.restore(()=>true);
  assert.equal(f.sent.length,4);
  assert.deepEqual(f.sent.at(-1),{op:'unlock',password});
  await restarted.accepted();
  await restarted.forget();
  await f.make().restore(()=>true);
  assert.equal(f.sent.length,4);
});

test('forget cancels a pending saved read and waits out a pending keychain write',async()=>{
  const f=fixture();const controller=f.make();
  let read;
  f.secrets.get=()=>new Promise(resolve=>{read=resolve;});
  const restoring=controller.restore(()=>true);
  await controller.forget();
  read(password);
  await restoring;
  assert.deepEqual(f.sent,[]);

  let release;
  const store=f.secrets.store;
  f.secrets.store=async(...args)=>{
    await new Promise(resolve=>{release=resolve;});
    await store(...args);
  };
  controller.request(password,true);
  const accepting=controller.accepted();
  await Promise.resolve();
  const forgetting=controller.forget();
  release();
  await Promise.all([accepting,forgetting]);
  assert.equal(f.saved(),undefined);
});
