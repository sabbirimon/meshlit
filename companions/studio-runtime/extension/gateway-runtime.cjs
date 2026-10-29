// Explicitly started, separately owned upstream services. No installer or shell hooks.
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const net=require('node:net');
const http=require('node:http');
const {spawn}=require('node:child_process');
const SERVICES={
 OmniRoute:{version:'3.8.52',metadata:'package.json',entry:'dist/server.js',port:20128},
 FreeLLMAPI:{version:'0.2.1',metadata:'server/package.json',entry:'server/dist/index.js',port:31415}
};
function privateDirectory(directory){
 fs.mkdirSync(directory,{recursive:true,mode:0o700});
 const st=fs.lstatSync(directory);
 if(st.isSymbolicLink()||!st.isDirectory()||(process.getuid&&st.uid!==process.getuid()))throw Error('Gateway storage must be an owned directory');
 if(process.platform!=='win32')fs.chmodSync(directory,0o700);
}
function secretsFor(home){
 const file=path.join(home,'runtime-secrets.json');
 try{fs.writeFileSync(file,JSON.stringify(Object.fromEntries(['JWT_SECRET','API_KEY_SECRET','STORAGE_ENCRYPTION_KEY','ENCRYPTION_KEY'].map(k=>[k,crypto.randomBytes(32).toString('hex')]))),{mode:0o600,flag:'wx'});}catch(e){if(e.code!=='EEXIST')throw e;}
 const st=fs.lstatSync(file);
 if(st.isSymbolicLink()||!st.isFile()||st.size>2048||(process.getuid&&st.uid!==process.getuid())||(process.platform!=='win32'&&(st.mode&0o077)))throw Error('Gateway secret storage is not private');
 const values=JSON.parse(fs.readFileSync(file,'utf8'));
 for(const k of ['JWT_SECRET','API_KEY_SECRET','STORAGE_ENCRYPTION_KEY','ENCRYPTION_KEY'])if(!/^[a-f0-9]{64}$/.test(values[k]))throw Error('Invalid gateway secret material');
 return values;
}
function environment(service,home,root,node,port,secrets){
 const env={PATH:path.dirname(node)+(process.platform==='win32'?';':':')+(process.platform==='win32'?(process.env.SystemRoot||'C:\\Windows'):'/usr/bin:/bin'),HOME:home,USERPROFILE:home,TMPDIR:path.join(home,'tmp'),TMP:path.join(home,'tmp'),TEMP:path.join(home,'tmp'),LANG:'en_US.UTF-8',NODE_ENV:'production',HOST:'127.0.0.1',HOSTNAME:'127.0.0.1',PORT:String(port)};
 if(process.platform==='win32')env.SystemRoot=process.env.SystemRoot;
 if(service==='OmniRoute')Object.assign(env,{
  DATA_DIR:path.join(home,'data'),OMNIROUTE_DATA_DIR:path.join(home,'data'),INITIAL_PASSWORD:'',
  JWT_SECRET:secrets.JWT_SECRET,API_KEY_SECRET:secrets.API_KEY_SECRET,STORAGE_ENCRYPTION_KEY:secrets.STORAGE_ENCRYPTION_KEY,
  OMNIROUTE_DISABLE_BACKGROUND_SERVICES:'true',OMNIROUTE_CONTEXT_KEYCHAIN_DISABLED:'true',
  OMNIROUTE_DISABLE_CLI_TOKEN:'true',OMNIROUTE_DISABLE_CONVERSATION_TRACKING:'true',NEXT_TELEMETRY_DISABLED:'1',
  AWS_EC2_METADATA_DISABLED:'true',MODELS_DEV_SYNC_ENABLED:'false',PRICING_SYNC_ENABLED:'false',FREE_PROXY_AUTO_SYNC_ENABLED:'false'
 });
 else Object.assign(env,{
  FREEAPI_DB_PATH:path.join(home,'data','freeapi.db'),FREEAPI_ENV_PATH:path.join(home,'unused.env'),
  CLIENT_DIST:path.join(root,'client','dist'),ENCRYPTION_KEY:secrets.ENCRYPTION_KEY,
  CATALOG_SYNC_DISABLED:'1',COOLDOWN_PROBE_DISABLED:'1',MODEL_PROBE_ENABLED:'0',BUILTIN_MODEL_DISCOVERY:'off',
  ALLOWED_HOSTS:'127.0.0.1',DASHBOARD_ORIGINS:`http://127.0.0.1:${port}`
 });
 return env;
}
async function vacant(port){await new Promise((resolve,reject)=>{const s=net.createServer();s.once('error',()=>reject(Error('Gateway port is already in use. Stop that service or use its existing dashboard.')));s.listen(port,'127.0.0.1',()=>s.close(resolve));});}
function reachable(port){return new Promise(resolve=>{const request=http.get({host:'127.0.0.1',port,path:'/',timeout:1500},res=>{res.resume();resolve(res.statusCode>=200&&res.statusCode<400);});request.on('timeout',()=>request.destroy());request.on('error',()=>resolve(false));});}
class GatewayRuntime{
 constructor(config,storage){this.config=config||{};this.storage=storage;this.jobs=new Map();this.states=new Map();this.listeners=new Set();}
 onChange(fn){this.listeners.add(fn);return{dispose:()=>this.listeners.delete(fn)};}
 status(service){return this.states.get(service)||{state:this.config[service]?'stopped':'unavailable'};}
 update(service,value){this.states.set(service,value);for(const fn of this.listeners)fn(service,value);}
 async start(service){
  if(this.jobs.has(service))return this.status(service);
  const spec=SERVICES[service],installed=this.config[service];
  if(!spec||!installed)throw Error('This build does not include this gateway runtime. An existing dashboard can still be connected.');
  const job={};this.jobs.set(service,job);this.update(service,{state:'starting'});
  try{
   const root=path.resolve(installed.root),node=path.resolve(installed.node),entry=path.join(root,spec.entry);
   const metadata=JSON.parse(fs.readFileSync(path.join(root,spec.metadata),'utf8'));
   if(metadata.version!==spec.version||!fs.statSync(node).isFile())throw Error('Gateway runtime differs from the reviewed version');
   if(!/^[a-f0-9]{64}$/.test(installed.sha256)||crypto.createHash('sha256').update(fs.readFileSync(entry)).digest('hex')!==installed.sha256)throw Error('Gateway launcher checksum mismatch');
   // Do not read another installation's provider keys through dotenv.
   for(const candidate of ['.env','.env.local','.env.production','.env.production.local'])if(fs.existsSync(path.join(root,candidate))||fs.existsSync(path.join(root,'dist',candidate)))throw Error('Use a clean packaged runtime without private environment files');
   const home=path.join(this.storage,service.toLowerCase());privateDirectory(home);privateDirectory(path.join(home,'data'));privateDirectory(path.join(home,'tmp'));
   await vacant(spec.port);
   if(job.stopping)throw Error('Gateway start cancelled');
   const child=spawn(node,['--max-old-space-size=768',entry],{cwd:root,env:environment(service,home,root,node,spec.port,secretsFor(home)),stdio:'ignore',windowsHide:true});job.child=child;
   const onExit=()=>{if(!job.exited)child.kill('SIGTERM');};process.once('exit',onExit);
   child.once('error',()=>{job.failed=true;process.removeListener('exit',onExit);});
   child.once('exit',()=>{process.removeListener('exit',onExit);job.exited=true;if(this.jobs.get(service)===job){this.jobs.delete(service);this.update(service,{state:job.stopping?'stopped':'error',...(job.stopping?{}:{message:'Gateway exited. Check its packaged dependencies; your saved gateway data is retained.'})});}});
   for(let i=0;i<120;i++){
    if(job.failed||job.exited||job.stopping)throw Error('Gateway could not start');
    if(await reachable(spec.port)){
     if(job.exited||job.stopping)throw Error('Gateway stopped during startup');
     this.update(service,{state:'running',url:`http://127.0.0.1:${spec.port}/`,api:`http://127.0.0.1:${spec.port}/v1`,version:spec.version});return this.status(service);
    }
    await new Promise(r=>setTimeout(r,250));
   }
   throw Error('Gateway startup timed out');
  }catch(error){const cancelled=job.stopping;if(this.jobs.get(service)===job)await this.stop(service);if(!this.jobs.has(service)&&!cancelled)this.update(service,{state:'error',message:error.message});throw error;}
 }
 async stop(service){
  const job=this.jobs.get(service);if(!job){this.update(service,{state:this.config[service]?'stopped':'unavailable'});return;}
  job.stopping=true;
  if(job.child&&!job.exited){
   job.child.kill('SIGTERM');
   await new Promise(resolve=>{const timer=setTimeout(()=>{if(!job.exited)job.child.kill('SIGKILL');resolve();},5000);job.child.once('exit',()=>{clearTimeout(timer);resolve();});});
  }
  if(this.jobs.get(service)===job)this.jobs.delete(service);
  this.update(service,{state:'stopped'});
 }
 dispose(){for(const [service] of this.jobs)this.stop(service).catch(()=>{});this.listeners.clear();}
}
module.exports={GatewayRuntime,SERVICES,environment,secretsFor,privateDirectory};
