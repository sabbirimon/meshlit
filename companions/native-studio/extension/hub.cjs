'use strict';
const fs = require('node:fs/promises'), path = require('node:path'), crypto = require('node:crypto');
const {imported} = require('./catalog.cjs');
const HUB = 'https://huggingface.co';
const repoPattern = /^[\w.-]+\/[\w.-]+$/;
async function metadata(url, signal) {
    const response = await fetch(url, {signal, redirect:'error'});
    if (!response.ok) throw Error('Model Hub returned HTTP '+response.status);
    const chunks=[]; let size=0;
    for await (const part of response.body) {
        size+=part.length;
        if(size>4*1024*1024)throw Error('Hub metadata is too large');
        chunks.push(part);
    }
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}
function files(repo, data) {
    if (!repoPattern.test(repo) || !/^[a-f0-9]{40}$/.test(data.sha)) throw Error('Missing immutable model revision');
    return (data.siblings||[]).filter(s=>typeof s.rfilename==='string' && s.rfilename.split('/').every(x=>x.length>0&&x!=='.'&&x!=='..') &&
        /\.gguf$/i.test(s.rfilename) && !/^mmproj[-_.]/i.test(path.posix.basename(s.rfilename)) && !/-\d{5}-of-\d{5}\.gguf$/i.test(s.rfilename) &&
        /^[a-f0-9]{64}$/.test(s.lfs?.sha256) && Number.isSafeInteger(s.size) && s.size>=24 && s.size<=64*1024**3)
        .map(s=>({name:s.rfilename,bytes:s.size,sha:s.lfs.sha256,url:HUB+'/'+repo+'/resolve/'+data.sha+'/'+s.rfilename.split('/').map(encodeURIComponent).join('/')}));
}
async function download(item, dir, signal, progress, fetcher=fetch) {
    await fs.mkdir(dir,{recursive:true});
    const output=path.join(dir,item.sha+'.gguf');
    const temp=path.join(dir,crypto.randomUUID()+'.partial');
    const h=await fs.open(temp,'wx'); let received=0, closed=false;
    const hash=crypto.createHash('sha256');
    try {
        const response=await fetcher(item.url,{signal});
        if(!response.ok)throw Error('Download returned HTTP '+response.status);
        for await(const chunk of response.body) {
            signal.throwIfAborted();
            received+=chunk.length;
            if(received>item.bytes)throw Error('Model download exceeds declared size');
            hash.update(chunk);
            await h.writeFile(chunk);
            progress(received/item.bytes);
        }
        if(received!==item.bytes||hash.digest('hex')!==item.sha)throw Error('Downloaded model size or SHA-256 mismatch');
        await h.close(); closed=true;
        signal.throwIfAborted();
        try { await fs.link(temp,output); } catch(err) {if(err.code!=='EEXIST')throw err;}
        const route=await imported(output);
        if(route.sha256!==item.sha)throw Error('Cached GGUF digest mismatch');
        return {...route,model:path.posix.basename(item.name)};
    } finally {
        if(!closed)await h.close();
        await fs.rm(temp,{force:true});
    }
}
async function discover(vscode, directory) {
    const item=await require('./discovery.cjs').chooseFile(vscode,metadata,files);
    if(!item)return;
    return install(vscode,directory,item);
}
async function install(vscode,directory,item) {
    const ok=await vscode.window.showInformationMessage('Download '+item.name+' ('+(item.bytes/1024**3).toFixed(2)+' GiB) from '+item.repo+' into Studio storage?',{modal:true},'Download');
    if(!ok)return;
    return vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'Downloading '+item.name,cancellable:true},async(progress,token)=>{
        const ctl=new AbortController(), sub=token.onCancellationRequested(()=>ctl.abort());
        let last=0;
        try{return await download(item,directory,AbortSignal.any([ctl.signal,AbortSignal.timeout(3600000)]),fraction=>{
            const now=Math.floor(100*fraction);if(now>last){progress.report({increment:now-last});last=now;}
        });}finally{sub.dispose();}
    });
}
module.exports={discover,files,download,install};
