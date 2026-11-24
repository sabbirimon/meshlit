const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),os=require('node:os'),path=require('node:path'),crypto=require('node:crypto');
const {parseInvite,invite,newIdentity,contact,verify}=require('./pairing.cjs');
const {displayOutput,imported}=require('./catalog.cjs');
test('Android-format P-256 proof over live HTTP; wrong key, expiry and replay rejected',async()=>{
 const id=newIdentity(),host=await invite(id,'127.0.0.1','Workstation');
 try {
   const parsed=parseInvite(host.link);await contact(parsed);
   await assert.rejects(contact({...parsed,key:newIdentity().key}));
   assert.throws(()=>parseInvite(host.link,parsed.expires+1));
   assert.throws(()=>parseInvite(host.link+'&node='+id.node));
   const nonce=crypto.randomBytes(32).toString('hex');
   const response=await fetch(parsed.base+'/v1/identity?nonce='+nonce);
   const proof=await response.json();
   verify(parsed,nonce,proof);
   assert.throws(()=>verify(parsed,'0'.repeat(64),proof));
 }finally{host.stop();}
});
test('output filter on/off leaves unrelated text and original input intact',()=>{
 const source='Hello a@example.test · ghp_12345678901234567890123';
 assert.equal(displayOutput(source,true),'Hello [email hidden] · [token hidden]');
 assert.equal(displayOutput(source,false),source);
});
test('GGUF registration verifies header and hashes without copying model files',async()=>{
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'meshlit-import-'));
 try {const p=path.join(dir,'sample.gguf'),data=Buffer.alloc(64);data.write('GGUF');data.writeUInt32LE(3,4);
 await fs.writeFile(p,data);assert.equal((await imported(p)).sha256,crypto.createHash('sha256').update(data).digest('hex'));
 await fs.writeFile(p,Buffer.alloc(64));await assert.rejects(imported(p));}finally{await fs.rm(dir,{recursive:true});}
});
test('Hub discovery pins eligible GGUF revisions and downloads verified bytes',async()=>{
    const {files,download}=require('./hub.cjs');
    const data=Buffer.alloc(64);data.write('GGUF');data.writeUInt32LE(3,4);
    const sha=crypto.createHash('sha256').update(data).digest('hex');
    const entry={rfilename:'model/sample.gguf',lfs:{sha256:sha},size:data.length};
    const [item]=files('team/project',{sha:'f'.repeat(40),siblings:[entry,{...entry,rfilename:'model-00001-of-00002.gguf'},{...entry,rfilename:'../bad.gguf'}]});
    assert.match(item.url,/\/resolve\/f{40}\/model\/sample.gguf$/);
    const dir=await fs.mkdtemp(path.join(os.tmpdir(),'meshlit-hub-'));
    try {
        const get=async()=>new Response(data),signal=new AbortController().signal;
        const route=await download(item,dir,signal,()=>{},get);
        assert.equal(route.sha256,sha);
        for(const bad of [{...item,sha:'0'.repeat(64)},{...item,bytes:10}])
            await assert.rejects(download(bad,dir,signal,()=>{},get));
        await assert.rejects(download(item,dir,AbortSignal.abort(),()=>{},get));
        assert.deepEqual((await fs.readdir(dir)).sort(),['.downloads',sha+'.gguf']);
    }finally{await fs.rm(dir,{recursive:true,force:true});}
});

test('file attachment accepts bounded UTF-8 text and rejects oversized or mislabeled images',async t=>{
    const {attachment}=require('./chat-tools.cjs');
    const dir=await fs.mkdtemp(path.join(os.tmpdir(),'studio-attach-'));
    t.after(()=>fs.rm(dir,{recursive:true,force:true}));
    const text=path.join(dir,'notes.txt'),image=path.join(dir,'image.jpg');
    await fs.writeFile(text,'example π');
    assert.deepEqual(await attachment(text),{name:'notes.txt',text:'example π'});
    await fs.writeFile(text,Buffer.alloc(32769));await assert.rejects(attachment(text),/32 KiB/);
    await fs.writeFile(image,'not image bytes');await assert.rejects(attachment(image),/signature/);
    await fs.writeFile(text,Buffer.from([255]));await assert.rejects(attachment(text));
});
test('chat webview contains one target for each action and the generated script parses',()=>{
    const page=require('./welcome.cjs').page('testnonce');
    const vm=require('node:vm');
    new vm.Script(page.match(/<script nonce="testnonce">([\s\S]*?)<\/script>/)[1]);
    for(const id of ['back','permissions','web','voice','attach','guard','stop','send','feedback','metrics','prompt','addMenu','accessMenu','addPanel','accessPanel','toolAccess','model','tuneChat']){
        assert.equal(page.split('id="'+id+'"').length,2,id);
    }
});

