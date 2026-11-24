// SPDX-License-Identifier: Apache-2.0
'use strict';
const origin = 'https://huggingface.co';
const secretKey = 'hugging-face.catalog.read-token';

function account(vscode, context, fetcher = fetch) {
    async function request(url, options = {}) {
        if (new URL(url).origin !== origin) throw Error('Unsupported Hugging Face request origin');
        const token = await context.secrets.get(secretKey);
        const headers = {...options.headers};
        if(token)headers.Authorization='Bearer '+token;
        return fetcher(url, {...options, headers, referrerPolicy:'no-referrer'});
    }
    async function profile(token) {
        const oauth=token.startsWith('hf_oauth_');
        const response = await fetcher(origin+(oauth?'/oauth/userinfo':'/api/whoami-v2'), {
            headers:{Authorization:'Bearer '+token}, redirect:'error', signal:AbortSignal.timeout(10000)
        });
        if (!response.ok) throw Error(response.status === 401 ? 'Hugging Face rejected the token. Use an active read token.' : 'Hugging Face account check returned HTTP '+response.status);
        const user = await response.json();
        const name=oauth?user.preferred_username:user.name;
        if (typeof name!=='string'||!/^[\w.-]+$/.test(name)) throw Error('Hugging Face returned an invalid account name.');
        return name;
    }
    return {
        request,
        async name() {
            if (!await context.secrets.get(secretKey)) return '';
            return context.globalState.get('hugging-face.account-name','');
        },
        async signIn() {
            const choice=await vscode.window.showQuickPick(['Sign in with Hugging Face in browser','Create a Hugging Face account','Use an existing read token','Open Hugging Face token settings'],{title:'Connect Hugging Face'});
            if(!choice)return;
            if(choice==='Create a Hugging Face account'){
                await vscode.env.openExternal(vscode.Uri.parse(origin+'/join'));
                return;
            }
            if(choice==='Open Hugging Face token settings'){
                await vscode.env.openExternal(vscode.Uri.parse(origin+'/settings/tokens'));
                return;
            }
            let value;
            if(choice==='Sign in with Hugging Face in browser'){
                value=await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'Waiting for Hugging Face sign-in',cancellable:true},async(_,cancel)=>{
                    const controller=new AbortController();
                    const listener=cancel.onCancellationRequested(()=>controller.abort());
                    try{return await require('./hf-oauth.cjs').authorize(url=>vscode.env.openExternal(vscode.Uri.parse(url)),AbortSignal.any([controller.signal,AbortSignal.timeout(180000)]));}
                    finally{listener.dispose();}
                });
            }else{
                value=await vscode.window.showInputBox({title:'Hugging Face access token',password:true,prompt:'Enter a read or fine-grained token',ignoreFocusOut:true});
            }
            if(!value)return;
            const token=value.trim(),name=await profile(token);
            await context.secrets.store(secretKey,token);
            await context.globalState.update('hugging-face.account-name',name);
        },
        async signOut() {
            await context.secrets.delete(secretKey);
            await context.globalState.update('hugging-face.account-name',undefined);
        }
    };
}

function nextPage(link) {
    for (const match of (link || '').matchAll(/<([^>]+)>;\s*rel="next"/g)) {
        const url=new URL(match[1],origin);
        if(url.origin===origin && url.pathname==='/api/models' && url.searchParams.has('cursor'))return url.href;
    }
}
function modelRows(rows) {
    return rows.filter(m=>typeof m.id==='string' && /^[\w-][\w.-]*\/[\w-][\w.-]*$/.test(m.id)).map(m=>{
        const tags=Array.isArray(m.tags)?m.tags:[];
        const base=tags.find(t=>t.startsWith('base_model:quantized:'))||tags.find(t=>/^base_model:[^:]+\//.test(t));
        const publisher=base?.split(':').pop().split('/')[0]||m.id.split('/')[0];
        return {id:m.id,publisher,summary:[m.pipeline_tag,publisher,Number.isFinite(m.downloads)?m.downloads.toLocaleString()+' downloads':''].filter(Boolean).join(' · '),
            caps:tags.filter(t=>['image-text-to-text','tool-use','reasoning'].includes(t)),likes:m.likes,downloads:m.downloads,lastModified:Date.parse(m.lastModified)||0};
    });
}
async function avatar(publisher, signal) {
    if(!/^[\w-][\w.-]{0,95}$/.test(publisher))return;
    for(const kind of ['organizations','users']){
        const response=await fetch(origin+'/api/'+kind+'/'+encodeURIComponent(publisher)+'/avatar',{signal,redirect:'error'});
        if(response.status===404)continue;
        if(!response.ok)return;
        const {avatarUrl}=await response.json();
        if(typeof avatarUrl==='string' && new URL(avatarUrl,origin).origin==='https://cdn-avatars.huggingface.co')return avatarUrl;
    }
}
module.exports={account,nextPage,modelRows,avatar};
