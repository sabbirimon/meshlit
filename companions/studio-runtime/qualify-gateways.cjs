// Explicit real upstream startup/auth/shutdown check. Disposable state, no accounts or model calls.
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {GatewayRuntime}=require('./extension/gateway-runtime.cjs');
async function status(url){return new Promise((resolve,reject)=>{const r=http.get(url,{timeout:3000},s=>{s.resume();resolve(s.statusCode);});r.on('timeout',()=>r.destroy(Error('Timeout')));r.on('error',reject);});}
async function main(){
 const [manifest,output]=process.argv.slice(2);if(!manifest||!output)throw Error('Usage: node qualify-gateways.cjs MANIFEST OUTPUT');
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'meshlit-gateway-qualification-'));
 const manager=new GatewayRuntime(JSON.parse(fs.readFileSync(manifest,'utf8')),directory);
 const result={providerRequests:0,accountsCreated:0,services:[]};
 try{
  for(const name of ['OmniRoute','FreeLLMAPI']){
   const running=await manager.start(name);
   const root=await status(running.url),auth=await status(running.url+'api/auth/status'),keys=await status(running.url+'api/keys');
   assert(root>=200&&root<400);assert.equal(auth,200);assert.equal(keys,401);
   await manager.stop(name);assert.equal(manager.status(name).state,'stopped');
   let closed=false;try{await status(running.url);}catch{closed=true;}assert(closed,'Owned gateway must release its listener');
   result.services.push({name,version:running.version,dashboardStatus:root,authStatus:auth,unauthenticatedKeysStatus:keys,stoppedAndPortClosed:closed});
  }
  fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
 }finally{await Promise.all(['OmniRoute','FreeLLMAPI'].map(n=>manager.stop(n)));fs.rmSync(directory,{recursive:true,force:true});}
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
