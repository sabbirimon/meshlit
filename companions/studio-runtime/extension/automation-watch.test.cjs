const test=require('node:test'),assert=require('node:assert/strict');
const {watch}=require('./automation-watch.cjs');
test('admitted automation stops on deadline and grant revocation without resubmission',async()=>{
 let clock=0,stops=0,observed=0;
 await watch({valid:async()=>true,observe:async()=>{observed++;return 'OBSERVED';},stop:async()=>{stops++;},now:()=>clock,delay:async ms=>{clock+=ms;},deadlineMs:6000}).done;
 assert.equal(observed,2);assert.equal(stops,1);
 await watch({valid:async()=>false,observe:async()=>{throw Error('revoked task must not be polled');},stop:async()=>{stops++;}}).done;
 assert.equal(stops,2);
});
test('confirmed terminal tasks need no Stop; observation failures retain cleanup',async()=>{
 let stops=0;
 await watch({valid:async()=>true,observe:async()=> 'COMPLETED',stop:async()=>{stops++;}}).done;
 assert.equal(stops,0);
 await watch({valid:async()=>true,observe:async()=>{throw Error('connection lost');},stop:async()=>{stops++;throw Error('Stop unconfirmed');}}).done;
 assert.equal(stops,1);
});
