const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const loaded={exports:{}};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'agent-teams.cjs'),'utf8'),{module:loaded,require:n=>n==='vscode'?{}:require(n),setInterval,clearInterval});
const {buildPlan,render,validateProfile}=loaded.exports;
function config(){return {goal:'Check task',mode:'SINGLE_DEVICE',control:'USER_DIRECTED',maxRounds:2,parallelWorkers:2,roles:[null,null,null,null]};}
const sources=()=>Array.from({length:4},()=>({kind:'local'}));
test('roles remain disabled until owner enables them; no authority is imported',()=>{
 const role={id:'11111111-1111-1111-1111-111111111111',name:'Reviewer',instruction:'Review uncertainty',enabled:false};validateProfile(role);
 const input={...config(),roles:[role.id,null,null,null]};assert.throws(()=>buildPlan(input,[role],sources()),/disabled/);
 const plan=buildPlan(input,[{...role,enabled:true}],sources());assert.equal(plan.members[0].guidance,role.instruction);assert.equal(plan.members[0].modelId,'meshlit-local');assert.equal(plan.members[0].tools,undefined);
});
test('cannot claim cluster execution with local sources or exceed bounded controls',()=>{
 assert.throws(()=>buildPlan({...config(),mode:'REMOTE_CLUSTER'},[],sources()),/remote/);
 assert.throws(()=>buildPlan({...config(),maxRounds:4},[],sources()));assert.throws(()=>buildPlan({...config(),parallelWorkers:8},[],sources()));
 const remote={kind:'compatible',id:'approved',model:'model',endpoint:'https://node.invalid/v1',token:'test'};
 assert.throws(()=>buildPlan(config(),[],[remote,...sources().slice(1)]),/Single/);
 const plan=buildPlan({...config(),mode:'MULTI_DEVICE',control:'SUPERVISED_AUTONOMOUS'},[],[remote,...sources().slice(1)]);
 assert.equal(plan.members[0].sourceId,'approved');assert.equal(JSON.stringify(plan).includes('token'),false);
});
test('catalog and outputs enter DOM through textContent with nonce CSP',()=>{
 const html=render();assert.match(html,/default-src 'none'/);assert.match(html,/script-src 'nonce-/);assert.match(html,/textContent=value/);assert.doesNotMatch(html,/innerHTML|eval\(/);
 const script=html.match(/<script nonce="[^"]+">([\s\S]*)<\/script>/)[1];assert.doesNotThrow(()=>new vm.Script(script));
});
test('agent conversations reuse selected roles and source checks',()=>{
 const local=buildPlan({...config(),control:'AGENT_DIALOGUE'},[],sources());
 assert.equal(local.control,'AGENT_DIALOGUE');assert.equal(local.members.length,4);
 assert.deepEqual(Array.from(local.members,m=>m.id),['agent-1','agent-2','agent-3','agent-4']);
 assert.throws(()=>buildPlan({...config(),control:'AGENT_DIALOGUE',mode:'REMOTE_CLUSTER'},[],sources()),/remote/);
 const disabled={id:'11111111-1111-1111-1111-111111111111',name:'Agent',instruction:'Analyze',enabled:false};
 assert.throws(()=>buildPlan({...config(),control:'AGENT_DIALOGUE',roles:[disabled.id,null,null,null]},[disabled],sources()),/disabled/);
});
