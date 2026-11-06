const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm');
const {render,exportMessages}=require('./chat-view.cjs');
function fixture(unlocked=true){
 const html=render('Chat <unsafe>','chat'),elements=new Map(),sent=[],listeners={};
 const element=()=>({value:'',textContent:'',children:[],handlers:{},dataset:{},className:'',disabled:false,append(x){x.parentElement=this;this.children.push(x);},setAttribute(k,v){this[k]=v;},focus(){},querySelectorAll(selector){return this.children.flatMap(child=>[...((selector==='[data-reply]'&&Object.hasOwn(child.dataset,'reply')||selector==='[data-message-action]'&&Object.hasOwn(child.dataset,'messageAction'))?[child]:[]),...child.querySelectorAll(selector)]);},addEventListener(k,f){this.handlers[k]=f;}});
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

test('completed assistant file references offer scoped native file actions',()=>{
 const f=fixture();
 f.receive({type:'delta',text:'See [Guide](<docs/My Guide.md:9>) and [website](https://example.test/readme.md).'});
 assert.equal(f.get('history').children[0].children.length,2);
 f.receive({type:'done'});
 const files=f.get('history').children[0].children[2].children;
 assert.equal(files.length,1);
 assert.equal(files[0].textContent,'Guide');
 assert.deepEqual(JSON.parse(files[0].dataset.vscodeContext),{webviewSection:'meshlitFile',meshlitSession:'',meshlitReference:'docs/My Guide.md:9',preventDefaultContextMenuItems:true});
 files[0].onclick();
 assert.equal(f.sent.at(-1).op,'workspaceFile');
 assert.equal(f.sent.at(-1).reference,'docs/My Guide.md:9');
});

test('passwordless session enables local chat but leaves hosted accounting protected',()=>{
 const f=fixture(false);f.receive({type:'runtime',ready:true,unlocked:true,usageMode:'session',localModel:{state:'ready'}});
 assert.equal(f.get('unlock').hidden,true);assert.equal(f.get('send').disabled,false);
 f.receive({type:'configuration',mode:'chat',label:'Remote',endpoint:'https://example.test',local:false});
 assert.equal(f.get('send').disabled,true);assert.equal(f.get('unlock').hidden,false);
 f.get('prompt').value='hello';f.key({});assert.equal(f.sent.filter(m=>m.op==='send').length,0);
 f.receive({type:'runtime',ready:true,unlocked:true,usageMode:'protected',localModel:{state:'ready'}});
 f.key({});assert.equal(f.sent.at(-1).op,'send');
});

test('message actions copy exact text, quote safely and wait for host acknowledgement',()=>{
 const f=fixture();f.get('prompt').value='Hello <unsafe>';f.key({});f.receive({type:'delta',text:'Answer <script>not executable</script>'});f.receive({type:'done'});
 const user=f.get('history').children[0],assistant=f.get('history').children[1];
 assert.deepEqual(user.children[1].children.map(button=>button.textContent),['Copy','Reply']);
 assert.deepEqual(assistant.children[1].children.map(button=>button.textContent),['Copy','Reply','Read aloud']);
 assistant.children[1].children[0].onclick();assert.equal(f.sent.at(-1).op,'copyMessage');assert.equal(f.sent.at(-1).text,'Answer <script>not executable</script>');assert.equal(f.get('messageActionStatus').textContent,'');
 f.receive({type:'messageAction',message:'Copied message.'});assert.equal(f.get('messageActionStatus').textContent,'Copied message.');
 user.children[1].children[1].onclick();assert.match(f.get('prompt').value,/> You:/);assert.match(f.get('prompt').value,/Hello <unsafe>/);
 assert.match(f.html,/user-select:text/);assert.match(f.html,/Nothing is published automatically/);
});
test('local export includes message roles and bodies without action labels, and blocks empty or running chats',()=>{
 const f=fixture();assert.equal(f.get('exportChat').disabled,true);f.get('exportChat').onclick();assert.equal(f.sent.filter(message=>message.op==='exportChat').length,0);
 f.get('prompt').value='Question';f.key({});f.get('exportChat').onclick();assert.equal(f.sent.filter(message=>message.op==='exportChat').length,0);
 f.receive({type:'delta',text:'An answer'});f.receive({type:'done'});assert.equal(f.get('exportChat').disabled,false);f.get('exportChat').onclick();
 assert.deepEqual(JSON.parse(JSON.stringify(f.sent.at(-1))),{op:'exportChat',messages:[{role:'user',text:'Question'},{role:'assistant',text:'An answer'}]});
});
test('read aloud and speech stop are explicit, with host-controlled speech status',()=>{
 const f=fixture();f.get('prompt').value='Question';f.key({});f.receive({type:'delta',text:'Speak this.'});
 const read=f.get('history').children[1].children[1].children[2];assert.equal(read.disabled,true);read.onclick();assert.equal(f.sent.filter(message=>message.op==='readAloud').length,0);
 f.receive({type:'done'});assert.equal(read.disabled,false);read.onclick();assert.equal(f.sent.at(-1).op,'readAloud');assert.equal(f.sent.at(-1).text,'Speak this.');assert.equal(f.get('stopSpeech').disabled,true);
 f.receive({type:'speech',speaking:true,message:'Reading aloud'});assert.equal(f.get('stopSpeech').disabled,false);f.get('stopSpeech').onclick();assert.equal(f.sent.at(-1).op,'stopSpeech');
 f.receive({type:'speech',speaking:false});assert.equal(f.get('stopSpeech').disabled,true);
});
test('export validation rejects oversized or unknown roles and skips empty placeholder replies',()=>{
 assert.deepEqual(exportMessages([{role:'user',text:'A'},{role:'assistant',text:''}]),[{role:'user',text:'A'}]);
 for(const input of [[],[{role:'system',text:'hidden'}],[{role:'assistant',text:1}],[{role:'user',text:'x'.repeat(131073)}],Array.from({length:201},()=>({role:'user',text:'A'})),Array.from({length:9},()=>({role:'assistant',text:'x'.repeat(131072)}))])assert.throws(()=>exportMessages(input));
});
