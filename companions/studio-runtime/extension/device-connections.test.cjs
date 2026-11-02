const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const loaded={exports:{}};
vm.runInNewContext(fs.readFileSync(path.join(__dirname,'device-connections.cjs'),'utf8'),{module:loaded,require:n=>n==='vscode'?{workspace:{isTrusted:true}}:require(n),setInterval,clearInterval,setTimeout,clearTimeout});
const {parsePrompt,create,publicReply}=loaded.exports;
test('only fresh explicit commands produce source queries; endpoints and credentials never do',()=>{
 assert.equal(parsePrompt('please connect my Samsung').query,'my Samsung');
 assert.equal(parsePrompt('list saved devices').action,'list');
 for(const s of ['do not connect Samsung','"connect Samsung"','how can I connect Samsung?'])assert.equal(parsePrompt(s),undefined);
 for(const s of ['connect https://unknown.invalid/v1','connect token abc','connect a device'])assert.equal(parsePrompt(s).query,'');
});
test('a foreign, expired or unapproved offer cannot even read saved credentials',async()=>{
 let reads=0;const events=[];
 const context={globalState:{get:()=>[]},secrets:{get:()=>{reads++;return 'never-read';}}};
 const manager=create(context,e=>{events.push(e);queueMicrotask(()=>manager.accept({requestId:e.requestId,result:{enabled:true,sources:[]}}));},{workspace:{isTrusted:true}});
 await assert.rejects(manager.run('chat:one',{action:'connect',offerId:'foreign',sourceId:'model:id'}),/approval changed/);
 assert.equal(reads,0);assert.equal(events.length,1);assert.equal(events[0].operation,'device_connection_sync');manager.dispose();
});
test('model requests cannot write owner approval or inject endpoints',async()=>{
 const manager=create({globalState:{get:()=>[]}},()=>{throw Error('should not send');},{workspace:{isTrusted:true}});
 await assert.rejects(manager.run('chat:one',{action:'policy',enabled:true}),/Unsupported/);
 await assert.rejects(manager.run('chat:one',{action:'connect',endpoint:'https://unknown.invalid'}),/Unsupported/);manager.dispose();
});
test('chat choice UI has valid nonce script and uses textContent for results',()=>{
 const html=require('./chat-view.cjs').render('Test','chat');
 assert.match(html,/deviceConnectChoice/);assert.match(html,/device_connection_result/);assert.doesNotMatch(html,/innerHTML/);
 const script=html.match(/<script nonce="[^"]+">([\s\S]*)<\/script>/)[1];new vm.Script(script);
 assert.match(publicReply({offer:{enabled:false,choices:[]}}).text,/off/);
});
