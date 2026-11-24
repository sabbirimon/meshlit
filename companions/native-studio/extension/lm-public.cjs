// SPDX-License-Identifier: Apache-2.0
'use strict';
const origin='https://lmstudio.ai';
const repo=/^[\w-][\w.-]*\/[\w-][\w.-]*$/;
const root=require('node:fs').existsSync(require('node:path').join(__dirname,'catalog-sdk'))?'./catalog-sdk':'../catalog-sdk';
const {parse}=require(root+'/node_modules/node-html-parser');
async function page(path,signal){
    const response=await fetch(origin+path,{signal,redirect:'error'});
    if(!response.ok)throw Error('LM Studio catalog returned HTTP '+response.status);
    const chunks=[];let length=0;
    for await(const chunk of response.body){
        length+=chunk.length;if(length>5*1024*1024)throw Error('LM Studio catalog page too large');
        chunks.push(chunk);
    }
    return parse(Buffer.concat(chunks).toString('utf8'),{blockTextElements:{script:false,style:false}});
}
function decodeList(doc){
    const rows=[];
    for(const link of doc.querySelectorAll('a[data-model-page-has-downloads="true"]')){
        const path=link.getAttribute('href');
        if(!/^\/models\/[\w.-]+$/.test(path))continue;
        const name=link.querySelector('h2')?.text.trim();if(!name)continue;
        const star=link.structuredText.match(/([\d,]+)\s+stars?\b/i);
        const caps=link.querySelectorAll('[data-model-page-capabilities] [title]').map(el=>({'Tool use':'tool-use','Reasoning':'reasoning','Vision':'vision'})[el.getAttribute('title')]).filter(Boolean);
        rows.push({id:path.slice(1),name,summary:link.querySelector('p')?.text.trim(),lmPublic:true,caps,
            downloads:Number(link.querySelector('[data-model-page-downloads]')?.getAttribute('data-model-page-downloads'))||0,likes:star?Number(star[1].replaceAll(',','')):undefined,
            lastModified:Date.parse(link.querySelector('time')?.getAttribute('dateTime'))||0});
    }
    if(!rows.length)throw Error('LM Studio returned no downloadable catalog entries. Open the Hugging Face Hub source to browse GGUF models.');
    return rows;
}
function publicCatalog(){
    let inventory;
    const summaries=new Map();
    async function summary(id,signal){
        if(!/^models\/[\w.-]+$/.test(id))throw Error('Unknown model catalog page');
        if(summaries.has(id))return summaries.get(id);
        const doc=await page('/'+id,signal);
        const artifacts=[...new Set((doc.querySelector('[data-model-page-artifacts]')?.querySelectorAll('a')||[])
            .map(a=>a.getAttribute('href')).filter(p=>/^\/models\/[\w.-]+\/[\w.-]+$/.test(p)))];
        const details={artifacts,publisher:artifacts[0]?.split('/')[2],readme:doc.querySelector('[data-model-page-markdown]')?.structuredText.slice(0,12000)};
        summaries.set(id,details);return details;
    }
    return {
        summary,
        async list(query,sort,signal){
            inventory ||=decodeList(await page('/models',signal));
            const terms=query.toLowerCase().split(/\s+/).filter(Boolean);
            const rows=inventory.filter(r=>terms.every(t=>(r.name+' '+r.summary).toLowerCase().includes(t)));
            if(sort)rows.sort((a,b)=>(b[sort]||0)-(a[sort]||0));
            return rows;
        },
        async details(row,signal){
            const result=await summary(row.id,signal);
            const sources=[];
            // A catalog family can have multiple parameter sizes. Preserve each concrete source.
            for(const artifact of result.artifacts.slice(0,24)){
                const doc=await page(artifact,signal);
                for(const a of doc.querySelectorAll('a[href]')){
                    const link=a.getAttribute('href');
                    if(!link.startsWith('https://huggingface.co/'))continue;
                    const id=link.slice('https://huggingface.co/'.length).replace(/\/$/,'');
                    if(repo.test(id)&&/gguf/i.test(id)&&!sources.includes(id))sources.push(id);
                }
            }
            return {...result,sources};
        }
    };
}
module.exports={publicCatalog,decodeList};
