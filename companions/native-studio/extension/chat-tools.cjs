'use strict';
const fs=require('node:fs/promises');
const path=require('node:path');
const {execFile}=require('node:child_process');
const {promisify}=require('node:util');
const images={'.png':'image/png','.jpg':'image/jpeg','.jpeg':'image/jpeg','.webp':'image/webp'};
async function attachment(file){
    const handle=await fs.open(file,'r');
    try{
        const stat=await handle.stat(),name=path.basename(file),mime=images[path.extname(name).toLowerCase()];
        if(!stat.isFile()||stat.size<1||stat.size>(mime?4*1024*1024:32*1024))throw Error('Choose a text file up to 32 KiB or an image up to 4 MiB');
        const bytes=Buffer.alloc(stat.size);
        let n=0;while(n<bytes.length){const part=await handle.read(bytes,n,bytes.length-n,n);if(!part.bytesRead)throw Error('File changed while reading');n+=part.bytesRead;}
        if(mime){
            const matches=mime==='image/png'?bytes.subarray(0,8).equals(Buffer.from('89504e470d0a1a0a','hex')):mime==='image/jpeg'?bytes[0]===255&&bytes[1]===216:bytes.toString('ascii',0,4)==='RIFF'&&bytes.toString('ascii',8,12)==='WEBP';
            if(!matches)throw Error('Image signature does not match the filename');
            return {name,mime,url:'data:'+mime+';base64,'+bytes.toString('base64')};
        }
        if(bytes.includes(0))throw Error('Select a UTF-8 text file');
        return {name,text:new TextDecoder('utf-8',{fatal:true}).decode(bytes)};
    }finally{await handle.close();}
}
async function search(query,signal){
    const url=new URL('https://en.wikipedia.org/w/api.php');
    url.search=new URLSearchParams({action:'query',list:'search',srsearch:query,srlimit:'3',srprop:'snippet',format:'json',utf8:'1'});
    const r=await fetch(url,{signal,redirect:'error',headers:{'User-Agent':'MeshlitStudio/2.0 (https://imon.com.bd/meshlit/)'}});
    if(!r.ok)throw Error('Wikipedia returned HTTP '+r.status);
    const chunks=[];let bytes=0;
    for await(const chunk of r.body){bytes+=chunk.length;if(bytes>128*1024)throw Error('Search response too large');chunks.push(chunk);}
    const hits=JSON.parse(Buffer.concat(chunks)).query?.search;
    if(!Array.isArray(hits)||!hits.length)throw Error('No Wikipedia results');
    return hits.slice(0,3).map(h=>String(h.title)+' — '+String(h.snippet).replace(/<[^>]*>/g,'').slice(0,700)+'\nhttps://en.wikipedia.org/?curid='+Number(h.pageid)).join('\n\n');
}
async function dictate(){
    if(process.platform!=='win32')throw Error('Focus the message box and use dictation from your operating system.');
    const script="$ErrorActionPreference='Stop';[Console]::OutputEncoding=[Text.UTF8Encoding]::new();Add-Type -AssemblyName System.Speech;$r=[System.Speech.Recognition.SpeechRecognitionEngine]::new();try{$r.LoadGrammar([System.Speech.Recognition.DictationGrammar]::new());$r.SetInputToDefaultAudioDevice();$s=$r.Recognize([TimeSpan]::FromSeconds(12));if($s){[Console]::Write($s.Text)}}finally{$r.Dispose()}";
    const powershell=path.join(process.env.SystemRoot||'C:\\Windows','System32','WindowsPowerShell','v1.0','powershell.exe');
    const {stdout}=await promisify(execFile)(powershell,['-NoProfile','-NonInteractive','-EncodedCommand',Buffer.from(script,'utf16le').toString('base64')],{windowsHide:true,timeout:25000,maxBuffer:32768});
    if(!stdout.trim())throw Error('No speech recognized. Check the Windows microphone and speech language settings.');
    return stdout.trim();
}
async function readAloud(text,signal){
    if(typeof text!=='string'||!text.trim()||text.length>16000)throw Error('Choose a reply of up to 16,000 characters for read-aloud');
    const run=promisify(execFile),options={windowsHide:true,timeout:180000,signal,maxBuffer:32768};
    if(process.platform==='darwin')return run('/usr/bin/say',['--',text],options);
    if(process.platform==='linux')return run('spd-say',['--wait','--',text],options);
    const {spawn}=require('node:child_process');
    const ps=path.join(process.env.SystemRoot||'C:\\Windows','System32','WindowsPowerShell','v1.0','powershell.exe');
    const script="[Console]::InputEncoding=[Text.UTF8Encoding]::new();Add-Type -AssemblyName System.Speech;$s=[System.Speech.Synthesis.SpeechSynthesizer]::new();try{$s.Speak([Console]::In.ReadToEnd())}finally{$s.Dispose()}";
    return new Promise((resolve,reject)=>{
        const child=spawn(ps,['-NoProfile','-NonInteractive','-EncodedCommand',Buffer.from(script,'utf16le').toString('base64')],{...options,stdio:['pipe','ignore','ignore']});
        child.once('error',reject);child.once('close',code=>code===0?resolve():reject(Error('Windows speech output stopped')));
        child.stdin.on('error',()=>{});child.stdin.end(text);
    });
}
module.exports={attachment,search,dictate,readAloud};
