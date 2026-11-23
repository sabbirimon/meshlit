// SPDX-License-Identifier: Apache-2.0
'use strict';
const crypto = require('node:crypto');
const os = require('node:os');
const GiB = 1024 ** 3;

// This CPU budget excludes swap and shared GPU memory. Reserve space for the
// runtime and KV cache; actual consumption depends on architecture and context.
function budget(total = os.totalmem(), free = os.freemem()) {
    return Math.max(0, Math.floor(Math.min(total * 0.65, free * 0.80) - 512 * 1024 ** 2));
}
function recommended(models, capacity) {
    return models.filter(model => model.sizeBytes > 0 && model.sizeBytes <= capacity)
        .sort((a, b) => b.sizeBytes - a.sizeBytes)[0];
}
const size = bytes => (bytes / GiB).toFixed(2) + ' GiB';

function repoInput(query){
    const id=/^[\w-][\w.-]*\/[\w-][\w.-]*$/;
    if(id.test(query))return query;
    if(!/^https?:\/\//i.test(query))return;
    const url=new URL(query),repo=url.pathname.replace(/^\/|\/$/g,'');
    if(url.protocol!=='https:'||!['huggingface.co','hf.co'].includes(url.hostname)||url.username||url.password||url.port||!id.test(repo))
        throw Error('Paste a Hugging Face repository link, such as https://huggingface.co/author/model.');
    return repo;
}

const {render}=require('./picker-view.cjs');
const picks=[
    ['lmstudio-community/Bonsai-27B-GGUF','Bonsai 27B','Compact quantized model from PrismML. Check the publisher card for runtime requirements.'],
    ['bartowski/granite-3.1-2b-instruct-GGUF','Granite 3.1 2B','An instruction model from IBM.'],
    ['unsloth/Phi-4-mini-instruct-GGUF','Phi 4 Mini Instruct','Compact instruction model from Microsoft.'],
    ['bartowski/Mistral-7B-Instruct-v0.3-GGUF','Mistral 7B Instruct v0.3','A seven-billion-parameter instruction model from Mistral AI.'],
    ['lmstudio-community/Qwen2.5-0.5B-Instruct-GGUF','Qwen2.5 0.5B Instruct','Compact instruction-following model from Qwen.'],
    ['lmstudio-community/gemma-3-1b-it-GGUF','Gemma 3 1B','A local instruction model from Google.'],
    ['lmstudio-community/Qwen2.5-Coder-1.5B-Instruct-GGUF','Qwen2.5 Coder 1.5B','Code-focused model from Qwen.'],
    ['lmstudio-community/SmolLM2-360M-Instruct-GGUF','SmolLM2 360M','Small instruction model from Hugging Face.']
];
async function readCard(repo,revision,signal){
    const res=await fetch('https://huggingface.co/'+repo+'/raw/'+revision+'/README.md',{signal});
    if(!res.ok)return 'Publisher README unavailable. Open the model card for details.';
    const reader=res.body.getReader(),chunks=[];let length=0;
    try{while(length<24000){const {done,value}=await reader.read();if(done)break;chunks.push(Buffer.from(value).subarray(0,24000-length));length+=value.length;}}
    finally{await reader.cancel();}
    return Buffer.concat(chunks).toString('utf8').replace(/^---[\s\S]*?\n---\s*/, '').replace(/<[^>]{0,1000}>/g,'').replace(/!\[[^\]]*\]\([^)]*\)/g,'').trim().slice(0,12000)||'Open the publisher model card for details.';
}
function chooseFile(vscode, metadata, files, brand, local=[]) {
    const panel = vscode.window.createWebviewPanel('meshlit.discovery','Meshlit · Find a model',vscode.ViewColumn.Active,{enableScripts:true,localResourceRoots:[]});
    return new Promise(resolve => {
        let request, version, revision, selected, repos = new Set(), choices = [];
        const snapshots=new Map();
        const capacity = budget();
        panel.onDidDispose(() => {request?.abort();resolve(undefined);});
        panel.webview.onDidReceiveMessage(async msg => {
            if(msg?.op==='copy'&&repos.has(msg.repo))return vscode.env.clipboard.writeText(msg.repo);
            if(msg?.op==='source'&&(['vendor','more'].includes(msg.action)||(msg.action==='local'&&local.some(x=>x.id===msg.id)))){
                resolve({action:msg.action,id:msg.id});panel.dispose();return;
            }
            if(msg?.op==='readme'&&msg.seq===version&&msg.repo===selected&&/^[a-f0-9]{40}$/.test(revision)){
                const controller=request;
                const text=await readCard(selected,revision,AbortSignal.any([controller.signal,AbortSignal.timeout(8000)])).catch(()=>'Open the publisher model card to read the README.');
                if(!controller.signal.aborted)await panel.webview.postMessage({op:'readme',seq:msg.seq,text});
                return;
            }
            if(msg?.op==='card'&&repos.has(msg.repo))return vscode.env.openExternal(vscode.Uri.parse('https://huggingface.co/'+msg.repo));
            if (msg?.op === 'close') return panel.dispose();
            if (msg?.op === 'ready') return panel.webview.postMessage({op:'ready',capacity,local,caption:size(capacity)+' · CPU memory budget'});
            if (msg?.op === 'download') {
                const item = choices.find(f => f.sha === msg.sha);
                if (item && msg.seq === version) {resolve(item);panel.dispose();}
                return;
            }
            if (!['search','details'].includes(msg?.op) || !Number.isSafeInteger(msg.seq)) return;
            request?.abort();
            const controller = new AbortController();
            request = controller;
            version = msg.seq;
            choices = [];
            const signal = AbortSignal.any([controller.signal,AbortSignal.timeout(15000)]);
            const post = payload => !controller.signal.aborted && panel.webview.postMessage({...payload,seq:msg.seq});
            try {
                if (msg.op === 'search') {
                    repos = new Set();
                    if (typeof msg.q !== 'string' || msg.q.length > 220 || !['','likes','downloads','lastModified'].includes(msg.sort)) throw Error('Invalid search');
                    if(!msg.q){
                        const previews=await Promise.all(picks.map(async ([id,name,summary])=>{
                            let data=snapshots.get(id);
                            try{if(!data){data=await metadata('https://huggingface.co/api/models/'+id+'?blobs=true',signal);snapshots.set(id,data);}}
                            catch{return {id,name,summary};}
                            const sizes=files(id,data).map(f=>f.bytes);
                            return {id,name,summary,minBytes:sizes.length?Math.min(...sizes):undefined,likes:data.likes||0,downloads:data.downloads||0,lastModified:Date.parse(data.lastModified)||0};
                        }));
                        if(controller.signal.aborted)return;
                        if(msg.sort)previews.sort((a,b)=>(b[msg.sort]||0)-(a[msg.sort]||0));
                        repos=new Set(previews.map(x=>x.id));await post({op:'results',items:previews});return;
                    }
                    const exact=repoInput(msg.q);
                    if(exact){repos.add(exact);await post({op:'results',items:[{id:exact,summary:'Hugging Face repository'}]});return;}
                    const params = new URLSearchParams({filter:'gguf',limit:'30',search:msg.q});
                    if (msg.sort) {params.set('sort',msg.sort);params.set('direction','-1');}
                    const rows = await metadata('https://huggingface.co/api/models?'+params,signal);
                    const items = rows.filter(m=>typeof m.id==='string' && /^[\w-][\w.-]*\/[\w-][\w.-]*$/.test(m.id)).map(m=>({
                        id:m.id,summary:[Number.isFinite(m.downloads)?m.downloads+' downloads':'',Number.isFinite(m.likes)?m.likes+' likes':'',m.lastModified?'Updated '+String(m.lastModified).slice(0,10):''].filter(Boolean).join(' · ')
                    }));
                    if (controller.signal.aborted) return;
                    repos = new Set(items.map(m=>m.id));
                    await post({op:'results',items});
                } else {
                    if (!repos.has(msg.repo)) throw Error('Select a repository from the search results');
                    const data = snapshots.get(msg.repo)||await metadata('https://huggingface.co/api/models/'+msg.repo+'?blobs=true',signal);
                    if (controller.signal.aborted) return;
                    selected=msg.repo;revision=data.sha;
                    choices = files(msg.repo,data).map(item=>({...item,repo:msg.repo}));
                    const facts = ['GGUF','Bundled runtime: CPU'];
                    if(data.siblings?.some(file=>/(^|\/)mmproj[-_.]/i.test(file.rfilename)))facts.push('Vision weights');
                    for(const capability of ['vision','image-text-to-text','tool-use','reasoning'])if(data.tags?.includes(capability))facts.push(capability);
                    if (typeof data.gguf?.architecture === 'string') facts.push(data.gguf.architecture);
                    if (Number.isSafeInteger(data.gguf?.total) && data.gguf.total > 0) {
                        facts.push(new Intl.NumberFormat('en', {notation:'compact'}).format(data.gguf.total) + ' parameters');
                    }
                    if (typeof data.pipeline_tag === 'string') facts.push(data.pipeline_tag);
                    const metrics=[Number.isFinite(data.downloads)?data.downloads.toLocaleString()+' downloads':'',Number.isFinite(data.likes)?data.likes.toLocaleString()+' likes':'',data.lastModified?'Updated '+data.lastModified.slice(0,10):''];
                    await post({op:'files',metrics,facts,files:choices.map(({name,sha,bytes})=>({name,sha,bytes,size:size(bytes)}))});
                }
            } catch (error) {await post({op:'error',message:error.message});}
        });
        if (brand) panel.iconPath = vscode.Uri.file(brand.file);
        panel.webview.html = render(crypto.randomBytes(20).toString('hex'), brand?.markup);
    });
}
module.exports = {budget,recommended,size,render,chooseFile,repoInput};
