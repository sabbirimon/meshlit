const {test}=require('node:test'),assert=require('node:assert/strict');const {AppAccessPolicy}=require('./app-access-policy.cjs');
test('app access defaults off and revocation changes the generation',()=>{const p=new AppAccessPolicy(),a={action:'snapshot',bundle_id:'org.example.Editor'};assert.throws(()=>p.validate(a));p.allow([a.bundle_id]);const g=p.validate(a);p.stop();assert.throws(()=>p.validate(a));p.allow([a.bundle_id]);assert.notEqual(p.validate(a),g);});
test('protected apps and injected operations cannot be scoped',()=>{const p=new AppAccessPolicy();assert.throws(()=>p.allow(['com.apple.SecurityAgent']));p.allow(['org.example.Editor']);assert.throws(()=>p.validate({action:'shell',bundle_id:'org.example.Editor'}));assert.throws(()=>p.validate({action:'snapshot',bundle_id:'org.example.Other'}));});
test('actions require a snapshot handle and bound text',()=>{const p=new AppAccessPolicy();p.allow(['org.example.Editor']);const a={action:'set_value',bundle_id:'org.example.Editor',node_id:'12345678-1234-1234-1234-123456789abc',text:'test'};p.validate(a);assert.throws(()=>p.validate({...a,text:'x'.repeat(8193)}));assert.throws(()=>p.validate({...a,node_id:'guessed'}));});
const binding={owner:'12345678-1234-1234-1234-123456789abc',model:'local:qwen',task:'Find the project documentation'};
const action={action:'snapshot',bundle_id:'org.example.Editor'};
test('task grants bind selected app, exact model and human task and count the first action',()=>{
 let now=100;const p=new AppAccessPolicy({clock:()=>now});p.allow([action.bundle_id,'org.example.Other']);
 const scope=p.taskScope(binding);assert.equal(p.consumeTask(action,scope),false);p.grantTask(action,scope);
 assert.equal(p.consumeTask({...action,bundle_id:'org.example.Other'},scope),false);
 assert.equal(p.consumeTask(action,p.taskScope({...binding,owner:'22345678-1234-1234-1234-123456789abc'})),false);
 assert.throws(()=>p.consumeTask(action,p.taskScope({...binding,model:'online:other'})),/model changed/);
 assert.throws(()=>p.consumeTask(action,p.taskScope({...binding,task:'Delete all files'})),/task or selected model changed/);
 for(let n=0;n<25;n++)assert.equal(p.consumeTask(action,scope),true);
 assert.throws(()=>p.consumeTask(action,scope),/25-operation/);
 assert.throws(()=>p.grantTask(action,scope),/25-operation/);
});
test('task expiry, task completion, app rescoping and Stop revoke previous grants and pending approvals',()=>{
 let now=0;const p=new AppAccessPolicy({clock:()=>now});p.allow([action.bundle_id]);
 let scope=p.taskScope(binding);p.grantTask(action,scope);now=600000;
 assert.throws(()=>p.consumeTask(action,scope),/expired/);
 p.endTask(binding.owner);assert.throws(()=>p.grantTask(action,scope),/ended or was revoked/);
 scope=p.taskScope(binding);p.grantTask(action,scope);assert(p.consumeTask(action,scope));
 p.allow([action.bundle_id]);assert.throws(()=>p.consumeTask(action,scope),/ended or was revoked/);
 scope=p.taskScope(binding);p.grantTask(action,scope);p.stop();assert.throws(()=>p.consumeTask(action,scope),/off or outside/);
 assert.equal(p.grants.size,0);
});
test('invalid task bindings cannot create reusable access',()=>{
 const p=new AppAccessPolicy();p.allow([action.bundle_id]);assert.equal(p.taskScope(undefined),undefined);
 for(const value of [null,{}, {...binding,owner:'wrong'}, {...binding,model:''}, {...binding,task:'x'.repeat(16001)}])assert.throws(()=>p.taskScope(value));
 const scope=p.taskScope(binding);assert.throws(()=>p.grantTask({...action,bundle_id:'com.apple.Passwords'},scope));
});
