const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const personality=require('./chat-personality.cjs');
test('personality choices are strict and custom instructions bounded',()=>{
 assert.equal(Object.keys(personality.presets).length,8);
 for(const preset of Object.keys(personality.presets))assert.equal(personality.validate({...personality.defaults,personality:preset}).personality,preset);
 for(const value of [null,[],{}, {...personality.defaults,personality:'constructor'}, {...personality.defaults,contentStyle:'uncensored'}, {...personality.defaults,customInstructions:'x'.repeat(1501)}, {...personality.defaults,customInstructions:'bad\0value'}, {...personality.defaults,token:'secret'}])assert.throws(()=>personality.validate(value));
 assert.equal(personality.validate({...personality.defaults,customInstructions:'Line 1\nLine 2'}).customInstructions,'Line 1\nLine 2');
 assert.deepEqual(personality.read({globalState:{get:()=>({broken:true})}}),personality.defaults);
});
test('personality page escapes instructions and explains local style boundaries',()=>{
 const html=personality.render({...personality.defaults,customInstructions:'</textarea><script>evil()</script>'});
 assert(!html.includes('<script>evil()'));assert(html.includes('&lt;/textarea&gt;'));
 assert.match(html,/not a safety bypass/);assert.match(html,/omitted from external model requests/);assert.match(html,/Do not put passwords/);
 const nonce=html.match(/<script nonce="([^"]+)"/)[1];assert(html.includes("script-src 'nonce-"+nonce+"'"));
});
function fixture(){
 const commands=new Map(),saved=new Map(),panels=[];let fail=false;
 const shell={createPanel:()=>{const panel={sent:[],reveal(){this.revealed=true;},onDidDispose:fn=>panel.close=fn,webview:{postMessage:async message=>panel.sent.push(message),onDidReceiveMessage:fn=>panel.receive=fn}};panels.push(panel);return panel;}};
 const loaded={exports:{}};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'chat-personality.cjs'),'utf8'),{module:loaded,require:name=>name==='./shell.cjs'?shell:require(name)});
 const context={subscriptions:[],globalState:{get:(key,fallback)=>saved.has(key)?saved.get(key):fallback,update:async(key,value)=>{if(fail)throw Error('private backend detail');saved.set(key,value);}}};
 loaded.exports.register(context,{ViewColumn:{One:1},commands:{registerCommand:(id,fn)=>{commands.set(id,fn);return {dispose(){}};}}});
 return {context,panels,saved,setFail:value=>fail=value,open:()=>{commands.get('meshlit.chatPersonality')();return panels.at(-1);}};
}
test('personality saves app preferences and reuses the panel without arbitrary dispatch',async()=>{
 const f=fixture(),panel=f.open(),value={personality:'teacher',customInstructions:'Explain with examples.',contentStyle:'direct'};
 await panel.receive({personalityPage:true,op:'save',values:value});assert.equal(personality.read(f.context).personality,'teacher');assert.match(panel.sent.at(-1).message,/next Chat request/);
 await panel.receive({personalityPage:true,op:'reload'});assert.match(panel.webview.html,/value="teacher" selected/);
 f.open();assert.equal(f.panels.length,1);assert(panel.revealed);
 await panel.receive({personalityPage:true,op:'executeCommand',values:{}});assert.equal(f.saved.size,1);
 panel.close();await panel.receive({personalityPage:true,op:'save',values:personality.defaults});assert.equal(personality.read(f.context).personality,'teacher');
});
test('personality validation and persistence failures do not claim successful changes',async()=>{
 const f=fixture(),panel=f.open();
 await panel.receive({personalityPage:true,op:'save',values:{...personality.defaults,contentStyle:'bypass'}});assert.equal(f.saved.size,0);assert(panel.sent.at(-1).error);
 f.setFail(true);await panel.receive({personalityPage:true,op:'save',values:personality.defaults});assert.equal(f.saved.size,0);assert.match(panel.sent.at(-1).error,/Could not save/);assert(!panel.sent.at(-1).error.includes('private backend'));
});
