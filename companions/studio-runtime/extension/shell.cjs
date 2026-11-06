'use strict';
// The empty native window is Meshlit's application shell. Project windows keep
// their full editor surface and never enter this wrapper.
const crypto=require('node:crypto');
let context;
const panels=new Map();
const routes=Object.freeze({
 management:'meshlit.management',network:'meshlit.protocols',credentials:'meshlit.credentialsDashboard',ssh:'meshlit.sshDashboard',newChat:'meshlit.chat',workspaces:'meshlit.workspaces',agents:'meshlit.workspaceAgents',
 personality:'meshlit.chatPersonality',voice:'meshlit.chatVoice',liveVoice:'meshlit.voice',bots:'meshlit.botDashboard',models:'meshlit.models',llm:'meshlit.llmSettings',
 devices:'meshlit.deviceConnections',tools:'meshlit.connectedCapabilities',
 connectors:'meshlit.connectors',memory:'meshlit.memory',addModel:'meshlit.addModelSource',unlock:'meshlit.unlockUsage',review:'meshlit.workspaceChanges',permissions:'meshlit.permissions',access:'meshlit.appAccess',resources:'meshlit.systemMonitor',usage:'meshlit.usage',settings:'meshlit.settings',ide:'meshlit.openIde'
});
const escape=value=>String(value).replace(/[&<>"']/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
// Small original SVG icons share one grid and stroke; no platform font glyphs.
const iconPaths={
 newChat:'<path d="M12 5v14M5 12h14"/>',
 workspaces:'<path d="M3 7V5a1 1 0 0 1 1-1h5l2 3h9a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7Z"/>',
 bots:'<rect x="4" y="7" width="16" height="13" rx="4"/><path d="M12 3v4M1 12v4M23 12v4M9 16h6"/><circle cx="9" cy="12" r=".7"/><circle cx="15" cy="12" r=".7"/>',
 management:'<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
 models:'<path d="m12 3 9 5-9 5-9-5 9-5ZM3 12l9 5 9-5M3 16l9 5 9-5"/>',
 devices:'<rect x="2" y="4" width="14" height="11" rx="2"/><path d="M9 15v5M5 20h8"/><rect x="17" y="10" width="5" height="11" rx="1"/>',
 network:'<circle cx="12" cy="5" r="3"/><circle cx="5" cy="19" r="3"/><circle cx="19" cy="19" r="3"/><path d="M12 8v4M5 16v-4h14v4"/>',
 resources:'<path d="M3 12h4l3-8 4 16 3-8h4"/>',
 credentials:'<circle cx="8" cy="8" r="5"/><path d="m12 12 9 9M17 17l3-3M20 20l2-2"/>',
 ssh:'<rect x="3" y="4" width="18" height="16" rx="3"/><path d="m7 9 3 3-3 3M13 15h4"/>',
 usage:'<path d="M12 3v9h9M8 3.9A9 9 0 1 0 20.1 16"/>',
 settings:'<path d="M4 6h16M4 12h16M4 18h16"/><circle cx="8" cy="6" r="2"/><circle cx="16" cy="12" r="2"/><circle cx="10" cy="18" r="2"/>',
 personality:'<circle cx="12" cy="12" r="9"/><path d="M8 14a4 4 0 0 0 8 0M8 9h.01M16 9h.01"/>',
 voice:'<path d="M4 10v4M8 6v12M12 3v18M16 6v12M20 10v4"/>',
 liveVoice:'<rect x="9" y="3" width="6" height="12" rx="3"/><path d="M5 11v1a7 7 0 0 0 14 0v-1M12 19v3M8 22h8"/>',
 permissions:'<path d="m12 3 8 3v5c0 5-4 8-8 10-4-2-8-5-8-10V6l8-3Z"/><path d="m8 12 3 3 5-6"/>',
 connectors:'<path d="M8 3v4M16 3v4M6 7h12v4a6 6 0 0 1-12 0V7ZM12 17v4"/>',
 memory:'<path d="M12 6c-3-3-6-3-9-2v15c3-1 6-1 9 2 3-3 6-3 9-2V4c-3-1-6-1-9 2ZM12 6v15"/>',
 unlock:'<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3"/>',
 agents:'<circle cx="9" cy="7" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 4a3 3 0 0 1 0 6M18 14a5 5 0 0 1 3 4v3"/>',
 review:'<path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9l-6-6ZM14 3v6h6M8 13h8M8 17h5"/>',
 access:'<rect x="3" y="3" width="18" height="14" rx="2"/><path d="M8 21h8M12 17v4m-3-9 2 2 4-4"/>',
 sidebar:'<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/>',
 ide:'<path d="m8 7-5 5 5 5M16 7l5 5-5 5M14 4l-4 16"/>',
 chevron:'<path d="m9 5 7 7-7 7"/>'
};
function icon(name){const key={llm:'settings',addModel:'newChat',tools:'connectors'}[name]||name;return `<svg class="shell-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${iconPaths[key]||iconPaths.settings}</svg>`;}
function initialize(value){context=value;}
function enabled(vscode){
 return !!context&&!!vscode.workspace&&typeof vscode.workspace.getConfiguration==='function'&&!(vscode.workspace.workspaceFolders?.length)&&vscode.workspace.getConfiguration('meshlit').get('startupView','chat')!=='editor';
}
function snapshot(){return [...panels].map(([id,item])=>({id,title:String(item.panel.title),type:item.type,active:!!item.panel.active}));}
function update(){const message={type:'shellNavigation',panels:snapshot()};for(const item of panels.values())void Promise.resolve(item.panel.webview.postMessage(message)).catch(()=>{});}
function createPanel(vscode,...args){
 const panel=vscode.window.createWebviewPanel(...args);
 if(!enabled(vscode))return panel;
 const id=crypto.randomUUID(),type=String(args[0]),entry={panel,type};
 panels.set(id,entry);
 const disposables=[];
 disposables.push(panel.webview.onDidReceiveMessage(async message=>{
  if(message?.op!=='meshlit.shell')return;
  if(message.action==='ready'){update();return;}
  if(message.action==='reveal'||message.action==='close'){
   if(typeof message.id!=='string')return;
   const target=panels.get(message.id)?.panel;if(!target)return;
   if(message.action==='close'){if(panels.size>1)target.dispose();}else target.reveal();
   return;
  }
  const command=Object.hasOwn(routes,message.action)?routes[message.action]:undefined;
  if(!command)return;
  try{await vscode.commands.executeCommand(command);}
  catch{void panel.webview.postMessage({type:'shellError',message:'This page could not open. Please try again.'});}
 }));
 if(panel.onDidChangeViewState)disposables.push(panel.onDidChangeViewState(update));
 panel.onDidDispose(()=>{panels.delete(id);for(const disposable of disposables)disposable?.dispose?.();update();});
 const webview=new Proxy(panel.webview,{
  get(target,key){const value=Reflect.get(target,key,target);return typeof value==='function'?value.bind(target):value;},
  set(target,key,value){return Reflect.set(target,key,key==='html'?wrap(value,panel.title,type,id):value,target);}
 });
 const wrapped=new Proxy(panel,{
  get(target,key){if(key==='webview')return webview;const value=Reflect.get(target,key,target);return typeof value==='function'?value.bind(target):value;},
  set(target,key,value){const result=Reflect.set(target,key,value,target);if(key==='title')update();return result;}
 });
 context?.subscriptions?.push({dispose(){if(panels.has(id))panel.dispose();}});
 update();return wrapped;
}
function wrap(html,title,type,id=''){
 if(typeof html!=='string'||html.includes('data-meshlit-shell="true"'))return html;
 const nonce=html.match(/<(?:script|style)\b[^>]*\bnonce=["']([^"']+)["']/i)?.[1];
 // Do not loosen a document's security policy when it has no supported nonce.
 if(!nonce||!/^[a-zA-Z0-9+/_=-]+$/.test(nonce)||!/<body\b/i.test(html))return html;
 html=html.replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi,style=>style.replace(/(?<![\w.#-])body(?![\w-])/g,'.meshlit-page'));
 // VS Code allows acquireVsCodeApi once per document. Existing page scripts and
 // shell navigation deliberately share one instance, never a second acquisition.
 html=html.replace(/\bacquireVsCodeApi\s*\(\s*\)/g,'window.__meshlitShellApi');
 const nav=[['newChat','New chat'],['workspaces','Workspaces'],['bots','Bots']];
 const chatSettings=[['models','Models & modes'],['llm','LLM & reasoning'],['personality','Personality'],['voice','Chat voice'],['liveVoice','Live voice & translation'],['permissions','Tools & permissions'],['addModel','Connect model / gateway'],['connectors','MCP connectors'],['devices','Device connections'],['memory','Project memory'],['unlock','Protected Usage']];
 const management=[['management','Overview'],['models','Models & compute'],['devices','Devices & clusters'],['network','Network & connections'],['resources','Health & resources'],['credentials','Credentials'],['ssh','SSH & terminals'],['usage','Usage & budgets']];
 const studio=[['agents','Workspace agents'],['tools','Connected tools'],['review','Workspace changes'],['access','App access'],['resources','Resource monitor']];
 const active={'meshlit.management':'management','meshlit.settings':'settings','meshlit.usage':'usage','meshlit.credentials':'credentials','meshlit.ssh':'ssh','meshlit.personality':'personality','meshlit.main.models':'models','meshlit.main.workspaces':'workspaces','meshlit.main.settings':'llm','meshlit.main.permissions':'permissions'}[type]||(type.includes('device')?'devices':type.includes('bot')?'bots':null);
 const buttons=items=>items.map(([action,label])=>`<button type="button" data-shell-action="${action}" ${active===action?'aria-current="page"':''} class="${action==='newChat'?'shell-new':''}">${icon(action)}<span class="shell-nav-label">${label}</span></button>`).join('');
 const sidebar=`<aside id="meshlit-sidebar" aria-label="Meshlit navigation"><div class="shell-brand"><svg viewBox="0 0 40 40" aria-hidden="true"><path d="M7 31V9L20 22L33 9V31"/></svg><strong>Meshlit</strong></div><nav class="shell-nav">${buttons(nav)}</nav><div class="shell-scroll"><details class="shell-studio shell-management" open><summary>${icon("chevron")}<span>Management</span></summary><nav class="shell-nav" aria-label="Management dashboards">${buttons(management)}</nav></details><details class="shell-studio shell-chat-settings"><summary>${icon("chevron")}<span>Chat settings</span></summary><nav class="shell-nav" aria-label="Chat settings">${buttons(chatSettings)}</nav></details><details class="shell-studio"><summary>${icon("chevron")}<span>Studio tools</span></summary><nav class="shell-nav" aria-label="Studio tools">${buttons(studio)}</nav></details><div class="shell-session-lists"><div class="shell-list-heading">Conversations</div><div id="meshlit-open-chats" role="list" aria-label="Open conversations"></div><div id="meshlit-pages-heading" class="shell-list-heading" hidden>Open pages</div><div id="meshlit-open-pages" role="list" aria-label="Open settings and tools pages"></div></div></div><div class="shell-bottom">${buttons([['usage','Usage'],['settings','Settings']])}</div></aside>`;
 const css=`<style nonce="${nonce}">
html{height:100%;overflow:hidden}body[data-meshlit-shell]{margin:0!important;padding:0!important;max-width:none!important;width:100%;height:100vh;min-width:320px;display:grid!important;grid-template-columns:238px minmax(0,1fr);background:var(--vscode-editor-background);color:var(--vscode-foreground);font:14px var(--vscode-font-family);overflow:hidden}
#meshlit-sidebar{display:flex;flex-direction:column;min-height:0;padding:12px 12px 12px;overflow-y:auto;background:var(--vscode-sideBar-background,var(--vscode-editor-background));border-right:1px solid var(--vscode-panel-border);color:var(--vscode-sideBar-foreground,var(--vscode-foreground));overflow-y:auto}
.shell-brand{display:flex;align-items:center;gap:10px;padding:2px 10px 16px;font-size:17px;letter-spacing:-.3px}.shell-brand svg{width:27px;height:27px;fill:none;stroke:var(--vscode-textLink-foreground);stroke-width:3;stroke-linecap:round;stroke-linejoin:round}.shell-brand strong{font-weight:600}
#meshlit-sidebar button,.shell-topbar button{margin:0;font:inherit;color:inherit;border:0;border-radius:8px;background:transparent;text-align:left;cursor:pointer;line-height:1.4}
#meshlit-sidebar button:hover,.shell-topbar button:hover{background:var(--vscode-list-hoverBackground)}#meshlit-sidebar button:focus-visible,.shell-topbar button:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:-2px}
#meshlit-sidebar .shell-nav{display:flex;flex-direction:column;gap:3px;flex-shrink:0;position:static;align-self:stretch;top:auto}#meshlit-sidebar .shell-nav button,.shell-bottom button{display:flex;gap:11px;align-items:center;width:100%;padding:7px 12px}#meshlit-sidebar button span[aria-hidden]{width:18px;text-align:center;font-size:18px;color:var(--vscode-descriptionForeground)}#meshlit-sidebar .shell-new{background:var(--vscode-input-background);margin-bottom:14px;border:1px solid var(--vscode-panel-border);font-weight:500}#meshlit-sidebar button[aria-current=page]{background:var(--vscode-list-activeSelectionBackground);color:var(--vscode-list-activeSelectionForeground)}
.shell-studio{flex-shrink:0;margin:10px 0 0}.shell-studio summary{cursor:pointer;padding:10px 12px;font-size:12px;color:var(--vscode-descriptionForeground)}.shell-studio summary:focus-visible{outline:2px solid var(--vscode-focusBorder)}.shell-session-lists{flex:1 0 auto;min-height:90px;overflow:visible}.shell-list-heading{font-size:11px;color:var(--vscode-descriptionForeground);padding:26px 12px 10px;letter-spacing:.02em}#meshlit-open-pages,#meshlit-open-chats{min-height:0}.shell-page-row{display:flex;align-items:center;border-radius:8px;margin:2px 0}.shell-page-row.current{background:var(--vscode-list-inactiveSelectionBackground)}#meshlit-sidebar .shell-page-title{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding:10px 12px;font-size:13px}#meshlit-sidebar .shell-page-close:disabled{visibility:hidden}#meshlit-sidebar .shell-page-close{padding:4px 8px;margin-right:4px;color:var(--vscode-descriptionForeground);opacity:0}.shell-page-row:hover .shell-page-close,.shell-page-row:focus-within .shell-page-close{opacity:1!important}.shell-bottom{margin-top:12px;border-top:1px solid var(--vscode-panel-border);padding-top:10px}
.shell-content{display:flex;flex-direction:column;min-height:0;min-width:0;overflow:hidden}.shell-topbar{height:48px;flex-shrink:0;display:flex;align-items:center;gap:10px;padding:0 20px;border-bottom:1px solid transparent}.shell-topbar button{font-size:12px;padding:6px 10px}.shell-topbar .shell-open-ide{margin-left:auto;border:1px solid var(--vscode-panel-border);border-radius:20px}.shell-page-caption{font-size:12px;color:var(--vscode-descriptionForeground);overflow:hidden;white-space:nowrap;text-overflow:ellipsis}#meshlit-shell-error{margin:0 20px;color:var(--vscode-errorForeground)}#meshlit-shell-error:empty{display:none}
.shell-content>.meshlit-page{width:100%;height:calc(100vh - 48px);flex:1;min-height:0;overflow:auto;margin:0 auto}.shell-content>.meshlit-page.general{max-width:920px;padding:20px 36px 30px}.meshlit-page.general .launch-controls,.meshlit-page.general .source-strip{display:none}.meshlit-page.general header{display:none}.meshlit-page.general #route{font-size:11px}.meshlit-page.general #localStatus{font-size:11px}.meshlit-page.general #history{flex:1!important;min-height:100px;margin-top:12px!important}.meshlit-page.general #welcome{min-height:160px}.meshlit-page.general #welcome h2{font-size:29px;letter-spacing:-.8px}.meshlit-page.general #welcome svg{width:48px;height:48px;margin-bottom:24px}.meshlit-page.general .composer{padding:14px;border-radius:22px}.meshlit-page.general textarea{min-height:76px}.meshlit-page.general #keyboardHint{font-size:10px}

/* Shell controls remain consistent across the independently styled pages. */
body[data-meshlit-shell]{grid-template-columns:248px minmax(0,1fr)}
#meshlit-sidebar{padding:18px 12px 10px;overflow:hidden;gap:0}
.shell-brand{flex-shrink:0;padding:2px 12px 22px;font-size:18px;gap:11px;user-select:none}.shell-brand svg{width:28px;height:28px}
.shell-scroll{flex:1;min-height:0;overflow:auto;scrollbar-width:thin;scrollbar-color:var(--vscode-scrollbarSlider-background) transparent;margin:10px -5px 0;padding:0 5px 14px}
#meshlit-sidebar .shell-icon,.shell-topbar .shell-icon{width:18px;height:18px;flex:0 0 18px;color:var(--vscode-descriptionForeground)}
#meshlit-sidebar .shell-nav button,.shell-bottom button{min-height:36px;padding:8px 11px;gap:11px;font-size:13px;font-weight:400;transition:background .12s ease}
#meshlit-sidebar .shell-nav-label{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:inherit;color:inherit}
#meshlit-sidebar .shell-new{min-height:40px;margin-bottom:9px;border-radius:9px;font-weight:500;border-color:var(--vscode-panel-border);box-shadow:0 1px 2px #0000000d}
#meshlit-sidebar button[aria-current=page] .shell-icon{color:inherit}
.shell-studio{margin:9px 0 0}.shell-studio summary{display:flex;align-items:center;gap:7px;list-style:none;padding:9px 10px;font-size:11px;font-weight:600;letter-spacing:.025em;user-select:none;border-radius:6px}.shell-studio summary::-webkit-details-marker{display:none}.shell-studio summary:hover{color:var(--vscode-foreground);background:var(--vscode-list-hoverBackground)}
#meshlit-sidebar .shell-studio summary .shell-icon{width:12px;height:12px;flex-basis:12px;transition:transform .12s ease}.shell-studio[open]>summary .shell-icon{transform:rotate(90deg)}
#meshlit-sidebar .shell-studio .shell-nav{gap:1px}#meshlit-sidebar .shell-studio .shell-nav button{min-height:33px;padding:6px 11px;font-size:12px}#meshlit-sidebar .shell-studio .shell-nav .shell-icon{width:17px;height:17px;flex-basis:17px}
.shell-session-lists{min-height:72px;flex:initial}.shell-list-heading{padding:23px 11px 9px;font-size:11px;font-weight:600;letter-spacing:.025em;user-select:none}
.shell-page-row{margin:2px 0}.shell-page-row.current{background:var(--vscode-list-inactiveSelectionBackground);box-shadow:inset 2px 0 var(--vscode-focusBorder)}#meshlit-sidebar .shell-page-title{padding:8px 11px;min-height:34px;font-size:12px}#meshlit-sidebar .shell-page-row.current .shell-page-title[aria-current=page]{background:transparent;color:inherit}.shell-page-close{flex-shrink:0}
.shell-bottom{flex-shrink:0;margin-top:0;padding:8px 0 0;background:var(--vscode-sideBar-background,var(--vscode-editor-background))}.shell-bottom button{min-height:34px;font-size:12px}
.shell-topbar{height:52px;padding:0 22px;gap:12px}.shell-topbar button{display:inline-flex;align-items:center;justify-content:center;gap:7px;min-height:30px}.shell-topbar .shell-page-caption{font-size:13px}.shell-topbar .shell-open-ide{padding:5px 11px;border-radius:7px}.shell-topbar .shell-open-ide .shell-icon{width:15px;height:15px;flex-basis:15px}
.shell-content>.meshlit-page{height:calc(100vh - 52px)}
@media(max-height:700px){.shell-content>.meshlit-page.general{padding-top:10px;padding-bottom:12px}.meshlit-page.general #welcome{min-height:90px;height:auto;padding:8px 0}.meshlit-page.general #welcome svg{display:none}.meshlit-page.general #welcome h2{font-size:22px;margin-bottom:5px}.meshlit-page.general #welcome p{font-size:12px}.meshlit-page.general #history{min-height:90px;margin-top:6px!important}.meshlit-page.general textarea{min-height:56px}.meshlit-page.general .composer{padding:10px}}
@media(prefers-reduced-motion:reduce){#meshlit-sidebar .shell-nav button,#meshlit-sidebar .shell-studio summary .shell-icon{transition:none}}
body[data-meshlit-shell].shell-sidebar-hidden{grid-template-columns:0 minmax(0,1fr)}body.shell-sidebar-hidden #meshlit-sidebar{visibility:hidden;padding:0;border:0}@media(max-width:760px){body[data-meshlit-shell]{grid-template-columns:190px minmax(0,1fr)}#meshlit-sidebar{padding:16px 6px}.shell-content>.meshlit-page.general{padding:14px 18px 20px}.shell-nav button{font-size:12px!important}.shell-topbar{padding:0 12px}.shell-brand{padding-bottom:16px}}@media(max-width:540px){body[data-meshlit-shell]{grid-template-columns:0 minmax(0,1fr)}#meshlit-sidebar{visibility:hidden}body[data-meshlit-shell].shell-mobile-open{grid-template-columns:190px minmax(0,1fr)}body.shell-mobile-open #meshlit-sidebar{visibility:visible}.meshlit-page.general #welcome h2{font-size:23px}}
</style>`;
 const script=`<script nonce="${nonce}">(function(){
 const api=acquireVsCodeApi();window.__meshlitShellApi=api;
 function send(action,id){api.postMessage({op:'meshlit.shell',action,id});}
 document.addEventListener('DOMContentLoaded',()=>{
  for(const button of document.querySelectorAll('[data-shell-action]'))button.addEventListener('click',()=>send(button.dataset.shellAction));
  const toggle=document.getElementById('meshlit-toggle-sidebar');
  function syncSidebar(){toggle.setAttribute('aria-expanded',String(window.innerWidth<=540?document.body.classList.contains('shell-mobile-open'):!document.body.classList.contains('shell-sidebar-hidden')));}
  toggle.onclick=()=>{if(window.innerWidth<=540)document.body.classList.toggle('shell-mobile-open');else document.body.classList.toggle('shell-sidebar-hidden');syncSidebar();};
  window.addEventListener('resize',syncSidebar);syncSidebar();
  send('ready');
 });
 window.addEventListener('message',({data})=>{
  if(data?.type==='shellError'){const error=document.getElementById('meshlit-shell-error');if(error)error.textContent=data.message;return;}
  if(data?.type!=='shellNavigation'||!Array.isArray(data.panels))return;
  const list=document.getElementById('meshlit-open-pages'),chats=document.getElementById('meshlit-open-chats');if(!list||!chats)return;list.replaceChildren();chats.replaceChildren();let pages=0;
  for(const item of data.panels){if(typeof item.id!=='string'||typeof item.title!=='string')continue;
   const row=document.createElement('div');row.className='shell-page-row'+(item.id===document.body.dataset.shellId?' current':'');row.setAttribute('role','listitem');
   const button=document.createElement('button');button.className='shell-page-title';button.textContent=item.title;button.title=item.title;button.onclick=()=>send('reveal',item.id);if(item.id===document.body.dataset.shellId)button.setAttribute('aria-current','page');
   const close=document.createElement('button');close.className='shell-page-close';close.textContent='×';close.setAttribute('aria-label','Close '+item.title);close.disabled=data.panels.length===1;close.onclick=()=>send('close',item.id);row.append(button,close);if(item.type==='meshlit.chat')chats.append(row);else{list.append(row);pages++;}
  }
  document.getElementById('meshlit-pages-heading').hidden=pages===0;
 });
})();</script>`;
 html=html.replace(/<\/head>/i,css+script+'</head>');
 html=html.replace(/<body\b([^>]*)>/i,(_,attrs)=>{
  const classes=attrs.match(/\bclass=["']([^"']*)["']/i)?.[1]||'';
  return `<body data-meshlit-shell="true" data-shell-id="${escape(id)}">${sidebar}<div class="shell-content"><div class="shell-topbar"><button id="meshlit-toggle-sidebar" aria-label="Toggle sidebar" aria-controls="meshlit-sidebar" aria-expanded="true" title="Toggle sidebar">${icon("sidebar")}</button><span class="shell-page-caption">${escape(title)}</span><button class="shell-open-ide" data-shell-action="ide">${icon("ide")}<span>Open IDE</span></button></div><div id="meshlit-shell-error" role="status"></div><div class="meshlit-page ${escape(classes)}">`;
 });
 return html.replace(/<\/body>/i,'</div></div></body>');
}
module.exports={initialize,createPanel,wrap};
