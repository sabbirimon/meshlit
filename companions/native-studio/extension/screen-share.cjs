'use strict';
const http = require('node:http');
const {randomBytes} = require('node:crypto');

// A fresh loopback origin lets the system browser offer its normal OS capture picker.
async function chooseScreenFrame(openBrowser, signal) {
    signal.throwIfAborted();
    const key = randomBytes(24).toString('hex');
    const route = '/'+key;
    let origin;
    let settle;
    const result = new Promise(resolve=>{settle=resolve});
    const server = http.createServer(async (req,res)=>{
        res.setHeader('Cache-Control','no-store');
        res.setHeader('Referrer-Policy','no-referrer');
        res.setHeader('X-Content-Type-Options','nosniff');
        const end=(status,body='')=>{res.writeHead(status);res.end(body)};
        if('http://'+req.headers.host!==origin || req.url!==route)return end(404);
        if(req.method==='GET'){
            res.setHeader('Content-Type','text/html; charset=utf-8');
            res.setHeader('Content-Security-Policy',`default-src 'none'; script-src 'nonce-${key}'; connect-src 'self'; media-src blob:; style-src 'nonce-${key}'; frame-ancestors 'none'; base-uri 'none'`);
            res.setHeader('Permissions-Policy','display-capture=(self), camera=(), microphone=()');
            return end(200,`<!doctype html><meta name="viewport" content="width=device-width"><title>Meshlit · Share screen</title>
<style nonce="${key}">body{font:17px system-ui;color-scheme:light dark;max-width:56rem;margin:8vh auto;padding:1rem}video{display:block;width:100%;max-height:56vh;margin:1rem 0}button{font:inherit;padding:.65em 1em;margin-right:.5em}</style>
<h1>Share a screen with Meshlit</h1><p id="status">Choose a screen or window. Attach one frame to your chat draft.</p>
<button id="choose">Choose screen</button><button id="attach" disabled>Attach this frame</button><button id="cancel">Cancel</button><video muted autoplay playsinline></video>
<script nonce="${key}">const by=id=>document.getElementById(id),video=document.querySelector('video');let stream;
function stop(){stream?.getTracks().forEach(t=>t.stop());stream=null;video.srcObject=null;by('attach').disabled=true}
window.addEventListener('pagehide',stop);
by('choose').onclick=async()=>{try{stop();stream=await navigator.mediaDevices.getDisplayMedia({video:true,audio:false});video.srcObject=stream;await video.play();stream.getVideoTracks()[0].onended=stop;by('attach').disabled=false}catch(e){by('status').textContent=e.message;stop()}};
by('cancel').onclick=async()=>{stop();await fetch(location.href,{method:'DELETE'});by('status').textContent='Sharing cancelled.';by('choose').disabled=true};
by('attach').onclick=async()=>{try{if(!video.videoWidth)return;const scale=Math.min(1,1280/Math.max(video.videoWidth,video.videoHeight));const c=document.createElement('canvas');c.width=Math.max(1,Math.round(video.videoWidth*scale));c.height=Math.max(1,Math.round(video.videoHeight*scale));c.getContext('2d').drawImage(video,0,0,c.width,c.height);stop();const frame=await new Promise(r=>c.toBlob(r,'image/jpeg',.82));const reply=await fetch(location.href,{method:'POST',body:frame});if(!reply.ok)throw Error('Capture expired or could not be attached');by('status').textContent='Frame attached. Return to Meshlit to review and send.';by('choose').disabled=true}catch(e){by('status').textContent=e.message}};
</script>`);
        }
        if(req.headers.origin!==origin)return end(403);
        if(req.method==='DELETE'){end(204);settle();return}
        if(req.method!=='POST'||req.headers['content-type']!=='image/jpeg')return end(415);
        const bytes=[],limit=2*1024*1024;
        let size=0;
        try{
            for await(const part of req){size+=part.length;if(size>limit)return end(413);bytes.push(part)}
            const frame=Buffer.concat(bytes);
            if(size<4 || frame.readUInt16BE(0)!==0xffd8 || frame.readUInt16BE(size-2)!==0xffd9)return end(400);
            end(204);
            settle({name:'Shared screen.jpg',mime:'image/jpeg',url:'data:image/jpeg;base64,'+frame.toString('base64')});
        }catch{if(!res.headersSent)end(400)}
    });
    const cancel=()=>settle();
    signal.addEventListener('abort',cancel,{once:true});
    try{
        await new Promise((ok,fail)=>{server.once('error',fail);server.listen(0,'127.0.0.1',ok)});
        signal.throwIfAborted();
        origin='http://127.0.0.1:'+server.address().port;
        if(!await openBrowser(origin+route))throw Error('Could not open the screen picker in your browser.');
        return await result;
    }finally{
        signal.removeEventListener('abort',cancel);
        server.close();server.closeIdleConnections();
    }
}
module.exports={chooseScreenFrame};
