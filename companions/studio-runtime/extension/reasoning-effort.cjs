// Capability declarations are owner-reviewed configuration, not execution evidence.
const levels=['auto','low','medium','high'];
const labels={auto:'Auto',low:'Low',medium:'Medium',high:'High'};
function support(route){
 const s=route?.effortSupport;
 if(route?.kind!=='compatible'||!s||s.endpoint!==route.endpoint||s.model!==route.model||!['reasoning_effort','reasoning.effort'].includes(s.adapter))return [];
 if(s.adapter==='reasoning.effort'&&new URL(route.endpoint).hostname!=='openrouter.ai')return [];
 return Array.isArray(s.values)?levels.slice(1).filter(v=>s.values.includes(v)):[];
}
function validate(route,effort='auto'){
 if(!levels.includes(effort))throw Error('Unknown reasoning effort');
 if(effort!=='auto'&&!support(route).includes(effort))throw Error('Reasoning effort is unavailable for this exact connection and model');
 return effort;
}
const binding=route=>JSON.stringify([route.kind,route.endpoint||'',route.model||'']);
function defaultFor(context,route){
 const value=context.workspaceState?.get('meshlit.effortDefaults',{})?.[binding(route)]||'auto';
 try{return validate(route,value);}catch{return 'auto';}
}
async function choose(context,route,current,vscode){
 const values=support(route),items=levels.map(id=>({id,label:labels[id]+(id===current?' ✓':''),description:id==='auto'?'Use the server default; omit the effort parameter':values.includes(id)?'Configured support; provider acceptance has not been tested':'Unavailable for this connection/model'}));
 if(route.kind==='compatible')items.push({id:'configure',label:'Configure supported effort levels…',description:'Review your provider documentation for this exact model first'});
 if(context.workspaceState)items.push({id:'default',label:'Use current effort for new chats in this workspace',description:labels[current]+' · only this exact connection/model'});
 const picked=await vscode.window.showQuickPick(items,{title:'Reasoning effort',placeHolder:'Effort can affect latency and billed reasoning tokens; output limits stay separate'});
 if(!picked)return;
 if(picked.id==='default'){
  const previous=context.workspaceState.get('meshlit.effortDefaults',{});
  await context.workspaceState.update('meshlit.effortDefaults',{...previous,[binding(route)]:validate(route,current)});
  return {route,effort:current};
 }
 if(picked.id==='configure'){
  const adapters=[{id:'none',label:'Unknown or unsupported — Auto only'},{id:'reasoning_effort',label:'Chat Completions: reasoning_effort',description:'Only if this provider/model documents this field'}];
  if(new URL(route.endpoint).hostname==='openrouter.ai')adapters.push({id:'reasoning.effort',label:'OpenRouter: reasoning.effort',description:'Use only levels supported by the selected model'});
  const adapter=await vscode.window.showQuickPick(adapters,{title:'Documented effort API for '+route.model});if(!adapter)return;
  let allowed=[];
  if(adapter.id!=='none'){
   const choices=await vscode.window.showQuickPick(levels.slice(1).map(id=>({id,label:labels[id],picked:values.includes(id)})),{canPickMany:true,title:'Select only documented supported levels for '+route.model});
   if(!choices)return;allowed=choices.map(x=>x.id).filter(x=>levels.slice(1).includes(x));
   const decision=await vscode.window.showWarningMessage('Save these documented effort levels for '+route.model+' at '+route.endpoint+'? This configures request fields; it does not verify provider support or approve tools. An unsupported request will fail without automatic fallback.',{modal:true},'Save support');
   if(decision!=='Save support')return;
  }
  const effortSupport={endpoint:route.endpoint,model:route.model,adapter:adapter.id,values:allowed};
  if(route.id)await context.globalState.update('meshlit.effortSupport.'+route.id,effortSupport);
  const next={...route,effortSupport};
  return {route:next,effort:current==='auto'||support(next).includes(current)?current:'auto'};
 }
 validate(route,picked.id);
 return {route,effort:picked.id};
}
module.exports={levels,labels,support,validate,defaultFor,choose};
