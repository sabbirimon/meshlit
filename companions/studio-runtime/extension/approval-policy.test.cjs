const test=require('node:test'),assert=require('node:assert/strict');
const policy=require('./approval-policy.cjs');
test('Ask is default and automatic workspace modes never authorize external or protected tools',()=>{
 for(const tool of ['list_files','read_file','propose_edit','mcp_request','desktop_control','credential_write','policy_set','run_command'])assert.equal(policy.automatic(undefined,tool),false);
 const reads={mode:'read-only',tools:[]},selected={mode:'selected',tools:['list_files','read_file','propose_edit']};
 for(const p of [reads,selected])for(const tool of ['mcp_request','desktop_control','credential_write','policy_set','run_command'])assert.equal(policy.automatic(p,tool),false);
 assert.equal(policy.automatic(reads,'read_file'),true);assert.equal(policy.automatic(reads,'propose_edit'),false);assert.equal(policy.automatic(selected,'propose_edit'),true);
 assert.throws(()=>policy.normalize({mode:'selected',tools:['run_command']}));
});
test('broader workspace policy requires human confirmation and cancelled review leaves default unchanged',async()=>{
 const p=await policy.choose(undefined,{window:{showQuickPick:async()=>({id:'read-only'}),showWarningMessage:async()=>undefined}});
 assert.equal(p,undefined);assert.deepEqual(policy.normalize(),{mode:'ask',tools:[]});
});
