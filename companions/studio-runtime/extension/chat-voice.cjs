const {spawn}=require('node:child_process');
const {StringDecoder}=require('node:string_decoder');
const KEY='meshlit.chatVoice';
const DEFAULTS=Object.freeze({voice:'',rate:180});
const MAX_TEXT=6000,MAX_CATALOG_BYTES=65536;
const voiceName=value=>typeof value==='string'&&/^[\p{L}\p{N}][\p{L}\p{N} ._'()-]{0,127}$/u.test(value);
function parseVoices(output){
 if(typeof output!=='string'||Buffer.byteLength(output)>MAX_CATALOG_BYTES)throw Error('Installed voice list exceeded the supported size.');
 const voices=new Map();
 for(const line of output.split(/\r?\n/)){
  const match=line.match(/^(.+?)\s+([a-z]{2,3}_[A-Z]{2}(?:_[A-Za-z0-9]+)?)\s+#(?:\s|$)/);
  if(!match)continue;
  const id=match[1].trim();if(!voiceName(id))continue;
  if(!voices.has(id))voices.set(id,{id,locale:match[2]});
  if(voices.size>512)throw Error('Installed voice list exceeded the supported size.');
 }
 return [...voices.values()];
}
function settings(context){
 const value=context.globalState.get(KEY,DEFAULTS);
 if(!value||typeof value!=='object'||Array.isArray(value)||!(value.voice===''||voiceName(value.voice))||!Number.isInteger(value.rate)||value.rate<100||value.rate>300)throw Error('Chat voice settings are invalid. Reset the local voice defaults in Chat voice.');
 return {voice:value.voice,rate:value.rate};
}
function create(context,vscode,options={}){
 const spawnProcess=options.spawn||spawn,platform=options.platform||process.platform;
 const setTimer=options.setTimeout||setTimeout,clearTimer=options.clearTimeout||clearTimeout;
 const environment={PATH:'/usr/bin:/bin',...Object.fromEntries(['HOME','LANG','LC_ALL','TMPDIR'].filter(key=>typeof process.env[key]==='string').map(key=>[key,process.env[key]]))};
 const catalogs=new Set();let current,disposed=false,configuring=false;
 const notify=message=>{void vscode.window.showErrorMessage(message);};
 function catalog(job){
  if(disposed)return Promise.reject(Error('Chat voice is closed.'));
  if(platform!=='darwin')return Promise.reject(Error('Local read aloud is available on macOS. Open Voice and vision to configure OpenLive.'));
  return new Promise((resolve,reject)=>{
   let child,output='',bytes=0,done=false,timer;const decoder=new StringDecoder('utf8');
   const request={cancel:()=>finish(Error('Voice listing was cancelled.'))};
   const finish=(error,value)=>{if(done)return;done=true;clearTimer(timer);catalogs.delete(request);if(job?.cancelLookup===request.cancel)job.cancelLookup=undefined;if(error){try{child?.kill();}catch{}reject(error);}else resolve(value);};
   catalogs.add(request);if(job)job.cancelLookup=request.cancel;
   try{
    child=spawnProcess('/usr/bin/say',['-v','?'],{shell:false,stdio:['ignore','pipe','ignore'],env:environment});
    timer=setTimer(()=>finish(Error('Installed voice listing timed out.')),8000);
    child.on('error',()=>finish(Error('The macOS speech command could not start.')));
    child.stdout.on('data',chunk=>{if(done)return;bytes+=Buffer.byteLength(chunk);if(bytes>MAX_CATALOG_BYTES){finish(Error('Installed voice list exceeded the supported size.'));return;}output+=decoder.write(Buffer.isBuffer(chunk)?chunk:Buffer.from(chunk));});
    child.on('close',code=>{if(done)return;if(code!==0){finish(Error('macOS could not list installed voices.'));return;}try{output+=decoder.end();finish(undefined,parseVoices(output));}catch(error){finish(error);}});
   }catch{finish(Error('The macOS speech command could not start.'));}
  });
 }
 function stop(owner){
  const job=current;if(!job||(owner!==undefined&&owner!==job.owner))return false;
  job.finish(undefined,{status:'stopped'});job.cancelLookup?.();try{job.child?.kill();}catch{}return true;
 }
 function speak(text,owner){
  const invalid=disposed?'Chat voice is closed.':platform!=='darwin'?'Local read aloud is available on macOS. Open Voice and vision to configure OpenLive.':typeof owner!=='string'||!owner.trim()||owner.length>128?'A chat owner is required for read aloud.':typeof text!=='string'||!text.trim()||text.length>MAX_TEXT||/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(text)?'Choose a message of 1–6,000 characters to read aloud.':undefined;
  if(invalid){notify(invalid);return Promise.reject(Error(invalid));}
  let selected;try{selected=settings(context);}catch(error){notify(error.message);return Promise.reject(error);}
  stop();
  let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});
  const job={owner,done:false,child:undefined,timer:undefined,finish:(error,result)=>{
   if(job.done)return;job.done=true;clearTimer(job.timer);if(current===job)current=undefined;
   if(error){try{job.child?.kill();}catch{}notify(error.message);reject(error);}else resolve(result);
  }};
  current=job;
  void (async()=>{
   try{
    if(selected.voice){const voices=await catalog(job);if(job.done)return;if(!voices.some(voice=>voice.id===selected.voice))throw Error('The selected voice is no longer installed. Choose a voice in Chat voice settings.');}
    if(job.done||disposed)return;
    const args=['-r',String(selected.rate),...(selected.voice?['-v',selected.voice]:[])];
    const child=job.child=spawnProcess('/usr/bin/say',args,{shell:false,stdio:['pipe','ignore','ignore'],env:environment});
    job.timer=setTimer(()=>job.finish(Error('Read aloud exceeded its 10-minute limit and was stopped.')),10*60*1000);
    child.on('error',()=>job.finish(Error('Read aloud could not start. Check the installed macOS voice.')));
    child.on('close',(code,signal)=>{if(job.done)return;if(code===0&&!signal)job.finish(undefined,{status:'completed'});else job.finish(Error('macOS could not finish read aloud. Check the selected voice and audio output.'));});
    child.stdin.on('error',()=>job.finish(Error('The macOS speech process could not accept the message.')));
    // Message text never appears in process arguments, shell commands, files or logs.
    child.stdin.end(text);
   }catch(error){if(!job.done)job.finish(error);}
  })();
  return promise;
 }
 async function configure(){
  if(disposed||configuring)return;configuring=true;
  try{
   if(platform!=='darwin'){
    const choice=await vscode.window.showInformationMessage('Local read aloud is available on macOS. Voice conversations on this platform use an explicitly configured OpenLive companion.','Open Voice and vision');
    if(choice==='Open Voice and vision')await vscode.commands.executeCommand('meshlit.voice');return;
   }
   let selected;try{selected=settings(context);}catch{selected={...DEFAULTS};}
   const choice=await vscode.window.showQuickPick([
    {id:'voice',label:'Choose installed voice',description:selected.voice||'System default'},
    {id:'rate',label:'Speaking rate',description:selected.rate+' words per minute'},
    {id:'default',label:'Reset local voice defaults',description:'System voice · 180 words per minute'},
    {id:'stop',label:'Stop read aloud'},
    {id:'openlive',label:'Voice conversation and microphone setup',description:'OpenLive · separate runtime and permissions'}
   ],{title:'Chat voice · local read aloud'});
   if(!choice)return;
   if(choice.id==='stop'){stop();return;}
   if(choice.id==='openlive'){await vscode.commands.executeCommand('meshlit.voice');return;}
   if(choice.id==='default'){await context.globalState.update(KEY,{...DEFAULTS});return;}
   if(choice.id==='voice'){
    const voices=await catalog();
    const voice=await vscode.window.showQuickPick([{label:'System default',id:'',description:'Use the voice selected in macOS'},...voices.map(item=>({label:item.id,id:item.id,description:item.locale}))],{title:'Installed macOS voices'});
    if(!voice)return;
    if(voice.id!==''&&!voices.some(item=>item.id===voice.id))throw Error('Choose an installed voice.');
    await context.globalState.update(KEY,{...selected,voice:voice.id});return;
   }
   if(choice.id==='rate'){
    const validateInput=value=>/^\d{3}$/.test(value)&&Number(value)>=100&&Number(value)<=300?undefined:'Enter a whole number from 100 to 300.';
    const rate=await vscode.window.showInputBox({title:'Read-aloud rate',prompt:'Words per minute · 100–300',value:String(selected.rate),validateInput});
    if(rate===undefined)return;if(validateInput(rate))throw Error('Speaking rate must be a whole number from 100 to 300.');
    await context.globalState.update(KEY,{...selected,rate:Number(rate)});
   }
  }catch(error){if(!disposed)notify(error.message||'Chat voice settings could not be updated.');}
  finally{configuring=false;}
 }
 function dispose(){if(disposed)return;disposed=true;stop();for(const request of [...catalogs])request.cancel();}
 context.subscriptions.push({dispose},vscode.commands.registerCommand('meshlit.chatVoice',configure));
 return {speak,stop,dispose};
}
module.exports={create,parseVoices,settings,DEFAULTS,MAX_TEXT};
