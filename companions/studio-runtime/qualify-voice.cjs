// Real OpenLive smoke test. Uses a disposable data store; never generates or captures media.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {execFileSync}=require('node:child_process');
const {VoiceRuntime,REVISION,ORIGIN}=require('./extension/voice-runtime.cjs');
async function main(){
 const [root,node=process.execPath,output]=process.argv.slice(2);
 if(!root)throw Error('Usage: node qualify-voice.cjs /absolute/openlive [/absolute/node] [evidence.json]');
 const observedRevision=execFileSync('git',['-C',root,'rev-parse','HEAD'],{encoding:'utf8',timeout:10000}).trim();
 if(observedRevision!==REVISION)throw Error('Qualification requires the reviewed OpenLive revision');
 const dataHome=fs.mkdtempSync(path.join(os.tmpdir(),'meshlit-openlive-qualification-'));
 const runtime=new VoiceRuntime({dataHome});const evidence={date:new Date().toISOString(),reviewedRevision:REVISION,observedRevision,platform:process.platform,architecture:process.arch,nodeVersion:process.version,checks:{},limits:['No microphone, camera, screen, speech-model, LLM or hosted-provider execution.','Separate disposable OpenLive data store; no existing settings modified.']};
 const start=Date.now();let failed;
 try{
  await runtime.start(root,node);evidence.startupMs=Date.now()-start;evidence.checks.health=runtime.state==='running';
  const before=await runtime.request('/api/settings');evidence.checks.settingsRead=typeof before==='object'&&!Array.isArray(before);
  const providers=await runtime.request('/api/providers');evidence.checks.providerList=Array.isArray(providers);
  const instructions='Meshlit isolated settings qualification';
  await runtime.request('/api/settings',{liveEffort:'low',customInstructions:instructions});
  const after=await runtime.request('/api/settings');evidence.checks.settingsWrite=after.liveEffort==='low'&&after.customInstructions===instructions;
  runtime.stop();await new Promise(r=>setTimeout(r,2000));
  await runtime.start(root,node);const restarted=await runtime.request('/api/settings');
  evidence.checks.settingsPersistAfterRestart=restarted.liveEffort==='low'&&restarted.customInstructions===instructions;
  const page=await fetch(ORIGIN,{signal:AbortSignal.timeout(30000),redirect:'error'});const html=await page.text();evidence.checks.webPage=page.ok&&html.includes('OpenLive');
 }catch(e){failed=e;evidence.failure=e.message;}
 finally{
  runtime.stop();await new Promise(r=>setTimeout(r,2000));
  try{await fetch('http://127.0.0.1:47833/health',{signal:AbortSignal.timeout(1500)});evidence.checks.agentStopped=false;}catch{evidence.checks.agentStopped=true;}
  try{await fetch(ORIGIN,{signal:AbortSignal.timeout(1500)});evidence.checks.webStopped=false;}catch{evidence.checks.webStopped=true;}
  fs.rmSync(dataHome,{recursive:true,force:true});
 }
 evidence.passed=!failed&&Object.values(evidence.checks).every(v=>v===true);
 const serialized=JSON.stringify(evidence,null,2)+'\n';if(output){fs.mkdirSync(path.dirname(path.resolve(output)),{recursive:true});fs.writeFileSync(output,serialized);}process.stdout.write(serialized);
 if(!evidence.passed)process.exitCode=1;
}
main().catch(e=>{process.stderr.write(e.message+'\n');process.exitCode=1;});
