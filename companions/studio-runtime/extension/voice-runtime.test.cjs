const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),net=require('node:net');
const {VoiceRuntime,tuning}=require('./voice-runtime.cjs');
test('tuning refuses secret/command settings and remote local-server destinations',()=>{
 for(const value of [{apiKey:'secret'},{'acpCommand:codex':'anything'},{ollamaBaseUrl:'https://example.com'},{ollamaBaseUrl:'http://user@127.0.0.1'},{liveEffort:'invented'}])assert.throws(()=>tuning(value));
 assert.deepEqual(tuning({liveModel:'qwen',ollamaBaseUrl:'http://127.0.0.1:8080/v1',liveEffort:'low'}),{liveModel:'qwen',ollamaBaseUrl:'http://127.0.0.1:8080/v1',liveEffort:'low'});
});
test('real child lifecycle, saved API tuning, port conflict, and Stop',async()=>{
 if(process.platform==='win32')return;
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'meshlit-voice-test-')),runtime=new VoiceRuntime();
 const write=(name,text)=>{const target=path.join(root,name);fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,text);};
 write('package.json',JSON.stringify({name:'openlive'}));write('apps/web/public/vad/silero_vad_v6.onnx','fixture');
 write('services/agent/node_modules/tsx/dist/cli.mjs',"import http from 'node:http';http.createServer((q,s)=>s.end('{\"ok\":true}')).listen(47833,'127.0.0.1');");
 write('apps/web/node_modules/next/dist/bin/next',"const http=require('node:http');let settings={liveModel:'before'};http.createServer((q,s)=>{let b='';q.on('data',c=>b+=c);q.on('end',()=>{if(b)Object.assign(settings,JSON.parse(b));s.setHeader('Content-Type','application/json');s.end(JSON.stringify(settings));});}).listen(47834,'127.0.0.1');");
 try{
  await runtime.start(root,process.execPath);assert.equal(runtime.state,'running');
  assert.equal((await runtime.request('/api/settings')).liveModel,'before');
  await runtime.request('/api/settings',{liveModel:'after'});assert.equal((await runtime.request('/api/settings')).liveModel,'after');
  const other=new VoiceRuntime();await assert.rejects(other.start(root,process.execPath),/occupied/);assert.equal(runtime.state,'running');
  runtime.stop();assert.equal(runtime.state,'stopped');await assert.rejects(runtime.request('/api/settings'),/Start/);
 }finally{runtime.stop();fs.rmSync(root,{recursive:true,force:true});}
});
