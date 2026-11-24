const test=require('node:test'),assert=require('node:assert/strict');
const fresh=()=>{delete require.cache[require.resolve('./local-models.cjs')];return require('./local-models.cjs');};
const starter={id:'qwen25-1.5b',label:'Starter',present:true,thinkingSupported:false};
const reasoning={id:'qwen3-4b',label:'Reasoning',present:true,thinkingSupported:true};
test('catalog accepts only known bounded model records and returns independent copies',()=>{
 const models=fresh();models.accept([starter,reasoning,{id:'unknown',label:'Injected',present:true,thinkingSupported:true}]);
 assert.deepEqual(models.list(),[starter,reasoning]);
 const copy=models.list();copy[0].label='Changed';assert.equal(models.list()[0].label,'Starter');
 models.accept([starter,starter]);assert.equal(models.list().length,2);
 models.accept([{...reasoning,present:'yes'},{...starter,label:'x'.repeat(150)}]);assert.equal(models.list().length,2);
 models.accept(null);assert.equal(models.list().length,2);
});
test('local routes bind allowlisted present model identity and never infer hosted thinking',()=>{
 const models=fresh();models.accept([starter,reasoning]);
 assert.deepEqual(models.route('local'),{kind:'local',modelId:starter.id,label:'Starter',thinkingSupported:false});
 const selected=models.route('local:qwen3-4b');assert.equal(selected.modelId,reasoning.id);assert(models.thinking(selected));
 assert(!models.thinking({kind:'compatible',modelId:reasoning.id}));assert(!models.thinking(models.route('local')));
 for(const invalid of ['local:missing','../../model.gguf','local:local:qwen3-4b'])assert.throws(()=>models.route(invalid));
 models.accept([starter,{...reasoning,present:false}]);assert.throws(()=>models.route('local:qwen3-4b'),/missing/);
});
test('readiness belongs to exactly the selected local model and legacy state belongs to starter',()=>{
 const models=fresh();models.accept([starter,reasoning]);
 const ready={state:'ready',label:'Runtime starter'};assert.equal(models.state(models.route('local'),ready),ready);
 const other=models.state(models.route('local:qwen3-4b'),ready);assert.equal(other.state,'standby');assert.equal(other.label,'Reasoning');
 const loaded={modelId:reasoning.id,state:'loading'};assert.equal(models.state(models.route('local:qwen3-4b'),loaded),loaded);
 models.accept([starter,{...reasoning,present:false}]);assert.equal(models.state({kind:'local',modelId:reasoning.id},ready).state,'error');
});
