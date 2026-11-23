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

function render(nonce) {
    return `<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}'">
<style nonce="${nonce}">
*{box-sizing:border-box}
body{font:14px/1.5 var(--vscode-font-family,system-ui);margin:0;padding:24px;color:var(--vscode-foreground);background:var(--vscode-editor-background)}
header,.filters,form{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
header,.layout{max-width:1120px;margin:auto}
header{margin-bottom:16px}h1{font-size:24px;margin:0}h2{font-size:22px;margin:0;overflow-wrap:anywhere}
button,select,input{font:inherit;color:inherit}button,select{cursor:pointer}button,input,select{border:1px solid var(--vscode-panel-border);border-radius:6px;background:var(--vscode-input-background);padding:7px 10px}
button:focus-visible,input:focus-visible,select:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:2px}
.layout{display:grid;grid-template-columns:minmax(255px,2fr) minmax(0,3fr);border:1px solid var(--vscode-panel-border);border-radius:14px;overflow:hidden;min-height:60vh}
.browser,.detailPane{padding:20px;min-width:0}.browser{background:var(--vscode-sideBar-background);border-inline-end:1px solid var(--vscode-panel-border)}
input[type=search]{flex:1;min-width:10em}.filters{margin-top:12px}.filters label:last-child{display:flex;align-items:baseline;gap:6px}
#capacity,#status,#summary,#hint,.resultDetail,#empty{color:var(--vscode-descriptionForeground);font-size:13px}
#status{margin:12px 0 4px}.results{max-height:57vh;overflow:auto}
.results button{width:100%;display:block;text-align:left;border:0;border-bottom:1px solid var(--vscode-panel-border);border-radius:0;background:transparent;padding:12px 10px;overflow-wrap:anywhere}
.results button:hover{background:var(--vscode-list-hoverBackground)}.results [aria-pressed=true]{background:var(--vscode-list-activeSelectionBackground);color:var(--vscode-list-activeSelectionForeground);border-radius:8px}
.resultDetail{display:block}.results [aria-pressed=true] .resultDetail{color:inherit}
#facts{display:flex;flex-wrap:wrap;gap:6px}#facts span{padding:3px 8px;border:1px solid var(--vscode-panel-border);border-radius:5px;font-size:12px}
h3{margin:24px 0 8px;font-size:16px}#variant{max-width:100%;width:100%}#download{background:var(--vscode-button-background);color:var(--vscode-button-foreground);font-weight:600}button:disabled{opacity:.55;cursor:default}
@media(max-width:660px){body{padding:12px}.layout{grid-template-columns:1fr;min-height:0}.browser{border-inline-end:0;border-bottom:1px solid var(--vscode-panel-border)}.results{max-height:24vh}.browser,.detailPane{padding:14px}}</style>
<header><button id="back">← Back</button><h1>Find a model</h1></header>
<main class="layout"><section class="browser" aria-label="Model search">
<form><input id="q" type="search" aria-label="Search Hugging Face by model or author" placeholder="Model or author on Hugging Face" maxlength="120"><button>Search</button></form>
<div class="filters"><label>Sort <select id="sort"><option value="">Best match</option><option value="likes">Most likes</option><option value="downloads">Most downloads</option><option value="lastModified">Recently updated</option></select></label><label><input id="fit" type="checkbox" checked>Within estimated RAM budget</label></div>
<p id="capacity"></p><div role="status" id="status"></div><div class="results" id="results"></div></section>
<div class="detailPane"><p id="empty">Search by model or author to compare available downloads.</p>
<section id="details" hidden><h2 id="name"></h2><p id="summary"></p><div id="facts"></div>
<h3>Download options</h3><label>GGUF file <select id="variant"></select></label><p id="hint"></p><button id="download" disabled>Download selected file</button><p><button id="card">Read publisher model card ↗</button></p></section></div></main>
<script nonce="${nonce}">
const api=acquireVsCodeApi(),el=id=>document.getElementById(id);
let seq=0,items=[],picked,files=[],capacity=0,loading=false;
function search(){seq++;picked=null;items=[];files=[];el('results').replaceChildren();el('details').hidden=true;el('empty').hidden=false;el('status').textContent='Searching Hugging Face…';api.postMessage({op:'search',q:el('q').value.trim(),sort:el('sort').value,seq})}
function variants(){const displayed=files.filter(f=>!el('fit').checked||f.bytes<=capacity);el('variant').replaceChildren(...displayed.map(f=>new Option(f.name+' · '+f.size,f.sha)));el('download').disabled=loading||!displayed.length;updateDownload();el('hint').textContent=loading?'Reading available files…':displayed.length?'Download size is the size on disk. Runtime memory also depends on context.':files.length?'No variant fits the estimate. Clear the RAM filter to see all files.':'No supported single-file GGUF with a published checksum.'}
function choose(item){seq++;picked=item;files=[];loading=true;el('details').hidden=false;el('empty').hidden=true;el('facts').replaceChildren();el('name').textContent=item.id;el('summary').textContent=item.summary;for(const b of el('results').children)b.setAttribute('aria-pressed',String(b.dataset.repo===item.id));variants();api.postMessage({op:'details',repo:item.id,seq})}
function updateDownload(){const file=files.find(f=>f.sha===el('variant').value);el('download').textContent=file?'Download · '+file.size:'Download selected file'}
el('card').onclick=()=>api.postMessage({op:'card',repo:picked?.id});el('variant').onchange=updateDownload;
el('back').onclick=()=>api.postMessage({op:'close'});document.querySelector('form').onsubmit=e=>{e.preventDefault();search()};el('sort').onchange=search;el('fit').onchange=variants;el('download').onclick=()=>api.postMessage({op:'download',sha:el('variant').value,seq});
window.addEventListener('message',({data:m})=>{if(m.op==='ready'){capacity=m.capacity;el('capacity').textContent=m.caption;return}if(m.seq!==seq)return;if(m.op==='error'){loading=false;el('status').textContent=m.message;variants();return}if(m.op==='results'){items=m.items;el('status').textContent=items.length?items.length+' repositories':'No matching repositories.';for(const item of items){const b=document.createElement('button');b.dataset.repo=item.id;const title=document.createElement('strong');title.textContent=item.id.split('/').pop();const detail=document.createElement('small');detail.className='resultDetail';detail.textContent=item.id.split('/')[0];b.append(title,detail);b.onclick=()=>choose(item);el('results').append(b)}if(items.length)choose(items[0])}if(m.op==='files'){loading=false;files=m.files;el('facts').replaceChildren(...(m.facts||[]).map(value=>{const tag=document.createElement('span');tag.textContent=value;return tag}));variants()}});api.postMessage({op:'ready'});el('q').focus();
</script></html>`;
}

