// SPDX-License-Identifier: Apache-2.0
'use strict';
const path=require('node:path');
const {createRequire}=require('node:module');
const fs=require('node:fs');
async function clientFactory(options){
    // Probe the SDK's published local ports first. Its eager client constructor can reject
    // asynchronously when no desktop daemon is running, before a repository RPC is attached.
    if(!options.baseUrl){
        const port=await Promise.any([41343,52993,16141,39414,22931,1234].map(async port=>{
            const reply=await fetch('http://127.0.0.1:'+port+'/lmstudio-greeting',{redirect:'error',signal:AbortSignal.timeout(1200)});
            if(!reply.ok||(await reply.json()).lmstudio!==true)throw Error('No LM Studio daemon');
            return port;
        }));
        options={...options,baseUrl:'ws://127.0.0.1:'+port};
    }
    const sdk=fs.existsSync(path.join(__dirname,'catalog-sdk'))?path.join(__dirname,'catalog-sdk'):path.join(__dirname,'../catalog-sdk');
    const {LMStudioClient}=createRequire(path.join(sdk,'package.json'))('@lmstudio/sdk');
    return new LMStudioClient(options);
}
function bounded(promise,signal){
    signal.throwIfAborted();
    return new Promise((resolve,reject)=>{
        const abort=()=>reject(signal.reason);
        signal.addEventListener('abort',abort,{once:true});
        promise.then(resolve,reject).finally(()=>signal.removeEventListener('abort',abort));
    });
}
function catalog(options={}, connect=clientFactory){
    let client,cache;
    const get=()=>client ||= Promise.resolve().then(()=>connect(options));
    async function list(query,sort,signal){
        cache ||= get().then(c=>c.repository.unstable.getModelCatalog()).catch(error=>{cache=undefined;throw error;});
        const rows=await bounded(cache,AbortSignal.any([signal,AbortSignal.timeout(3500)]));
        signal.throwIfAborted();
        const terms=query.toLowerCase().split(/\s+/).filter(Boolean);
        const items=rows.filter(row=>row.type==='model' && /^[\w.-]+$/.test(row.owner) && /^[\w.-]+$/.test(row.name)).map(row=>{
            const m=row.metadata;
            const id=row.owner+'/'+row.name;
            return {id,publisher:row.owner,name:row.name.replaceAll('-',' '),summary:row.description,likes:row.likeCount,downloads:row.downloads,
                lm:true,staff:!!row.staffPickedAt,minBytes:m.minMemoryUsageBytes,caps:[m.vision===true?'vision':'',m.reasoning===true?'reasoning':'',m.trainedForToolUse===true?'tool-use':''].filter(Boolean),
                facts:[...m.paramsStrings,...m.architectures,m.type,...m.compatibilityTypes.map(x=>x.toUpperCase())],picked:row.staffPickedAt||0};
        }).filter(row=>terms.every(term=>(row.id+' '+row.summary).toLowerCase().includes(term)));
        if(sort==='downloads'||sort==='likes')items.sort((a,b)=>b[sort]-a[sort]);
        else items.sort((a,b)=>b.picked-a.picked);
        return items;
    }
    async function variants(row,signal){
        const entries=await bounded(get().then(c=>c.repository.searchModels({searchTerm:row.id,limit:25})),signal);
        signal.throwIfAborted();
        const entry=entries.find(e=>e.isExactMatch())||entries.find(e=>e.name.toLowerCase()===row.id.toLowerCase()||e.name.toLowerCase().replaceAll(' ','-')===row.id.split('/')[1].toLowerCase());
        if(!entry)return [];
        return (await bounded(entry.getDownloadOptions(),signal)).map((option,i)=>({name:[option.name,option.quantization].filter(Boolean).join(' · '),format:option.compatibilityType.toUpperCase(),
            bytes:option.sizeBytes,sha:'lm-'+i,fit:option.fitEstimation,option}));
    }
    return {list,variants,dispose:()=>client?.then(c=>c[Symbol.asyncDispose]?.()).catch(()=>{})};
}
async function install(vscode,item){
    const ok=await vscode.window.showInformationMessage('Download '+item.name+' into the LM Studio model library?',{modal:true},'Download');
    if(!ok)return;
    await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'LM Studio · '+item.name,cancellable:true},async(progress,token)=>{
        const ctl=new AbortController(),sub=token.onCancellationRequested(()=>ctl.abort());
        let last=0;
        try{
            const id=await item.option.download({signal:ctl.signal,onProgress:update=>{
                if(!update.totalBytes)return;
                const now=Math.floor(100*update.downloadedBytes/update.totalBytes);
                if(now>last){progress.report({increment:now-last});last=now;}
            }});
            vscode.window.showInformationMessage('Available in your LM Studio library: '+id);
        }finally{sub.dispose();}
    });
}
module.exports={catalog,install};
