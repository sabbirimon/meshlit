const test=require('node:test'),assert=require('node:assert/strict'),{EventEmitter}=require('node:events');
const {create,parseVoices}=require('./chat-voice.cjs');
function fixture(platform='darwin'){
 const commands=new Map(),children=[],errors=[],saved=new Map(),picks=[],inputs=[],external=[],timers=new Map();let seq=0;
 const context={subscriptions:[],globalState:{get:(key,fallback)=>saved.has(key)?saved.get(key):fallback,update:async(key,value)=>saved.set(key,value)}};
 const vscode={commands:{registerCommand:(id,fn)=>{commands.set(id,fn);return {dispose(){}};},executeCommand:async id=>external.push(id)},window:{showErrorMessage:message=>errors.push(message),showInformationMessage:async()=>picks.shift(),showQuickPick:async()=>picks.shift(),showInputBox:async()=>inputs.shift()}};
 const spawn=(executable,args,options)=>{const child=new EventEmitter();child.stdout=new EventEmitter();child.stdin=new EventEmitter();child.stdin.end=value=>child.input=value;child.kill=()=>{child.killed=true;queueMicrotask(()=>child.emit('close',null,'SIGTERM'));};Object.assign(child,{executable,args,options});children.push(child);return child;};
 const controller=create(context,vscode,{platform,spawn,setTimeout:(fn,delay)=>{const id=++seq;timers.set(id,{fn,delay});return id;},clearTimeout:id=>timers.delete(id)});
 return {controller,commands,children,errors,saved,picks,inputs,external,timers,configure:()=>commands.get('meshlit.chatVoice')(),list:output=>{const child=children.at(-1);child.stdout.emit('data',Buffer.from(output));child.emit('close',0);}};
}
const list='Samantha             en_US    # Hello there\nEddy (English (US))  en_US    # Hello\nAmélie               fr_CA    # Bonjour\n';
const next=()=>new Promise(resolve=>setImmediate(resolve));
test('voice parser accepts installed names and locales while rejecting option-like or malformed entries',()=>{
 assert.deepEqual(parseVoices(list).map(v=>v.id),['Samantha','Eddy (English (US))','Amélie']);
 assert.deepEqual(parseVoices('-o /tmp/private en_US # injected\nvoice;cmd en_US # injected\nSamantha en_US # Hello\nSamantha en_US # repeated').map(v=>v.id),['Samantha']);
 assert.throws(()=>parseVoices('x'.repeat(65537)),/size/);
});
test('read aloud sends text only through stdin and stops only the owned playback',async()=>{
 const f=fixture();const text='Hello $(touch /tmp/not-run) `command` --output secret';
 const pending=f.controller.speak(text,'chat-one');const child=f.children[0];
 assert.equal(child.executable,'/usr/bin/say');assert.equal(child.options.shell,false);assert.deepEqual(child.args,['-r','180']);assert.equal(child.input,text);assert(!JSON.stringify(child.args).includes(text));
 assert.equal(f.controller.stop('chat-two'),false);assert(!child.killed);
 assert.equal(f.controller.stop('chat-one'),true);assert(child.killed);assert.equal((await pending).status,'stopped');assert.equal(f.errors.length,0);f.controller.dispose();
});
test('new speech cancels prior speech and reports completion only after the process exits',async()=>{
 const f=fixture();const first=f.controller.speak('First message','one');const second=f.controller.speak('Second message','two');
 assert(f.children[0].killed);assert.equal((await first).status,'stopped');
 let completed=false;second.then(()=>completed=true);await next();assert.equal(completed,false);
 f.children[1].emit('close',0);assert.equal((await second).status,'completed');assert.equal(f.timers.size,0);f.controller.dispose();
});
test('voice and rate settings persist and selected voices must remain installed',async()=>{
 const f=fixture();f.picks.push({id:'voice'},{id:'Amélie'});const configuring=f.configure();await next();
 const bytes=Buffer.from(list),accent=bytes.indexOf(Buffer.from('é'));
 f.children[0].stdout.emit('data',bytes.subarray(0,accent+1));f.children[0].stdout.emit('data',bytes.subarray(accent+1));f.children[0].emit('close',0);await configuring;
 assert.deepEqual(f.saved.get('meshlit.chatVoice'),{voice:'Amélie',rate:180});
 f.picks.push({id:'rate'});f.inputs.push('220');await f.configure();assert.equal(f.saved.get('meshlit.chatVoice').rate,220);
 const playback=f.controller.speak('Bonjour','one');f.list(list);await next();const speech=f.children.at(-1);assert.deepEqual(speech.args,['-r','220','-v','Amélie']);speech.emit('close',0);await playback;
 const missing=f.controller.speak('Bonjour','one'),reject=assert.rejects(missing,/no longer installed/);f.list('Samantha en_US # Hello');await reject;assert.equal(f.errors.length,1);
 f.picks.push({id:'default'});await f.configure();assert.deepEqual(f.saved.get('meshlit.chatVoice'),{voice:'',rate:180});f.controller.dispose();
});
test('catalog timeout, process errors and invalid settings fail visibly without claiming playback',async()=>{
 const f=fixture();f.saved.set('meshlit.chatVoice',{voice:'Samantha',rate:180});
 const pending=f.controller.speak('Hello','one'),rejected=assert.rejects(pending,/timed out/);[...f.timers.values()].find(t=>t.delay===8000).fn();await rejected;assert(f.children[0].killed);
 f.saved.set('meshlit.chatVoice',{voice:'',rate:180});const failed=f.controller.speak('Hello','one'),failure=assert.rejects(failed,/could not start/);f.children.at(-1).emit('error',Error('native error'));await failure;
 const broken=f.controller.speak('Hello','one'),brokenFailure=assert.rejects(broken,/accept the message/);f.children.at(-1).stdin.emit('error',Error('EPIPE'));await brokenFailure;
 const count=f.children.length;f.saved.set('meshlit.chatVoice',{voice:'--output',rate:180});await assert.rejects(f.controller.speak('Hello','one'),/settings are invalid/);assert.equal(f.children.length,count);
 assert.equal(f.errors.length,4);f.controller.dispose();
});
test('cancel during voice lookup cannot launch speech later and disposal owns catalog cleanup',async()=>{
 const f=fixture();f.saved.set('meshlit.chatVoice',{voice:'Samantha',rate:180});
 const pending=f.controller.speak('Hello','one');f.controller.stop('one');assert.equal((await pending).status,'stopped');f.list(list);await next();assert.equal(f.children.length,1);
 f.picks.push({id:'voice'});const configuring=f.configure();await next();f.controller.dispose();await configuring;assert(f.children.at(-1).killed);assert.equal(f.timers.size,0);
});
test('unsupported platforms offer OpenLive setup and never spawn local speech',async()=>{
 const f=fixture('linux');await assert.rejects(f.controller.speak('Hello','one'),/macOS/);f.picks.push('Open Voice and vision');await f.configure();assert.deepEqual(f.external,['meshlit.voice']);assert.equal(f.children.length,0);f.controller.dispose();
});
test('empty and oversized messages and out-of-range rate edits cannot execute',async()=>{
 const f=fixture();await assert.rejects(f.controller.speak('','one'));await assert.rejects(f.controller.speak('x'.repeat(6001),'one'));assert.equal(f.children.length,0);
 f.picks.push({id:'rate'});f.inputs.push('301');await f.configure();assert.equal(f.saved.has('meshlit.chatVoice'),false);assert.match(f.errors.at(-1),/100 to 300/);f.controller.dispose();
});