test('first-run fit reserves RAM and offers only a starter that fits',()=>{
    const {budget,recommended}=require('./discovery.cjs');
    const starters=require('../../../desktopApp/distribution/studio-models.json').models;
    const GiB=1024**3;
    assert.equal(recommended(starters,budget(8*GiB,GiB)),undefined);
    assert.equal(recommended(starters,budget(8*GiB,2*GiB)).id,'qwen25-1.5b');
    assert.equal(recommended(starters,budget(32*GiB,12*GiB)).id,'qwen3-4b');
});

test('discovery resolves only a revision-pinned variant returned for the current repository',async()=>{
    const {chooseFile,render}=require('./discovery.cjs');
    const {files}=require('./hub.cjs');
    const html=render('testnonce');
    new (require('node:vm').Script)(html.match(/<script nonce="testnonce">([\s\S]*?)<\/script>/)[1]);
    let receive,dispose;
    const posted=[],urls=[];
    const panel={webview:{onDidReceiveMessage:fn=>{receive=fn},postMessage:m=>posted.push(m)},onDidDispose:fn=>{dispose=fn},dispose:()=>dispose()};
    const vscode={ViewColumn:{Active:1},window:{createWebviewPanel:()=>panel}};
    const sha='a'.repeat(64);
    const promise=chooseFile(vscode,async url=>{
        urls.push(url);
        return url.includes('blobs=true')?{sha:'b'.repeat(40),siblings:[{rfilename:'checkpoint.gguf',size:256,lfs:{sha256:sha}}]}:[{id:'org/demo',likes:3}];
    },files);
    await receive({op:'search',q:'a & b',sort:'likes',seq:1});
    assert.equal(new URL(urls[0]).searchParams.get('search'),'a & b');
    await receive({op:'details',repo:'other/demo',seq:2});
    assert.equal(posted.at(-1).op,'error');
    assert.equal(urls.length,1);
    await receive({op:'details',repo:'org/demo',seq:3});
    assert.equal(posted.at(-1).files[0].bytes,256);
    await receive({op:'download',sha,seq:3});
    assert.match((await promise).url,/org\/demo\/resolve\/b{40}\/checkpoint.gguf$/);
});

test('LM Studio inventory uses native v1 and unloads the selected instance',async t=>{
    const {manage}=require('./lm-studio.cjs');
    const http=require('node:http'),seen=[];
    const server=http.createServer(async(req,res)=>{
        const parts=[];for await(const part of req)parts.push(part);
        seen.push([req.method,req.url,req.headers.authorization,Buffer.concat(parts).toString()]);
        res.setHeader('Content-Type','application/json');
        res.end(JSON.stringify(req.method==='GET'?{models:[{key:'org/demo',type:'llm',loaded_instances:[{id:'instance-2'}]}]}:{instance_id:'instance-2'}));
    });
    await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
    t.after(()=>new Promise(resolve=>server.close(resolve)));
    const vscode={window:{showQuickPick:async choices=>choices.find(c=>c.op==='unload'),showInformationMessage:()=>{}}};
    await manage(vscode,{kind:'LM Studio',base:'http://127.0.0.1:'+server.address().port+'/v1',model:'org/demo'},'test-only');
    assert.deepEqual(seen,[['GET','/api/v1/models','Bearer test-only',''],['POST','/api/v1/models/unload','Bearer test-only','{"instance_id":"instance-2"}']]);
});
test('import sources accept public Hugging Face repository links and list single-file library models',async()=>{
 const {repoInput}=require('./discovery.cjs'),{libraryFiles}=require('./catalog.cjs');
 assert.equal(repoInput('https://huggingface.co/owner/model/'),'owner/model');
 assert.equal(repoInput('owner/model'),'owner/model');
 assert.equal(repoInput('model name'),undefined);
 assert.throws(()=>repoInput('https://example.org/owner/model'));
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'studio-library-')),repo=path.join(dir,'owner','model');
 try{
  await fs.mkdir(repo,{recursive:true});
  for(const name of ['Q4.gguf','mmproj-model.gguf','large-00001-of-00002.gguf','notes.txt'])await fs.writeFile(path.join(repo,name),'');
  assert.deepEqual(await libraryFiles(dir),[path.join(repo,'Q4.gguf')]);
 }finally{await fs.rm(dir,{recursive:true,force:true});}
});

