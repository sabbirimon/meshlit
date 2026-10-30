const vscode=require('vscode');
const crypto=require('node:crypto');
const fs=require('node:fs');
const path=require('node:path');
function endpoint(text) {
 const u=new URL(text);
 if(u.username||u.password||u.search||u.hash) throw Error('Do not put credentials or query parameters in URLs');
 if(u.protocol!=='https:' && !(u.protocol==='http:'&&['127.0.0.1','[::1]'].includes(u.hostname))) throw Error('Use HTTPS, or literal loopback HTTP');
 const base=u.hostname==='openrouter.ai'?'/api/v1':'/v1';
 if(!['','/',base,base+'/'].includes(u.pathname)) throw Error('Use the OpenAI-compatible API base');
 u.pathname=base;return u.toString().replace(/\/$/,'');
}
const saved=c=>c.globalState.get('meshlit.connections',[]);
async function addConnection(context, requestedService, sourceKind) {
 const service=requestedService||await vscode.window.showQuickPick(['OmniRoute','FreeLLMAPI','OpenRouter','Hugging Face','OpenAI','Local/distributed server','Other OpenAI-compatible'],{title:'Add model connection'});if(!service)return;
 const defaults={'OmniRoute':'http://127.0.0.1:20128/v1','FreeLLMAPI':'http://127.0.0.1:3001/v1','OpenRouter':'https://openrouter.ai/api/v1','Hugging Face':'https://router.huggingface.co/v1','OpenAI':'https://api.openai.com/v1'};
 if(service==='FreeLLMAPI'){
  const kind=await vscode.window.showQuickPick(['Desktop app','Server / Docker'],{title:'FreeLLMAPI installation'});if(!kind)return;
  if(kind==='Desktop app')defaults.FreeLLMAPI='http://127.0.0.1:31415/v1';
 }
 const input=await vscode.window.showInputBox({title:service+' API endpoint',value:defaults[service]||'',prompt:'Your running OpenAI-compatible endpoint. No service is installed automatically.'});if(!input)return;
 const url=endpoint(input);
 const token=await vscode.window.showInputBox({title:service+' API key',password:true,prompt:'Stored in editor SecretStorage, bound to this connection. Leave empty only for an unauthenticated loopback service.'});if(token===undefined)return;
 if(!new URL(url).hostname.match(/^(127\.0\.0\.1|\[::1\])$/)&&!token)throw Error('Remote endpoints require a token');
 let models=[];
 try {
  models=await require('./model-catalog.cjs').fetchCatalog(url,token);
 }catch{await vscode.window.showInformationMessage('Model catalog unavailable. You can enter a model ID from your provider; connectivity is not confirmed.');}
 let model;
 if(models.length){const picked=await vscode.window.showQuickPick(models.map(m=>({label:m.id,description:m.cost+' · provider-reported',model:m.id})),{title:'Select a model reported by '+service});model=picked?.model;}
 else model=await vscode.window.showInputBox({title:'Exact provider model ID'});
 if(!model)return;
 const gateway=['OmniRoute','FreeLLMAPI','OpenRouter','Hugging Face','Local/distributed server','Other OpenAI-compatible'].includes(service);
 let pricing={};
 if(!gateway){
  const rates=await vscode.window.showInputBox({title:'Optional reviewed USD prices per million tokens',prompt:'input,output (for example 0.20,0.80). Leave blank for unknown. Estimates are not provider billing caps.'});
  if(rates){const parts=rates.split(',').map(Number);if(parts.length!==2||parts.some(x=>!Number.isFinite(x)||x<0||x>100000))throw Error('Invalid prices');pricing={inputRate:Math.round(parts[0]*1e9),outputRate:Math.round(parts[1]*1e9),checkedAt:Date.now()};}
 }
 const id=crypto.randomUUID(), profile={id,kind:'compatible',service,sourceKind:sourceKind||'cloud',endpoint:url,model,gateway,...pricing};
 await context.secrets.store('meshlit.model.'+id,token);
 await context.globalState.update('meshlit.connections',[...saved(context),profile]);
 await context.globalState.update('meshlit.catalog.'+id,models);
 return profile;
}
async function chooseConnection(context, preferredId) {
 const profiles=saved(context);
 const choice=preferredId?{id:preferredId}:await vscode.window.showQuickPick([{label:'Bundled local model',id:'local'},...profiles.map(p=>({label:require('./model-sources.cjs').label(p)+' · '+p.model,description:p.endpoint,id:p.id})),{label:'Add a connection…',id:'add'}],{title:'Model for this conversation'});
 if(!choice)return;
 if(choice.id==='local')return {kind:'local',label:'Bundled local model'};
 let profile=choice.id==='add'?await addConnection(context):profiles.find(p=>p.id===choice.id);
 if(!profile)return;
 if(!preferredId && choice.id!=='add'){
  const catalog=context.globalState.get('meshlit.catalog.'+profile.id,[]);
  const models=[{label:profile.model,description:'Current model',model:profile.model},...catalog.filter(m=>m.id!==profile.model).map(m=>({label:m.id,description:(m.provider?m.provider+' · ':'')+m.cost+' (reported)',model:m.id}))];
  const selected=await vscode.window.showQuickPick(models,{title:profile.service+' · model for this chat',placeHolder:'Search available models',matchOnDescription:true});
  if(!selected)return;profile={...profile,model:selected.model};
 }
 const allowed=await vscode.window.showWarningMessage(`Send this conversation and approved file content to ${profile.endpoint} using ${profile.model}? Charges may apply. Unknown/gateway prices stay unknown; existing budget rules may block the request.`,{modal:true},'Use connection');
 if(allowed!=='Use connection')return;
 return {...profile,token:await context.secrets.get('meshlit.model.'+profile.id)||'',label:require('./model-sources.cjs').label(profile)+' · '+profile.model};
}
async function addMcp(context) {
 const type=await vscode.window.showQuickPick(['stdio','http'],{title:'MCP transport: installed local server or Streamable HTTP'});if(!type)return;
 const name=await vscode.window.showInputBox({title:'Connector name',validateInput:s=>/^[A-Za-z0-9_-]{1,64}$/.test(s)?undefined:'Use letters, digits, hyphens and underscores'});if(!name)return;
 let config={name,type};
 if(type==='stdio'){
  const selected=await vscode.window.showOpenDialog({title:'Choose the installed MCP executable',canSelectMany:false});if(!selected)return;
  const command=selected[0].fsPath;if(!path.isAbsolute(command)||!fs.statSync(command).isFile())throw Error('Select an installed executable');
  const input=await vscode.window.showInputBox({title:'MCP argument array (JSON)',value:'[]',prompt:'No secrets here. This executable runs with your user permissions when enabled.'});if(input===undefined)return;
  const args=JSON.parse(input);if(!Array.isArray(args)||args.length>32||args.some(a=>typeof a!=='string'||a.length>2048))throw Error('Invalid argument array');
  config={...config,command,args};
 }else{
  const input=await vscode.window.showInputBox({title:'MCP Streamable HTTP URL',prompt:'HTTPS or literal-loopback HTTP. No URL credentials. OAuth/header authentication is not implemented yet.'});if(!input)return;
  const u=new URL(input);if(u.username||u.password||u.search||u.hash||!(u.protocol==='https:'||u.protocol==='http:'&&['127.0.0.1','[::1]'].includes(u.hostname)))throw Error('Unsupported MCP URL');
  config.url=u.toString();
 }
 const previous=context.globalState.get('meshlit.mcp',[]);
 if(previous.some(p=>p.name===name))throw Error('Use a unique connector name');
 await context.globalState.update('meshlit.mcp',[...previous,config]);
 vscode.window.showInformationMessage('Connector saved, disabled by default. Select it when starting an Edit or Autopilot agent.');
}
async function chooseMcp(context, {editing=false, selected=[]}={}) {
 const servers=context.globalState.get('meshlit.mcp',[]);if(!servers.length)return [];
 const auth=require('./mcp-auth.cjs');
 const names=new Set(selected.map(item=>typeof item==='string'?item:item.name));
 const choices=await vscode.window.showQuickPick(servers.map(s=>({label:s.name,description:s.url||s.command,server:s,picked:names.has(s.name)})),{canPickMany:true,title:'Optional MCP connectors — leave empty for none'});
 if(choices===undefined)return editing?undefined:[];
 if(!choices.length)return [];
 if(choices.length>4)throw Error('At most four MCP connectors per agent');
 const configs=choices.map(choice=>auth.configuredConnector(choice.server));
 const answer=await vscode.window.showWarningMessage('Start these trusted MCP servers? Local servers run with your user permissions; remote servers receive approved tool arguments.\n'+configs.map(auth.connectorPreview).join('\n'),{modal:true},'Start selected servers');
 if(answer!=='Start selected servers')return editing?undefined:[];
 const unchanged=()=>{const current=context.globalState.get('meshlit.mcp',[]);for(const config of configs){
  const savedConfig=current.find(item=>item.name===config.name);
  if(!savedConfig||JSON.stringify(auth.configuredConnector(savedConfig))!==JSON.stringify(config))throw Error('MCP configuration changed during approval; choose it again');
 }};
 unchanged();
 const resolved=await Promise.all(configs.map(config=>auth.resolveConnector(context,config)));
 unchanged();
 return resolved;
}
async function checkSavedConnection(context) {
 const profiles=saved(context);
 if(!profiles.length){await vscode.window.showInformationMessage('Add a model connection first. No API was checked.');return;}
 const choice=await vscode.window.showQuickPick(profiles.map(p=>({label:require('./model-sources.cjs').label(p)+' · '+p.model,description:p.endpoint,profile:p})),{title:'Check API connection — reads /models; does not generate text'});
 if(!choice)return;
 const profile=choice.profile;
 const answer=await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'Checking '+profile.service},async()=>{
  endpoint(profile.endpoint);
  const token=await context.secrets.get('meshlit.model.'+profile.id)||'';
  return require('./connection-health.cjs').checkConnection(profile,token);
 });
 const history=context.globalState.get('meshlit.connectionHealth',{});history[profile.id]=answer;
 await context.globalState.update('meshlit.connectionHealth',history);
 const labels={'reachable':'API catalog reachable','authentication-failed':'Authentication failed','access-denied':'Access denied','rate-limited':'Rate limited','service-unavailable':'Service unavailable','catalog-unsupported':'Model catalog not supported at this address','http-error':'API returned an HTTP error','invalid-address':'Invalid address','invalid-catalog':'Response is not an OpenAI-compatible model catalog','catalog-too-large':'Catalog exceeds the size limit','timed-out':'Connection timed out','connection-failed':'Connection failed; network, TLS or redirect may be responsible'};
 const details=answer.status==='reachable'?` · ${answer.modelCount} models · selected model ${answer.selectedModelListed?'listed':'not listed'}`:'';
 await vscode.window.showInformationMessage(`${profile.service}: ${labels[answer.status]}${details}. Checked ${answer.checkedAt}. Generation and gateway upstream providers were not tested.`);
 return answer;
}
module.exports={endpoint,chooseConnection,addConnection,addMcp,chooseMcp,checkSavedConnection};
