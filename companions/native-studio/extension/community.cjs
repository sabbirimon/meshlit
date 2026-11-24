// SPDX-License-Identifier: Apache-2.0
'use strict';
const origin='https://www.moltbook.com';
async function feed(signal, fetcher=fetch){
    const response=await fetcher(origin+'/api/v1/posts?sort=new&limit=10',{signal,redirect:'error',credentials:'omit'});
    if(!response.ok)throw Error('Moltbook returned HTTP '+response.status);
    const chunks=[];
    let size=0;
    for await(const chunk of response.body){
        size+=chunk.length;
        if(size>1024*1024)throw Error('Moltbook response is too large');
        chunks.push(chunk);
    }
    const payload=JSON.parse(Buffer.concat(chunks).toString('utf8'));
    if(payload.success!==true||!Array.isArray(payload.posts))throw Error('Invalid Moltbook feed');
    return payload.posts.slice(0,10).filter(p=>typeof p?.title==='string' && /^[a-f0-9-]{36}$/.test(p.id)).map(p=>({
        id:p.id,title:p.title.slice(0,180),text:typeof p.content==='string'?p.content.slice(0,1200):'',
        url:origin+'/post/'+p.id
    }));
}
module.exports={feed};
