const COSTS=['all','free','paid','trial','subscription','unknown'];
// Catalog labels are provider claims, never route or billing guarantees.
function normalizeCatalog(payload,source,checkedAt=new Date().toISOString()) {
 if(!Array.isArray(payload?.data))throw Error('Invalid model catalog');
 const seen=new Set();return payload.data.slice(0,2048).flatMap(row=>{
  if(!row||typeof row.id!=='string'||!row.id.trim()||row.id.length>512||seen.has(row.id))return [];
  seen.add(row.id);
  let cost='unknown';const price=row.pricing;
  const numeric=v=>(typeof v==='number'||typeof v==='string'&&v.trim()!=='')&&Number.isFinite(Number(v))&&Number(v)>=0;
  if(price&&numeric(price.prompt)&&numeric(price.completion))cost=Number(price.prompt)===0&&Number(price.completion)===0?'free':'paid';
  const tier=typeof row.cost_type==='string'?row.cost_type.toLowerCase():'';
  if(['trial','subscription'].includes(tier))cost=tier;
  return [{id:row.id,provider:typeof row.owned_by==='string'?row.owned_by.slice(0,120):'',cost,source,checkedAt,capabilities:Array.isArray(row.supported_parameters)?row.supported_parameters.filter(x=>typeof x==='string').slice(0,32):[]}];
 });
}
function filterModels(models,{search='',cost='all',provider=''}={}) {
 if(!COSTS.includes(cost))throw Error('Unknown cost filter');
 const needle=String(search).toLowerCase().slice(0,128);
 return models.filter(m=>(cost==='all'||m.cost===cost)&&(!provider||m.provider===provider)&&(m.id+' '+m.provider).toLowerCase().includes(needle));
}
async function fetchCatalog(endpoint,token,request=fetch) {
 const r=await request(endpoint+'/models',{headers:token?{Authorization:'Bearer '+token}:{},redirect:'error',signal:AbortSignal.timeout(8000)});
 if(!r.ok){await r.body?.cancel();throw Error('Catalog request returned HTTP '+r.status);}
 const reader=r.body?.getReader();if(!reader)throw Error('Empty catalog response');
 let size=0;const chunks=[];
 try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>2097152)throw Error('Catalog exceeds 2 MiB');chunks.push(Buffer.from(value));}}finally{await reader.cancel();}
 return normalizeCatalog(JSON.parse(Buffer.concat(chunks).toString()),endpoint);
}
module.exports={COSTS,normalizeCatalog,filterModels,fetchCatalog};
