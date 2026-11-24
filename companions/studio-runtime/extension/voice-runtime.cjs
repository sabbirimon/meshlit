const fs=require('node:fs'),path=require('node:path'),net=require('node:net'),{spawn}=require('node:child_process');
const REVISION='c9121ebe6ed8217f5b8e7b77c78cb36620c1a252';
const ORIGIN='http://127.0.0.1:47834';
const KEYS=['liveProviderId','liveModel','visionProviderId','visionModel','liveEffort','ollamaBaseUrl','customInstructions'];
function tuning(input){
 if(!input||typeof input!=='object'||Array.isArray(input))throw Error('Invalid settings');
 const out={};for(const [k,v] of Object.entries(input)){
  if(!KEYS.includes(k)||typeof v!=='string'||v.length>(k==='customInstructions'?8000:512))throw Error('Invalid setting: '+k);
  if(k==='liveEffort'&&!['auto','none','minimal','low','medium','high','xhigh'].includes(v))throw Error('Invalid effort');
  if(k==='ollamaBaseUrl'&&v){const u=new URL(v);if(!['http:','https:'].includes(u.protocol)||!['localhost','127.0.0.1','[::1]'].includes(u.hostname)||u.username||u.password||u.search||u.hash||!['','/','/v1','/v1/'].includes(u.pathname))throw Error('Use a local model server URL');}
  out[k]=v;
 }return out;
}
function available(port){return new Promise((resolve,reject)=>{const s=net.createServer();s.once('error',()=>reject(Error('Voice port '+port+' is occupied; stop the existing service yourself.')));s.listen(port,'127.0.0.1',()=>s.close(resolve));});}
class VoiceRuntime{
 constructor({dataHome=path.join(require('node:os').homedir(),'.openlive')}={}){if(dataHome&&!path.isAbsolute(dataHome))throw Error('Voice data directory must be absolute');this.dataHome=dataHome;this.children=[];this.state='stopped';this.generation=0;this.failure='';}
 cancelPreview(){this.previewAbort?.abort();this.previewAbort=undefined;}
 stop(){this.cancelPreview();this.generation++;for(const child of this.children){try{process.kill(-child.pid,'SIGTERM');}catch{}const timer=setTimeout(()=>{try{process.kill(-child.pid,'SIGKILL');}catch{}},1500);timer.unref();}this.children=[];this.state='stopped';}
 async start(root,node){
  if(process.platform==='win32')throw Error('Managed voice startup currently requires macOS or Linux. Use OpenLive desktop on Windows.');
  if(this.state!=='stopped')throw Error('Stop the current voice runtime first');
  if(!path.isAbsolute(root)||!path.isAbsolute(node))throw Error('Select absolute source and Node executable paths');
  const pkg=JSON.parse(fs.readFileSync(path.join(root,'package.json'),'utf8'));
  if(pkg.name!=='openlive')throw Error('Select the installed OpenLive source directory');
  const web=path.join(root,'apps/web'),agent=path.join(root,'services/agent');
  const next=path.join(web,'node_modules/next/dist/bin/next'),tsx=path.join(agent,'node_modules/tsx/dist/cli.mjs');
  for(const file of [node,next,tsx,path.join(web,'public/vad/silero_vad_v6.onnx')])if(!fs.statSync(file).isFile())throw Error('Missing OpenLive dependency or prepared voice assets');
  const generation=++this.generation;this.state='starting';this.failure='';
  try{await available(47834);await available(47833);}catch(e){if(generation===this.generation)this.stop();throw e;}
  if(generation!==this.generation)throw Error('Voice startup stopped');
  const env={...Object.fromEntries(['HOME','PATH','TMPDIR','LANG','LC_ALL'].filter(k=>process.env[k]).map(k=>[k,process.env[k]])),...(this.dataHome?{OPENLIVE_HOME:this.dataHome}:{}),ELECTRON_RUN_AS_NODE:'1',OPENLIVE_TELEMETRY:'0',DO_NOT_TRACK:'1',NEXT_TELEMETRY_DISABLED:'1',AGENT_HOST:'127.0.0.1',AGENT_PORT:'47833',WEB_PORT:'47834',WEB_PUBLIC_URL:ORIGIN,NEXT_PUBLIC_LIVE_WS_URL:'ws://127.0.0.1:47833'};
  const launch=(cwd,args)=>{const child=spawn(node,args,{cwd,env,shell:false,detached:true,stdio:'ignore'});this.children.push(child);child.once('error',()=>{if(generation===this.generation){this.failure='Could not execute the selected Node runtime';this.stop();}});child.once('exit',(code,signal)=>{if(generation===this.generation){this.failure='OpenLive '+path.basename(cwd)+' exited ('+(signal||code)+'). Check the installed dependencies and Node version.';this.stop();}});};
  try{
   launch(agent,[tsx,'src/server.ts']);launch(web,[next,'dev','--hostname','127.0.0.1','--port','47834']);
   const deadline=Date.now()+90000;
   while(Date.now()<deadline){
    if(generation!==this.generation)throw Error(this.failure||'Voice startup stopped');
    try{await this.request('/api/settings');const r=await fetch('http://127.0.0.1:47833/health',{signal:AbortSignal.timeout(1500),redirect:'error'});if(r.ok&&(await r.json()).ok===true){if(generation!==this.generation)throw Error('Voice startup stopped');this.state='running';return;}}catch{}
    await new Promise(r=>setTimeout(r,500));
   }throw Error('Voice startup timed out. Check installed Node and OpenLive dependencies.');
  }catch(e){if(generation===this.generation)this.stop();throw e;}
 }
 async previewSpeech(engine,voice,text){
  if(this.state!=='running')throw Error('Start the voice runtime first');
  if(!/^(kokoro-|pocket-)[A-Za-z0-9_.-]+$/.test(engine)||!/^[A-Za-z0-9_-]{1,80}$/.test(voice)||typeof text!=='string'||!text.trim()||text.length>200)throw Error('Invalid voice preview');
  this.cancelPreview();const controller=new AbortController();this.previewAbort=controller;
  try{
   const response=await fetch(ORIGIN+'/api/voice/tts',{method:'POST',headers:{Origin:ORIGIN,'Content-Type':'application/json'},body:JSON.stringify({engine,voice,text,lang:'en'}),signal:AbortSignal.any([controller.signal,AbortSignal.timeout(180000)]),redirect:'error'});
   if(!response.ok)throw Error('Speech preview HTTP '+response.status+'; install the selected engine in OpenLive');
   if(response.headers.get('content-type')?.split(';')[0]!=='application/octet-stream')throw Error('Expected raw speech PCM');
   const reader=response.body.getReader();const chunks=[];let size=0;
   try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>4*1024*1024)throw Error('Preview exceeds limit');chunks.push(Buffer.from(value));}}finally{await reader.cancel();}
   return require('./natural-audio.cjs').pcmWav(Buffer.concat(chunks),Number(response.headers.get('x-sample-rate')),true).toString('base64');
  }finally{if(this.previewAbort===controller)this.previewAbort=undefined;}
 }
 async request(route,body){
  if(!['starting','running'].includes(this.state))throw Error('Start the voice runtime first');
  if(!['/api/settings','/api/providers','/api/voice/engines'].includes(route))throw Error('Unsupported voice API');
  const response=await fetch(ORIGIN+route,{method:body?'PUT':'GET',headers:{Origin:ORIGIN,'Content-Type':'application/json'},body:body?JSON.stringify(tuning(body)):undefined,signal:AbortSignal.timeout(10000),redirect:'error'});
  if(!response.ok)throw Error('OpenLive rejected the request (HTTP '+response.status+')');
  const reader=response.body.getReader();let size=0;const chunks=[];
  try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>131072)throw Error('Voice response too large');chunks.push(Buffer.from(value));}}finally{await reader.cancel();}
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
 }
}
module.exports={VoiceRuntime,tuning,KEYS,ORIGIN,REVISION};
