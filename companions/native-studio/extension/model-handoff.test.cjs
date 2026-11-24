'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {intent,candidates,handoffs,inputMessages}=require('./model-handoff.cjs');
const {request}=require('./runtime.cjs');
const mockRoute={kind:'Other OpenAI-compatible server',base:'http://127.0.0.1:1234/v1',model:'gemini-test'};
function fixture(){
    const state=new Map(),calls=[],choices=[];
    const options={routes:()=>[mockRoute],modelId:r=>[r.base,r.model].join('#'),configure:async()=>undefined};
    const ask=(...args)=>{calls.push(args);return choices.shift();};
    const globalState={get:k=>state.get(k),update:async(k,v)=>v===undefined?state.delete(k):state.set(k,v)};
    const api={window:{showQuickPick:ask,showInformationMessage:ask}};
    return {state,calls,choices,options,make:()=>handoffs(api,globalState,options)};
}
test('named requests preserve the task and ignore discussion of model names',()=>{
    assert.deepEqual(intent('Please ask Gemini to summarize A\nB'),{name:'Gemini',task:'summarize A\nB'});
    assert.deepEqual(intent('connect to ChatGPT.'),{name:'ChatGPT',connect:true});
    assert.deepEqual(intent('give a task to ChatGPT: write a poem'),{name:'ChatGPT',task:'write a poem'});
    assert.deepEqual(intent('tell OpenAI: what is 2+2?'),{name:'ChatGPT',task:'what is 2+2?'});
    for(const text of ['What is Gemini?','Please explain how to ask ChatGPT to read my file','Ask Geminius to start','ask Gemini to'])assert.equal(intent(text),undefined);
});
test('one-time approval prompts again; automatic approval survives restart and binds the exact route',async()=>{
    const f=fixture(),match=intent('ask Gemini to count');
    f.choices.push('Send once');assert.equal((await f.make().prepare(match)).automatic,false);
    f.choices.push('Always send named tasks');assert.equal((await f.make().prepare(match)).automatic,true);
    assert.equal((await f.make().prepare(match)).route,mockRoute);
    assert.equal(f.calls.length,2);
    f.options.routes=()=>[{...mockRoute,base:'https://changed.example/v1'}];
    assert.equal(await f.make().prepare(match),undefined);
    assert.equal(f.calls.length,3);
    await f.make().removed(f.options.modelId(mockRoute));assert.equal(f.state.size,0);
});
test('missing provider, denied permission and revoked automatic access never fall through to another route',async()=>{
    const f=fixture(),match=intent('ask ChatGPT to count');
    assert.equal(candidates('ChatGPT',[mockRoute,{kind:'peer',model:'gpt-test',base:mockRoute.base}]).length,0);
    assert.equal(await f.make().prepare(match),undefined);
    assert.equal(f.state.size,0);
    f.choices.push('Always send named tasks');await f.make().prepare({name:'Gemini',connect:true});
    f.choices.push({name:'Gemini'},'Forget connection and automatic permission');
    await f.make().manage();assert.equal(f.state.size,0);
});
test('saved automatic named tasks send only the addressed text over the compatible model API',async t=>{
    let seen;
    const server=require('node:http').createServer(async(req,res)=>{
        const chunks=[];for await(const chunk of req)chunks.push(chunk);
        seen={url:req.url,body:JSON.parse(Buffer.concat(chunks))};
        res.end(JSON.stringify({choices:[{message:{content:'fixture reply'}}]}));
    });
    await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>server.close());
    const f=fixture(),route={...mockRoute,base:'http://127.0.0.1:'+server.address().port+'/v1'};
    f.options.routes=()=>[route];
    const match=intent('tell Gemini to count to three');
    f.choices.push('Always send named tasks');await f.make().prepare(match);
    const grant=await f.make().prepare(match);
    const messages=inputMessages(match.task,[{role:'user',content:'private prior conversation'}],grant);
    const answer=await request(grant.route.base,'/chat/completions',null,{model:grant.route.model,messages},AbortSignal.timeout(5000));
    assert.deepEqual(seen,{url:'/v1/chat/completions',body:{model:'gemini-test',messages:[{role:'user',content:'count to three'}]}});
    assert.equal(answer.choices[0].message.content,'fixture reply');
});
test('voice and IDE agents require explicit persisted source grants for the exact route',async()=>{
    const f=fixture(),task=intent('ask Gemini to alphabetize A and B');
    f.state.set('named-model-handoff:Gemini',{id:f.options.modelId(mockRoute),automatic:true});
    for(const source of ['voice','agents'])assert.equal(f.make().automaticRoute('Gemini',source),undefined);
    f.choices.push('Always send named tasks');await f.make().prepare({...task,connect:true});
    assert.equal(f.make().automaticRoute('Gemini','voice'),mockRoute);
    assert.equal(f.make().automaticRoute('Gemini','agents'),undefined);
    f.choices.push('Always allow, including agents');await f.make().prepare({...task,connect:true});
    assert.equal(f.make().automaticRoute('Gemini','agents'),mockRoute);
    f.options.routes=()=>[{...mockRoute,model:'other'}];assert.equal(f.make().automaticRoute('Gemini','agents'),undefined);
    await f.make().removed(f.options.modelId(mockRoute));assert.equal(f.make().automaticRoute('Gemini','voice'),undefined);
});
test('programmatic tasks require literal named text and cannot request setup or ambient context',()=>{
    const {checkTask}=require('./model-handoff.cjs');
    assert.throws(()=>checkTask({model:'Gemini',task:'review'}));
    assert.throws(()=>checkTask('connect to Gemini'));
    assert.throws(()=>checkTask('ask ChatGPT to summarize this'),/Paste the text/);
    assert.equal(checkTask('ask ChatGPT to summarize this: text supplied directly').task,'summarize this: text supplied directly');
});
