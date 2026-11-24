const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto'),net=require('node:net');
const {GatewayRuntime,SERVICES,environment,secretsFor,privateDirectory}=require('./gateway-runtime.cjs');
test('gateway environment isolates homes, secrets and public listeners',()=>{
 process.env.MESHLIT_TEST_PROVIDER_SECRET='never inherit';
 for(const service of ['OmniRoute','FreeLLMAPI']){
  const env=environment(service,'/private/gateway','/runtime','/usr/bin/node',31415,{JWT_SECRET:'j',API_KEY_SECRET:'a',STORAGE_ENCRYPTION_KEY:'s',ENCRYPTION_KEY:'e'});
  assert.equal(env.HOME,'/private/gateway');assert.equal(env.HOST,'127.0.0.1');assert.equal(env.HOSTNAME,'127.0.0.1');
  assert.equal(env.MESHLIT_TEST_PROVIDER_SECRET,undefined);assert.equal(env.NODE_OPTIONS,undefined);assert.equal(env.HTTP_PROXY,undefined);
  assert.equal(env.NODE_ENV,'production');
  if(service==='OmniRoute'){assert.equal(env.INITIAL_PASSWORD,'');assert.equal(env.OMNIROUTE_DISABLE_BACKGROUND_SERVICES,'true');}
  else assert.equal(env.CATALOG_SYNC_DISABLED,'1');
 }
 delete process.env.MESHLIT_TEST_PROVIDER_SECRET;
});
test('private gateway secrets survive restart and reject symlink substitution',()=>{
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'meshlit-gateway-secrets-'));
 try{
  privateDirectory(home);const first=secretsFor(home);assert.deepEqual(secretsFor(home),first);
  assert.equal(new Set(Object.values(first)).size,4);
  if(process.platform!=='win32')assert.equal(fs.statSync(path.join(home,'runtime-secrets.json')).mode&0o777,0o600);
  fs.renameSync(path.join(home,'runtime-secrets.json'),path.join(home,'saved.json'));
  fs.symlinkSync(path.join(home,'saved.json'),path.join(home,'runtime-secrets.json'));
  assert.throws(()=>secretsFor(home),/private/);
 }finally{fs.rmSync(home,{recursive:true,force:true});}
});
test('unknown and tampered gateway packages never launch',async()=>{
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'meshlit-gateway-fixture-'));
 try{
  const manager=new GatewayRuntime({},home);await assert.rejects(manager.start('OmniRoute'),/does not include/);
  fs.mkdirSync(path.join(home,'dist'));fs.writeFileSync(path.join(home,'package.json'),JSON.stringify({version:'3.8.52'}));
  fs.writeFileSync(path.join(home,'dist/server.js'),'throw Error("must not run")');
  const bad=new GatewayRuntime({OmniRoute:{root:home,node:process.execPath,sha256:'a'.repeat(64)}},path.join(home,'state'));
  await assert.rejects(bad.start('OmniRoute'),/checksum mismatch/);assert.equal(bad.jobs.size,0);
 }finally{fs.rmSync(home,{recursive:true,force:true});}
});
test('port conflicts never attach to or stop an existing service',async()=>{
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'meshlit-gateway-port-'));
 const server=net.createServer(),originalPort=SERVICES.OmniRoute.port;
 try{
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
  SERVICES.OmniRoute.port=server.address().port;
  fs.mkdirSync(path.join(home,'dist'));fs.writeFileSync(path.join(home,'package.json'),JSON.stringify({version:'3.8.52'}));
  const source='throw Error("must not run")';fs.writeFileSync(path.join(home,'dist/server.js'),source);
  const manager=new GatewayRuntime({OmniRoute:{root:home,node:process.execPath,sha256:crypto.createHash('sha256').update(source).digest('hex')}},path.join(home,'state'));
  await assert.rejects(manager.start('OmniRoute'),/already in use/);assert.equal(server.listening,true);
 }finally{SERVICES.OmniRoute.port=originalPort;await new Promise(resolve=>server.close(resolve));fs.rmSync(home,{recursive:true,force:true});}
});
test('owned fixture service starts once, stops, and cancels an unfinished startup',async()=>{
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'meshlit-gateway-lifecycle-')),originalPort=SERVICES.OmniRoute.port;
 const probe=net.createServer();await new Promise(resolve=>probe.listen(0,'127.0.0.1',resolve));const port=probe.address().port;await new Promise(resolve=>probe.close(resolve));
 const source="require('node:http').createServer((q,s)=>s.end('synthetic lifecycle fixture')).listen(Number(process.env.PORT),process.env.HOST)";
 fs.mkdirSync(path.join(home,'dist'));fs.writeFileSync(path.join(home,'package.json'),JSON.stringify({version:'3.8.52'}));fs.writeFileSync(path.join(home,'dist/server.js'),source);
 SERVICES.OmniRoute.port=port;
 const manager=new GatewayRuntime({OmniRoute:{root:home,node:process.execPath,sha256:crypto.createHash('sha256').update(source).digest('hex')}},path.join(home,'state'));
 try{
  const first=await manager.start('OmniRoute'),pid=manager.jobs.get('OmniRoute').child.pid;
  assert.equal(first.state,'running');await manager.start('OmniRoute');assert.equal(manager.jobs.get('OmniRoute').child.pid,pid);
  await manager.stop('OmniRoute');assert.equal(manager.status('OmniRoute').state,'stopped');assert.equal(manager.jobs.size,0);
  const starting=manager.start('OmniRoute');await manager.stop('OmniRoute');await assert.rejects(starting,/cancelled/);
  assert.equal(manager.status('OmniRoute').state,'stopped');assert.equal(manager.jobs.size,0);
 }finally{await manager.stop('OmniRoute');SERVICES.OmniRoute.port=originalPort;fs.rmSync(home,{recursive:true,force:true});}
});
