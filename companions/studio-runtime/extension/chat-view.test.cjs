const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm');
const {render}=require('./chat-view.cjs');
function fixture(unlocked=true){
 const html=render('Chat <unsafe>','chat'),elements=new Map(),sent=[],listeners={};
 const element=()=>({value:'',textContent:'',children:[],handlers:{},className:'',disabled:false,append(x){this.children.push(x);},addEventListener(k,f){this.handlers[k]=f;}});
 const get=id=>{if(!elements.has(id))elements.set(id,element());return elements.get(id);};
 const script=html.match(/<script nonce="[^"]+">([\s\S]*?)<\/script>/)[1];
 vm.runInNewContext(script,{acquireVsCodeApi:()=>({postMessage:m=>sent.push(m)}),document:{getElementById:get,createElement:element},window:{addEventListener:(k,f)=>listeners[k]=f}});
 if(unlocked)listeners.message({data:{type:'runtime',ready:true,unlocked:true,localModel:{state:'ready',label:'Bundled fixture'}}});
 return {html,get,sent,receive:e=>listeners.message({data:e}),key:extra=>{let prevented=false;get('prompt').handlers.keydown({key:'Enter',preventDefault(){prevented=true;},...extra});return prevented;}};
}
test('Enter submits once, Shift+Enter and composing input do not submit',()=>{
 const f=fixture();f.get('prompt').value='বাংলা message';
 for(const e of [{shiftKey:true},{isComposing:true},{keyCode:229},{ctrlKey:true},{altKey:true},{metaKey:true}])assert.equal(f.key(e),false);
 assert.equal(f.sent.filter(m=>m.op==='send').length,0);
 assert.equal(f.key({}),true);assert.equal(f.sent.filter(m=>m.op==='send').length,1);
 f.get('prompt').value='second';f.key({});assert.equal(f.sent.filter(m=>m.op==='send').length,1);
 f.receive({type:'done'});f.key({});assert.equal(f.sent.filter(m=>m.op==='send').length,2);
});
test('model/mode state stays visible and concurrent new chat remains enabled',()=>{
 const f=fixture();f.receive({type:'configuration',mode:'plan',label:'OmniRoute · selected-model',endpoint:'http://127.0.0.1:20128/v1',started:false});
 assert.equal(f.get('mode').textContent,'Plan ▾');assert.match(f.get('route').textContent,/20128/);
 f.get('prompt').value='plan';f.key({});assert.equal(f.get('mode').disabled,true);assert.equal(f.get('connection').disabled,true);
 f.get('newChat').onclick();assert.equal(f.sent.at(-1).op,'newChat');
 f.receive({type:'done'});assert.equal(f.get('connection').disabled,false);
 f.get('stop').onclick();f.get('prompt').value='blocked';f.key({});assert.equal(f.sent.at(-1).op,'stop');
 assert.match(f.html,/Chat &lt;unsafe&gt;/);assert.match(f.html,/aria-label="Send message"/);
});

test('locked usage prevents optimistic messages and offers explicit unlock',()=>{
 const f=fixture(false);f.receive({type:'runtime',ready:true,unlocked:false,localModel:{state:'ready',label:'Bundled fixture'}});
 f.get('prompt').value='hello';f.key({});
 assert.equal(f.sent.filter(m=>m.op==='send').length,0);
 assert.equal(f.get('history').children.length,0);
 assert.equal(f.get('send').disabled,true);assert.equal(f.get('unlock').hidden,false);
 f.get('unlock').onclick();assert.equal(f.sent.at(-1).op,'unlock');
 f.receive({type:'runtime',ready:true,unlocked:true,localModel:{state:'ready',label:'Bundled fixture'}});f.key({});
 assert.equal(f.sent.at(-1).op,'send');assert.equal(f.get('unlock').hidden,true);
 assert.match(f.html,/margin:10px 0 10px auto/);
});

test('default local model blocks send until real model-ready event',()=>{
 const f=fixture(false);f.receive({type:'runtime',ready:true,unlocked:true,localModel:{state:'loading'}});
 f.get('prompt').value='hello';f.key({});assert.equal(f.sent.filter(m=>m.op==='send').length,0);
 assert.equal(f.get('send').disabled,true);
 f.receive({type:'runtime',ready:true,unlocked:true,localModel:{state:'error',message:'Model missing'}});
 assert.equal(f.get('loadLocal').hidden,false);assert.equal(f.get('localStatus').textContent,'Model missing');
 f.receive({type:'runtime',ready:true,unlocked:true,localModel:{state:'ready',label:'Qwen fixture'}});
 f.key({});assert.equal(f.sent.at(-1).op,'send');
});
