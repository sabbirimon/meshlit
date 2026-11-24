'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {create}=require('./project-security.cjs');
function fixture(t){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'meshlit-project-security-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 const values=new Map(),choices=[],prompts=[],writes=[];let unlocked=false,unlockCount=0,acceptUnlock=false;
 const context={globalState:{get:(key,fallback)=>values.get(key)??fallback,update:async(key,value)=>{writes.push(value);values.set(key,value);}}};
 const vscode={Uri:{file:fsPath=>({scheme:'file',fsPath})},window:{showQuickPick:async(items,options)=>{prompts.push({items,options});return choices.shift();}}};
 const security=create(context,vscode,{unlockProtected:async()=>{unlockCount++;unlocked=acceptUnlock;return acceptUnlock;},isProtected:()=>unlocked});
 return {root,uri:vscode.Uri.file(root),security,choices,prompts,writes,values,get unlockCount(){return unlockCount;},allowUnlock(){acceptUnlock=true;}};
}
test('new project cancellation neither saves a preference nor unlocks',async t=>{
 const f=fixture(t);assert.equal(await f.security.ensure(f.root),false);assert.equal(f.writes.length,0);assert.equal(f.unlockCount,0);assert.equal(f.security.status(f.root),null);
 assert.match(f.prompts[0].options.title,/Set up project security/);
});
test('standard preference is remembered without becoming an unlock or tool grant',async t=>{
 const f=fixture(t);f.choices.push({mode:'standard'},{mode:'ask'});assert.equal(await f.security.ensure(f.root),true);
 assert.equal(await f.security.ensure(f.root),true);assert.equal(f.prompts.length,2);assert.equal(f.unlockCount,0);
 assert.equal(f.security.status(f.root).mode,'standard');assert.deepEqual(Object.keys(f.writes[0][0]).sort(),['approval','mode','reviewedAt','root']);
 assert.match(f.prompts[0].items.find(item=>item.mode==='standard').detail,/grants remain separate/);
});
test('pre-open protected review saves only preference; coding-window ensure requires actual unlock',async t=>{
 const f=fixture(t);f.choices.push({mode:'protected'},{mode:'ask'});assert.equal(await f.security.review(f.uri),true);assert.equal(f.unlockCount,0);
 assert.equal(await f.security.ensure(f.root),false);assert.equal(f.unlockCount,1);
 f.allowUnlock();assert.equal(await f.security.ensure(f.root),true);assert.equal(f.unlockCount,2);
 assert.equal(await f.security.ensure(f.root),true);assert.equal(f.unlockCount,2);assert.equal(f.prompts.length,2);
});
test('force review can change preference and cancellation retains previous choice',async t=>{
 const f=fixture(t);f.choices.push({mode:'standard'},{mode:'ask'});await f.security.review(f.uri);
 assert.equal(await f.security.review(f.uri,{force:true}),false);assert.equal(f.security.status(f.root).mode,'standard');
 f.choices.push({mode:'protected'},{mode:'ask'});assert.equal(await f.security.review(f.uri,{force:true}),true);assert.equal(f.security.status(f.root).mode,'protected');
 assert.equal(f.writes.at(-1).length,1);assert.equal(f.unlockCount,0);
});
test('rejects remote URIs, relative paths, files and nonexistent folders before prompting',async t=>{
 const f=fixture(t),file=path.join(f.root,'file.txt');fs.writeFileSync(file,'test');
 assert.equal(await f.security.review({scheme:'vscode-remote',fsPath:f.root}),false);
 for(const invalid of ['relative',file,path.join(f.root,'missing'),null])assert.equal(await f.security.ensure(invalid),false);
 assert.equal(f.prompts.length,0);assert.equal(f.writes.length,0);
});
test('provider request stays blocked when unlock throws or lies about state',async t=>{
 const f=fixture(t);f.choices.push({mode:'protected'},{mode:'ask'});await f.security.review(f.uri);
 for(const unlockProtected of [async()=>true,async()=>{throw Error('Locked');}]){
  const security=create({globalState:{get:(key,fallback)=>f.values.get(key)??fallback}},{},{unlockProtected,isProtected:()=>false});
  assert.equal(await security.ensure(f.root),false);
 }
});

test('cancelling action preset saves nothing, including a force review',async t=>{
 const f=fixture(t);f.choices.push({mode:'standard'});assert.equal(await f.security.review(f.uri),false);assert.equal(f.writes.length,0);
 f.choices.push({mode:'standard'},{mode:'read-only'});await f.security.review(f.uri);
 f.choices.push({mode:'protected'});assert.equal(await f.security.review(f.uri,{force:true}),false);
 assert.equal(f.security.status(f.root).mode,'standard');assert.equal(f.security.status(f.root).approval.mode,'read-only');assert.equal(f.writes.length,1);
});
test('workspace autonomy is saved as a bounded project preset without external grants',async t=>{
 const f=fixture(t);f.choices.push({mode:'standard'},{mode:'workspace'});assert.equal(await f.security.ensure(f.root),true);
 assert.deepEqual(f.security.status(f.root).approval,{mode:'workspace',tools:[]});assert.equal(f.unlockCount,0);
 const choice=f.prompts[1].items.find(item=>item.mode==='workspace');assert.match(choice.description,/this selected project/);assert.match(choice.detail,/Unsaved editor changes/);assert.match(choice.detail,/No shell, device, MCP, credential, or provider grants/);
});
test('legacy and malformed approval records default to ask with empty tool grants',t=>{
 const f=fixture(t),root=fs.realpathSync(f.root);
 for(const approval of [undefined,{mode:'all',tools:['shell']},{mode:'ask',tools:['credentials']}]){
  f.values.set('meshlit.projectSecurity',[{root,mode:'standard',reviewedAt:1,approval}]);
  assert.deepEqual(f.security.status(root).approval,{mode:'ask',tools:[]});
 }
});

test('local autonomous is first and saves bounded workspace edits with no second chooser or password',async t=>{
 const f=fixture(t);f.choices.push({mode:'local'});assert.equal(await f.security.ensure(f.root),true);
 assert.equal(f.prompts.length,1);assert.equal(f.prompts[0].items[0].label,'Local autonomous · no password');
 assert.deepEqual(f.security.status(f.root).approval,{mode:'workspace',tools:[]});assert.equal(f.security.status(f.root).mode,'local');assert.equal(f.unlockCount,0);
 assert.match(f.prompts[0].items[0].description,/this selected project/);assert.match(f.prompts[0].items[0].detail,/Unsaved editor changes/);assert.match(f.prompts[0].items[0].detail,/Stop/);assert.match(f.prompts[0].items[0].detail,/No shell, device, MCP, credential, or provider grants/);
 assert.equal(await f.security.ensure(f.root),true);assert.equal(f.prompts.length,1);assert.equal(f.unlockCount,0);
});
test('a protected project can explicitly switch to local autonomy without unlocking protected data',async t=>{
 const f=fixture(t);f.choices.push({mode:'protected'},{mode:'ask'});assert.equal(await f.security.review(f.uri),true);
 f.choices.push({mode:'local'});assert.equal(await f.security.review(f.uri,{force:true}),true);assert.equal(await f.security.ensure(f.root),true);
 assert.equal(f.security.status(f.root).mode,'local');assert.equal(f.security.status(f.root).approval.mode,'workspace');assert.equal(f.unlockCount,0);
});
