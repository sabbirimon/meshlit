// SPDX-License-Identifier: Apache-2.0
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const {authorize,clientId}=require('./hf-oauth.cjs');
test('browser sign-in binds loopback state and the verifier to the code exchange',async()=>{
    let auth;
    const token=await authorize(async link=>{
        auth=new URL(link);
        assert.equal(auth.origin,'https://huggingface.co');
        const callback=new URL(auth.searchParams.get('redirect_uri'));
        assert.equal(callback.hostname,'127.0.0.1');
        callback.search=new URLSearchParams({code:'test-code',state:'wrong'});
        assert.equal((await fetch(callback)).status,400);
        callback.searchParams.set('state',auth.searchParams.get('state'));
        assert.equal((await fetch(callback)).status,200);
        return true;
    },AbortSignal.timeout(4000),async(url,options)=>{
        assert.equal(url,'https://huggingface.co/oauth/token');
        const form=options.body;
        assert.equal(form.get('client_id'),clientId);
        assert.equal(form.get('code'),'test-code');
        assert.equal(crypto.createHash('sha256').update(form.get('code_verifier')).digest('base64url'),auth.searchParams.get('code_challenge'));
        return Response.json({access_token:'hf_oauth_fixture',token_type:'Bearer'});
    });
    assert.equal(token,'hf_oauth_fixture');
});
test('Moltbook extracts bounded text from public posts and rejects errors',async()=>{
    const {feed}=require('./community.cjs');
    const posts=await feed(undefined,async()=>Response.json({success:true,posts:[{id:crypto.randomUUID(),title:'Test',content:'a'.repeat(2000)}]}));
    assert.equal(posts[0].text.length,1200);assert.match(posts[0].url,/^https:\/\/www\.moltbook\.com\/post\//);
    await assert.rejects(feed(undefined,async()=>new Response('',{status:429})),/429/);
});
