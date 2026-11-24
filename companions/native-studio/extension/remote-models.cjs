// SPDX-License-Identifier: Apache-2.0
'use strict';
const {LocalServer}=require('./runtime.cjs');
async function streamReply(route,messages,options,signal,delta) {
    const response=await fetch(route.base+'/chat/completions',{method:'POST',redirect:'error',signal,
        headers:{'Content-Type':'application/json',Authorization:'Bearer '+route.token},
        body:JSON.stringify({model:route.model,messages,max_tokens:options.maxTokens,temperature:options.temperature,stream:true,stream_options:{include_usage:true}})});
    if(!response.ok)throw Error('Model server returned HTTP '+response.status);
    const decoder=new TextDecoder();let buffer='',bytes=0,count,rate;
    for await(const chunk of response.body){
        bytes+=chunk.length;if(bytes>4*1024*1024)throw Error('Model response exceeds the Studio limit');
        buffer+=decoder.decode(chunk,{stream:true});let n;
        while((n=buffer.indexOf('\n'))>=0){
            const line=buffer.slice(0,n).trim();buffer=buffer.slice(n+1);
            if(!line.startsWith('data:'))continue;
            const data=line.slice(5).trim();
            if(data==='[DONE]')return {count,rate};
            const event=JSON.parse(data);
            if(event.error)throw Error('Model generation failed');
            if(event.usage?.completion_tokens!=null)count=event.usage.completion_tokens;
            if(event.timings?.predicted_per_second!=null)rate=event.timings.predicted_per_second;
            const text=event.choices?.[0]?.delta?.content;if(typeof text==='string'&&text)await delta(text);
        }
    }
    throw Error('Model stream ended early');
}
function serving(root,available,grants,threads,log){
    let current,server,timer;
    function stop(){clearTimeout(timer);server?.stop();server=undefined;current=undefined;}
    function inventory(key){return available().filter(m=>grants(key).includes(m.id)).map(({id,name})=>({id,name}));}
    async function generate(key,msg,signal,delta){
        const model=available().find(m=>m.id===msg.model&&grants(key).includes(m.id));
        if(!model)throw Error('The host has not shared this model with this device');
        if(!Array.isArray(msg.messages)||msg.messages.length<1||msg.messages.length>9||msg.messages.some(m=>!m||!['user','assistant'].includes(m.role)||typeof m.content!=='string'||m.content.length>50000)||
            !Number.isInteger(msg.maxTokens)||msg.maxTokens<1||msg.maxTokens>128||!Number.isFinite(msg.temperature)||msg.temperature<0||msg.temperature>2)throw Error('Invalid chat request');
        clearTimeout(timer);
        if(current!==model.id){stop();server=new LocalServer(root);current=model.id;}
        const instance=server;
        log('Loading '+model.name+' for a paired device');
        try {
            const route=await instance.start(signal,threads(),model.route.kind==='import'?model.route:undefined);
            const result=await streamReply(route,msg.messages,msg,signal,delta);
            log('Finished '+model.name+' · '+(result.count??'unreported')+' completion tokens');
            return result;
        }catch(e){log('Generation ended · '+e.message.slice(0,120));throw e;}
        finally{if(instance===server)timer=setTimeout(stop,120000);}
    }
    return {stop,inventory,generate};
}
module.exports={serving,streamReply};
