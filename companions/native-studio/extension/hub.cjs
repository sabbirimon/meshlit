'use strict';
const fs = require('node:fs/promises'), path = require('node:path');
const {imported} = require('./catalog.cjs');
const HUB = 'https://huggingface.co';
const repoPattern = /^[\w.-]+\/[\w.-]+$/;
async function metadata(url, signal, fetcher=fetch, page) {
    let response;
    for(let attempt=0;attempt<4;attempt++){
        const target=new URL(url);
        if(target.origin!==HUB||!target.pathname.startsWith('/api/models'))throw Error('Unexpected model catalog URL');
        response=await fetcher(target.href,{signal,redirect:'manual'});
        if(![301,302,303,307,308].includes(response.status))break;
        const location=response.headers.get('location');await response.body?.cancel();
        if(!location)throw Error('Missing catalog redirect destination');
        url=new URL(location,target).href;
    }
    if (!response.ok) throw Error('Model Hub returned HTTP '+response.status+([401,403].includes(response.status)?'. Sign in with a read token and check your access on the publisher model card.':''));
    if(page)page.next=require('./hugging-face.cjs').nextPage(response.headers.get('link'));
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
    signal.throwIfAborted();
    if(!/^[a-f0-9]{64}$/.test(item.sha)||!Number.isSafeInteger(item.bytes)||item.bytes<24)throw Error('Invalid model download manifest');
    const cache=path.join(dir,'.downloads');
    await fs.mkdir(cache,{recursive:true,mode:0o700});
    const output=path.join(dir,item.sha+'.gguf'),temp=path.join(cache,item.sha+'.partial.gguf');
    const db=new (require('node:sqlite').DatabaseSync)(path.join(cache,item.sha+'.lock.sqlite'));
    try{
        try{db.exec('BEGIN IMMEDIATE');}catch(e){if(e.errcode===5)throw Error('Another Studio window is already downloading this model.');throw e;}
        const existing=await fs.stat(output).catch(e=>{if(e.code!=='ENOENT')throw e;});
        if(existing){
            const route=await imported(output,signal);
            if(route.sha256!==item.sha||route.bytes!==item.bytes)throw Error('Cached GGUF digest or size mismatch');
            return {...route,model:path.posix.basename(item.name)};
        }
        let offset=(await fs.stat(temp).catch(e=>{if(e.code!=='ENOENT')throw e;}))?.size||0;
        if(offset>item.bytes){await fs.rm(temp);offset=0;}
        if(offset<item.bytes){
            const response=await fetcher(item.url,{signal,headers:offset?{Range:'bytes='+offset+'-'}:{}});
            if(!response.ok)throw Error('Download returned HTTP '+response.status);
            if(response.status===206){
                const expected='bytes '+offset+'-'+(item.bytes-1)+'/'+item.bytes;
                if(response.headers.get('content-range')!==expected)throw Error('The model server returned an unexpected byte range.');
            }else if(response.status===200)offset=0;
            else throw Error('Unexpected model download response');
            const handle=await fs.open(temp,offset?'a':'w',0o600);
            let received=offset,oversize=false;
            try{
                progress(offset/item.bytes);
                for await(const chunk of response.body){
                    signal.throwIfAborted();received+=chunk.length;
                    if(received>item.bytes){oversize=true;throw Error('Model download exceeds declared size');}
                    await handle.writeFile(chunk);progress(received/item.bytes);
                }
                if(received!==item.bytes)throw Error('Incomplete download. Select this variant again to resume.');
            }finally{await handle.close();if(oversize)await fs.rm(temp,{force:true});}
        }
        signal.throwIfAborted();
        let route;
        try{
            route=await imported(temp,signal);
            if(route.sha256!==item.sha||route.bytes!==item.bytes)throw Error('Downloaded model SHA-256 or size mismatch');
        }catch(e){if(!signal.aborted)await fs.rm(temp,{force:true});throw e;}
        signal.throwIfAborted();
        await fs.rename(temp,output);
        return {...route,file:output,model:path.posix.basename(item.name)};
    }finally{db.close();}

}
async function install(vscode,directory,item,fetcher=fetch) {
    const ok=await vscode.window.showInformationMessage('Download '+item.name+' ('+(item.bytes/1024**3).toFixed(2)+' GiB) from '+item.repo+' into Studio storage?',{modal:true},'Download');
    if(!ok)return;
    return vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'Downloading '+item.name,cancellable:true},async(progress,token)=>{
        const ctl=new AbortController(), sub=token.onCancellationRequested(()=>ctl.abort());
        let last=0;
        try{return await download(item,directory,AbortSignal.any([ctl.signal,AbortSignal.timeout(3600000)]),fraction=>{
            const now=Math.floor(100*fraction);if(now>last){progress.report({increment:now-last});last=now;}
        },fetcher);}catch(error){
            if(!ctl.signal.aborted)throw error;
            vscode.window.showInformationMessage('Download paused. Choose this variant again to resume.');
        }finally{sub.dispose();}
    });
}
module.exports={metadata,files,download,install};
