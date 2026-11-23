'use strict';
const fs=require('node:fs/promises'),path=require('node:path');
const {digest}=require('./runtime.cjs');
async function imported(file) {
    const absolute=await fs.realpath(file),before=await fs.stat(absolute);
    if(!before.isFile()||before.size<24||path.extname(absolute).toLowerCase()!=='.gguf')throw Error('Choose a complete GGUF model file');
    if(/-\d{5}-of-\d{5}\.gguf$/i.test(absolute))throw Error('This importer accepts single-file GGUF models');
    const handle=await fs.open(absolute,'r');
    try {const header=Buffer.alloc(8);await handle.read(header,0,8,0);if(header.toString('ascii',0,4)!=='GGUF'||![2,3].includes(header.readUInt32LE(4)))throw Error('Unsupported GGUF header');}
    finally {await handle.close();}
    const sha256=await digest(absolute),after=await fs.stat(absolute);
    if(before.size!==after.size||before.mtimeMs!==after.mtimeMs||before.ino!==after.ino)throw Error('Model changed during import');
    return {kind:'import',model:path.basename(absolute),file:absolute,sha256,bytes:after.size};
}
function displayOutput(text,enabled) {
    if(!enabled)return text;
    return text.replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi,'[email hidden]')
        .replace(/\b(?:sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\b/g,'[token hidden]');
}
module.exports={imported,displayOutput};
