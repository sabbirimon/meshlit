// SPDX-License-Identifier: Apache-2.0
'use strict';
const tls = require('node:tls');
const crypto=require('node:crypto');
const {walletId}=require('./meshcoin.cjs');
const net = require('node:net');
const os = require('node:os');
const {once} = require('node:events');
const {credentials,deviceId,socketKey} = require('./device-tls.cjs');
const {discover} = require('./device-lan.cjs');
const MAX_FRAME = 256 * 1024;
function line(socket,value) {return socket.write(JSON.stringify(value)+'\n');}
async function* frames(socket) {
    let pending = Buffer.alloc(0);
    for await(const chunk of socket.iterator({destroyOnReturn:false})) {
        pending=Buffer.concat([pending,chunk]);
        let split;
        while((split=pending.indexOf(10))>=0) {
            if(split>MAX_FRAME)throw Error('Device message too large');
            const item=JSON.parse(pending.subarray(0,split).toString());
            pending=pending.subarray(split+1);
            yield item;
        }
        if(pending.length>MAX_FRAME)throw Error('Device message too large');
    }
    if(pending.length)throw Error('Incomplete device message');
}
class Devices {
    constructor(identity,hooks) {
        this.tls=credentials(identity);this.key=identity.key;this.id=deviceId(identity.key);
        this.session=crypto.randomUUID();this.startedAt=new Date().toISOString();
        this.name=os.hostname().slice(0,128);this.node=identity.node;
        this.hooks=hooks;this.nearby=new Map();this.sockets=new Set();this.pendingApproval=false;
        this.lastPrompt=new Map();
    }
    async start({host='0.0.0.0',advertise=true}={}) {
        this.server=tls.createServer({...this.tls,minVersion:'TLSv1.3',requestCert:true,rejectUnauthorized:false,handshakeTimeout:5000},
            socket=>this.serve(socket));
        this.server.maxConnections=12;
        this.server.on('tlsClientError',()=>{});
        this.server.listen(0,host);await once(this.server,'listening');this.port=this.server.address().port;
        if(advertise) {
            try {this.stopDiscovery=await discover({id:this.id,name:this.name},this.port,p=>{
                for(const [id,old] of this.nearby)if(Date.now()-old.seen>15000)this.nearby.delete(id);
                if(this.nearby.size<128||this.nearby.has(p.id))this.nearby.set(p.id,p);
            });} catch(e){this.stop();throw e;}
        }
    }
    identityCard(){
        return {application:'bd.com.imon.meshlit.studio',version:require('./package.json').version,
            node:this.node,contributor:walletId(this.key),device:this.id,session:this.session,startedAt:this.startedAt};
    }
    known(key) {return this.hooks.peers().find(p=>p.key===key);}
    async serve(socket) {
        this.sockets.add(socket);socket.on('error',()=>{});
        const controller=new AbortController();
        socket.once('end',()=>controller.abort());
        socket.once('close',()=>{controller.abort();this.sockets.delete(socket);});
        socket.setTimeout(240000,()=>socket.destroy());
        const first=setTimeout(()=>socket.destroy(),5000);
        try {
            const key=socketKey(socket),id=deviceId(key);
            for await(const msg of frames(socket)) {
                clearTimeout(first);
                const peer=this.known(key);
                if(msg.op==='pair'){
                    if(!/^[a-f0-9-]{36}$/i.test(msg.node)||typeof msg.name!=='string'||!msg.name||msg.name.length>128||/[\x00-\x1f]/.test(msg.name))throw Error('Invalid device');
                    if(!peer) {
                        const now=Date.now();
                        for(const [k,t] of this.lastPrompt)if(t+60000<now)this.lastPrompt.delete(k);
                        if(!this.hooks.accepting()||this.pendingApproval||this.lastPrompt.has(key)||this.lastPrompt.size>=8)throw Error('Open Devices on the receiving computer and retry');
                        this.lastPrompt.set(key,now);this.pendingApproval=true;
                        try {
                            if(!await this.hooks.approve(msg.name,id))throw Error('Pairing declined');
                            controller.signal.throwIfAborted();
                            await this.hooks.save({key,node:msg.node,name:msg.name});
                        } finally {this.pendingApproval=false;}
                    }
                    line(socket,{ok:true,name:this.name,node:this.node});
                    this.hooks.audit?.('pair.verified',key);
                }else{
                    if(!peer)throw Error('Pair this device first');
                    if(msg.op==='identity'){line(socket,{identity:this.identityCard()});this.hooks.audit?.('identity.verified',key);}
                    else if(msg.op==='walletPage'&&this.hooks.wallet){const wallet=await this.hooks.wallet();line(socket,{blocks:wallet.page(key,msg.height,msg.anchor)});}
                    else if(msg.op==='walletAck'&&this.hooks.wallet){const wallet=await this.hooks.wallet();wallet.acknowledge(key,msg.hash,msg.proof);line(socket,{ok:true});}
                    else if(msg.op==='models')line(socket,{models:this.hooks.inventory(key)});
                    else if(msg.op==='chat'){
                        if(this.generating)throw Error('Host is busy');
                        this.generating=true;
                        try {
                            const result=await this.hooks.generate(key,msg,controller.signal,async text=>{
                                if(!line(socket,{text}))await once(socket,'drain',{signal:controller.signal});
                            });
                            line(socket,{done:true,...result});
                        } finally {this.generating=false;}
                    }else throw Error('Unknown device action');
                }
                socket.end();return;
            }
        }catch(e){if(!socket.destroyed)socket.end(JSON.stringify({error:e.message.slice(0,200)})+'\n');}
        finally{clearTimeout(first);}
    }
    async call(device,msg,signal,onDelta=()=>{}) {
        signal.throwIfAborted();
        let raw;
        if(device.host) {
            raw=net.connect({host:device.host,port:device.port});
            try{await once(raw,'connect',{signal:AbortSignal.any([signal,AbortSignal.timeout(3500)])});}
            catch(e){raw.destroy();if(!this.relay)throw e;raw=undefined;}
        }
        if(!raw){if(!this.relay)throw Error('Device is offline. Open Devices on the host or connect a relay.');raw=await this.relay.connect(device.id,signal);}
        const socket=tls.connect({...this.tls,socket:raw,rejectUnauthorized:false});
        const abort=()=>socket.destroy();signal.addEventListener('abort',abort,{once:true});socket.on('error',()=>{});
        try{
            await once(socket,'secureConnect',{signal:AbortSignal.any([signal,AbortSignal.timeout(5000)])});
            const key=socketKey(socket);
            if(deviceId(key)!==device.id || device.key && device.key!==key)throw Error('Device identity changed');
            line(socket,msg.op==='chat'&&!msg.jobId?{...msg,jobId:crypto.randomUUID()}:msg);
            for await(const answer of frames(socket)){
                if(answer.error)throw Error(answer.error);
                if(typeof answer.text==='string'){onDelta(answer.text);continue;}
                return {...answer,key};
            }
            throw Error('Device disconnected before completing the request');
        }finally{signal.removeEventListener('abort',abort);socket.destroy();}
    }
    locate(peer){
        const id=deviceId(peer.key);const recent=this.nearby.get(id);
        return {...(recent&&Date.now()-recent.seen<15000?recent:{}),id,key:peer.key,name:peer.name};
    }
    stop(){this.stopDiscovery?.();this.relay?.stop();for(const s of this.sockets)s.destroy();this.server?.close();}
}
module.exports={Devices};
