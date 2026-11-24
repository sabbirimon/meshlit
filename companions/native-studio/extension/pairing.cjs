'use strict';
const crypto = require('node:crypto');
const http = require('node:http');
const os = require('node:os');
const {once} = require('node:events');
const {isIP} = require('node:net');
const TTL = 600000;
function privateIp(value) {
    const h=value.toLowerCase().replace(/^\[|\]$/g,'');
    if (isIP(h)===6) return h==='::1' || /^(fc|fd|fe[89ab])/.test(h);
    if (isIP(h)!==4) return false;
    const [a,b]=h.split('.').map(Number);
    return a===127 || a===10 || (a===172&&b>=16&&b<=31) || (a===192&&b===168) || (a===100&&b>=64&&b<=127) || (a===169&&b===254);
}
function parseInvite(raw,now=Date.now()) {
    if(typeof raw!=='string'||raw.length>8192||!/^meshlit:\/\/pair\?/i.test(raw.trim())) throw Error('Paste a Meshlit pairing link');
    const u=new URL(raw.trim());
    if(u.username||u.password||u.port||u.hash||u.pathname||u.host!=='pair')throw Error('Invalid pairing link');
    const q=u.searchParams;
    if(new Set(q.keys()).size!==[...q.keys()].length)throw Error('Duplicate pairing field');
    const node=q.get('node')||'',key=q.get('key')||'',name=q.get('name')||node;
    if(!/^(?:[a-f\d]{64}|[a-f\d]{8}(?:-[a-f\d]{4}){3}-[a-f\d]{12})$/i.test(node)||name.length>128||/[\x00-\x1f\x7f]/.test(name))throw Error('Invalid device identity');
    if(!/^(?:[\da-f]{2}){1,512}$/i.test(key))throw Error('Ask the owner to share a link containing its identity key');
    const publicKey=crypto.createPublicKey({key:Buffer.from(key,'hex'),format:'der',type:'spki'});
    if(publicKey.asymmetricKeyType!=='ec'||publicKey.asymmetricKeyDetails.namedCurve!=='prime256v1')throw Error('Unsupported identity key');
    const base=new URL(q.get('url'));
    if(base.username||base.password||base.search||base.hash||base.port==='0'||!(base.protocol==='https:'||(base.protocol==='http:'&&privateIp(base.hostname))))throw Error('Use a private IP address or a verified HTTPS endpoint');
    const expires=q.has('exp')?Number(q.get('exp')):null;
    if(expires!==null&&(!/^\d+$/.test(q.get('exp'))||!Number.isSafeInteger(expires)||expires<=now||expires>now+TTL))throw Error('Invite expired or invalid. Ask the owner for a fresh link.');
    return {node,key,name,base:base.href.replace(/\/$/,''),expires,fingerprint:crypto.createHash('sha256').update(Buffer.from(key,'hex')).digest('hex')};
}
const message=p=>Buffer.from(['meshlit.identity.v1',p.nodeId,p.publicKey,p.nonce,p.issuedAtMs].join('\n'));
function verify(peer,nonce,p,now=Date.now()) {
    if(!p||p.nodeId!==peer.node||p.publicKey!==peer.key||p.nonce!==nonce||!Number.isSafeInteger(p.issuedAtMs)||p.issuedAtMs<now-120000||p.issuedAtMs>now+30000||!/^([a-f\d]{2}){1,80}$/i.test(p.signature)||peer.expires!==null&&peer.expires<=now)throw Error('Device identity or challenge did not match');
    if(!crypto.verify('sha256',message(p),{key:Buffer.from(peer.key,'hex'),format:'der',type:'spki'},Buffer.from(p.signature,'hex')))throw Error('Peer signature did not verify');
}
async function contact(peer) {
    const nonce=crypto.randomBytes(32).toString('hex');
    const response=await fetch(peer.base+'/v1/identity?nonce='+nonce,{redirect:'error',signal:AbortSignal.timeout(12000)});
    if(!response.ok)throw Error('Peer returned HTTP '+response.status);
    let total=0;const chunks=[];
    for await(const chunk of response.body){total+=chunk.length;if(total>8192)throw Error('Identity response is oversized');chunks.push(chunk);}
    verify(peer,nonce,JSON.parse(Buffer.concat(chunks).toString('utf8')));
}
function newIdentity() {
    const keys=crypto.generateKeyPairSync('ec',{namedCurve:'prime256v1'});
    return {node:crypto.randomUUID(),key:keys.publicKey.export({format:'der',type:'spki'}).toString('hex'),privateKey:keys.privateKey.export({format:'pem',type:'pkcs8'}).toString()};
}
async function invite(identity,ip,name) {
    if(!privateIp(ip))throw Error('Choose a private local or VPN interface');
    const expires=Date.now()+TTL;
    const server=http.createServer({maxHeaderSize:4096,requestTimeout:5000,headersTimeout:5000},(req,res)=>{
        const found=/^\/v1\/identity\?nonce=([a-f0-9]{64})$/.exec(req.url);
        res.setHeader('Cache-Control','no-store');res.setHeader('Connection','close');
        if(!found||req.method!=='GET'||Date.now()>=expires){res.writeHead(404);res.end();return;}
        const proof={nodeId:identity.node,publicKey:identity.key,nonce:found[1],issuedAtMs:Date.now()};
        proof.signature=crypto.sign('sha256',message(proof),identity.privateKey).toString('hex');
        res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(proof));
    });
    server.maxConnections=8;server.setTimeout(5000,s=>s.destroy());server.listen(0,ip);await once(server,'listening');
    const stop=()=>{clearTimeout(timer);server.close();server.closeAllConnections();};
    const timer=setTimeout(stop,TTL);timer.unref();
    const base='http://'+(ip.includes(':')?'['+ip+']':ip)+':'+server.address().port;
    const q=new URLSearchParams({name,url:base,node:identity.node,key:identity.key,tier:'DESKTOP',exp:String(expires)});
    return {link:'meshlit://pair?'+q.toString(),stop};
}
function register(vscode,context,hooks) {
    let hosting,identityPromise;
    async function identity() {
        if(!identityPromise)identityPromise=(async()=>{const saved=await context.secrets.get('pair-identity');if(saved)return JSON.parse(saved);const value=newIdentity();await context.secrets.store('pair-identity',JSON.stringify(value));return value;})().catch(e=>{identityPromise=undefined;throw e;});
        return identityPromise;
    }
    const peers=()=>context.globalState.get('verified-peers',[]);
    const {Devices}=require('./device-service.cjs'),{deviceId}=require('./device-tls.cjs');
    let service,starting,acceptUntil=0;
    const grants=key=>context.globalState.get('model-access:'+deviceId(key),[]);
    const serving=require('./remote-models.cjs').serving(hooks.root,hooks.models,grants,hooks.threads,hooks.log);
    const {Wallet,walletId,validUUID}=require('./meshcoin.cjs');
    let walletInstance;
    async function wallet(){
        const id=await identity();
        if(!walletInstance)walletInstance=new Wallet(require('node:path').join(context.globalStorageUri.fsPath,'contributions'),id);
        return walletInstance;
    }
    function audit(event,key){
        if(!service)return;
        const session=service.session;
        wallet().then(w=>w.audit(session,event,key?walletId(key):'')).catch(e=>hooks.log('Node audit: '+e.message));
    }
    async function nodeDetails(peer){
        const id=await identity(),w=await wallet();let card;
        if(peer&&service){
            const response=await service.call(service.locate(peer),{op:'identity'},AbortSignal.timeout(10000));
            card=response.identity;
            if(card?.contributor!==walletId(peer.key)||card.node!==peer.node||!validUUID(card.session))throw Error('The peer did not return a matching live node identity');
            audit('peer.identity.verified',peer.key);
        }else if(peer)throw Error('Enable device networking to check this peer’s live session.');
        else card=service?.identityCard()||{application:'bd.com.imon.meshlit.studio',version:require('./package.json').version,node:id.node,contributor:walletId(id.key),session:null};
        const doc=await vscode.workspace.openTextDocument({language:'json',content:JSON.stringify({identity:card,localAudit:w.events().filter(e=>!peer||e.peer===card.contributor)},null,2)});
        await vscode.window.showTextDocument(doc,{preview:true});
    }
    const showWallet=require('./wallet-view.cjs').walletView(vscode,context,wallet,peers,()=>nodeDetails(),()=>service,require('./legacy/brand.cjs'));
    async function hostedGeneration(key,msg,signal,delta){
        const w=await wallet();
        const policy=w.config('record',false)?w.config('keeper',{}):null;
        const job=validUUID(msg.jobId)?msg.jobId:crypto.randomUUID();
        const requestId=crypto.createHash('sha256').update(key+'\n'+job).digest('hex');
        audit('inference.started',key);
        try{
            const result=await serving.generate(key,msg,signal,delta);signal.throwIfAborted();
            if(policy!==null)try{w.record(requestId,result.count,policy);}catch(e){hooks.log('Contribution was not recorded: '+e.message);}
            audit('inference.completed',key);return result;
        }catch(e){audit('inference.interrupted',key);throw e;}
    }
    let writes=Promise.resolve();
    function save(peer) {
        const work=writes.then(async()=>{
            const list=peers(),previous=list.find(p=>p.node===peer.node||p.key===peer.key);
            if(previous&&previous.key!==peer.key)throw Error('Device identity changed');
            if(!previous&&list.length>=64)throw Error('Forget a device before pairing another');
            const entry={...previous,...peer,fingerprint:crypto.createHash('sha256').update(Buffer.from(peer.key,'hex')).digest('hex'),verifiedAt:Date.now(),expires:null};
            await context.globalState.update('verified-peers',[...list.filter(p=>p!==previous),entry]);
        });
        writes=work.catch(()=>{});return work;
    }
    async function start() {
        if(service)return service;
        if(!starting)starting=(async()=>{
            const id=await identity();
            const next=new Devices(id,{peers,save,accepting:()=>Date.now()<acceptUntil,
                approve:async(name,code)=>await vscode.window.showInformationMessage('Pair with '+name+'? Device ID '+code,{modal:true},'Pair device')==='Pair device',
                inventory:serving.inventory,generate:hostedGeneration,wallet,audit});
            await next.start();service=next;audit('network.started');
            const url=context.globalState.get('device-relay');
            if(url){
                const token=await context.secrets.get('device-relay-token');
                if(token){next.relay=new (require('./device-relay.cjs').Relay)(url,token,id,next.port);next.relay.start().catch(()=>next.relay?.retry());}
            }
            return next;
        })().finally(()=>{starting=undefined;});
        return starting;
    }
    function stop(){audit('network.stopped');service?.stop();service=undefined;serving.stop();}
    async function connect(item) {
        const svc=await start(), signal=AbortSignal.timeout(90000);
        const reply=await svc.call(item,{op:'pair',node:svc.node,name:svc.name},signal);
        if(reply.ok!==true||typeof reply.name!=='string'||reply.name.length>128||!/^[a-f0-9-]{36}$/i.test(reply.node))throw Error('Invalid device response');
        await save({name:reply.name,key:reply.key,node:reply.node});
        vscode.window.showInformationMessage('Paired with '+reply.name);
    }
    async function nearby(svc) {
        const picker=vscode.window.createQuickPick();picker.title='Nearby devices · choose one to pair';
        picker.placeholder='Open Devices and enable nearby pairing on the other computer';
        const refresh=()=>{
            const candidates=[...svc.nearby.values()].filter(p=>Date.now()-p.seen<15000);
            picker.items=candidates.map(p=>({label:p.name,description:p.id,device:p}));picker.busy=!candidates.length;
        };
        const selected=await new Promise(resolve=>{
            const timer=setInterval(refresh,1000);refresh();
            picker.onDidAccept(()=>{if(picker.selectedItems[0]){resolve(picker.selectedItems[0].device);picker.hide();}});
            picker.onDidHide(()=>{clearInterval(timer);resolve(undefined);picker.dispose();});picker.show();
        });
        if(selected)await connect(selected);
    }
    async function chooseRemote(peer){
        const svc=await start();
        const {models}=await svc.call(svc.locate(peer),{op:'models'},AbortSignal.timeout(15000));
        if(!Array.isArray(models)||!models.length)throw Error('No models shared. On '+peer.name+', open Devices, choose this computer and select Share models.');
        const choice=await vscode.window.showQuickPick(models.filter(m=>typeof m.name==='string'&&typeof m.id==='string').slice(0,128).map(m=>({label:m.name,id:m.id})),{title:'Use a model on '+peer.name});
        if(choice)await hooks.select({kind:'peer',device:deviceId(peer.key),hostName:peer.name,model:choice.label,remoteModel:choice.id});
    }
    async function receive(link) {
        if(!link)return;
        const peer=parseInvite(link),previous=peers().find(p=>p.node===peer.node);
        if(previous&&previous.key!==peer.key)throw Error('This device has changed its identity key. Verify with the owner before forgetting the former identity.');
        if(await vscode.window.showWarningMessage(peer.name+'\n'+peer.base+'\nSHA-256 '+peer.fingerprint,{modal:true},'Verify device')!=='Verify device')return;
        await contact(peer);
        await save(peer);
        vscode.window.showInformationMessage(peer.name+' proved its identity. No remote-control permission was granted.');
    }
    async function manage(link) {
        if(link)return receive(link);
        if(service)acceptUntil=Date.now()+120000;
        const options=service?[
            {label:'Find nearby devices',op:'nearby'},
            {label:'Pair by device ID',op:'dial'},
            {label:'Copy my device ID',description:service.id,op:'copy'},
            {label:'Configure internet relay',description:context.globalState.get('device-relay',''),op:'relay'},
            {label:'Turn off device networking',op:'disable'}
        ]:[{label:'Enable nearby pairing',description:'Make this computer visible on the LAN',op:'enable'}];
        const pick=await vscode.window.showQuickPick([{label:'Node identity and audit',op:'identity'},{label:'MeshCoin contribution wallet',op:'wallet'},...options,{label:'Pair using a link',op:'receive'},{label:'Share this device for 10 minutes',op:'share'},...(hosting?[{label:'Stop sharing identity',op:'stop'}]:[]),...peers().map(peer=>({label:peer.name,description:deviceId(peer.key),peer}))],{title:'Devices'});
        if(!pick)return;
        if(pick.op==='identity')return nodeDetails();
        if(pick.op==='wallet')return showWallet();
        if(pick.op==='enable'){await start();await context.globalState.update('device-network',true);return manage();}
        if(pick.op==='disable'){stop();await context.globalState.update('device-network',false);return;}
        if(pick.op==='copy')return vscode.env.clipboard.writeText(service.id);
        if(pick.op==='nearby')return nearby(service);
        if(pick.op==='dial'){
            const input=await vscode.window.showInputBox({title:'Meshlit device ID',validateInput:v=>/^[a-f0-9]{20}$/i.test(v.trim())?undefined:'Enter the 20-character device ID'});
            if(input){const id=input.trim().toLowerCase();await connect(service.nearby.get(id)||{id});}return;
        }
        if(pick.op==='relay'){
            const url=await vscode.window.showInputBox({title:'Internet relay HTTPS origin (clear to disconnect)',value:context.globalState.get('device-relay','')});
            if(url===undefined)return;
            if(url){
                const {Relay,relayURL}=require('./device-relay.cjs');relayURL(url);
                const token=await vscode.window.showInputBox({title:'Relay access token',password:true});if(!token)return;
                const relay=new Relay(url,token,await identity(),service.port);
                await relay.start();service.relay?.stop();service.relay=relay;await context.secrets.store('device-relay-token',token);
            }else{service.relay?.stop();service.relay=undefined;await context.secrets.delete('device-relay-token');}
            await context.globalState.update('device-relay',url||undefined);return;
        }
        if(pick.op==='stop'){hosting?.stop();hosting=undefined;return;}
        if(pick.op==='receive')return receive(await vscode.window.showInputBox({title:'Pairing link from the other device',ignoreFocusOut:true}));
        if(pick.op==='share'){
            const choices=Object.entries(os.networkInterfaces()).flatMap(([name,addrs])=>(addrs||[]).filter(a=>!a.internal&&privateIp(a.address)&&!a.address.includes('%')&&!a.address.toLowerCase().startsWith('fe80:')).map(a=>({label:name+' · '+a.address,ip:a.address})));
            if(!choices.length)throw Error('No usable private network interface is available');
            const choice=await vscode.window.showQuickPick(choices,{title:'Network for identity verification'});if(!choice)return;
            hosting?.stop();hosting=await invite(await identity(),choice.ip,os.hostname().slice(0,128));
            await vscode.env.clipboard.writeText(hosting.link);vscode.window.showInformationMessage('Pairing link copied. Paste it on the other device within 10 minutes.');return;
        }
        const action=await vscode.window.showQuickPick([...(service?['Use a shared model','Share models','Live node identity']:[]),'Verify again','Forget device'],{title:pick.peer.name});
        if(action==='Live node identity')return nodeDetails(pick.peer);
        if(action==='Use a shared model')return chooseRemote(pick.peer);
        if(action==='Share models'){
            const key=pick.peer.key,choices=hooks.models().map(m=>({label:m.name,id:m.id,picked:grants(key).includes(m.id)}));
            const selected=await vscode.window.showQuickPick(choices,{title:'Models '+pick.peer.name+' may use on this computer',canPickMany:true});
            if(selected){serving.stop();await context.globalState.update('model-access:'+deviceId(key),selected.map(m=>m.id));}return;
        }
        if(action==='Verify again'){
            if(service)await service.call(service.locate(pick.peer),{op:'models'},AbortSignal.timeout(15000));
            else if(pick.peer.base)await contact(pick.peer);else throw Error('Enable device networking to verify this device');
            vscode.window.showInformationMessage('Identity verified: '+pick.peer.name);
        }
        if(action==='Forget device'){
            const w=await wallet(), id=walletId(pick.peer.key);
            w.set('share:'+id,false);
            if(w.config('keeper',null)?.keeper===id)w.set('keeper',null);
            serving.stop();await context.globalState.update('model-access:'+deviceId(pick.peer.key),undefined);
            await context.globalState.update('verified-peers',peers().filter(p=>p.key!==pick.peer.key));
        }
    }
    manage.chat=async(route,messages,options,signal,onDelta)=>{
        const peer=peers().find(p=>deviceId(p.key)===route.device);
        if(!peer||!service)throw Error('Open Devices and enable the paired connection');
        return service.call(service.locate(peer),{op:'chat',model:route.remoteModel,messages,...options},signal,onDelta);
    };
    manage.wallet=showWallet;
    manage.restore=()=>context.globalState.get('device-network',false)?start():Promise.resolve();
    context.subscriptions.push({dispose:()=>{hosting?.stop();stop();}});return manage;
}
module.exports={parseInvite,verify,contact,newIdentity,invite,register};
