// Explicit development companion launch; no dependency/model installation or paid calls.
const {execFileSync}=require('node:child_process');
const {VoiceRuntime,REVISION,ORIGIN}=require('../studio-runtime/extension/voice-runtime.cjs');
const source=process.argv[2],node=process.argv[3]||process.execPath;
if(!source)throw Error('Usage: node companions/voice/serve.cjs /absolute/openlive [/absolute/node]');
if(execFileSync('git',['-C',source,'rev-parse','HEAD'],{encoding:'utf8',timeout:10000}).trim()!==REVISION)throw Error('Use the reviewed OpenLive revision '+REVISION);
const runtime=new VoiceRuntime();let timer,closing=false;
function stop(code=0){if(closing)return;closing=true;clearInterval(timer);runtime.stop();setTimeout(()=>process.exit(code),2000);}
process.on('SIGINT',()=>stop());process.on('SIGTERM',()=>stop());
runtime.start(source,node).then(()=>{
 process.stdout.write('OpenLive development companion: '+ORIGIN+'\nVoice data: '+runtime.dataHome+'\nCtrl+C stops owned servers. Install/select speech engines in OpenLive Settings.\n');
 timer=setInterval(()=>{if(runtime.state!=='running'){process.stderr.write((runtime.failure||'Voice runtime stopped')+'\n');stop(1);}},1000);
}).catch(e=>{process.stderr.write(e.message+'\n');stop(1);});
