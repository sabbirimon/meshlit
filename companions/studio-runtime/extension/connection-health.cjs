// A read-only catalog probe. It deliberately never sends a completion request.
async function checkConnection(profile, token, request=fetch) {
 const checkedAt=new Date().toISOString();
 const result=(status,extra={})=>({status,checkedAt,generationVerified:false,upstreamProvidersVerified:false,...extra});
 const url=new URL(profile.endpoint);
 if(url.username||url.password||url.search||url.hash||!(url.protocol==='https:'||url.protocol==='http:'&&['127.0.0.1','[::1]'].includes(url.hostname)))return result('invalid-address');
 try {
  const response=await request(url.toString().replace(/\/$/,'')+'/models',{method:'GET',headers:token?{Authorization:'Bearer '+token}:{},redirect:'error',signal:AbortSignal.timeout(8000)});
  if(!response.ok){
   await response.body?.cancel();
   const status=response.status;
   return result(status===401?'authentication-failed':status===403?'access-denied':status===429?'rate-limited':status>=500?'service-unavailable':status===404?'catalog-unsupported':'http-error',{httpStatus:status});
  }
  const reader=response.body?.getReader();if(!reader)return result('invalid-catalog');
  const chunks=[];let size=0;
  try {
   while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>2097152)return result('catalog-too-large');chunks.push(Buffer.from(value));}
  }finally{await reader.cancel();}
  let data;try{data=JSON.parse(Buffer.concat(chunks).toString('utf8'));}catch{return result('invalid-catalog');}
  if(!Array.isArray(data.data))return result('invalid-catalog');
  const models=data.data.filter(item=>item&&typeof item.id==='string');
  return result('reachable',{modelCount:models.length,selectedModelListed:models.some(item=>item.id===profile.model)});
 }catch(error){return result(error.name==='TimeoutError'||error.name==='AbortError'?'timed-out':'connection-failed');}
}
module.exports={checkConnection};
