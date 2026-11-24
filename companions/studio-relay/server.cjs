// SPDX-License-Identifier: Apache-2.0
'use strict';
const {WebSocketServer,WebSocket}=require('ws');
const crypto=require('node:crypto');
const http=require('node:http');
const {deviceId}=require('../native-studio/extension/device-tls.cjs');
// The public reverse proxy terminates WSS. Only the two Studios terminate
// the mutually authenticated TLS connection inside each binary WebSocket.
function broker(token) {
    if(typeof token!=='string'||token.length<32)throw Error('Set a relay access token of at least 32 characters');
    const digest=s=>crypto.createHash('sha256').update(s).digest();
    const server=http.createServer((_req,res)=>{res.writeHead(404);res.end();});
    const ws=new WebSocketServer({server,path:'/v1/devices',maxPayload:128*1024,perMessageDeflate:false});
    const hosts=new Map(),waiting=new Map();
    const close=s=>{if(s.readyState<2)s.terminate();};
    function bridge(a,b) {
        for(const [from,to] of [[a,b],[b,a]]) {
            from.on('message',(data,binary)=>{
                if(!binary||to.readyState!==WebSocket.OPEN||to.bufferedAmount>1024*1024)return close(from);
                to.send(data,{binary:true},err=>{if(err)close(from);});
            });
            from.once('close',()=>{if(to.readyState===WebSocket.OPEN)to.close();});
        }
        a.send('ready');b.send('ready');
    }
    ws.on('connection',socket=>{
        if(ws.clients.size>256)return close(socket);
        socket.on('error',()=>close(socket));
        const nonce=crypto.randomBytes(32).toString('hex');socket.send(nonce);
        const timer=setTimeout(()=>close(socket),8000);
        socket.once('close',()=>clearTimeout(timer));
        socket.once('message',(raw,binary)=>{
            try{
                if(binary||raw.length>2048)throw Error();
                const m=JSON.parse(raw);
                if(typeof m.token!=='string'||!crypto.timingSafeEqual(digest(m.token),digest(token)))throw Error();
                if(m.op==='register'){
                    if(!/^[0-9a-f]{182}$/.test(m.key))throw Error();
                    const key=crypto.createPublicKey({key:Buffer.from(m.key,'hex'),type:'spki',format:'der'});
                    if(key.asymmetricKeyDetails?.namedCurve!=='prime256v1'||!crypto.verify('sha256',Buffer.from('meshlit-relay-register:'+nonce),key,Buffer.from(m.sig,'hex')))throw Error();
                    const id=deviceId(m.key);closeIfPresent(hosts.get(id));hosts.set(id,socket);
                    socket.once('close',()=>{if(hosts.get(id)===socket)hosts.delete(id);});
                    socket.on('message',()=>close(socket));socket.send('registered');clearTimeout(timer);
                }else if(m.op==='dial'){
                    const host=hosts.get(m.id);if(!host||waiting.size>=64)throw Error();
                    const ticket=crypto.randomBytes(24).toString('hex');
                    waiting.set(ticket,socket);socket.once('close',()=>waiting.delete(ticket));
                    host.send(JSON.stringify({ticket}));
                    socket.relayTimer=timer;
                }else if(m.op==='accept'){
                    const dial=waiting.get(m.ticket);if(!dial)throw Error();
                    waiting.delete(m.ticket);clearTimeout(dial.relayTimer);clearTimeout(timer);bridge(dial,socket);
                }else throw Error();
            }catch{close(socket);}
        });
    });
    function closeIfPresent(s){if(s)close(s);}
    return {server,stop(){for(const s of ws.clients)close(s);ws.close();server.close();}};
}
if(require.main===module){
    const service=broker(process.env.MESHLIT_RELAY_TOKEN);
    service.server.listen(Number(process.env.PORT||8765),'127.0.0.1',()=>console.log('Studio relay listening on loopback'));
}
module.exports={broker};
