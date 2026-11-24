'use strict';
// Only observes an already admitted task. Never submits or replays a task.
function watch({valid,observe,stop,now=Date.now,delay=ms=>new Promise(resolve=>setTimeout(resolve,ms)),deadlineMs=600000}) {
 let cancelled=false,finished=false;
 const deadline=now()+deadlineMs;
 const done=(async()=>{
  try {
   while(!cancelled&&now()<deadline) {
    if(!await valid())break;
    const phase=await observe();
    if(['COMPLETED','FAILED','STOPPED'].includes(phase)){finished=true;break;}
    await delay(3000);
   }
  }catch{/* Cleanup stays bound to the original task and endpoint. */}
  finally{if(!finished)await stop().catch(()=>{});}
 })();
 return {done,cancel(){cancelled=true;}};
}
module.exports={watch};