test('Hugging Face sign-in validates before saving and keeps credentials inside the Hub origin',async()=>{
    const {account,nextPage,modelRows}=require('./hugging-face.cjs');
    const store=new Map(),profile=new Map(),seen=[];
    const context={secrets:{get:k=>store.get(k),store:(k,v)=>store.set(k,v),delete:k=>store.delete(k)},globalState:{get:(k,d)=>profile.get(k)||d,update:(k,v)=>profile.set(k,v)}};
    let accepted=false;
    const hf=account({window:{showInputBox:async opts=>{assert.equal(opts.password,true);return 'unit-test-token'}}},context,async(url,init)=>{
        seen.push([url,init.headers.Authorization]);return new Response(JSON.stringify({name:'test-account'}),{status:accepted?200:401});
    });
    await assert.rejects(hf.signIn(),/rejected/);assert.equal(store.size,0);
    accepted=true;await hf.signIn();assert.equal(await hf.name(),'test-account');
    await hf.request('https://huggingface.co/api/models');assert.equal(seen.at(-1)[1],'Bearer unit-test-token');
    await assert.rejects(hf.request('https://example.org/'),/origin/);
    await hf.signOut();await hf.request('https://huggingface.co/api/models');assert.equal(seen.at(-1)[1],undefined);
    assert.equal(nextPage('<https://example.org/api/models?cursor=a>; rel="next"'),undefined);
    assert.match(nextPage('</api/models?cursor=abc>; rel="next"'),/^https:\/\/huggingface.co\/api\/models/);
    assert.equal(modelRows([{id:'community/weights',tags:['base_model:quantized:Qwen/test']}])[0].publisher,'Qwen');
});

test('wallet view loads during native Studio activation',()=>{
    assert.equal(typeof require('./wallet-view.cjs').walletView,'function');
});

test('Hub downloads resume at the saved byte and restart on a full response',async t=>{
    const {download}=require('./hub.cjs'),data=Buffer.alloc(256);data.write('GGUF');data.writeUInt32LE(3,4);
    const item={sha:crypto.createHash('sha256').update(data).digest('hex'),bytes:data.length,name:'weights.gguf',url:'https://huggingface.co/test/model'};
    const signal=new AbortController().signal;
    for(const ranges of [true,false]){
        const dir=await fs.mkdtemp(path.join(os.tmpdir(),'meshlit-resume-'));
        t.after(()=>fs.rm(dir,{force:true,recursive:true}));
        await assert.rejects(download(item,dir,signal,()=>{},async()=>new Response(data.subarray(0,96))),/resume/);
        const get=async(url,opts)=>{
            assert.equal(opts.headers.Range,'bytes=96-');
            return ranges?new Response(data.subarray(96),{status:206,headers:{'content-range':'bytes 96-255/256'}}):new Response(data);
        };
        assert.equal((await download(item,dir,signal,()=>{},get)).sha256,item.sha);
    }
});

test('encrypted chat history survives restart, isolates routes and forks concurrent continuations',async t=>{
    const {ChatHistory}=require('./chat-history.cjs'),keys=new Map();
    const secrets={get:k=>keys.get(k),store:(k,v)=>keys.set(k,v),delete:k=>keys.delete(k)};
    const dir=await fs.mkdtemp(path.join(os.tmpdir(),'meshlit-history-'));
    let w;
    t.after(async()=>{w?.close();await fs.rm(dir,{recursive:true,force:true});});
    const id=crypto.randomUUID(),row={title:'private prompt',messages:[{role:'user',content:'private words'}]};
    const first=new ChatHistory(dir,secrets);assert.equal(await first.save('A',id,0,row),undefined);
    first.enable(true);await first.save('A',id,0,row);first.close();
    w=new ChatHistory(dir,secrets);
    assert.equal((await w.recent('A'))[0][1].title,row.title);assert.equal((await w.recent('B')).length,0);
    assert.equal((await fs.readFile(path.join(dir,'chat-history.sqlite'))).includes(Buffer.from(row.title)),false);
    await w.save('A',id,1,row);const fork=await w.save('A',id,1,row);assert.notEqual(id,fork.id);
    await w.forget(id);assert.equal((await w.recent('A')).length,1);w.enable(false);assert.deepEqual(await w.recent('A'),[]);
});
