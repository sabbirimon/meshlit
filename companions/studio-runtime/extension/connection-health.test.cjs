const {test}=require('node:test');const assert=require('node:assert/strict');
const {checkConnection}=require('./connection-health.cjs');
const profile={endpoint:'http://127.0.0.1:20128/v1',model:'chosen'};
test('catalog probe preserves limits of evidence and only uses GET /models',async()=>{
 const r=await checkConnection(profile,'fixture-only',async(url,options)=>{assert.equal(url,profile.endpoint+'/models');assert.equal(options.method,'GET');assert.equal(options.redirect,'error');assert.equal(options.headers.Authorization,'Bearer fixture-only');return new Response(JSON.stringify({data:[{id:'chosen'}]}));});
 assert.equal(r.status,'reachable');assert.equal(r.selectedModelListed,true);assert.equal(r.generationVerified,false);assert.equal(r.upstreamProvidersVerified,false);assert(!JSON.stringify(r).includes('fixture-only'));
});
test('authentication, throttling, unavailable and unsupported are distinct',async()=>{
 for(const [code,status] of [[401,'authentication-failed'],[403,'access-denied'],[429,'rate-limited'],[503,'service-unavailable'],[404,'catalog-unsupported']]){
  assert.equal((await checkConnection(profile,'',async()=>new Response('private provider diagnostics',{status:code}))).status,status);
 }
});
test('invalid, empty, oversized, timeout and unreachable responses stay honest',async()=>{
 assert.equal((await checkConnection(profile,'',async()=>new Response('<html>Login</html>'))).status,'invalid-catalog');
 const empty=await checkConnection(profile,'',async()=>new Response('{"data":[]}'));assert.equal(empty.modelCount,0);assert.equal(empty.selectedModelListed,false);
 assert.equal((await checkConnection(profile,'',async()=>new Response('x'.repeat(2097153)))).status,'catalog-too-large');
 assert.equal((await checkConnection(profile,'',async()=>{throw new DOMException('fixture','TimeoutError');})).status,'timed-out');
 assert.equal((await checkConnection(profile,'',async()=>{throw new Error('fixture');})).status,'connection-failed');
 let calls=0;assert.equal((await checkConnection({...profile,endpoint:'http://remote.example/v1'},'',async()=>{calls++;})).status,'invalid-address');assert.equal(calls,0);
});
