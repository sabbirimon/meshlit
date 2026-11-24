// SPDX-License-Identifier: Apache-2.0
'use strict';
const http = require('node:http');
const crypto = require('node:crypto');
const clientId = 'https://cdn.jsdelivr.net/gh/sabbirimon/meshlit@hf-oauth-v1/docs/oauth-client.json';
const origin = 'https://huggingface.co';

async function authorize(open, signal, fetcher = fetch) {
    const verifier = crypto.randomBytes(32).toString('base64url');
    const state = crypto.randomBytes(32).toString('base64url');
    const server = http.createServer({maxHeaderSize:8192,requestTimeout:5000});
    let redirect;
    try {
        await new Promise((resolve,reject) => {
            server.once('error',reject);
            server.listen(0,'127.0.0.1',resolve);
        });
        redirect = 'http://127.0.0.1:'+server.address().port+'/hf/callback';
        const auth = new URL('/oauth/authorize', origin);
        auth.search = new URLSearchParams({client_id:clientId,response_type:'code',redirect_uri:redirect,
            scope:'profile read-repos gated-repos',state,code_challenge_method:'S256',
            code_challenge:crypto.createHash('sha256').update(verifier).digest('base64url')});
        const code = await new Promise((resolve,reject) => {
            const abort = () => reject(Error('Hugging Face sign-in expired or was cancelled.'));
            signal.addEventListener('abort',abort,{once:true});
            if(signal.aborted)return abort();
            server.on('request',(req,res) => {
                const url = URL.parse(req.url,redirect);
                const valid = url && req.method==='GET' && req.headers.host===new URL(redirect).host &&
                    url.origin===new URL(redirect).origin && url.pathname==='/hf/callback' && url.searchParams.get('state')===state;
                res.writeHead(valid?200:400, {'Content-Type':'text/plain; charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer'});
                res.end(valid?'Return to Meshlit to see the sign-in result.':'Invalid sign-in callback.');
                if(!valid)return;
                signal.removeEventListener('abort',abort);
                const value=url.searchParams.get('code');
                if(value && value.length<=4096 && !url.searchParams.has('error'))resolve(value);
                else reject(Error('Hugging Face authorization was declined.'));
            });
            Promise.resolve().then(()=>open(auth.href)).then(opened=>{if(!opened)reject(Error('Could not open the system browser.'));},reject);
        });
        const response = await fetcher(origin+'/oauth/token',{method:'POST',redirect:'error',signal,
            body:new URLSearchParams({grant_type:'authorization_code',client_id:clientId,code,redirect_uri:redirect,code_verifier:verifier})});
        if(!response.ok)throw Error('Hugging Face token exchange returned HTTP '+response.status);
        const result=await response.json();
        if(!/^Bearer$/i.test(result.token_type)||typeof result.access_token!=='string'||!/^hf_[^\s]{1,508}$/.test(result.access_token))throw Error('Invalid Hugging Face access token response.');
        return result.access_token;
    } finally { server.close();server.closeAllConnections(); }
}
module.exports={authorize,clientId};
