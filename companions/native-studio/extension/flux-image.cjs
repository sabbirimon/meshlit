'use strict';
const {randomInt}=require('node:crypto');
const origin='https://black-forest-labs-flux-1-schnell.hf.space';
const infer=origin+'/gradio_api/call/infer';
async function read(response,limit,check){
    if(!response.ok)throw Error('Hugging Face image service returned HTTP '+response.status+'.');
    if(Number(response.headers.get('content-length'))>limit)throw Error('Image service response too large.');
    const chunks=[];
    let size=0;
    for await(const chunk of response.body){
        check();
        size+=chunk.length;
        if(size>limit)throw Error('Image service response too large.');
        chunks.push(chunk);
    }
    return Buffer.concat(chunks);
}
async function generate(prompt,check,signal,fetcher=fetch){
    if(typeof prompt!=='string'||!prompt.trim()||prompt.length>2000)throw Error('Enter an image prompt of up to 2,000 characters.');
    async function get(url,options,limit){
        check();
        signal.throwIfAborted();
        const res=await fetcher(url,{...options,signal,redirect:'error',credentials:'omit'});
        const bytes=await read(res,limit,check);
        check();
        return bytes;
    }
    const start=await get(infer,{method:'POST',headers:{'content-type':'application/json'},
        body:JSON.stringify({data:[prompt.trim(),randomInt(2147483647),false,512,512,4]})},8192);
    const id=JSON.parse(start).event_id;
    if(typeof id!=='string'||!/^[a-zA-Z0-9-]{1,128}$/.test(id))throw Error('The image service returned an invalid job ID.');
    const events=(await get(infer+'/'+id,{},256*1024)).toString();
    const complete=/^event: *complete\r?\ndata: *([^\r\n]+)/m.exec(events);
    if(!complete)throw Error('The free image service did not complete this request. Its queue or daily quota may be full.');
    const address=JSON.parse(complete[1])?.[0]?.url;
    const url=new URL(address);
    if(url.origin!==origin || url.username || url.password || !url.pathname.startsWith('/gradio_api/file='))throw Error('Unexpected image service file URL.');
    const data=await get(url.href,{},8*1024*1024);
    let mimeType;
    if(data.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))mimeType='image/png';
    if(data[0]===255 && data[1]===216 && data[2]===255)mimeType='image/jpeg';
    if(data.toString('ascii',0,4)==='RIFF' && data.toString('ascii',8,12)==='WEBP')mimeType='image/webp';
    if(!mimeType)throw Error('The service did not return a supported image.');
    return {data,mimeType};
}
module.exports={generate,origin};
