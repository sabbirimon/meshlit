const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const test=require('node:test');
const vm=require('node:vm');

test('chat sources separate cluster profiles from action launchers and require route approval',async()=>{
  const profile={id:'lan-fixture',sourceKind:'local-cluster',kind:'compatible',endpoint:'https://cluster.test/v1',model:'qwen',service:'Fixture'};
  const calls=[],choices=[];
  const vscode={QuickPickItemKind:{Separator:-1},commands:{executeCommand:async command=>calls.push(command)},
    window:{showQuickPick:async items=>choices.shift()(items),showWarningMessage:async message=>{
      assert.match(message,/https:\/\/cluster\.test\/v1/);
      assert.match(message,/placement are managed by the coordinator/);
      return choices.shift()();
    }}};
  const context={globalState:{get:(key,fallback)=>key==='meshlit.connections'?[profile]:fallback},
    secrets:{get:async key=>{calls.push(key);return 'fixture-only';}}};
  const module={exports:{}};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,'connections.cjs'),'utf8'),{module,
    URL,require:name=>name==='vscode'?vscode:require(name)});
  const {chooseConnection}=module.exports;

  choices.push(items=>{
    assert(items.some(item=>item.label==='Cluster coordinators'&&item.kind===-1));
    assert(items.some(item=>item.source==='remote-cluster'));
    return items.find(item=>item.command==='meshlit.bots');
  });
  assert.equal(await chooseConnection(context,undefined,{launchActions:true}),undefined);
  assert.deepEqual(calls,['meshlit.bots']);
  const pickCluster=items=>items.find(item=>item.id===profile.id);
  choices.push(pickCluster,items=>items[0],()=>undefined);
  assert.equal(await chooseConnection(context),undefined);
  assert.deepEqual(calls,['meshlit.bots']);
  choices.push(pickCluster,items=>items[0],()=> 'Use connection');
  const route=await chooseConnection(context);
  assert.equal(route.label,'LAN cluster coordinator · qwen');
  assert.equal(route.endpoint,profile.endpoint);
  assert.deepEqual(calls,['meshlit.bots','meshlit.model.lan-fixture']);
});
