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
function register(vscode,context) {
    let hosting,identityPromise;
    async function identity() {
        if(!identityPromise)identityPromise=(async()=>{const saved=await context.secrets.get('pair-identity');if(saved)return JSON.parse(saved);const value=newIdentity();await context.secrets.store('pair-identity',JSON.stringify(value));return value;})().catch(e=>{identityPromise=undefined;throw e;});
        return identityPromise;
    }
    const peers=()=>context.globalState.get('verified-peers',[]);
    async function receive(link) {
        if(!link)return;
        const peer=parseInvite(link),previous=peers().find(p=>p.node===peer.node);
        if(previous&&previous.key!==peer.key)throw Error('This device has changed its identity key. Verify with the owner before forgetting the former identity.');
        if(await vscode.window.showWarningMessage(peer.name+'\n'+peer.base+'\nSHA-256 '+peer.fingerprint,{modal:true},'Verify device')!=='Verify device')return;
        await contact(peer);
        const current=peers(),old=current.find(p=>p.node===peer.node);
        if(old&&old.key!==peer.key)throw Error('Saved identity changed during verification');
        if(!old&&current.length>=64)throw Error('Forget a saved device before adding another');
        await context.globalState.update('verified-peers',[...current.filter(p=>p.node!==peer.node),{...peer,expires:null,verifiedAt:Date.now()}]);
        vscode.window.showInformationMessage(peer.name+' proved its identity. No remote-control permission was granted.');
    }
    async function manage(link) {
        if(link)return receive(link);
        const pick=await vscode.window.showQuickPick([{label:'Pair using a link',op:'receive'},{label:'Share this device for 10 minutes',op:'share'},...(hosting?[{label:'Stop sharing identity',op:'stop'}]:[]),...peers().map(peer=>({label:peer.name,description:peer.base,peer}))],{title:'Paired devices'});
        if(!pick)return;
        if(pick.op==='stop'){hosting?.stop();hosting=undefined;return;}
        if(pick.op==='receive')return receive(await vscode.window.showInputBox({title:'Pairing link from the other device',ignoreFocusOut:true}));
        if(pick.op==='share'){
            const choices=Object.entries(os.networkInterfaces()).flatMap(([name,addrs])=>(addrs||[]).filter(a=>!a.internal&&privateIp(a.address)&&!a.address.includes('%')&&!a.address.toLowerCase().startsWith('fe80:')).map(a=>({label:name+' · '+a.address,ip:a.address})));
            if(!choices.length)throw Error('No usable private network interface is available');
            const choice=await vscode.window.showQuickPick(choices,{title:'Network for identity verification'});if(!choice)return;
            hosting?.stop();hosting=await invite(await identity(),choice.ip,os.hostname().slice(0,128));
            await vscode.env.clipboard.writeText(hosting.link);vscode.window.showInformationMessage('Pairing link copied. Paste it on the other device within 10 minutes.');return;
        }
        const action=await vscode.window.showQuickPick(['Verify again','Forget device'],{title:pick.peer.name});
        if(action==='Verify again'){await contact(pick.peer);vscode.window.showInformationMessage('Identity verified: '+pick.peer.name);}
        if(action==='Forget device')await context.globalState.update('verified-peers',peers().filter(p=>p.node!==pick.peer.node));
    }
    context.subscriptions.push({dispose:()=>hosting?.stop()});return manage;
}
module.exports={parseInvite,verify,contact,newIdentity,invite,register};
