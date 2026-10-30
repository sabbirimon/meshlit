const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),os=require('node:os'),path=require('node:path');
const {ProjectMemory,MAX_ACTIONS}=require('./memory-store.cjs');
async function fixture(t){const root=await fs.mkdtemp(path.join(os.tmpdir(),'meshlit-memory-'));t.after(()=>fs.rm(root,{recursive:true,force:true}));const values=new Map(),secrets={get:async k=>values.get(k),store:async(k,v)=>values.set(k,v)};const directory=path.join(root,'vault');return {root,directory,values,secrets,store:new ProjectMemory(directory,secrets,path.join(root,'project-a'))};}
test('notes persist encrypted with separate default-off local and remote recall',async t=>{
 const f=await fixture(t),note=await f.store.add('Build decision','Use the amber CPU backend for the offline runner.');
 assert.equal(await f.store.context('amber CPU',false),'');assert.equal(await f.store.context('amber CPU',true),'');
 const file=await fs.readFile(f.store.file);assert.equal(file.includes(Buffer.from('amber')),false);assert.equal(file.includes(Buffer.from(note.title)),false);
 await f.store.policy({localRecall:true,remoteRecall:false,actionHistory:false});
 const reopened=new ProjectMemory(f.directory,f.secrets,path.join(f.root,'project-a'));assert.equal((await reopened.snapshot()).notes[0].id,note.id);
 assert.match(await reopened.context('amber CPU',false),/untrusted reference data/);assert.equal(await reopened.context('amber CPU',true),'');
 await reopened.policy({localRecall:false,remoteRecall:true,actionHistory:false});assert.equal(await reopened.context('amber CPU',false),'');assert.match(await reopened.context('amber CPU',true),/Manually saved project note/);
 await reopened.forget(note.id);assert.equal(await reopened.context('amber CPU',true),'');assert.equal((await reopened.snapshot()).notes.length,0);
});
test('project identity isolates keys, files, recall and authenticated ciphertext',async t=>{
 const f=await fixture(t);await f.store.add('Private A','Project A deployment detail');const other=new ProjectMemory(f.directory,f.secrets,path.join(f.root,'project-b'));assert.equal((await other.snapshot()).notes.length,0);
 await other.add('Private B','Project B release detail');await fs.copyFile(f.store.file,other.file);
 await assert.rejects(other.snapshot(),/Cannot decrypt/);assert.equal((await f.store.snapshot()).notes[0].title,'Private A');
});
test('missing secret storage and corrupt ciphertext fail closed without overwriting data',async t=>{
 const f=await fixture(t);await f.store.add('Keep','Do not replace this note');const before=await fs.readFile(f.store.file),key=f.values.get(f.store.secretName);f.values.clear();
 await assert.rejects(f.store.add('Other','must not save'),/key is unavailable/);assert.deepEqual(await fs.readFile(f.store.file),before);
 f.values.set(f.store.secretName,key);const corrupted=Buffer.from(before);corrupted[corrupted.length-1]^=1;await fs.writeFile(f.store.file,corrupted);
 await assert.rejects(f.store.policy({localRecall:true,remoteRecall:false,actionHistory:false}),/Cannot decrypt/);assert.deepEqual(await fs.readFile(f.store.file),corrupted);
 const absent=new ProjectMemory(f.directory,{get:async()=>undefined,store:async()=>{throw Error('locked');}},path.join(f.root,'project-c'));
 await assert.rejects(absent.add('Secret','sensitive text'),/No plaintext fallback/);await assert.rejects(fs.access(absent.file),{code:'ENOENT'});
});
test('receipt history is opt-in, bounded, encrypted, and strips raw tool arguments',async t=>{
 const f=await fixture(t);assert.equal(await f.store.record({tool:'read_file',status:'complete',mode:'plan',arguments:{path:'SECRET'}}),false);
 await f.store.policy({localRecall:false,remoteRecall:false,actionHistory:true});
 for(let i=0;i<MAX_ACTIONS+2;i++)await f.store.record({tool:'read_file',status:i===0?'denied':'complete',mode:'plan',arguments:{path:'SECRET'},prompt:'SECRET',root:'SECRET'});
 const snapshot=await f.store.snapshot();assert.equal(snapshot.actions.length,MAX_ACTIONS);assert.equal(snapshot.actions.some(a=>a.status==='denied'),false);assert.equal(JSON.stringify(snapshot).includes('SECRET'),false);
 assert.deepEqual(Object.keys(snapshot.actions[0]).sort(),['at','id','mode','status','tool']);
 await assert.rejects(f.store.record({tool:'SECRET',status:'complete',mode:'plan'}),/Invalid action metadata/);
 await f.store.policy({localRecall:false,remoteRecall:false,actionHistory:false});assert.equal(await f.store.record({tool:'mcp_request',status:'failed',mode:'edit'}),false);assert.equal((await f.store.snapshot()).actions.length,MAX_ACTIONS);
 await f.store.clearActions();assert.equal((await f.store.snapshot()).actions.length,0);
});
test('concurrent store instances preserve every note and recover a dead owner lock',async t=>{
 const f=await fixture(t),other=new ProjectMemory(f.directory,f.secrets,path.join(f.root,'project-a'));
 await Promise.all(Array.from({length:12},(_,i)=>(i%2?other:f.store).add('Note '+i,'Distinct observation '+i)));
 assert.equal((await f.store.snapshot()).notes.length,12);assert.equal(f.values.size,1);
 // 2147483647 cannot be an allocated PID on the tested platforms; no process is signalled.
 await fs.writeFile(f.store.file+'.lock',JSON.stringify({pid:2147483647}));assert.equal((await other.snapshot()).notes.length,12);
});
test('note and recall bounds reject malformed writes and cap selected context',async t=>{
 const f=await fixture(t);await assert.rejects(f.store.add('Empty','  '));await assert.rejects(f.store.add('Long','a'.repeat(4001)));await assert.rejects(f.store.add('Control','bad\u0000text'));
 for(let i=0;i<5;i++)await f.store.add('Amber '+i,'amber '.repeat(600));
 await f.store.policy({localRecall:true,remoteRecall:false,actionHistory:false});const text=await f.store.context('amber',false);
 assert.equal((text.match(/\[Memory /g)||[]).length,3);assert.ok(text.length<3000);assert.equal(await f.store.context('',false),'');
 assert.equal(await f.store.context('amber',false,0),'');assert.equal(await f.store.context('amber',false,200),'');assert.ok((await f.store.context('amber',false,1200)).length<=1200);
 await assert.rejects(f.store.context('a'.repeat(12001),false));await assert.rejects(f.store.policy({localRecall:true,remoteRecall:'yes',actionHistory:false}));assert.equal((await f.store.snapshot()).notes.length,5);
});
test('symbolic-link storage is rejected rather than reading or replacing its target',async t=>{
 if(process.platform==='win32'){t.skip('Symlink creation requires a Windows OS grant');return;}
 const f=await fixture(t);await fs.mkdir(f.directory);const target=path.join(f.root,'target');await fs.writeFile(target,'unchanged');await fs.symlink(target,f.store.file);
 await assert.rejects(f.store.snapshot());await assert.rejects(f.store.add('No','No replacement'));assert.equal(await fs.readFile(target,'utf8'),'unchanged');
});
