const crypto=require('node:crypto');
const digest=value=>crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex');
function metadata(context){
 const profiles=context.globalState.get('meshlit.connections',[]),mcp=context.globalState.get('meshlit.mcp',[]);
 if(!Array.isArray(profiles)||!Array.isArray(mcp)||profiles.length+mcp.length>64)throw Error('Review saved connections first');
 const models=profiles.map(p=>{
  if(p.kind!=='compatible'||typeof p.id!=='string'||typeof p.endpoint!=='string'||typeof p.model!=='string')throw Error('Invalid saved model source');
  return {id:'model:'+p.id,name:String(p.service||'Model API').slice(0,100),kind:'compatible',endpoint:require('./connections.cjs').endpoint(p.endpoint),model:p.model,descriptor:JSON.stringify({sourceKind:p.sourceKind||'compatible'})};
 });
 const connectors=mcp.map(p=>({id:'mcp:'+digest(p.name).slice(0,32),name:String(p.name).slice(0,100),kind:'mcp_setup',descriptor:JSON.stringify({type:p.type,command:p.command||'',url:p.url||''})}));
 return [...models,...connectors].sort((a,b)=>a.id.localeCompare(b.id));
}
function parsePrompt(text){
 if(typeof text!=='string'||text.length<5||text.length>240||/[\x00-\x1f]/.test(text)||/^["`]/.test(text)||/\b(don't|dont|do not|never|without)\b/i.test(text))return;
 const command=text.trim().replace(/[?!]$/,'').replace(/^(please |can you |could you )/i,'');
 if(/^(show|check|list) (device )?connection (status|results)$/i.test(command))return {action:'status'};
 if(/^(list|show) (the )?((available|saved|approved|nearby) )?devices$/i.test(command))return {action:'list'};
 const match=command.match(/^(connect|reconnect)(?: (.+))?$/i);if(!match)return;
 let query=(match[2]||'').trim().replace(/^(to |the device |my device |device )/i,'');
 if(['','a device','another device','devices','device','my devices','a node','another node'].includes(query.toLowerCase())||query.length>160||query.includes('://')||query.includes('@')||/\b(password|token|secret|api key)\b/i.test(query))query='';
 return {action:'request',query};
}
function publicReply(result){
 if(result.offer){const o=result.offer;return {text:!o.enabled?'Device connection assistance is off. Review Connection assistance in Settings and approve exact saved sources. No connection was started.':!o.choices.length?'No saved devices match. Add a saved model API or complete device/MCP setup first. No network scan or enrollment was started.':'Choose a saved source. Contact checks keep model use, commands and worker enrollment separately scoped.',offer:o};}
 if(result.receipt){const r=result.receipt;return {text:`${r.name}: ${r.phase.toLowerCase().replaceAll('_',' ')}. ${r.detail}`,receipt:r};}
 if(Array.isArray(result.receipts))return {text:result.receipts.length?result.receipts.slice(-5).map(r=>`${r.name}: ${r.phase.toLowerCase()} · ${r.detail}`).join('\n'):'No connection checks are recorded for this chat.'};
 return {text:'Connection check cancellation requested.'};
}
function create(context,send,vscode=require('vscode')){
 const pending=new Map(),offers=new Map();let disposed=false;
 function request(operation,fields={}){
  if(disposed)return Promise.reject(Error('Connection controller closed'));
  const requestId=crypto.randomUUID();
  return new Promise((resolve,reject)=>{
   const timer=setTimeout(()=>{pending.delete(requestId);if(operation==='device_connection_action'&&fields.owner)request('device_connection_close_owner',{owner:fields.owner}).catch(()=>{});reject(Error('No connection result received; inspect status before retrying'));},40000);
   pending.set(requestId,{resolve,reject,timer});
   try{send({op:'device_backend',operation,requestId,fields});}catch(e){clearTimeout(timer);pending.delete(requestId);reject(e);}
  });
 }
 async function sync(){return request('device_connection_sync',{sources:metadata(context)});}
 async function run(owner,args){
  if(!vscode.workspace.isTrusted)throw Error('Trust this workspace before device assistance');
  if(!args||!['list','request','connect','status','cancel'].includes(args.action)||Object.keys(args).some(k=>!['action','query','offerId','sourceId','requestId'].includes(k)))throw Error('Unsupported connection request');
  const view=await sync();
  if(args.action==='connect'){
   const offered=offers.get(args.offerId),choice=offered?.offer.choices.find(c=>c.id===args.sourceId);
   const current=view.sources.find(s=>s.id===args.sourceId);
   if(!offered||offered.owner!==owner||offered.offer.expiresAtMs<=Date.now()||!choice?.canConnect||!view.enabled||!current?.approved||offered.bindings[args.sourceId]!==current.reviewBinding)throw Error('Connection choices or approval changed; refresh devices');
   const source=metadata(context).find(s=>s.id===args.sourceId);
   if(!source||source.kind!=='compatible')throw Error('This transport needs owner setup');
   const before=JSON.stringify(source);
   // Read the exact endpoint-bound credential only after fresh owner/source approval.
   const credential=await context.secrets.get('meshlit.model.'+source.id.slice(6))||'';
   if(JSON.stringify(metadata(context).find(s=>s.id===source.id))!==before)throw Error('Saved source changed before contact');
   let changed=false;
   const watch=setInterval(()=>{try{if(JSON.stringify(metadata(context).find(s=>s.id===source.id))!==before){changed=true;request('device_connection_close_owner',{owner}).catch(()=>{});}}catch{changed=true;request('device_connection_close_owner',{owner}).catch(()=>{});}},200);
   try{
    const result=await request('device_connection_action',{owner,arguments:args,credential});
    if(changed||JSON.stringify(metadata(context).find(s=>s.id===source.id))!==before)throw Error('Source changed during contact; refresh choices');
    return result;
   }finally{clearInterval(watch);}
  }
  const result=await request('device_connection_action',{owner,arguments:args});
  if(result.offer){
   while(offers.size>=16)offers.delete(offers.keys().next().value);
   offers.set(result.offer.id,{owner,offer:result.offer,bindings:Object.fromEntries(view.sources.map(s=>[s.id,s.reviewBinding]))});
   const only=result.offer.choices.length===1?result.offer.choices[0]:undefined;
   if(args.action==='request'&&args.query&&only?.canConnect&&(only.id===args.query||only.name.toLowerCase()===args.query.toLowerCase()))return run(owner,{action:'connect',offerId:result.offer.id,sourceId:only.id});
  }
  return result;
 }
 async function open(){
  let view=await sync();
  const choice=await vscode.window.showQuickPick([{label:view.enabled?'Turn connection assistance off':'Enable connection assistance',op:'toggle'},...view.sources.map(s=>({label:(s.approved?'✓ ':'')+s.name,description:s.supported?s.identityLabel:s.reason,source:s})),{label:'Add model API or device address…',op:'add'}],{title:'Connection assistance · exact saved sources only'});
  if(!choice)return;
  if(choice.op==='add'){await vscode.commands.executeCommand('meshlit.addModelSource');return;}
  if(choice.op==='toggle'){
   if(!view.enabled){const ok=await vscode.window.showWarningMessage('Allow agents and Chat to perform bounded contact checks on sources you approve individually? No identities, model use, commands, credentials or worker roles are granted automatically.',{modal:true},'Enable assistance');if(ok!=='Enable assistance')return;}
   await request('device_connection_policy',{policy:{enabled:!view.enabled}});return;
  }
  const source=choice.source;
  if(!source.supported){await vscode.window.showInformationMessage(source.reason);return;}
  if(source.approved){await request('device_connection_policy',{policy:{sourceId:source.id,allowed:false}});return;}
  const ok=await vscode.window.showWarningMessage(`${source.name}\n${source.identityLabel}\n\nApprove automatic contact checks using its saved scoped credential? This does not send chat history, enable generation, approve commands or enroll a worker. Changed source identity/configuration needs new approval.`,{modal:true},'Approve contact checks');
  if(ok!=='Approve contact checks')return;
  const fresh=await sync();if(fresh.sources.find(s=>s.id===source.id)?.reviewBinding!==source.reviewBinding)throw Error('Source changed; review it again');
  await request('device_connection_policy',{policy:{sourceId:source.id,allowed:true,reviewBinding:source.reviewBinding}});
 }
 return {
  run,open,parsePrompt,publicReply,
  accept(event){const p=pending.get(event.requestId);if(!p)return;pending.delete(event.requestId);clearTimeout(p.timer);if(event.error)p.reject(Error(event.error));else p.resolve(event.result);},
  cancelOwner(owner){for(const [key,value]of offers)if(value.owner===owner)offers.delete(key);return request('device_connection_close_owner',{owner});},
  dispose(){disposed=true;for(const p of pending.values()){clearTimeout(p.timer);p.reject(Error('Connection host closed'));}pending.clear();offers.clear();},
 };
}
module.exports={metadata,parsePrompt,publicReply,create};
