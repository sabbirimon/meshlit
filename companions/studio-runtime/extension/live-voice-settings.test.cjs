'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {validate,build,split,save}=require('./live-voice-settings.cjs');
const preset={mode:'translation',input:'bn',output:'en',style:'natural'};
test('translation preset preserves existing instructions and replaces only its own block',()=>{
 const first=build('Call me Sam.\nKeep answers factual.',preset);assert.match(first.customInstructions,/Bengali/);assert.match(first.customInstructions,/Write the reply in English/);assert.match(first.customInstructions,/Return only the translation/);
 assert.deepEqual(split(first.customInstructions),{base:'Call me Sam.\nKeep answers factual.',preset});
 const second=build(first.customInstructions,{...preset,mode:'conversation',output:'bn'});assert.equal(second.customInstructions.split('[Meshlit live voice preferences]').length,2);assert.match(second.customInstructions,/helpful natural conversation/);assert.match(second.customInstructions,/not speech-engine configuration or tool permissions/);
});
test('invalid fields and truncated or duplicated instruction blocks are rejected',()=>{
 for(const value of [null,{...preset,input:'arbitrary'},{...preset,output:'auto'},{...preset,style:'__proto__'},{...preset,voice:'secret'},{...preset,mode:'capture'}])assert.throws(()=>validate(value));
 assert.throws(()=>build('x'.repeat(1700),preset),/2,000/);
 assert.throws(()=>build('[Meshlit live voice preferences]',preset),/Review/);
 const one=build('',preset).customInstructions;assert.throws(()=>build(one+one,preset),/Review/);
});
test('save changes only supported customInstructions and verifies acknowledgement',async()=>{
 const calls=[];const runtime={request:async(route,body)=>{calls.push({route,body});return body?{...body}:{liveModel:'chosen',customInstructions:'Existing instructions',apiKey:'must not copy'};}};
 const result=await save(runtime,preset);assert.equal(calls.length,2);assert.deepEqual(Object.keys(calls[1].body),['customInstructions']);assert(!JSON.stringify(calls).includes('must not copy'));assert.equal(result.preset.output,'en');
});
test('failed read, rejected write or missing acknowledgement never reports saved',async()=>{
 await assert.rejects(save({request:async()=>{throw Error('Stopped');}},preset),/Stopped/);
 let calls=0;await assert.rejects(save({request:async()=>{if(++calls===1)return {};throw Error('Rejected');}},preset),/Rejected/);
 await assert.rejects(save({request:async()=>({})},preset),/did not confirm/);
 calls=0;await assert.rejects(save({request:async()=>{calls++;return {customInstructions:'x'.repeat(2000)};}},preset),/Shorten/);assert.equal(calls,1);
});

test('voice dashboard labels live transcription boundaries and routes preset saves through real settings contract',async()=>{
 const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
 const commands=new Map(),sent=[],calls=[];let receive,stored={customInstructions:'Preserve my existing instructions'},rejectWrite=false;
 const webview={postMessage:message=>sent.push(message),onDidReceiveMessage:handler=>receive=handler};
 const vscode={ViewColumn:{One:1},commands:{registerCommand:(id,handler)=>{commands.set(id,handler);return {dispose(){}};}},window:{createWebviewPanel:()=>({webview,onDidDispose(){}})}};
 class Runtime{constructor(){this.state='running';}stop(){}async request(route,body){calls.push({route,body});if(body){if(rejectWrite)throw Error('Rejected write');stored={...stored,...body};}return stored;}}
 const module={exports:{}};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'voice.cjs'),'utf8'),{module,require:name=>name==='vscode'?vscode:name==='./voice-runtime.cjs'?{VoiceRuntime:Runtime,KEYS:['customInstructions'],ORIGIN:'http://127.0.0.1:47834'}:name==='./shell.cjs'?{createPanel:()=>({webview,onDidDispose(){}})}:require(name),setInterval:()=>1,clearInterval(){}});
 module.exports.register({subscriptions:[],globalState:{get:()=>'/installed/openlive'}});commands.get('meshlit.voice')();
 assert.match(webview.html,/does not capture audio or display a live transcript/);assert.match(webview.html,/Bengali recognition and spoken output are not qualified/);assert.match(webview.html,/one session language for recognition and synthesis/);
 for(const [,script]of webview.html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))new vm.Script(script);
 await receive({op:'loadLive'});assert.equal(sent.at(-1).livePreset,null);assert.match(sent.at(-1).liveMessage,/draft until you save/);
 await receive({op:'saveLive',preferences:preset});assert.equal(sent.at(-1).livePreset.output,'en');assert.match(stored.customInstructions,/Preserve my existing instructions/);assert.match(sent.at(-1).liveMessage,/Microphone, captions, speech engine and voice settings are unchanged/);
 rejectWrite=true;await receive({op:'saveLive',preferences:{...preset,style:'formal'}});assert.equal(sent.at(-1).liveError,true);assert.match(sent.at(-1).liveMessage,/Rejected/);
 assert(calls.every(call=>call.route==='/api/settings'));assert(calls.filter(call=>call.body).every(call=>Object.keys(call.body).join() === 'customInstructions'));
});
