const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm');
const {render}=require('./chat-view.cjs');
function fixture(){
 const html=render('Chat <unsafe>','chat'),elements=new Map(),sent=[],listeners={};
 const element=()=>({value:'',textContent:'',children:[],handlers:{},className:'',disabled:false,append(x){this.children.push(x);},addEventListener(k,f){this.handlers[k]=f;}});
 const get=id=>{if(!elements.has(id))elements.set(id,element());return elements.get(id);};
 const script=html.match(/<script nonce="[^"]+">([\s\S]*?)<\/script>/)[1];
 vm.runInNewContext(script,{acquireVsCodeApi:()=>({postMessage:m=>sent.push(m)}),document:{getElementById:get,createElement:element},window:{addEventListener:(k,f)=>listeners[k]=f}});
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
