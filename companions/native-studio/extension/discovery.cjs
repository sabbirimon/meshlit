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
body{font:14px/1.5 var(--vscode-font-family);margin:auto;max-width:1060px;padding:24px;color:var(--vscode-foreground)}header,form,.filters{display:flex;align-items:center;gap:12px;flex-wrap:wrap}h1{font-size:26px}input,select,button{font:inherit;padding:8px;border:1px solid var(--vscode-panel-border);border-radius:6px;background:var(--vscode-input-background);color:inherit}button{cursor:pointer}input[type=search]{flex:1;min-width:12em}.filters{margin:14px 0}.layout{display:grid;grid-template-columns:minmax(13em,2fr) minmax(0,3fr);gap:24px}.results{max-height:60vh;overflow:auto}.results button{width:100%;text-align:left;margin-bottom:4px;overflow-wrap:anywhere}.results [aria-pressed=true]{background:var(--vscode-list-activeSelectionBackground);color:var(--vscode-list-activeSelectionForeground)}#details{border-inline-start:1px solid var(--vscode-panel-border);padding-inline-start:20px;overflow-wrap:anywhere}#variant{max-width:100%}button:focus-visible,input:focus-visible,select:focus-visible{outline:2px solid var(--vscode-focusBorder)}@media(max-width:620px){body{padding:12px}.layout{grid-template-columns:1fr}.results{max-height:24vh}#details{padding:0;border:0}}</style>
<header><button id="back">← Back</button><h1>Find a model</h1></header>
<form><input id="q" type="search" aria-label="Search Hugging Face by model or author" placeholder="Model or author on Hugging Face" maxlength="120"><button>Search</button></form>
<div class="filters"><label>Sort <select id="sort"><option value="">Best match</option><option value="likes">Most likes</option><option value="downloads">Most downloads</option><option value="lastModified">Recently updated</option></select></label><label><input id="fit" type="checkbox" checked>Show file variants within estimated RAM budget</label></div>
<p id="capacity"></p><div role="status" id="status"></div><div class="layout"><div class="results" id="results"></div><section id="details" hidden><h2 id="name"></h2><p id="summary"></p><label>GGUF file <select id="variant"></select></label><p id="hint"></p><button id="download" disabled>Download selected file</button></section></div>
<script nonce="${nonce}">
const api=acquireVsCodeApi(),el=id=>document.getElementById(id);
let seq=0,items=[],picked,files=[],capacity=0,loading=false;
function search(){seq++;picked=null;items=[];files=[];el('results').replaceChildren();el('details').hidden=true;el('status').textContent='Searching Hugging Face…';api.postMessage({op:'search',q:el('q').value.trim(),sort:el('sort').value,seq})}
function variants(){const displayed=files.filter(f=>!el('fit').checked||f.bytes<=capacity);el('variant').replaceChildren(...displayed.map(f=>new Option(f.name+' · '+f.size,f.sha)));el('download').disabled=loading||!displayed.length;el('hint').textContent=loading?'Reading available files…':displayed.length?'Download size is the size on disk. Runtime memory also depends on context.':files.length?'No variant fits the estimate. Clear the RAM filter to see all files.':'No supported single-file GGUF with a published checksum.'}
function choose(item){seq++;picked=item;files=[];loading=true;el('details').hidden=false;el('name').textContent=item.id;el('summary').textContent=item.summary;for(const b of el('results').children)b.setAttribute('aria-pressed',String(b.textContent===item.id));variants();api.postMessage({op:'details',repo:item.id,seq})}
el('back').onclick=()=>api.postMessage({op:'close'});document.querySelector('form').onsubmit=e=>{e.preventDefault();search()};el('sort').onchange=search;el('fit').onchange=variants;el('download').onclick=()=>api.postMessage({op:'download',sha:el('variant').value,seq});
window.addEventListener('message',({data:m})=>{if(m.op==='ready'){capacity=m.capacity;el('capacity').textContent=m.caption;return}if(m.seq!==seq)return;if(m.op==='error'){loading=false;el('status').textContent=m.message;variants();return}if(m.op==='results'){items=m.items;el('status').textContent=items.length?items.length+' repositories':'No matching repositories.';for(const item of items){const b=document.createElement('button');b.textContent=item.id;b.onclick=()=>choose(item);el('results').append(b)}if(items.length)choose(items[0])}if(m.op==='files'){loading=false;files=m.files;variants()}});api.postMessage({op:'ready'});el('q').focus();
</script></html>`;
}

function chooseFile(vscode, metadata, files) {
    const panel = vscode.window.createWebviewPanel('meshlit.discovery','Meshlit · Find a model',vscode.ViewColumn.Active,{enableScripts:true,localResourceRoots:[]});
    return new Promise(resolve => {
        let request, version, repos = new Set(), choices = [];
        const capacity = budget();
        panel.onDidDispose(() => {request?.abort();resolve(undefined);});
        panel.webview.onDidReceiveMessage(async msg => {
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
                    await post({op:'files',files:choices.map(({name,sha,bytes})=>({name,sha,bytes,size:size(bytes)}))});
                }
            } catch (error) {await post({op:'error',message:error.message});}
        });
        panel.webview.html = render(crypto.randomBytes(20).toString('hex'));
    });
}
module.exports = {budget,recommended,size,render,chooseFile};
