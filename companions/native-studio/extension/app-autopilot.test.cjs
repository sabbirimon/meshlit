'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {perform,textEntry}=require('./app-autopilot.cjs');
function editor(){
    const events=[];
    let value='';
    return {events,async request(req){
        events.push(req);
        assert.equal(req.bundle_id,'approved-editor');
        if(req.action==='snapshot')return {nodes:[{id:'editable-'+events.length,role:'text',value,editable:true}]};
        if(req.action==='set_value'){value=req.text;return {ok:true};}
        throw Error('Unexpected input');
    }};
}
test('types in the selected editor and refreshes the snapshot before finishing',async()=>{
    const client=editor();
    const count=await perform({client,appId:'approved-editor',task:'type "hello"',plan:textEntry,check(){},progress(){}});
    assert.equal(count,1);
    assert.deepEqual(client.events.map(e=>e.action),['snapshot','set_value','snapshot']);
    assert.equal(client.events[1].node_id,'editable-1');
});
test('revocation after planning prevents dispatch',async()=>{
    const client=editor();let blocked=false;
    await assert.rejects(perform({client,appId:'approved-editor',task:'type "hello"',plan(){blocked=true;return {action:'set_value',index:0,text:'hello'};},
        check(){if(blocked)throw Error('Access revoked');},progress(){}}),/Access revoked/);
    assert.deepEqual(client.events.map(e=>e.action),['snapshot']);
});
test('rejects stale indices, unsupported actions and ambiguous text shortcuts',async()=>{
    assert.equal(textEntry('type "x"',[{editable:true},{editable:true}],0),undefined);
    for(const step of [{action:'press',index:99},{action:'shell',index:0},{action:'press',index:0}]){
        const client=editor();
        await assert.rejects(perform({client,appId:'approved-editor',task:'example',plan:()=>step,check(){},progress(){}}));
        assert.equal(client.events.length,1);
    }
});
