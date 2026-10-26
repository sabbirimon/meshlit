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
async function addConnection(context) {
 const service=await vscode.window.showQuickPick(['OmniRoute','FreeLLMAPI','OpenRouter','Hugging Face','OpenAI','Local/distributed server','Other OpenAI-compatible'],{title:'Add model connection'});if(!service)return;
 const defaults={'OmniRoute':'http://127.0.0.1:20128/v1','FreeLLMAPI':'http://127.0.0.1:3001/v1','OpenRouter':'https://openrouter.ai/api/v1','Hugging Face':'https://router.huggingface.co/v1','OpenAI':'https://api.openai.com/v1'};
 const input=await vscode.window.showInputBox({title:service+' API endpoint',value:defaults[service]||'',prompt:'Your running OpenAI-compatible endpoint. No service is installed automatically.'});if(!input)return;
 const url=endpoint(input);
 const token=await vscode.window.showInputBox({title:service+' API key',password:true,prompt:'Stored in editor SecretStorage, bound to this connection. Leave empty only for an unauthenticated loopback service.'});if(token===undefined)return;
 if(!new URL(url).hostname.match(/^(127\.0\.0\.1|\[::1\])$/)&&!token)throw Error('Remote endpoints require a token');
 let models=[];
 try {
  const r=await fetch(url+'/models',{headers:token?{Authorization:'Bearer '+token}:{},redirect:'error',signal:AbortSignal.timeout(8000)});
  if(!r.ok)throw Error('Catalog unavailable');
  const reader=r.body.getReader();let size=0,chunks=[];
  try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>2097152)throw Error('Catalog too large');chunks.push(Buffer.from(value));}}finally{await reader.cancel();}
  models=JSON.parse(Buffer.concat(chunks).toString()).data.map(x=>x.id).filter(x=>typeof x==='string'&&x.length<=512).slice(0,2048);
 }catch{await vscode.window.showInformationMessage('Model catalog unavailable. You can enter a model ID from your provider; connectivity is not confirmed.');}
 let model;
 if(models.length) model=await vscode.window.showQuickPick(models,{title:'Select a model reported by '+service});
 else model=await vscode.window.showInputBox({title:'Exact provider model ID'});
 if(!model)return;
 const gateway=['OmniRoute','FreeLLMAPI','Local/distributed server','Other OpenAI-compatible'].includes(service);
 let pricing={};
 if(!gateway){
  const rates=await vscode.window.showInputBox({title:'Optional reviewed USD prices per million tokens',prompt:'input,output (for example 0.20,0.80). Leave blank for unknown. Estimates are not provider billing caps.'});
  if(rates){const parts=rates.split(',').map(Number);if(parts.length!==2||parts.some(x=>!Number.isFinite(x)||x<0||x>100000))throw Error('Invalid prices');pricing={inputRate:Math.round(parts[0]*1e9),outputRate:Math.round(parts[1]*1e9),checkedAt:Date.now()};}
 }
 const id=crypto.randomUUID(), profile={id,kind:'compatible',service,endpoint:url,model,gateway,...pricing};
 await context.secrets.store('meshlit.model.'+id,token);
 await context.globalState.update('meshlit.connections',[...saved(context),profile]);
 return profile;
}
async function chooseConnection(context) {
 const profiles=saved(context);
 const choice=await vscode.window.showQuickPick([{label:'Bundled local model',id:'local'},...profiles.map(p=>({label:p.service+' · '+p.model,description:p.endpoint,id:p.id})),{label:'Add a connection…',id:'add'}],{title:'Model for this conversation'});
 if(!choice)return;
 if(choice.id==='local')return {kind:'local',label:'Bundled local model'};
 const profile=choice.id==='add'?await addConnection(context):profiles.find(p=>p.id===choice.id);
 if(!profile)return;
 const allowed=await vscode.window.showWarningMessage(`Send this conversation and approved file content to ${profile.endpoint} using ${profile.model}? Charges may apply. Unknown/gateway prices stay unknown; existing budget rules may block the request.`,{modal:true},'Use connection');
 if(allowed!=='Use connection')return;
 return {...profile,token:await context.secrets.get('meshlit.model.'+profile.id)||'',label:profile.service+' · '+profile.model};
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
async function chooseMcp(context) {
 const servers=context.globalState.get('meshlit.mcp',[]);if(!servers.length)return [];
 const selected=await vscode.window.showQuickPick(servers.map(s=>({label:s.name,description:s.url||s.command,server:s})),{canPickMany:true,title:'Optional MCP connectors — leave empty for none'});
 if(!selected?.length)return [];
 if(selected.length>4)throw Error('At most four MCP connectors per agent');
 const answer=await vscode.window.showWarningMessage('Start these trusted MCP servers? Local servers run with your user permissions; remote servers receive approved tool arguments.\n'+selected.map(s=>JSON.stringify(s.server)).join('\n'),{modal:true},'Start selected servers');
 return answer==='Start selected servers'?selected.map(s=>s.server):[];
}
module.exports={endpoint,chooseConnection,addConnection,addMcp,chooseMcp};
