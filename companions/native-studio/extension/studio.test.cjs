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
