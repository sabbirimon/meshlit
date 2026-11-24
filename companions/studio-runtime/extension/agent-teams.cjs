const vscode=require('vscode'),crypto=require('node:crypto');
const modes=['SINGLE_DEVICE','MULTI_DEVICE','LOCAL_CLUSTER','REMOTE_CLUSTER'];
const controls=['USER_DIRECTED','SUPERVISED_AUTONOMOUS','AGENT_DIALOGUE'];
function profiles(context){const rows=context.globalState.get('meshlit.agentRoles',[]);if(!Array.isArray(rows)||rows.length>16)throw Error('Invalid role store');return rows;}
function validateProfile(p){if(!p||typeof p.id!=='string'||!/^[a-f0-9-]{36}$/.test(p.id)||typeof p.name!=='string'||!p.name.trim()||p.name.length>48||typeof p.instruction!=='string'||p.instruction.length>1200||p.instruction.includes('\0')||typeof p.enabled!=='boolean')throw Error('Invalid role template');}
function buildPlan(input,saved,sources){
 if(!input||typeof input.goal!=='string'||!input.goal.trim()||input.goal.length>4000||!modes.includes(input.mode)||!controls.includes(input.control)||!Number.isInteger(input.maxRounds)||input.maxRounds<1||input.maxRounds>3||![1,2].includes(input.parallelWorkers)||!Array.isArray(input.roles)||input.roles.length!==4)throw Error('Invalid team configuration');
 const bindings=new Map();for(const source of sources){if(source.kind==='local')continue;const previous=bindings.get(source.id);if(previous&&previous!==source.model)throw Error('Use one model per connection, or save distinct connections');bindings.set(source.id,source.model);}
 const members=['planner','worker','worker','reviewer'].map((role,i)=>{
  const profile=input.roles[i]?saved.find(p=>p.id===input.roles[i]&&p.enabled):undefined;
  if(input.roles[i]&&!profile)throw Error('A role is disabled or missing');
  const source=sources[i]||{kind:'local'};
  return {id:'agent-'+(i+1),role,sourceId:source.kind==='local'?'local':source.id,modelId:source.kind==='local'?'meshlit-local':source.model,profileId:profile?.id||null,guidance:profile?.instruction||''};
 });
 if(input.mode==='SINGLE_DEVICE'&&members.some(m=>m.sourceId!=='local'))throw Error('Single device requires local sources');
 if(input.mode!=='SINGLE_DEVICE'&&!members.some(m=>m.sourceId!=='local'))throw Error('Choose an approved remote model source');
 if(['LOCAL_CLUSTER','REMOTE_CLUSTER'].includes(input.mode)&&members.some(m=>m.sourceId==='local'))throw Error('Choose cluster sources for all roles');
 return {goal:input.goal,mode:input.mode,control:input.control,maxRounds:input.maxRounds,parallelWorkers:input.parallelWorkers,members};
}
function create(context,request){
 let panel,catalog=[],sources=Array.from({length:4},()=>({kind:'local',label:'Bundled local model'}));
 function post(message){panel?.webview.postMessage(message);}
 function configuration(){post({type:'team-config',profiles:profiles(context),sources:sources.map(s=>({label:s.label,sourceId:s.kind==='local'?'local':s.id}))});}
 const controller={
  open(){
   if(panel){panel.reveal();request({op:'team_status'});return;}
   panel=require('./shell.cjs').createPanel(vscode,'meshlit.agentTeams','Agent teams & roles',vscode.ViewColumn.Active,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});
   context.subscriptions.push(panel);panel.webview.html=render();panel.onDidDispose(()=>panel=undefined);
   panel.webview.onDidReceiveMessage(async message=>{
    try{
     if(message.op==='ready'){configuration();request({op:'team_catalog'});request({op:'team_status'});}
     else if(message.op==='refresh')request({op:'team_status'});
     else if(message.op==='unlock')await vscode.commands.executeCommand('meshlit.unlockUsage');
     else if(message.op==='save-role'){
      const existing=profiles(context),base=catalog.find(r=>r.id===message.catalogId);
      if(!base)throw Error('Choose a bundled role');
      if(existing.length>=16)throw Error('At most sixteen saved roles');
      const row={id:crypto.randomUUID(),name:message.name,instruction:message.instruction,enabled:false,sourceId:base.id};validateProfile(row);
      await context.globalState.update('meshlit.agentRoles',[...existing,row]);configuration();
     } else if(message.op==='edit-role'){
      const existing=profiles(context),row=existing.find(p=>p.id===message.id);if(!row)throw Error('Role missing');
      const next={...row,name:message.name,instruction:message.instruction,enabled:message.enabled};validateProfile(next);
      await context.globalState.update('meshlit.agentRoles',existing.map(p=>p.id===row.id?next:p));request({op:'team_revoke_profile',profileId:row.id});configuration();
      // Saved delegation is a snapshot: explicit replacement is needed to use edited guidance.
     } else if(message.op==='delete-role'){
      const existing=profiles(context);await context.globalState.update('meshlit.agentRoles',existing.filter(p=>p.id!==message.id));request({op:'team_revoke_profile',profileId:message.id});configuration();
     } else if(message.op==='source'){
      if(!Number.isInteger(message.index)||message.index<0||message.index>3)throw Error('Invalid role');
      const source=await require('./connections.cjs').chooseConnection(context);if(source){sources[message.index]=source;configuration();}
     } else if(['start','delegate'].includes(message.op)){
      if(!vscode.workspace.isTrusted)throw Error('Trust this workspace before running agents');
      const plan=buildPlan(message.config,profiles(context),sources),routes=Object.fromEntries(sources.filter(s=>s.kind!=='local').map(s=>[s.id,s]));
      request({op:message.op==='start'?'team_start':'team_delegation',plan,routes});
     } else if(message.op==='revoke-delegation')request({op:'team_delegation',plan:null});
     else if(message.op==='control'){
      if(typeof message.runId!=='string'||!['pause','resume','stop'].includes(message.action))throw Error('Invalid control');
      request({op:'team_control',runId:message.runId,action:message.action});
     }
    }catch(e){controller.failure(e.message||'Team action failed');}
   });
  },
  accept(event){
   if(event.operation==='team_catalog'){catalog=event.roles;post({type:'team-catalog',roles:catalog,commit:event.commit});}
   else if(event.operation==='team_status'||event.operation==='team_delegation')post({type:'team-state',...event});
   else {post({type:'team-notice',message:event.runId?'Team admitted: '+event.runId:'Control requested'});if(panel)request({op:'team_status'});}
  },
  failure(message){post({type:'team-error',message});},
  revokeSource(id){sources=sources.map(s=>s.id===id?{kind:'local',label:'Bundled local model'}:s);request({op:'team_revoke_source',sourceId:id});configuration();}
 };
 const timer=setInterval(()=>{if(panel?.visible)try{request({op:'team_status'});}catch{}},3000);
 context.subscriptions.push({dispose(){clearInterval(timer);}});
 return controller;
}
function render(){const nonce=crypto.randomBytes(24).toString('hex');return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-${nonce}'; style-src 'nonce-${nonce}';"><style nonce="${nonce}">
body{font:14px var(--vscode-font-family);color:var(--vscode-foreground);background:var(--vscode-editor-background);max-width:1100px;margin:auto;padding:28px;line-height:1.5}section,article{border:1px solid var(--vscode-panel-border);border-radius:12px;padding:18px;margin:16px 0}h1{font-size:25px;font-weight:500}h2{font-size:19px}h3{font-size:15px}.muted{color:var(--vscode-descriptionForeground)}button,input,select,textarea{font:inherit;color:var(--vscode-foreground);background:var(--vscode-input-background);border:1px solid var(--vscode-panel-border);border-radius:6px;padding:8px}button{cursor:pointer}button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible{outline:2px solid var(--vscode-focusBorder)}textarea{box-sizing:border-box;width:100%;min-height:110px}label{display:block;margin:10px 0}.row{display:flex;gap:10px;flex-wrap:wrap;align-items:center}.role{flex:1;min-width:210px}.primary{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}pre{white-space:pre-wrap;overflow-wrap:anywhere}#notice{white-space:pre-wrap}#catalog{max-height:440px;overflow:auto}#catalog article{margin:8px 0}.run{border-left:3px solid var(--vscode-textLink-foreground)}
</style></head><body><h1>Agent teams & roles</h1><p class="muted">Run a team for your task, or let a supervising manager assign and review bounded text work. Model requests use Meshlit Usage admission. Cluster source labels describe your setup; they do not prove topology or combine RAM.</p><div class="row"><button id="refresh">Refresh</button><button id="unlock">Unlock usage</button></div><p id="notice" role="status">Loading…</p>
<section><h2>Team setup</h2><div class="row"><label>Location <select id="mode"><option>SINGLE_DEVICE</option><option>MULTI_DEVICE</option><option>LOCAL_CLUSTER</option><option>REMOTE_CLUSTER</option></select></label><label>Control <select id="control"><option value="USER_DIRECTED">User directed</option><option value="SUPERVISED_AUTONOMOUS">Autonomous · supervised</option><option value="AGENT_DIALOGUE">Agent conversation</option></select></label><label>Maximum rounds <select id="rounds"><option>1</option><option selected>2</option><option>3</option></select></label><label>Parallel workers <select id="parallel"><option>1</option><option selected>2</option></select></label></div><div id="roles" class="row"></div><p class="muted">User directed: planner → workers → reviewer once. Autonomous: the manager assigns known workers, reviews findings and may request revisions, up to the chosen limit. Agent conversation: selected agents can delegate and reply to each other within shared call, depth and time limits. Ten-minute deadline; 256 output tokens per call. Pause holds new calls after active calls finish; the deadline continues. Every local model call shares the inference queue.</p><label>Team task<textarea id="goal" maxlength="4000" placeholder="What should this team analyze?"></textarea></label><button id="start" class="primary">Start team</button>
<h3>Other agents can supervise teams</h3><p id="delegation">Delegation status unknown</p><p class="muted">Enabling saves this exact setup for agent use, including selected connection credentials encrypted with your Usage passphrase. Other agents may submit goals and control their runs. This does not grant file, shell, phone or new device access. Changing this delegation cancels its active teams. Editing, disabling or deleting a role revokes teams using it; explicitly enable a new saved setup afterward.</p><div class="row"><button id="delegate">Enable / replace with this setup</button><button id="revoke">Disable delegation</button></div></section>
<section><h2>Live and recent teams</h2><div id="runs"></div></section><section><h2>Saved role templates</h2><p class="muted">New templates start disabled. Enable a template to select it in a team. Role guidance never grants tools or credentials.</p><div id="saved"></div></section>
<section><h2>Agency Agents role library</h2><p id="provenance" class="muted">Bundled MIT role templates · AgentLand Contributors</p><input id="search" type="search" placeholder="Search role, division or specialty" aria-label="Search roles"><div id="catalog"></div><div id="editor" hidden><h3>Adapt role</h3><label>Name <input id="role-name" maxlength="48"></label><label>Editable guidance<textarea id="instruction" maxlength="1200"></textarea></label><button id="save-role">Save disabled template</button></div></section>
<script nonce="${nonce}">
const api=acquireVsCodeApi(),el=id=>document.getElementById(id),send=(op,fields={})=>api.postMessage({op,...fields});let catalog=[],saved=[],sources=[],selected;const choices=Array(4).fill(null);
function text(tag,value,parent){const node=document.createElement(tag);node.textContent=value;parent.append(node);return node;}
function button(label,parent,action){const b=text('button',label,parent);b.onclick=action;return b;}
function config(){return {goal:el('goal').value,mode:el('mode').value,control:el('control').value,maxRounds:Number(el('rounds').value),parallelWorkers:Number(el('parallel').value),roles:choices};}
el('refresh').onclick=()=>send('refresh');el('unlock').onclick=()=>send('unlock');el('start').onclick=()=>send('start',{config:config()});el('delegate').onclick=()=>send('delegate',{config:{...config(),goal:'Task supplied by delegated agent'}});el('revoke').onclick=()=>send('revoke-delegation');
function roles(){el('roles').replaceChildren();(el('control').value==='AGENT_DIALOGUE'?['Lead','Agent 2','Agent 3','Agent 4']:['Manager / planner','Worker 1','Worker 2','Reviewer']).forEach((label,i)=>{const div=text('div','',el('roles'));div.className='role';text('h3',label,div);const select=document.createElement('select');select.setAttribute('aria-label',label+' template');select.append(new Option('Default',''));saved.filter(p=>p.enabled).forEach(p=>select.append(new Option(p.name,p.id)));select.value=choices[i]||'';if(!select.value)choices[i]=null;select.onchange=()=>choices[i]=select.value||null;div.append(select);button(sources[i]?.label||'Bundled local model',div,()=>send('source',{index:i}));});}
el('control').onchange=roles;
function savedRoles(){el('saved').replaceChildren();saved.forEach(p=>{const a=text('article','',el('saved')),name=document.createElement('input'),guidance=document.createElement('textarea'),enabled=document.createElement('input');name.value=p.name;name.maxLength=48;name.setAttribute('aria-label','Role name');guidance.value=p.instruction;guidance.maxLength=1200;guidance.setAttribute('aria-label','Role guidance');enabled.type='checkbox';enabled.checked=p.enabled;const label=text('label','Enabled ',a);label.append(enabled);a.append(name,guidance);button('Save changes',a,()=>send('edit-role',{id:p.id,name:name.value,instruction:guidance.value,enabled:enabled.checked}));button('Delete template',a,()=>send('delete-role',{id:p.id}));});}
function library(){el('catalog').replaceChildren();const query=el('search').value.toLowerCase();catalog.filter(r=>(r.name+' '+r.division+' '+r.description).toLowerCase().includes(query)).forEach(r=>{const a=text('article','',el('catalog'));text('h3',r.name+' · '+r.division,a);text('p',r.description,a);button('Adapt role',a,()=>{selected=r;el('editor').hidden=false;el('role-name').value=r.name.slice(0,48);el('instruction').value=r.instruction;el('editor').scrollIntoView({block:'nearest'});});});}
el('search').oninput=library;el('save-role').onclick=()=>send('save-role',{catalogId:selected?.id,name:el('role-name').value,instruction:el('instruction').value});
window.addEventListener('message',({data:d})=>{if(d.type==='team-catalog'){catalog=d.roles;el('provenance').textContent=catalog.length+' templates · MIT · pinned commit '+d.commit;library();}else if(d.type==='team-config'){saved=d.profiles;sources=d.sources;roles();savedRoles();}else if(d.type==='team-state'){el('notice').textContent='Status refreshed';el('delegation').textContent=d.delegationEnabled?'Enabled · '+d.delegatedPlan.control+' · '+d.delegatedPlan.maxRounds+' rounds':'Disabled';el('runs').replaceChildren();[...(d.runs||[])].reverse().forEach(run=>{const a=text('article','',el('runs'));a.className='run';text('h3',run.phase+' · '+run.plan.control,a);text('p',run.plan.goal,a);text('p',(run.agentInitiated?'Agent initiated':'User initiated')+' · '+run.id+' · Active: '+(run.active.join(', ')||'none'),a);if(['QUEUED','RUNNING','PAUSING','PAUSED'].includes(run.phase)){button(['PAUSED','PAUSING'].includes(run.phase)?'Resume':'Pause after active calls',a,()=>send('control',{runId:run.id,action:['PAUSED','PAUSING'].includes(run.phase)?'resume':'pause'}));button('Stop',a,()=>send('control',{runId:run.id,action:'stop'}));}if(run.error)text('p',run.error,a);if(run.phase==='LIMIT_REACHED')text('p','Budget reached. Review findings before starting another run.',a);if((run.messages||[]).length){text('h3','Agent conversation',a);run.messages.forEach(message=>{text('h3',message.from+' → '+message.to+' · '+message.kind+' · call '+message.callIndex,a);text('pre',message.body,a);if(message.replyTo)text('p','Reply to '+message.replyTo,a);});}run.outputs.forEach(out=>{text('h3',out.memberId+' · '+out.sourceId+' · call '+out.callIndex,a);text('pre',out.text,a);});});}else if(['team-error','team-notice'].includes(d.type))el('notice').textContent=d.message;});send('ready');
</script></body></html>`;}
module.exports={create,render,buildPlan,validateProfile};
