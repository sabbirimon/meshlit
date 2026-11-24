'use strict';
const fs=require('node:fs/promises'),path=require('node:path');
const {digest}=require('./runtime.cjs');
async function imported(file,signal) {
    const absolute=await fs.realpath(file),before=await fs.stat(absolute);
    if(!before.isFile()||before.size<24||path.extname(absolute).toLowerCase()!=='.gguf')throw Error('Choose a complete GGUF model file');
    if(/-\d{5}-of-\d{5}\.gguf$/i.test(absolute))throw Error('This importer accepts single-file GGUF models');
    const handle=await fs.open(absolute,'r');
    try {const header=Buffer.alloc(8);await handle.read(header,0,8,0);if(header.toString('ascii',0,4)!=='GGUF'||![2,3].includes(header.readUInt32LE(4)))throw Error('Unsupported GGUF header');}
    finally {await handle.close();}
    const sha256=await digest(absolute,signal),after=await fs.stat(absolute);
    if(before.size!==after.size||before.mtimeMs!==after.mtimeMs||before.ino!==after.ino)throw Error('Model changed during import');
    return {kind:'import',model:path.basename(absolute),file:absolute,sha256,bytes:after.size};
}
async function libraryFiles(root) {
    const found=[];
    async function visit(dir,depth){
        const entries=await fs.readdir(dir,{withFileTypes:true});
        for(const entry of entries){
            const file=path.join(dir,entry.name);
            if(entry.isDirectory()&&depth<3)await visit(file,depth+1);
            else if((entry.isFile()||entry.isSymbolicLink())&&/\.gguf$/i.test(entry.name)&&
                !/^(?:mmproj[-_.])|-\d{5}-of-\d{5}\.gguf$/i.test(entry.name))found.push(file);
        }
    }
    await visit(root,0);
    return found.sort();
}

async function pickLibraryFile(vscode){
    let root=path.join(require('node:os').homedir(),'.lmstudio','models');
    for(;;){
        let files=[];
        try{files=await libraryFiles(root);}
        catch(error){if(!['ENOENT','ENOTDIR','EACCES','EPERM'].includes(error.code))throw error;}
        const options=files.map(file=>({label:path.basename(file),description:path.relative(root,path.dirname(file)),file}));
        options.push({label:'Choose library folder…',description:'Use the local model directory set in LM Studio or Bionic'});
        const pick=await vscode.window.showQuickPick(options,{title:'Import from LM Studio or Bionic',placeHolder:files.length?'Select a GGUF to use in place':'No single-file GGUF found here. Choose your models directory.',matchOnDescription:true});
        if(!pick||pick.file)return pick?.file;
        const selected=await vscode.window.showOpenDialog({canSelectFiles:false,canSelectFolders:true,canSelectMany:false,openLabel:'Use model library'});
        if(!selected?.length)return;
        root=selected[0].fsPath;
    }
}
function displayOutput(text,enabled) {
    if(!enabled)return text;
    return text.replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi,'[email hidden]')
        .replace(/\b(?:sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\b/g,'[token hidden]');
}
module.exports={imported,displayOutput,libraryFiles,pickLibraryFile};
