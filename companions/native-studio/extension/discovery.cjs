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
const hf=require('./hugging-face.cjs');
async function readCard(repo,revision,signal,fetcher=fetch){
    const res=await fetcher('https://huggingface.co/'+repo+'/raw/'+revision+'/README.md',{signal});
    if(!res.ok)return 'Publisher README unavailable. Open the model card for details.';
    const reader=res.body.getReader(),chunks=[];let length=0;
    try{while(length<24000){const {done,value}=await reader.read();if(done)break;chunks.push(Buffer.from(value).subarray(0,24000-length));length+=value.length;}}
    finally{await reader.cancel();}
    return Buffer.concat(chunks).toString('utf8').replace(/^---[\s\S]*?\n---\s*/, '').replace(/<[^>]{0,1000}>/g,'').replace(/!\[[^\]]*\]\([^)]*\)/g,'').trim().slice(0,12000)||'Open the publisher model card for details.';
}
function chooseFile(vscode, metadata, files, brand, local=[], account, lmOptions={}) {
    const panel = vscode.window.createWebviewPanel('meshlit.discovery','Meshlit · Find a model',vscode.ViewColumn.Active,{enableScripts:true,localResourceRoots:[]});
    return new Promise(resolve => {
        let request, version, revision, selected, repos = new Set(), choices = [];
        const lm=require('./lm-catalog.cjs').catalog(lmOptions);
        const publicLm=require('./lm-public.cjs').publicCatalog();
        let preferPublic=false;
        const snapshots=new Map(), rows=new Map(), logos=new Map();
        let next, scope, changingAccount=false, transferred=false;
        const lifetime=new AbortController();
        const capacity = budget();
        panel.onDidDispose(() => {request?.abort();lifetime.abort();if(!transferred)lm.dispose();resolve(undefined);});
        panel.webview.onDidReceiveMessage(async msg => {
            if(msg?.op==='account' && account && !changingAccount){
                changingAccount=true;
                try{
                    if(msg.action==='tokens'){await vscode.env.openExternal(vscode.Uri.parse('https://huggingface.co/settings/tokens'));return;}
                    if(msg.action==='in')await account.signIn();
                    else if(msg.action==='out')await account.signOut();
                    else return;
                    request?.abort();snapshots.clear();rows.clear();repos.clear();next=undefined;
                    await panel.webview.postMessage({op:'account',name:await account.name()});
                }catch(error){vscode.window.showErrorMessage(error.message);}
                finally{changingAccount=false;}
                return;
            }
            if(msg?.op==='logo'){
                const row=rows.get(msg.repo);
                let publisher=row?.publisher;
                if(!publisher&&row?.lmPublic){
                    const detail=await publicLm.summary(row.id,AbortSignal.any([lifetime.signal,AbortSignal.timeout(9000)])).catch(()=>undefined);
                    publisher=detail?.publisher;
                }
                if(!publisher)return;
                if(!logos.has(publisher))logos.set(publisher,hf.avatar(publisher,AbortSignal.any([lifetime.signal,AbortSignal.timeout(7000)])).catch(()=>undefined));
                const url=await logos.get(publisher);
                if(url&&!lifetime.signal.aborted)await panel.webview.postMessage({op:'logo',publisher,repo:msg.repo,url});
                return;
            }
            if(msg?.op==='copy'&&repos.has(msg.repo))return vscode.env.clipboard.writeText(msg.repo);
            if(msg?.op==='source'&&(['vendor','more'].includes(msg.action)||(msg.action==='local'&&local.some(x=>x.id===msg.id)))){
                resolve({action:msg.action,id:msg.id});panel.dispose();return;
            }
            if(msg?.op==='readme'&&msg.seq===version&&msg.repo===selected&&/^[a-f0-9]{40}$/.test(revision)){
                const controller=request;
                const text=await readCard(selected,revision,AbortSignal.any([controller.signal,AbortSignal.timeout(8000)]),account?.request).catch(()=>'Open the publisher model card to read the README.');
                if(!controller.signal.aborted)await panel.webview.postMessage({op:'readme',seq:msg.seq,text});
                return;
            }
            if(msg?.op==='card'&&repos.has(msg.repo))return vscode.env.openExternal(vscode.Uri.parse((rows.get(msg.repo)?.lmPublic?'https://lmstudio.ai/':rows.get(msg.repo)?.lm?'https://lmstudio.ai/models/':'https://huggingface.co/')+msg.repo));
            if (msg?.op === 'close') return panel.dispose();
            if (msg?.op === 'ready') return panel.webview.postMessage({op:'ready',capacity,local,account:await account?.name(),caption:size(capacity)+' · CPU memory budget'});
            if (msg?.op === 'download') {
                const item = choices.find(f => f.sha === msg.sha);
                if(item && msg.seq===version) {
                    if(item.option){transferred=true;resolve({...item,action:'lm-download',dispose:lm.dispose});}
                    else resolve(item);
                    panel.dispose();
                }
                return;
            }
            if (!['search','page','details'].includes(msg?.op) || !Number.isSafeInteger(msg.seq)) return;
            request?.abort();
            const controller = new AbortController();
            request = controller;
            version = msg.seq;
            choices = [];
            const signal = AbortSignal.any([controller.signal,AbortSignal.timeout(15000)]);
            const post = payload => !controller.signal.aborted && panel.webview.postMessage({...payload,seq:msg.seq});
            try {
                if(msg.op==='search'||msg.op==='page'){
                    const append=msg.op==='page', source=msg.source||'hub';
                    if(!['hub','mine','lm'].includes(source)||typeof msg.q!=='string'||msg.q.length>220||!['','likes','downloads','lastModified'].includes(msg.sort))throw Error('Invalid search');
                    const key=JSON.stringify([source,msg.q,msg.sort]);
                    if(append && (!next||scope!==key))return;
                    if(!append){next=undefined;rows.clear();repos.clear();scope=key;}
                    let items, page={};
                    if(source==='lm'){
                        if(!preferPublic){
                            try{items=await lm.list(msg.q,msg.sort,signal);}
                            catch{preferPublic=true;}
                        }
                        if(preferPublic)items=await publicLm.list(msg.q,msg.sort,signal);
                    }else{
                        const exact=source==='hub'&&repoInput(msg.q);
                        if(exact)items=[{id:exact,publisher:exact.split('/')[0],summary:'Hugging Face repository'}];
                        else{
                            const params=new URLSearchParams({limit:'50'});
                            if(source==='mine'){
                                const name=await account?.name();
                                if(!name)throw Error('Sign in to Hugging Face to see your model repositories.');
                                params.set('author',name);
                            }else params.set('filter','gguf');
                            if(msg.q)params.set('search',msg.q);
                            if(msg.sort){params.set('sort',msg.sort);params.set('direction','-1');}
                            for(const field of ['author','likes','downloads','lastModified','tags','pipeline_tag'])params.append('expand',field);
                            items=hf.modelRows(await metadata(append?next:'https://huggingface.co/api/models?'+params,signal,page));
                        }
                    }
                    if(controller.signal.aborted)return;
                    next=page.next;
                    for(const item of items){rows.set(item.id,item);repos.add(item.id);}
                    await post({op:'results',items:[...rows.values()],more:!!next});
                } else {
                    if (!repos.has(msg.repo)) throw Error('Select a repository from the search results');
                    const row=rows.get(msg.repo);
                    if(row?.lmPublic){
                        const entry=await publicLm.details(row,signal);
                        const groups=await Promise.all(entry.sources.map(async repo=>{
                            const data=await metadata('https://huggingface.co/api/models/'+repo+'?blobs=true',signal);
                            return files(repo,data).map(item=>({...item,repo}));
                        }));
                        if(controller.signal.aborted)return;
                        choices=groups.flat();selected=row.id;revision=undefined;
                        await post({op:'files',metrics:[row.downloads.toLocaleString()+' downloads',Number.isFinite(row.likes)?row.likes+' likes':'',row.lastModified?'Updated '+new Date(row.lastModified).toISOString().slice(0,10):''],
                            facts:row.caps,readme:entry.readme||row.summary,description:row.summary,files:choices.map(({name,sha,bytes})=>({name,sha,bytes,size:size(bytes)}))});
                        return;
                    }
                    if(row?.lm){
                        selected=row.id;revision=undefined;
                        const options=await lm.variants(row,signal);
                        if(controller.signal.aborted)return;
                        choices=options;
                        await post({op:'files',metrics:[row.downloads.toLocaleString()+' downloads',row.likes.toLocaleString()+' likes',row.staff?'LM Studio staff pick':''],facts:[...row.facts,...row.caps],
                            description:row.summary,readme:row.summary,files:options.map(({name,bytes,sha,fit,format})=>({name,bytes,sha,fit,format,size:size(bytes)}))});
                        return;
                    }
                    const data = snapshots.get(msg.repo)||await metadata('https://huggingface.co/api/models/'+msg.repo+'?blobs=true',signal);
                    if (controller.signal.aborted) return;
                    if(snapshots.size>=100)snapshots.delete(snapshots.keys().next().value);
                    snapshots.set(msg.repo,data);
                    selected=msg.repo;revision=data.sha;
                    choices = files(msg.repo,data).map(item=>({...item,repo:msg.repo}));
                    const facts = choices.length?['GGUF','Bundled runtime: CPU']:[];
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
