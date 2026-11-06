const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

test('opening a workspace keeps the chat window and exposes model settings',async()=>{
  const commands=new Map(),calls=[],values=new Map(),picks=[];
  const folder={scheme:'file',fsPath:'/fixture/project'};
  const vscode={
    commands:{registerCommand:(name,handler)=>commands.set(name,handler),
      executeCommand:async(name,...args)=>{calls.push([name,...args]);return name==='meshlit.reviewProject'?true:commands.get(name)?.(...args);}},
    FileType:{Directory:2},workspace:{fs:{stat:async()=>({type:2})}},
    window:{registerTreeDataProvider:()=>({}),showQuickPick:async()=>picks.shift(),showOpenDialog:async()=>[folder]}
  };
  const module={exports:{}};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,'navigation.cjs'),'utf8'),
    {module,require:name=>name==='vscode'?vscode:require(name)});
  module.exports.register({subscriptions:[],globalState:{get:(key,fallback)=>values.get(key)||fallback,
    update:async(key,value)=>values.set(key,value)}});
  picks.push({action:'browse'});
  await commands.get('meshlit.openIde')();
  assert.equal(calls.at(-1)[0],'vscode.openFolder');
  assert.equal(calls.at(-1)[1],folder);
  assert.equal(calls.at(-1)[2].forceNewWindow,true);
  assert.equal(values.get('meshlit.recentWorkspaces')[0].path,folder.fsPath);
  await commands.get('meshlit.llmSettings')();
  assert.deepEqual(calls.at(-1),['meshlit.mainLlmSettings']);
});
