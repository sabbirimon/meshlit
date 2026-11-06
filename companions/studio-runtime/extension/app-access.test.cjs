const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const binding={owner:'12345678-1234-1234-1234-123456789abc',model:'Local · Qwen',task:'Find the project documentation'};
const action={action:'snapshot',bundle_id:'org.example.Editor'};
function fixture(){
 const commands=new Map(),warnings=[],native=[],answers=[];let line,warning;
 const api={window:{showQuickPick:async(items,opts)=>opts.canPickMany?[items[0]]:items[0],showWarningMessage:async(text,options,...choices)=>{warnings.push({text,choices});return warning?warning(text,choices):answers.shift()||'Enable selected apps';},showInformationMessage(){},showErrorMessage:message=>{throw Error(message);}},commands:{registerCommand:(name,fn)=>{commands.set(name,fn);return {dispose(){}};}},env:{openExternal(){}},Uri:{parse:value=>value}};
 const child={stdin:{on(){},write:text=>{const request=JSON.parse(text);native.push(request);const result=request.action==='status'?{trusted:true}:request.action==='apps'?{apps:[{id:action.bundle_id,name:'Example Editor'}]}:{ok:true};queueMicrotask(()=>line(JSON.stringify({id:request.id,result})));}},stdout:{},on(){},kill(){}};
 const loaded={exports:{}};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'app-access.cjs'),'utf8'),{module:loaded,process:{platform:'darwin',env:{}},require:name=>name==='vscode'?api:name==='node:fs'?{existsSync:()=>true}:name==='node:child_process'?{spawn:()=>child}:name==='node:readline'?{createInterface:()=>({on:(name,fn)=>line=fn})}:require(name),setTimeout,clearTimeout,Buffer});
 loaded.exports.register({extensionPath:'/fixture',subscriptions:[]});
 return {controller:loaded.exports,commands,warnings,native,answers,setWarning:value=>warning=value,enable:()=>commands.get('meshlit.appAccess')()};
}
test('one explicit task approval permits bounded same-app actions without repeated prompts',async()=>{
 const f=fixture();await f.enable();f.answers.push('Allow this task');
 for(let i=0;i<25;i++)await f.controller.execute(action,()=>true,binding);
 assert.equal(f.warnings.length,2);assert.match(f.warnings[1].text,/Example Editor/);assert.match(f.warnings[1].text,/Local · Qwen/);assert.match(f.warnings[1].text,/Find the project documentation/);assert.match(f.warnings[1].text,/25 snapshots and actions/);
 assert.equal(f.native.filter(item=>item.action==='snapshot').length,25);
 await assert.rejects(f.controller.execute(action,()=>true,binding),/25-operation/);
 assert.equal(f.warnings.length,2);f.controller.stop();
});
test('missing binding retains per-action Allow once and model changes cannot reuse a grant',async()=>{
 const f=fixture();await f.enable();f.answers.push('Allow once','Allow once');
 await f.controller.execute(action,()=>true);await f.controller.execute(action,()=>true);
 assert.deepEqual(f.warnings[1].choices,['Allow once']);assert.equal(f.warnings.length,3);
 f.answers.push('Allow this task');await f.controller.execute(action,()=>true,binding);
 await assert.rejects(f.controller.execute(action,()=>true,{...binding,model:'A different provider'}),/model changed/);
 assert.equal(f.native.filter(item=>item.action==='snapshot').length,3);f.controller.stop();
});
test('task completion revokes a pending approval and a later task must ask again',async()=>{
 const f=fixture();await f.enable();let decide;
 f.setWarning(()=>new Promise(resolve=>decide=resolve));
 const result=f.controller.execute(action,()=>true,binding);
 for(let n=0;n<10&&!decide;n++)await new Promise(resolve=>setImmediate(resolve));
 assert(decide);f.controller.endTask(binding.owner);decide('Allow this task');
 await assert.rejects(result,/ended or was revoked/);
 assert.equal(f.native.filter(item=>item.action==='snapshot').length,0);
 f.setWarning(undefined);f.answers.push('Allow this task');await f.controller.execute(action,()=>true,binding);
 f.controller.endTask(binding.owner);f.answers.push('Allow once');await f.controller.execute(action,()=>true,binding);
 assert.equal(f.warnings.length,4);f.controller.stop();
});
