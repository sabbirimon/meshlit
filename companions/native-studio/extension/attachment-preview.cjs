'use strict';
const {inflateRawSync}=require('node:zlib');

const extensions={
    'image/png':'png','image/jpeg':'jpg','image/webp':'webp','image/gif':'gif',
    'video/mp4':'mp4','video/webm':'webm','audio/mpeg':'mp3','audio/mp3':'mp3','audio/mp4':'m4a',
    'audio/wav':'wav','audio/x-wav':'wav','audio/ogg':'ogg','audio/flac':'flac',
    'application/pdf':'pdf','text/plain':'txt','text/markdown':'md','application/json':'json',
    'text/javascript':'js','application/javascript':'js','text/html':'html','text/css':'css',
    'text/x-python':'py','text/x-java-source':'java','text/x-c':'c','text/x-c++':'cpp','text/typescript':'ts','application/yaml':'yaml','application/xml':'xml','text/csv':'csv',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document':'docx'
};
function extension(mime){const type=String(mime).split(';')[0].toLowerCase();return extensions[type]||(type.startsWith('text/')?'txt':'bin');}

// Read just the document body, with an output bound independent of ZIP metadata.
function wordText(bytes){
    const end=bytes.lastIndexOf(Buffer.from([0x50,0x4b,5,6]));
    if(end<0||end+22>bytes.length)throw Error('Invalid Word document');
    let at=bytes.readUInt32LE(end+16);
    for(let i=0;i<Math.min(4096,bytes.readUInt16LE(end+10));i++){
        if(at+46>bytes.length||bytes.readUInt32LE(at)!==0x02014b50)break;
        const nameLength=bytes.readUInt16LE(at+28),name=bytes.toString('utf8',at+46,at+46+nameLength);
        if(name==='word/document.xml'){
            const local=bytes.readUInt32LE(at+42),method=bytes.readUInt16LE(at+10);
            if(bytes.readUInt32LE(local)!==0x04034b50)break;
            const start=local+30+bytes.readUInt16LE(local+26)+bytes.readUInt16LE(local+28);
            const size=bytes.readUInt32LE(at+20);
            if(size>2*1024*1024||start+size>bytes.length)break;
            const packed=bytes.subarray(start,start+size);
            const xml=method===8?inflateRawSync(packed,{maxOutputLength:2*1024*1024}):method===0?packed:null;
            if(!xml)break;
            return xml.toString('utf8').replace(/<\/w:p>/g,'\n').replace(/<[^>]+>/g,'')
                .replace(/&(amp|lt|gt|quot|apos);/g,(_,e)=>({amp:'&',lt:'<',gt:'>',quot:'"',apos:"'"}[e])).trim().slice(0,16000);
        }
        at+=46+nameLength+bytes.readUInt16LE(at+30)+bytes.readUInt16LE(at+32);
    }
    throw Error('Document preview unavailable');
}
function preview(filename,bytes){
    if(!bytes)return {kind:'missing'};
    const ext=filename.split('.').pop();
    if(ext==='docx'){
        try{return {kind:'text',text:wordText(bytes)};}catch{return {kind:'file'};}
    }
    const mime=Object.keys(extensions).find(key=>extensions[key]===ext);
    if(!mime)return {kind:'file'};
    if(mime.startsWith('text/')||['xml','json','js'].includes(ext)){
        return {kind:'text',text:bytes.toString('utf8',0,16000),truncated:bytes.length>16000};
    }
    return {kind:mime.split('/')[0]==='application'?'pdf':mime.split('/')[0],mime,data:bytes.toString('base64')};
}
module.exports={extension,preview};
