'use strict';
const crypto=require('node:crypto');
const {walletId,coinSerial,keeperUnits}=require('./meshcoin.cjs');
function coinMark(brand){
    return brand.replace('<title>Meshlit Studio</title>','<title>MeshCoin</title>').replace('<defs>','<defs><pattern id="meshcoin-circuit" width="20" height="20" patternUnits="userSpaceOnUse"><path d="M0 5h6l5 5h9M4 20v-5l4-4M14 0v4l4 4" fill="none" stroke="currentColor" stroke-width=".8"/><circle cx="4" cy="15" r="1.3" fill="currentColor"/></pattern>').replace('</defs>','</defs><circle cx="64" cy="64" r="60" fill="#5094ce" opacity=".08"/><circle cx="64" cy="64" r="59" fill="url(#meshcoin-circuit)" opacity=".12"/>');
}
function walletView(vscode,context,getWallet,peers,connect,live,brand){
    let panel,busy=false;
    async function refresh(){
        if(!panel)return;
        const wallet=await getWallet(),totals=wallet.totals(),policy=wallet.config('keeper',null);
        await panel.webview.postMessage({type:'wallet',owner:wallet.owner,record:wallet.config('record',false),policy:policy?((policy.bps/100)+'% · '+policy.keeper):'Self-recording · 0%',session:live()?.session||'Networking off',
            earned:totals.earned,kept:totals.kept,pending:totals.pending,paid:totals.paid,tokens:totals.generated,jobs:totals.blocks.length,
            blocks:[...totals.blocks,...totals.keeperBlocks].sort((a,b)=>b.receipt.awardedAtMs-a.receipt.awardedAtMs).map(b=>({height:b.height,hash:b.hash,count:b.receipt.generatedTokens,cut:keeperUnits(b),keeper:b.keeper,issuer:walletId(b.receipt.publicKey),confirmed:wallet.hasAcknowledgment(b),date:new Date(b.receipt.awardedAtMs).toLocaleString()})),busy});
    }
    async function peerChoice(title){return (await vscode.window.showQuickPick(peers().map(p=>({label:p.name,description:walletId(p.key),peer:p})),{title,placeHolder:'Choose a paired Studio node'}))?.peer;}
    async function perform(op){
        const w=await getWallet();
        if(op==='toggle')w.set('record',!w.config('record',false));
        if(op==='keeper'){
            const choice=await vscode.window.showQuickPick(['Self-record · 0%','Delegate future records…'],{title:'Recordkeeping for future contributions'});
            if(choice?.startsWith('Self'))w.set('keeper',null);
            else if(choice){
                const p=await peerChoice('Select a recordkeeper');if(!p)return;
                const raw=await vscode.window.showInputBox({title:'Recordkeeper percentage',prompt:'Percentage of future hosted-job rewards reserved for this peer. Whole MeshCoin units round down.',value:'0',validateInput:t=>/^\d{1,3}(\.\d{1,2})?$/.test(t)&&Number(t)<=100?undefined:'Enter 0 to 100, with up to two decimal places'});
                if(raw===undefined)return;
                const bps=Math.round(Number(raw)*100);
                const ok=await vscode.window.showInformationMessage('Share your signed contribution chain (job IDs, token counts and times) with '+p.name+' and reserve '+(bps/100)+'% of future rewards for its verified records?',{modal:true},'Use recordkeeper');
                if(ok){w.set('share:'+walletId(p.key),true);w.set('keeper',{keeper:walletId(p.key),bps});}
            }
        }
        if(op==='share'){
            const p=await peerChoice('Manage access to your contribution chain');if(!p)return;
            const allow=await vscode.window.showQuickPick(['Allow reading signed records','Stop sharing'],{title:p.name+' · signed job IDs, counts and times'});
            if(allow){const id=walletId(p.key);w.set('share:'+id,allow.startsWith('Allow'));if(!allow.startsWith('Allow')&&w.config('keeper',null)?.keeper===id)w.set('keeper',null);}
        }
        if(op==='sync'){
            const p=await peerChoice('Verify a contributor’s chain and acknowledge assigned records');if(!p)return;
            const svc=live();if(!svc)throw Error('Enable device networking in Devices, then retry.');
            const signal=AbortSignal.timeout(120000);await w.sync(p,msg=>svc.call(svc.locate(p),msg,signal));
            vscode.window.showInformationMessage('Verified signed contribution records from '+p.name+'.');
        }
        if(op==='backup'){
            const dest=await vscode.window.showSaveDialog({saveLabel:'Save signed MeshCoin backup',filters:{JSON:['json']}});
            if(dest)await vscode.workspace.fs.writeFile(dest,Buffer.from(JSON.stringify(w.backup())));
        }
        if(op==='restore'){
            const files=await vscode.window.showOpenDialog({openLabel:'Restore signed MeshCoin backup',canSelectMany:false,filters:{JSON:['json']}});
            if(!files?.length)return;
            if((await vscode.workspace.fs.stat(files[0])).size>32*1024*1024)throw Error('Backup exceeds 32 MiB.');
            w.restore(JSON.parse(Buffer.from(await vscode.workspace.fs.readFile(files[0])).toString('utf8')));
            vscode.window.showInformationMessage('Verified and merged signed MeshCoin records for this node.');
        }
        if(op==='export'){
            const rows=w.verified().map(b=>b.receipt),dest=await vscode.window.showSaveDialog({saveLabel:'Export verified receipts',filters:{JSON:['json']}});
            if(dest)await vscode.workspace.fs.writeFile(dest,Buffer.from(JSON.stringify(rows,null,2)));
        }
        if(op==='verify'){w.verified();vscode.window.showInformationMessage('Every local MeshCoin block and contribution receipt verified.');}
        if(op==='identity')await connect();
    }
    async function open(){
        if(panel){panel.reveal();await refresh();return;}
        const wallet=await getWallet();panel=vscode.window.createWebviewPanel('meshlit.wallet','MeshCoin',vscode.ViewColumn.Active,{enableScripts:true,localResourceRoots:[]});panel.iconPath=vscode.Uri.file(brand.file);
        panel.onDidDispose(()=>{panel=undefined});panel.webview.onDidReceiveMessage(async m=>{
            let acquired=false;
            try{
                if(m?.op==='ready')return await refresh();
                if(m?.op==='serial'){
                    const entry=typeof m.hash==='string'&&wallet.db.prepare('SELECT body FROM blocks WHERE hash=?').get(m.hash);if(!entry)return;const b=JSON.parse(entry.body);
                    const serial=coinSerial(b,m.index);return await panel?.webview.postMessage({type:'serial',serial});
                }
                if(busy||!['toggle','keeper','share','sync','export','backup','restore',verify','identity'].includes(m?.op))return;
                busy=true;acquired=true;await refresh();await perform(m.op);
            }catch(e){vscode.window.showErrorMessage(e.message);}finally{if(acquired){busy=false;await refresh().catch(()=>{});}}
        });
        const n=crypto.randomBytes(18).toString('hex');
        panel.webview.html=`<!DOCTYPE html><html lang="en"><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'nonce-${n}';script-src 'nonce-${n}'"><meta name="viewport" content="width=device-width,initial-scale=1"><style nonce="${n}">body{max-width:860px;margin:30px auto;padding:0 22px;font:14px/1.5 var(--vscode-font-family,system-ui);color:var(--vscode-foreground)}header{display:flex;align-items:center;gap:16px}header svg{width:70px;height:70px}h1,h2{color:var(--vscode-textLink-foreground);margin:0}h2{font-size:17px;margin-top:22px}.total{font-size:32px;font-weight:600}.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}button,input,select{font:inherit;padding:6px 9px;border:1px solid var(--vscode-panel-border);border-radius:6px;background:var(--vscode-input-background);color:inherit}button{cursor:pointer}p{overflow-wrap:anywhere}[hidden]{display:none!important}section{border-top:1px solid var(--vscode-panel-border);margin-top:20px}code,output{display:block;overflow-wrap:anywhere;user-select:all}#index{width:6em}output{margin:10px 0;font-family:monospace}</style><header>${coinMark(brand.markup)}<div><h1>MeshCoin</h1><div>Local contribution wallet</div></div></header><p class="total" id="balance"></p><p id="totals"></p><button id="toggle"></button> <button id="verify">Verify chain</button> <button id="export">Export Android receipts</button> <button id="backup">Back up wallet</button> <button id="restore">Restore wallet</button><section><h2>Recordkeeping</h2><p id="policy"></p><div class="row"><button id="keeper">Choose recordkeeper</button><button id="share">Manage sharing</button><button id="sync">Verify a peer’s records</button></div></section><section><h2>Coin serial number</h2><p id="empty">A completed, metered job from a paired device creates a signed block when recording is on.</p><div class="row" id="lookup" hidden><select id="block" aria-label="Contribution block"></select><label>Coin <input id="index" type="number" min="1" value="1"></label><button id="show">Show serial</button></div><output id="serial"></output><p id="transaction"></p></section><section><h2>Contributor identity</h2><code id="owner"></code><p id="session"></p><button id="identity">Node identity and audit</button></section><script nonce="${n}">const api=acquireVsCodeApi(),el=id=>document.getElementById(id);let blocks=[];for(const id of ['toggle','verify','export','backup','restore',keeper','share','sync','identity'])el(id).onclick=()=>api.postMessage({op:id});el('block').onchange=()=>{el('index').value=1;const b=blocks.find(b=>b.hash===el('block').value);el('index').max=b?.count||1;el('transaction').textContent=b?b.date+' · '+b.count+' MeshCoin · '+b.cut+' for recordkeeping'+(b.keeper?' · '+(b.confirmed?'acknowledged':'reserved')+' · '+b.keeper:' · self-recorded'):'';el('serial').textContent=''};el('index').oninput=()=>el('serial').textContent='';el('show').onclick=()=>{if(el('index').reportValidity())api.postMessage({op:'serial',hash:el('block').value,index:Number(el('index').value)})};window.addEventListener('message',({data:m})=>{if(m.type==='serial'){el('serial').textContent=m.serial;return}if(m.type!=='wallet')return;el('balance').textContent=(m.earned+m.kept).toLocaleString()+' MeshCoin';el('totals').textContent=m.tokens+' generated tokens · '+m.jobs+' hosted jobs · '+m.kept+' earned for recordkeeping · '+m.paid+' assigned to recordkeepers · '+m.pending+' reserved. Non-transferable preview credits.';el('policy').textContent=m.policy;el('owner').textContent=m.owner;el('session').textContent='Live session · '+m.session;el('toggle').textContent=m.record?'Turn off receipt recording':'Enable receipt recording';blocks=m.blocks;el('block').replaceChildren(...blocks.map(b=>new Option('Block '+b.height+' · '+b.count+' coins',b.hash)));el('block').onchange();el('lookup').hidden=!blocks.length;el('empty').hidden=!!blocks.length;for(const b of document.querySelectorAll('button'))b.disabled=m.busy});api.postMessage({op:'ready'});</script></html>`;
    }
    return open;
}
module.exports={walletView,coinMark};
