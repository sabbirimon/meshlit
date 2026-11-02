const test=require('node:test'),assert=require('node:assert/strict');
const effort=require('./reasoning-effort.cjs');
const route={kind:'compatible',id:'test',endpoint:'https://example.test/v1',model:'reasoning-model'};
const configured={...route,effortSupport:{endpoint:route.endpoint,model:route.model,adapter:'reasoning_effort',values:['low','high']}};
test('unknown/local support stays Auto-only, and declarations bind exact endpoint/model',()=>{
 for(const r of [{kind:'local'},route,{...configured,model:'different-model'},{...configured,endpoint:'https://different.test/v1'}]){
  assert.equal(effort.validate(r,'auto'),'auto');assert.throws(()=>effort.validate(r,'high'));
 }
 assert.deepEqual(effort.support(configured),['low','high']);assert.throws(()=>effort.validate(configured,'medium'));assert.throws(()=>effort.validate(configured,'ultra'));
 assert.deepEqual(effort.support({...configured,effortSupport:{...configured.effortSupport,adapter:'reasoning.effort'}}),[]);
});
test('unsupported selection cannot silently become a supported effort',async()=>{
 await assert.rejects(effort.choose({},route,'auto',{window:{showQuickPick:async()=>({id:'high'})}}));
});
test('workspace defaults only apply to the configured exact model and never local',async()=>{
 const values={};const context={workspaceState:{get:(_k,d)=>values.saved||d,update:async(_k,v)=>values.saved=v}};
 const result=await effort.choose(context,configured,'high',{window:{showQuickPick:async()=>({id:'default'})}});
 assert.equal(result.effort,'high');assert.equal(effort.defaultFor(context,configured),'high');assert.equal(effort.defaultFor(context,{...configured,model:'other'}),'auto');assert.equal(effort.defaultFor(context,{kind:'local'}),'auto');
});
test('cancelled support review neither saves declarations nor changes active route',async()=>{
 const picks=[{id:'configure'},{id:'reasoning_effort'},[{id:'high'}]],saved=[];
 const result=await effort.choose({globalState:{update:async(...x)=>saved.push(x)}},route,'auto',{window:{showQuickPick:async()=>picks.shift(),showWarningMessage:async()=>undefined}});
 assert.equal(result,undefined);assert.deepEqual(saved,[]);assert.equal(route.effortSupport,undefined);
});
