// SPDX-License-Identifier: Apache-2.0
'use strict';
const net=require('node:net');
const crypto=require('node:crypto');
const {Duplex}=require('node:stream');
function relayURL(value) {
    const url=new URL(value);
    if(url.username||url.password||url.search||url.hash||!['/','/v1/devices'].includes(url.pathname))throw Error('Enter the relay HTTPS origin');
    if(url.protocol==='https:')url.protocol='wss:';
    else if(url.protocol==='http:'&&['127.0.0.1','[::1]'].includes(url.hostname))url.protocol='ws:';
    else throw Error('Use HTTPS for a remote relay');
    url.pathname='/v1/devices';return url.href;
}
function nextMessage(ws,signal) {
    return new Promise((resolve,reject)=>{
        function done(fn,v){ws.removeEventListener('message',msg);ws.removeEventListener('close',end);ws.removeEventListener('error',end);signal.removeEventListener('abort',end);fn(v);}
        const msg=e=>done(resolve,e.data),end=()=>done(reject,Error('Relay connection unavailable'));
        ws.addEventListener('message',msg);ws.addEventListener('close',end);ws.addEventListener('error',end);
        signal.addEventListener('abort',end,{once:true});if(signal.aborted)end();
    });
}
function stream(ws) {
    const duplex=new Duplex({
        read(){},
        write(bytes,enc,cb){try{if(ws.readyState!==1||ws.bufferedAmount>1024*1024)throw Error('Relay congested');ws.send(bytes);cb();}catch(e){cb(e);}},
        destroy(e,cb){ws.close();cb(e);},final(cb){ws.close();cb();}
    });
    ws.onmessage=e=>{if(typeof e.data==='string'||duplex.readableLength>1024*1024)return duplex.destroy(Error('Invalid relay stream'));duplex.push(Buffer.from(e.data));};
    ws.onclose=()=>duplex.push(null);ws.onerror=()=>duplex.destroy(Error('Relay disconnected'));
    duplex.on('error',()=>{});return duplex;
}
class Relay {
    constructor(url,token,identity,port) {
        this.url=relayURL(url);this.token=token;this.identity=identity;this.port=port;this.sockets=new Set();
    }
    async open(message,expected,signal) {
        const ws=new WebSocket(this.url);ws.binaryType='arraybuffer';this.sockets.add(ws);
        ws.addEventListener('close',()=>this.sockets.delete(ws),{once:true});
        try {
            const nonce=await nextMessage(ws,signal);
            if(typeof nonce!=='string'||!/^[a-f0-9]{64}$/.test(nonce))throw Error('Unexpected relay response');
            if(message.op==='register'){
                message.key=this.identity.key;
                message.sig=crypto.sign('sha256',Buffer.from('meshlit-relay-register:'+nonce),this.identity.privateKey).toString('hex');
            }
            const ready=nextMessage(ws,signal);ws.send(JSON.stringify({...message,token:this.token}));
            if(await ready!==expected)throw Error('Relay request failed');
            return ws;
        }catch(e){ws.close();throw e;}
    }
    async start(){
        const ws=await this.open({op:'register'},'registered',AbortSignal.timeout(10000));
        ws.onmessage=async e=>{
            try{
                const {ticket}=JSON.parse(e.data);if(!/^[0-9a-f]{48}$/.test(ticket)||this.sockets.size>16)return;
                const conn=stream(await this.open({op:'accept',ticket},'ready',AbortSignal.timeout(8000)));
                const local=net.connect(this.port,'127.0.0.1');
                local.on('error',()=>conn.destroy());local.on('close',()=>conn.destroy());conn.on('close',()=>local.destroy());
                conn.pipe(local).pipe(conn);
            }catch{}
        };
        ws.onclose=()=>{if(!this.stopped)this.retry();};
    }
    retry(){this.timer=setTimeout(()=>{if(!this.stopped)this.start().catch(()=>{if(!this.stopped)this.retry();});},5000);}
    async connect(id,signal){return stream(await this.open({op:'dial',id},'ready',AbortSignal.any([signal,AbortSignal.timeout(10000)])));}
    stop(){this.stopped=true;clearTimeout(this.timer);for(const ws of this.sockets)ws.close();}
}
module.exports={Relay,relayURL};