function chooseFile(vscode, metadata, files) {
    const panel = vscode.window.createWebviewPanel('meshlit.discovery','Meshlit · Find a model',vscode.ViewColumn.Active,{enableScripts:true,localResourceRoots:[]});
    return new Promise(resolve => {
        let request, version, repos = new Set(), choices = [];
        const capacity = budget();
        panel.onDidDispose(() => {request?.abort();resolve(undefined);});
        panel.webview.onDidReceiveMessage(async msg => {
            if(msg?.op==='card'&&repos.has(msg.repo))return vscode.env.openExternal(vscode.Uri.parse('https://huggingface.co/'+msg.repo));
            if (msg?.op === 'close') return panel.dispose();
            if (msg?.op === 'ready') return panel.webview.postMessage({op:'ready',capacity,caption:'Estimated CPU weight budget now: '+size(capacity)+'. Includes a reserve for runtime memory.'});
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
                    if (typeof msg.q !== 'string' || msg.q.length > 120 || !['','likes','downloads','lastModified'].includes(msg.sort)) throw Error('Invalid search');
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
                    const data = await metadata('https://huggingface.co/api/models/'+msg.repo+'?blobs=true',signal);
                    if (controller.signal.aborted) return;
                    choices = files(msg.repo,data).map(item=>({...item,repo:msg.repo}));
                    const facts = ['GGUF','Bundled runtime: CPU'];
                    for(const capability of ['vision','image-text-to-text','tool-use','reasoning'])if(data.tags?.includes(capability))facts.push(capability);
                    if (typeof data.gguf?.architecture === 'string') facts.push(data.gguf.architecture);
                    if (Number.isSafeInteger(data.gguf?.total) && data.gguf.total > 0) {
                        facts.push(new Intl.NumberFormat('en', {notation:'compact'}).format(data.gguf.total) + ' parameters');
                    }
                    if (typeof data.pipeline_tag === 'string') facts.push(data.pipeline_tag);
                    await post({op:'files',facts,files:choices.map(({name,sha,bytes})=>({name,sha,bytes,size:size(bytes)}))});
                }
            } catch (error) {await post({op:'error',message:error.message});}
        });
        panel.webview.html = render(crypto.randomBytes(20).toString('hex'));
    });
}
module.exports = {budget,recommended,size,render,chooseFile};
