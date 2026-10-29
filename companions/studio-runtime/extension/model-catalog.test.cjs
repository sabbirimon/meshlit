const {test}=require('node:test');const assert=require('node:assert/strict');const {normalizeCatalog,filterModels}=require('./model-catalog.cjs');
test('free labels require explicit zero prices, never a name or missing metadata',()=>{
 const m=normalizeCatalog({data:[{id:'name:free'},{id:'zero',pricing:{prompt:'0',completion:'0'}},{id:'paid',pricing:{prompt:'0',completion:'.1'}},{id:'missing',pricing:{prompt:0}},{id:'invalid',pricing:{prompt:'',completion:null}},{id:'trial',cost_type:'trial'},{id:'zero'}]},'fixture','now');
 assert.deepEqual(m.map(x=>x.cost),['unknown','free','paid','unknown','unknown','trial']);assert.equal(m[0].checkedAt,'now');assert.equal(filterModels(m,{cost:'free'}).length,1);assert.equal(filterModels(m,{cost:'paid',search:'paid'})[0].id,'paid');
});
