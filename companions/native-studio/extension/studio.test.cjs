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
        assert.deepEqual(await fs.readdir(dir),[sha+'.gguf']);
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
    for(const id of ['back','permissions','web','voice','attach','guard','stop','send','feedback','metrics','prompt']){
        assert.equal(page.split('id="'+id+'"').length,2,id);
    }
});
