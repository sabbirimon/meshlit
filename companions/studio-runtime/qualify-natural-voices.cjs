// Explicitly installs the two reviewed engines and generates local, known-text samples.
const fs=require('node:fs'),path=require('node:path');
const {VoiceRuntime,ORIGIN}=require('./extension/voice-runtime.cjs');
const [source,dataHome,output]=process.argv.slice(2);
if(!source||!dataHome||!output)throw Error('Usage: node qualify-natural-voices.cjs SOURCE DATA_HOME OUTPUT');
const runtime=new VoiceRuntime({dataHome:path.resolve(dataHome)});
function wav(floatBytes,rate){
 if(!Number.isInteger(rate)||rate<8000||rate>48000||floatBytes.length%4||floatBytes.length>16*1024*1024)throw Error('Invalid PCM');
 const pcm=Buffer.alloc(floatBytes.length/2);for(let i=0;i<floatBytes.length/4;i++){const f=floatBytes.readFloatLE(i*4);if(!Number.isFinite(f))throw Error('Non-finite synthesis audio');pcm.writeInt16LE(Math.max(-32768,Math.min(32767,Math.round(f*32768))),i*2);}
 const h=Buffer.alloc(44);h.write('RIFF');h.writeUInt32LE(pcm.length+36,4);h.write('WAVEfmt ',8);h.writeUInt32LE(16,16);h.writeUInt16LE(1,20);h.writeUInt16LE(1,22);h.writeUInt32LE(rate,24);h.writeUInt32LE(rate*2,28);h.writeUInt16LE(2,32);h.writeUInt16LE(16,34);h.write('data',36);h.writeUInt32LE(pcm.length,40);return Buffer.concat([h,pcm]);
}
(async()=>{const report={date:new Date().toISOString(),models:[],limits:['No LLM, microphone or paid provider request. Host CPU synthesis, not Android native execution.']};fs.mkdirSync(output,{recursive:true});
 try{
  await runtime.start(source,process.execPath);
  for(const id of ['kokoro-en-v0_19-int8','pocket-int8']){
   let catalog=await runtime.request('/api/voice/engines');let engine=catalog.flatMap(f=>f.variants).find(e=>e.id===id);if(!engine?.runnable)throw Error(id+' is not runnable on this host');
   if(!engine.installed){
    process.stdout.write('Downloading '+id+' ('+engine.sizeBytes+' bytes, '+engine.license+')\n');
    const r=await fetch(ORIGIN+'/api/voice/engines/'+id+'/download',{method:'POST',headers:{Origin:ORIGIN,'Content-Type':'application/json'},body:'{}',signal:AbortSignal.timeout(600000),redirect:'error'});
    if(!r.ok)throw Error('Download HTTP '+r.status);
    const lines=(await r.text()).trim().split('\n').map(s=>JSON.parse(s));if(!lines.some(e=>e.done))throw Error('Download failed: '+(lines.at(-1)?.error||'incomplete'));
   }
   const voice=engine.voices[0];const start=Date.now();
   const r=await fetch(ORIGIN+'/api/voice/tts',{method:'POST',headers:{Origin:ORIGIN,'Content-Type':'application/json'},body:JSON.stringify({engine:id,voice:voice.id,text:'Hello. This is a natural voice running locally for Meshlit.',lang:'en'}),signal:AbortSignal.timeout(180000),redirect:'error'});
   if(!r.ok)throw Error('Synthesis HTTP '+r.status);
   const bytes=Buffer.from(await r.arrayBuffer());const rate=Number(r.headers.get('x-sample-rate'));const file=path.join(output,id+'.wav');fs.writeFileSync(file,wav(bytes,rate));
   const result={engine:id,voice:voice.id,rate,samples:bytes.length/4,synthesisMs:Date.now()-start,file:path.basename(file)};report.models.push(result);process.stdout.write(JSON.stringify(result)+'\n');
  }
  report.passed=true;
 }catch(e){report.passed=false;report.failure=e.message;process.exitCode=1;process.stderr.write(e.message+'\n');}
 finally{runtime.stop();fs.writeFileSync(path.join(output,'evidence.json'),JSON.stringify(report,null,2)+'\n');}
})();
