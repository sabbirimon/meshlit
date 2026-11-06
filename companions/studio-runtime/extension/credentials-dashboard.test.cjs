const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {render,tokenValid}=require('./credentials-dashboard.cjs');
const profile={id:'connection-1',kind:'compatible',service:'Owner API',endpoint:'https://models.example/v1',model:'qwen',requiresKey:true};
const secretKey='meshlit.model.connection-1',secret='CANARY_PRIVATE_KEY_DO_NOT_RENDER';
function fixture({persistentSecrets=false,missingStorage=false}={}){
 const profiles=[{...profile}],secrets=new Map([[secretKey,secret]]),secretCalls=[],commands=new Map(),calls=[],panels=[],inputs=[],confirmations=[],inputOptions=[],writes=[];
 let getFailure=false,storeFailure=false,removeFailure=false,getHook,storeHook,inputHook;
 const context={subscriptions:[],globalState:{get:(key,fallback)=>key==='meshlit.connections'?profiles:fallback,update:async(key,value)=>writes.push({key,value})},secrets:missingStorage?undefined:{get:async key=>{secretCalls.push(['get',key]);getHook?.();if(getFailure)throw Error(secret);return secrets.get(key);},store:async(key,value)=>{secretCalls.push(['store',key]);if(storeFailure)throw Error(secret);secrets.set(key,value);storeHook?.();},delete:async key=>{secretCalls.push(['delete',key]);if(removeFailure)throw Error(secret);secrets.delete(key);}}};
 const vscode={ViewColumn:{One:1},commands:{registerCommand:(id,fn)=>{commands.set(id,fn);return {dispose(){}};},executeCommand:async(...args)=>calls.push(args)},window:{showInputBox:async options=>{inputOptions.push(options);inputHook?.();return inputs.shift();},showWarningMessage:async()=>confirmations.shift()}};
 const shell={createPanel:()=>{const panel={sent:[],webview:{postMessage:async message=>panel.sent.push(message),onDidReceiveMessage:handler=>panel.receive=handler},reveal(){panel.revealed=true;},onDidDispose:handler=>panel.close=handler};panels.push(panel);return panel;}};
 const endpoint=value=>{const url=new URL(value);if(url.username||url.password||url.search||url.hash||url.protocol!=='https:'&&!(url.protocol==='http:'&&url.hostname==='127.0.0.1'))throw Error('Invalid endpoint');return url.toString().replace(/\/$/,'');};
 const loaded={exports:{}};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'credentials-dashboard.cjs'),'utf8'),{module:loaded,require:name=>name==='./shell.cjs'?shell:name==='./connections.cjs'?{endpoint}:require(name)});
 loaded.exports.register(context,vscode,{persistentSecrets});
 return {profiles,secrets,secretCalls,commands,calls,panels,inputs,confirmations,inputOptions,writes,setFailures:values=>{getFailure=!!values.get;storeFailure=!!values.store;removeFailure=!!values.remove;},setGetHook:value=>getHook=value,setStoreHook:value=>storeHook=value,setInputHook:value=>inputHook=value,open:()=>{commands.get('meshlit.credentialsDashboard')();return panels.at(-1);}};
}
test('opening credentials reads no keys and labels storage and unavailable hardware protection honestly',()=>{
 const f=fixture(),panel=f.open();assert.equal(f.secretCalls.length,0);assert(panel.webview.html.includes('Session-only storage'));assert(panel.webview.html.includes('do not survive app restart'));assert(panel.webview.html.includes('Not checked'));assert(!panel.webview.html.includes(secret));assert(panel.webview.html.includes('Full-app lock and biometric unlock'));
 assert(!panel.webview.html.includes('type="checkbox"'));assert(render([],{persistentSecrets:true}).includes('does not establish hardware-backed protection'));
 const missing=fixture({missingStorage:true}).open();assert(missing.webview.html.includes('Credential storage APIs are unavailable'));assert(missing.webview.html.includes('data-op="rotate" data-id="connection-1" disabled'));
 const nonce=panel.webview.html.match(/<script nonce="([^"]+)"/)[1];assert(panel.webview.html.includes("script-src 'nonce-"+nonce+"'"));
});
test('explicit presence check reports only a boolean-like availability and never the key',async()=>{
 const f=fixture(),panel=f.open();await panel.receive({credentialsPage:true,op:'check',id:profile.id});assert.equal(f.secretCalls.length,1);assert(panel.webview.html.includes('Key available'));
 assert(!JSON.stringify({html:panel.webview.html,messages:panel.sent,writes:f.writes}).includes(secret));
 f.setGetHook(()=>f.profiles[0].endpoint='https://changed.example/v1');await panel.receive({credentialsPage:true,op:'check',id:profile.id});assert(panel.sent.at(-1).error);assert(!panel.sent.at(-1).error.includes(secret));
});
test('replacement uses password input and secret storage only, with exact-source session revocation',async()=>{
 const f=fixture(),panel=f.open();const replacement='REPLACEMENT_CANARY';f.inputs.push(replacement);await panel.receive({credentialsPage:true,op:'rotate',id:profile.id});
 assert.equal(f.inputOptions[0].password,true);assert.equal(f.inputOptions[0].value,undefined);assert.equal(f.secrets.get(secretKey),replacement);assert.deepEqual(f.calls,[['meshlit.revokeConnection',profile.id]]);assert.equal(f.writes.length,0);
 assert.match(panel.sent.at(-1).message,/Stored key replaced/);assert(!JSON.stringify({html:panel.webview.html,messages:panel.sent,writes:f.writes}).includes(replacement));
 assert(!tokenValid(''));assert(!tokenValid('x'.repeat(8193)));assert(!tokenValid('bad\nkey'));assert(tokenValid('ordinary-key'));
});
test('cancelled or changed replacement prompts cannot write or revoke credentials',async()=>{
 const f=fixture(),panel=f.open();await panel.receive({credentialsPage:true,op:'rotate',id:profile.id});assert.equal(f.secretCalls.length,0);assert.equal(f.calls.length,0);
 f.inputs.push('new-key');f.setInputHook(()=>f.profiles[0].endpoint='https://changed.example/v1');await panel.receive({credentialsPage:true,op:'rotate',id:profile.id});assert.equal(f.secretCalls.length,0);assert.equal(f.calls.length,0);assert(panel.sent.at(-1).error);
 f.setInputHook(()=>panel.close());f.inputs.push('later-key');await panel.receive({credentialsPage:true,op:'rotate',id:profile.id});assert.equal(f.secretCalls.length,0);
});
test('a source changing during storage causes cleanup instead of a success report',async()=>{
 const f=fixture(),panel=f.open();f.inputs.push('new-key');f.setStoreHook(()=>f.profiles[0].endpoint='https://changed.example/v1');await panel.receive({credentialsPage:true,op:'rotate',id:profile.id});
 assert.deepEqual(f.secretCalls.map(row=>row[0]),['store','delete']);assert(!f.secrets.has(secretKey));assert(panel.sent.at(-1).error);assert(!panel.sent.some(message=>message.message?.includes('Stored key replaced')));
});
test('removal requires confirmation and storage failures do not expose diagnostics or claim success',async()=>{
 const f=fixture(),panel=f.open();await panel.receive({credentialsPage:true,op:'remove',id:profile.id});assert.equal(f.secretCalls.length,0);assert.equal(f.calls.length,0);
 f.confirmations.push('Remove stored key');f.setFailures({remove:true});await panel.receive({credentialsPage:true,op:'remove',id:profile.id});assert.equal(f.secrets.get(secretKey),secret);assert(panel.sent.at(-1).error);assert(!panel.sent.at(-1).error.includes(secret));
 f.inputs.push('new-key');f.setFailures({store:true});await panel.receive({credentialsPage:true,op:'rotate',id:profile.id});assert.equal(f.secrets.get(secretKey),secret);assert(panel.sent.at(-1).error);
 f.setFailures({});f.confirmations.push('Remove stored key');await panel.receive({credentialsPage:true,op:'remove',id:profile.id});assert(!f.secrets.has(secretKey));assert.match(panel.sent.at(-1).message,/Stored key removed/);
});
test('invalid endpoints, missing storage and arbitrary navigation cannot dispatch credential operations',async()=>{
 const f=fixture();f.profiles[0].endpoint='https://user:URL_SECRET@models.example/v1';const panel=f.open();assert(!panel.webview.html.includes('URL_SECRET'));assert(panel.webview.html.includes('Endpoint needs review'));
 await panel.receive({credentialsPage:true,op:'check',id:profile.id});assert.equal(f.secretCalls.length,0);assert(panel.sent.at(-1).error);
 for(const id of ['constructor','__proto__','workbench.action.terminal.new'])await panel.receive({credentialsPage:true,op:'link',id});assert.equal(f.calls.length,0);
 await panel.receive({credentialsPage:true,op:'link',id:'ssh'});assert.deepEqual(f.calls,[['meshlit.sshDashboard']]);
 const unavailable=fixture({missingStorage:true}),disabled=unavailable.open();await disabled.receive({credentialsPage:true,op:'check',id:profile.id});assert(disabled.sent.at(-1).error);
});
