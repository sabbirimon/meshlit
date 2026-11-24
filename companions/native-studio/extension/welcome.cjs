'use strict';
const viewHelp=require('./view-help.cjs');
const {systemDictation}=require('./system-dictation.cjs');
const icons={
 manage:'<path d="M4 6h9m4 0h3M4 12h3m4 0h9M4 18h10m4 0h2"/><circle cx="15" cy="6" r="2"/><circle cx="9" cy="12" r="2"/><circle cx="16" cy="18" r="2"/>',
 cloud:'<path d="M6 18a5 5 0 0 1-.8-9.9 7 7 0 0 1 13.2.2A5 5 0 0 1 18 18z"/>',
 thumb:'<path d="M6 10v11H3V10zm3 10V10l5-7c2 0 3 1 2 4l-1 3h5c1 0 2 1 2 2l-2 7c-.3 1-1 2-3 2h-4z"/>',
 coin:'<circle cx="12" cy="12" r="9"/><path d="M12 20V7m0 8-4-3V8m4 5 4-3V6"/>',
 history:'<circle cx="12" cy="12" r="9"/><path d="M12 6v6l-4 3"/>',
 tools:'<path d="M14 7a5 5 0 0 0-6 6L3 18l3 3 6-6a5 5 0 0 0 6-6l-3 3-3-3 3-3z"/>',
 shield:'<path d="M12 2l8 4v6c0 5-4 8-8 10-4-2-8-5-8-10V6z"/>',
 plus:'<path d="M12 5v14M5 12h14"/>', mic:'<rect x="9" y="3" width="6" height="12" rx="3"/><path d="M5.5 11v1a6.5 6.5 0 0 0 13 0v-1M12 18.5V22"/>',
 stop:'<rect x="6.5" y="6.5" width="11" height="11" rx="1" fill="currentColor" stroke="none"/>',
 compose:'<path d="m15 4 5 5M4 20l5-1L20 8a2 2 0 0 0-4-4L5 15z"/>',
 models:'<path d="m12 3 9 5-9 5-9-5zM3 12l9 5 9-5M3 16l9 5 9-5"/>',
 devices:'<rect x="2" y="4" width="14" height="11" rx="2"/><path d="M6 20h7m-4-5v5"/><rect x="17" y="10" width="5" height="11" rx="1"/>',
 control:'<path d="M4 3l4 16 4-6 7-2z"/>',settings:'<path d="M3 6h10m4 0h4M3 12h3m4 0h11M3 18h11m4 0h3"/><circle cx="15" cy="6" r="2"/><circle cx="8" cy="12" r="2"/><circle cx="16" cy="18" r="2"/>',
 ide:'<path d="m8 7-5 5 5 5m8-10 5 5-5 5m-3-13-2 16"/>',
 send:'<path d="M12 19V5m-6 6 6-6 6 6"/>',menu:'<rect x="3" y="4" width="18" height="16" rx="3"/><path d="M9 4v16"/>'
};
function icon(key){return '<svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round">'+icons[key]+'</svg>';}
function page(nonce,firstRun=false,brand="",pdf={}){
    const nav=(id,label,k)=>'<button class="nav" aria-label="'+label+'" title="'+label+'" id="'+id+'">'+icon(k)+'<span>'+label+'</span></button>';
    return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src blob:; media-src blob:; worker-src blob: ${pdf.origin||''}; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}' ${pdf.origin||''}"><style nonce="${nonce}">
:root{color-scheme:light dark;--bg:var(--vscode-editor-background);--rail:var(--vscode-sideBar-background);--ink:var(--vscode-foreground);--muted:var(--vscode-descriptionForeground);--line:var(--vscode-panel-border);--accent:var(--vscode-textLink-foreground)}
*{box-sizing:border-box}body{padding:0;margin:0;font:14px/1.45 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:var(--ink);background:var(--bg)}button,textarea{font:inherit;color:inherit}button{cursor:pointer;border:0;background:transparent}button:disabled{opacity:.5;cursor:default}button:focus-visible,textarea:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:2px}svg{width:18px;height:18px;flex-shrink:0}#app{display:grid;grid-template-columns:214px minmax(0,1fr);height:100vh}.collapsed #app{grid-template-columns:0 1fr}.collapsed aside{visibility:hidden}aside{overflow:auto;background:var(--rail);border-right:1px solid var(--line);display:flex;flex-direction:column;padding:24px 12px 14px}.brand svg{width:28px;height:28px}.brand{display:flex;align-items:center;gap:9px;font-size:16px;font-weight:650;padding:4px 10px 26px;letter-spacing:-.5px}.nav,.thread{width:100%;display:flex;gap:11px;align-items:center;padding:9px 10px;border-radius:7px;text-align:left;min-height:36px}.nav:hover,.thread:hover,#menu:hover,.chip:hover{background:var(--vscode-list-hoverBackground)}#recent{max-height:32vh;overflow-y:auto}.thread{color:var(--muted);display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.thread[aria-current=true]{color:var(--ink);background:var(--vscode-list-hoverBackground)}#recentTitle{font-size:12px;color:var(--muted);margin:22px 10px 5px}.bottom{margin-top:auto;padding-top:20px}.main{min-width:0;display:flex;flex-direction:column;overflow:hidden}.top{height:52px;display:flex;align-items:center;gap:10px;padding:0 18px;font-weight:550;flex-shrink:0}#menu{border-radius:6px;display:grid;place-items:center;padding:7px}.topSettings{margin-left:auto;display:flex;align-items:center;gap:7px;padding:6px 10px;border-radius:7px}.topSettings:hover{background:var(--vscode-list-hoverBackground)}.content{width:min(760px,100%);align-self:center;padding:0 24px 24px;display:flex;flex-direction:column;min-height:0;flex:1}.welcome{margin:auto 0 24px}.welcome h1{font-size:clamp(26px,4.3vw,43px);line-height:1.13;letter-spacing:-1.8px;font-weight:550;margin:0}.empty .composer{margin-bottom:auto}.empty #log{display:none}#log{flex:1;overflow:auto;margin:0 -10px;padding:12px 10px 20px}#log article{white-space:pre-wrap;overflow-wrap:anywhere;margin:0 0 22px}#log b{display:block;font-size:12px;color:var(--muted);margin-bottom:5px}.composer{border:1px solid var(--line);border-radius:18px;background:var(--vscode-input-background);padding:12px 14px;flex-shrink:0}.composer:focus-within{border-color:var(--vscode-focusBorder)}textarea{background:transparent;resize:vertical;width:100%;max-height:26vh;min-height:58px;border:0;padding:2px 4px;outline:0!important}.bar{display:flex;align-items:center;gap:8px}.chip{color:var(--muted);border-radius:7px;padding:5px 7px;max-width:78%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.submit{margin-left:auto;display:grid;place-items:center;background:var(--vscode-button-background);color:var(--vscode-button-foreground);border-radius:50%;width:32px;height:32px}#stop{margin-left:auto;padding:5px 10px;border-radius:6px;border:1px solid var(--line)}#feedback{color:var(--muted);font-size:12px;overflow-wrap:anywhere}#feedback:empty{display:none}[hidden]{display:none!important}@media(max-width:650px){#app{grid-template-columns:160px minmax(0,1fr)}aside{padding-inline:6px}.content{padding-inline:14px}.brand{font-size:14px}}@media(prefers-reduced-motion:no-preference){.nav:hover{transition:background .12s}}
#log article{width:100%;margin:0 0 30px;display:flex;align-items:flex-start;flex-direction:column}#log .user{align-items:flex-end}#log .body{max-width:min(92%,66ch);white-space:pre-wrap;overflow-wrap:anywhere;padding:10px 15px;line-height:1.55;border:1px solid var(--vscode-widget-border,var(--line));background:var(--vscode-editorWidget-background,var(--rail));border-radius:4px 16px 16px 16px}#log .user .body{background:var(--vscode-button-background,#225a92);color:var(--vscode-button-foreground,#fff);border-radius:8px}#toolAccess{font-size:12px;line-height:1.45;color:var(--muted);margin:4px 0;overflow-wrap:anywhere}.filePreview{max-width:100%;width:min(100%,580px);margin:.6rem 0}.filePreview summary{cursor:pointer}.filePreview img,.filePreview video,.filePreview canvas{display:block;width:100%;max-height:380px;object-fit:contain;margin:.5rem 0}.filePreview iframe{height:360px}.filePreview pre,.replyCode{white-space:pre-wrap;overflow:auto;background:var(--rail);padding:.75rem;border-radius:8px;max-height:26rem}.filePreview audio{max-width:100%}.replyActions,.utilities{display:flex;gap:4px;flex-wrap:wrap}.replyActions button,.utilities button,#back{font-size:12px;color:var(--muted);padding:4px 7px;border-radius:5px}.replyActions button:hover,.utilities button:hover,#back:hover{background:var(--vscode-list-hoverBackground)}.replyActions{margin-top:5px}.composer textarea{display:block;resize:none;min-height:46px;max-height:140px;line-height:1.5}.bar{flex-wrap:wrap}.chip{max-width:min(80%,480px)}.utilities{margin-bottom:5px}.utilities button{border:1px solid var(--line)}#attached{font-size:12px;color:var(--accent)}#metrics,#feedback{font-size:12px;color:var(--muted);margin-top:4px}.content{width:min(820px,100%)}.main{height:100vh}.welcome{text-align:center}.empty .composer{width:100%}@media(max-width:560px){.content{padding:0 10px 10px}.top{padding:0 8px}.chip{max-width:75%}}
#setup{width:min(100%,640px);align-self:center;margin:auto 0 18px}.setupTitle{font-size:clamp(32px,4.5vw,48px);letter-spacing:-1.7px;line-height:1.08;margin:0 0 12px}.setupIntro{color:var(--muted);font-size:15px;max-width:51ch}.setupChoices{border:1px solid var(--line);border-radius:14px;overflow:hidden;margin:22px 0 10px}.setupChoice{display:flex;width:100%;align-items:center;gap:14px;padding:14px 18px;text-align:left}.setupChoice+.setupChoice{border-top:1px solid var(--line)}.setupChoice:hover{background:var(--vscode-list-hoverBackground)}.setupChoice span{display:grid;gap:2px}.setupChoice small,#hostSummary{color:var(--muted);font-size:12px}.setupChoice strong{font-size:15px}.setupChoice:after{content:'›';margin-left:auto}.firstRun .welcome,.firstRun .composer,.firstRun #log{display:none}.firstRun{overflow:auto}.setupSkip{margin-top:8px;color:var(--accent);padding:6px 0}
.composer{position:relative;background:var(--vscode-input-background,var(--rail));padding:14px 18px 10px;border-radius:10px}.composer .bar{gap:5px;flex-wrap:nowrap;min-width:0}.composerGroup{position:relative;flex-shrink:0}.composerMenu{max-height:65vh;overflow-y:auto;position:absolute;bottom:calc(100% + 9px);left:0;z-index:5;min-width:185px;max-width:min(280px,75vw);padding:6px;background:var(--vscode-menu-background,var(--rail));border:1px solid var(--vscode-widget-border,var(--line));border-radius:10px;box-shadow:0 6px 20px #0003}.composerMenu button{display:block;width:100%;text-align:left;padding:7px 10px;border-radius:6px}.composerMenu button:hover,.iconButton:hover,#accessMenu:hover{background:var(--vscode-list-hoverBackground)}.composerMenu #toolAccess{padding:4px 10px;min-width:190px}.iconButton{width:32px;height:32px;display:grid;place-items:center;border-radius:7px}.bar .chip{margin-left:auto;min-width:0;max-width:60%;text-overflow:ellipsis;overflow:hidden;white-space:nowrap}.bar .submit,.bar #stop{margin:0;flex:none;border:0;padding:0;border-radius:50%;color:var(--vscode-button-foreground);background:var(--vscode-button-background)}#accessMenu{font-size:13px;color:var(--vscode-textLink-foreground);padding:5px 7px;border-radius:6px}.composer textarea{background:transparent}.composer #attached{font-size:12px;padding:3px 6px}.bar #stop[hidden],.bar #send[hidden],.composerMenu[hidden]{display:none}
.main{position:relative;isolation:isolate}.watermark{pointer-events:none;user-select:none;position:absolute;inset:0;display:grid;place-items:center;z-index:-1;opacity:.073}.watermark svg{width:min(43vw,43vh,370px);height:auto}.vscode-light .watermark{opacity:.048}.vscode-high-contrast .watermark,.vscode-high-contrast-light .watermark{display:none}.replyActions .rate{display:grid;place-items:center;width:30px;min-height:28px}.rate svg{width:16px;height:16px}.rate.down svg{transform:rotate(180deg)}.replyActions .rate[aria-pressed=true]{color:var(--vscode-textLink-foreground);background:var(--vscode-list-hoverBackground);outline:1px solid currentColor}
#app{--rail-width:214px;grid-template-columns:var(--rail-width) minmax(0,1fr)}.collapsed #app{grid-template-columns:54px minmax(0,1fr)}aside{padding-inline:8px}.nav{gap:12px;min-height:39px}.brand{min-height:50px}.collapsed #historyOptions{display:none}#historyOptions{text-align:left;font-size:12px;color:var(--muted);padding:5px 10px}.collapsed aside{visibility:visible;padding-inline:5px}.collapsed .brand,.collapsed .nav{justify-content:center;padding-inline:0}.collapsed aside .nav span,.collapsed aside .brand span,.collapsed #recent,.collapsed #recentTitle{display:none}.collapsed .brand{padding-bottom:26px}.collapsed .nav{width:100%}.nav:focus-visible{outline-offset:-2px}#historyToggle{display:none}.collapsed aside.hasRecents #historyToggle{display:flex}#recent{flex:1;max-height:none}.thread{padding-block:7px}@media(max-width:560px){#app{--rail-width:min(214px,62vw)}body:not(.collapsed) #rail{position:absolute;inset:0 auto 0 0;width:var(--rail-width);z-index:6}body:not(.collapsed) #app{grid-template-columns:54px minmax(0,1fr)}body:not(.collapsed) .main{grid-column:2}body:not(.collapsed) #menu{position:relative;left:calc(var(--rail-width) - 54px);z-index:7}}
.main{container-type:inline-size}.top{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);border-bottom:1px solid var(--line);gap:6px}.headStart,.headEnd{display:flex;align-items:center;gap:4px;min-width:0}.headEnd{justify-content:flex-end}.replyStats{font-variant-numeric:tabular-nums;color:var(--vscode-descriptionForeground);margin:6px 0;white-space:normal}.chatHeading{font-family:var(--vscode-font-family),system-ui,sans-serif;font-size:15px;font-weight:650;letter-spacing:.01em;text-align:center;white-space:nowrap;color:var(--vscode-symbolIcon-classForeground,var(--accent))}.welcome h1{color:var(--vscode-textLink-foreground)}#log article.assistant>b{color:var(--vscode-symbolIcon-methodForeground,var(--accent))}#log article.user>b{color:var(--vscode-textLink-foreground)}@container(max-width:510px){.top{padding-inline:6px}.topSettings span{display:none}.headStart,.headEnd{gap:0}.headStart #back{font-size:0}.headStart #back:before{content:'←';font-size:16px}}.topSettings{margin-left:0}.top button{white-space:nowrap}#chatFind:not([hidden]){display:flex;gap:6px;align-items:center;align-self:center;width:min(720px,96%);padding:8px 12px;background:var(--rail);border-radius:10px}#findText{min-width:0;flex:1;padding:6px;background:var(--vscode-input-background);color:var(--ink);border:1px solid var(--line);border-radius:4px}#log .findMatch .body{outline:2px solid var(--vscode-focusBorder);outline-offset:2px}.main article{line-height:1.55;overflow-wrap:anywhere}.nav:focus-visible,.composerGroup button:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:2px}@media(max-width:640px){.composerGroup button,.submit{min-height:34px}}
#log .body{font-size:16px;line-height:1.65}#log .assistant .body{padding:6px 0;border:0;background:transparent}.replyActions{gap:8px;margin-top:8px}.replyActions button{min-height:32px}.composer textarea{font:inherit;font-size:16px;line-height:1.5}.bar .submit,.bar #stop{border-radius:7px}
@media(pointer:coarse){.replyActions button,.iconButton,.composerMenu button,#accessMenu,.bar .chip{min-height:48px}.iconButton{min-width:48px}}
.railLabel{font-size:12px;color:var(--muted);padding:12px 10px 4px}.collapsed .railLabel{font-size:0;height:1px;padding:0;margin:7px;border-bottom:1px solid var(--line)}.transcript{display:flex;flex:1;min-height:0;gap:4px}.empty .transcript,.firstRun .transcript{display:none}.transcript #log{margin:0;min-width:0;overscroll-behavior:contain}#anchors{align-self:center;max-height:85%;overflow:auto;scrollbar-width:none;flex:0 0 22px}#anchors button{display:block;width:100%;padding:0;height:14px}#anchors button:before{content:'';display:block;width:6px;height:2px;background:var(--muted);opacity:.45}#anchors button[aria-current=true]:before,#anchors button:hover:before{width:12px;background:var(--ink);opacity:1}
.activityHidden .replyActivity{display:none}.replyActivity{color:var(--muted);font-size:13px;margin:6px 0}.replyActivity summary{cursor:pointer}.replyActivity ol{padding-left:22px}.replyActivity p{white-space:pre-wrap}
${viewHelp.styles}</style></head><body><div id="app"><aside id="rail" aria-label="Studio navigation"><div class="brand">${brand}<span>Meshlit Studio</span></div><div class="railLabel">Workspace</div>${nav('new','New chat','compose')}${nav('models','Models & providers','models')}${nav('pair','Devices','devices')}<div class="railLabel">Tools and access</div>${nav('tools','Tools','tools')}${nav('permissions','Permissions','shield')}${nav('control','Control an app','control')}<div class="railLabel">Services</div>${nav('cloud','Cloud providers','cloud')}${nav('wallet','MeshCoin','coin')}${nav('management','Management','manage')}${nav('historyToggle','Recent chats','history')}<button id="historyOptions" title="Save, pause or delete local chat history">Chat history settings</button><div id="recentTitle" hidden>Recent chats</div><div id="recent"></div><div class="bottom">${nav('ide','Open coding IDE','ide')}</div></aside><main class="main"><div class="watermark" aria-hidden="true">${brand.replaceAll('id="blue"','id="wm-blue"').replaceAll('url(#blue)','url(#wm-blue)')}</div><header class="top"><div class="headStart"><button id="menu" aria-label="Collapse sidebar" aria-controls="rail" aria-expanded="true">${icon('menu')}</button><button id="back" disabled data-reason="This is the first screen. Open another Studio page to use Back." aria-label="Go back">← Back</button></div><strong class="chatHeading">General chat</strong><div class="headEnd"><button id="findChat" title="Find a message in this chat" aria-expanded="false" aria-controls="chatFind">Find</button><button id="settings" class="topSettings">${icon('settings')}<span>Settings</span></button></div></header><div id="chatFind" hidden role="search"><input id="findText" aria-label="Find in this chat" placeholder="Find in this chat"><output id="findCount" aria-live="polite"></output><button id="findPrev" aria-label="Previous match">↑</button><button id="findNext" aria-label="Next match">↓</button><button id="findClose" aria-label="Close chat search">×</button></div><div class="content empty${firstRun?' firstRun':''}" id="content"><section id="setup" ${firstRun?'':'hidden'} aria-label="First-time setup"><div id="setupIntro"><h1 class="setupTitle">Welcome to Meshlit Studio</h1><button id="getStarted" class="setupChoice">Get started →</button></div><div id="firstModel" hidden><button id="setupBack">← Back</button><h1 class="setupTitle">Your first model</h1><p class="setupIntro">Choose a model for this computer or connect a provider.</p><div class="setupChoices"><button class="setupChoice" id="useSuggestion"><span><strong id="suggestedName">Checking available memory…</strong><small id="suggestedDetail"></small></span></button><button class="setupChoice" id="startLocal">${icon('compose')}<span><strong>Chat on this device</strong><small>Use the included Qwen model offline.</small></span></button><button class="setupChoice" id="startProvider">${icon('devices')}<span><strong>Connect a model provider</strong><small>Use Ollama, LM Studio or a compatible endpoint.</small></span></button><button class="setupChoice" id="startLibrary">${icon('models')}<span><strong>Browse the model library</strong><small>Import a checkpoint or find a model to download.</small></span></button></div><div id="hostSummary"></div><button id="skipSetup" class="setupSkip">Skip model setup</button></div><div id="advancedSetup" hidden><button id="advancedBack">← Back</button><h1 class="setupTitle">Advanced settings</h1><p><label><input type="checkbox" id="loginService"> Start Studio and load the local model at login</label></p><p><label><input type="checkbox" id="openServer"> Open model management after setup</label></p><button class="setupChoice" id="finishSetup">Continue to Studio →</button></div></section><div class="welcome" id="welcome"><h1>What would you like to work on?</h1></div><div class="transcript"><nav id="anchors" aria-label="Conversation points" hidden></nav><div id="log" role="log" aria-live="polite"></div></div><div class="composer"><textarea autofocus id="prompt" rows="2" aria-label="Message Meshlit" placeholder="Message Meshlit…"></textarea><button id="attached" hidden title="Remove attachment"></button><div class="bar">
<div class="composerGroup"><button id="addMenu" class="iconButton" aria-label="Add to chat" title="Add to chat" aria-expanded="false" aria-controls="addPanel">${icon('plus')}</button><div class="composerMenu" id="addPanel" hidden><button id="attach">Attach file or image</button><button id="screenFrame">Share screen…</button><button id="web">Internet search</button><button id="tuneChat">Generation settings</button><div id="composerPins"></div><button id="pasteResult">Paste tool result</button><button id="copyDraft">Copy draft for a tool</button><button id="composerInternet">Internet search</button><button id="toggleActivity" aria-pressed="true">Show reply activity</button><button id="composerAuto">Autopilot permission</button><button id="composerFiles">Browse device files…</button><button id="composerPair">Pair a device…</button><button id="newDocument">Create document…</button><button id="makeImage">Generate image…</button><button id="makeVideo">Generate video…</button><button id="researchTopic">Research…</button><button id="toolDownloads">Download tool output…</button><button id="composerServer">Allow local model clients…</button><button id="composerModels">Models and providers…</button><button id="composerAgents">Agents and model tools…</button><button id="moreTools">More tools…</button><button id="saveChat">Export this conversation…</button></div></div>
<div class="composerGroup"><button id="accessMenu" aria-controls="accessPanel" aria-expanded="false" title="Model access and chat actions">Chat only</button><div class="composerMenu" id="accessPanel" hidden><button id="namedAccess">Automatic named model tasks…</button><p id="toolAccess">Model tools: none</p><button id="replyPreference">Local reply preference…</button><button id="editAccess">Manage permissions</button><button id="guard">Reply filter</button></div></div>
<button class="chip" id="model" title="Choose model">Select model</button><button id="voice" class="iconButton" aria-label="Voice input" title="Voice input">${icon('mic')}</button><button class="submit" id="send" aria-label="Send message" title="Send message">${icon('send')}</button><button class="submit" id="stop" aria-label="Stop generating" title="Stop generating" hidden>${icon('stop')}</button></div><details id="liveActivity" class="replyActivity" hidden><summary>Activity</summary><ol></ol></details><div id="feedback" role="status"></div><div id="metrics"></div></div></div></main></div>
<script nonce="${nonce}">${viewHelp.script}const api=acquireVsCodeApi(),el=id=>document.getElementById(id);
let pendingSince=0,phase='',lastPrompt='';
let partial,activeModel='',access={},generating=false;
const waitForReply='A reply is running. Press Stop or wait for it to finish.';
let voiceHelp='Enable Voice input in Permissions, or use system dictation in the message box.';
const voiceField=el('prompt');
let voiceId,submittedVoice;
const dictation=(${systemDictation.toString()})(voiceField,{
 cancel(id){
    if(voiceId===id){voiceId=undefined;el('voice').setAttribute('aria-pressed','false');el('feedback').textContent='Named dictation ended. Your draft is editable.';}
    api.postMessage({op:'cancelSystemVoice',id});
 },
 submit(id,text){
    voiceId=undefined;submittedVoice={id,text};
    el('voice').setAttribute('aria-pressed','false');resize();busy(true);
    api.postMessage({op:'finishSystemVoice',id,text});
 }
});
document.addEventListener('visibilitychange',()=>{if(document.hidden)dictation.stop()});
el('voice').addEventListener('pointerdown',event=>{if(voiceId)event.preventDefault();});
let matchIndex=0;
function findMessages(delta=0){
 const term=el('findText').value.trim().toLocaleLowerCase();
 const rows=[...el('log').querySelectorAll('article')];
 rows.forEach(row=>row.classList.remove('findMatch'));
 const matches=term?rows.filter(row=>row.querySelector('.body')?.textContent.toLocaleLowerCase().includes(term)):[];
 matchIndex=matches.length?(matchIndex+delta+matches.length)%matches.length:0;
 el('findCount').textContent=!term?'':matches.length?(matchIndex+1)+' of '+matches.length:'No matches';
 el('findNext').disabled=el('findPrev').disabled=matches.length<2;
 if(matches.length){matches[matchIndex].classList.add('findMatch');matches[matchIndex].scrollIntoView({block:'nearest'});}
}
function toggleFind(show){
 el('chatFind').hidden=!show;el('findChat').setAttribute('aria-expanded',String(show));
 if(show){el('findText').focus();el('findText').select();findMessages();}
 else{el('findText').value='';findMessages();el('prompt').focus();}
}
el('findChat').onclick=()=>toggleFind(el('chatFind').hidden);
el('findClose').onclick=()=>toggleFind(false);
el('findText').oninput=()=>{matchIndex=0;findMessages()};
el('findNext').onclick=()=>findMessages(1);el('findPrev').onclick=()=>findMessages(-1);
el('findText').onkeydown=e=>{if(e.key==='Enter')findMessages(e.shiftKey?-1:1);if(e.key==='Escape')toggleFind(false);};
for(const [id,target] of [['composerInternet','internet'],['composerAuto','autopilot'],['composerFiles','files'],['composerPair','devices'],['composerModels','models'],['composerAgents','agents']])el(id).onclick=()=>api.postMessage({op:'composerBuiltin',target,draft:el('prompt').value});
for(const id of ['pasteResult','copyDraft','moreTools'])el(id).onclick=()=>api.postMessage({op:id,draft:el('prompt').value});
el('saveChat').onclick=()=>api.postMessage({op:'exportChat'});
function applyAccess(){
 const help={attach:'File access is blocked. Open Permissions and allow Read selected attachments.',web:'Internet search is blocked. Open Permissions and allow Internet search.',voice:voiceHelp};
 setAvailability(el('screenFrame'),generating?waitForReply:access.attach===false?help.attach:'');
 for(const id of ['attach','web','voice'])setAvailability(el(id),generating?waitForReply:access[id]===false?help[id]:'');
 for(const button of el('log').querySelectorAll('[data-action="speak"]'))setAvailability(button,access.speak===false?'Open Permissions and allow Read replies aloud.':'');
}
let anchorFrame=0,followEnd=true;
function refreshAnchors(){
 anchorFrame=0;
 const nav=el('anchors'),log=el('log'),top=log.getBoundingClientRect().top+24;
 let active=0;
 for(let i=0;i<nav.children.length;i++){
   if(nav.children[i].targetTurn.getBoundingClientRect().top>top)break;
   active=i;
 }
 for(let i=0;i<nav.children.length;i++)nav.children[i].setAttribute('aria-current',String(i===active));
}
function queueAnchors(){if(!anchorFrame)anchorFrame=requestAnimationFrame(refreshAnchors)}
function addAnchor(item,role,text){
 const nav=el('anchors'),button=document.createElement('button');
 button.title=(role==='user'?'You':'Meshlit')+' · '+text.slice(0,120);
 button.setAttribute('aria-label',button.title);button.targetTurn=item;
 button.onclick=()=>{followEnd=false;item.scrollIntoView({block:'start'});queueAnchors()};
 nav.append(button);nav.hidden=nav.children.length<2;queueAnchors();
}
el('log').addEventListener('scroll',()=>{const log=el('log');followEnd=log.scrollHeight-log.scrollTop-log.clientHeight<24;queueAnchors()},{passive:true});
for(const [id,command] of [['newDocument','document'],['makeImage','image'],['makeVideo','video'],['researchTopic','research'],['toolDownloads','downloads']])el(id).onclick=()=>api.postMessage({op:'composerBuiltin',target:command,draft:el('prompt').value});
el('namedAccess').onclick=()=>api.postMessage({op:'namedAccess'});
el('composerServer').onclick=()=>api.postMessage({op:'modelServer'});
el('toggleActivity').onclick=()=>api.postMessage({op:'toggleActivity'});
function clearPartial(){partial?.remove();partial=undefined;}
const previews=new Map();
function attachment(item,filename){
 const card=document.createElement('details'),label=document.createElement('summary'),slot=document.createElement('div');
 card.className='filePreview';card.open=true;
 label.textContent=filename;card.append(label,slot);
 const save=document.createElement('button');save.textContent='Download file';
 save.onclick=()=>api.postMessage({op:'downloadToolFile',filename});card.append(save);item.append(card);
 const group=previews.get(filename)||[];group.push(slot);previews.set(filename,group);
 api.postMessage({op:'previewFile',filename});
}
async function pdfPreview(slot,bytes){
 const config=${JSON.stringify(pdf)};
 try{
  const lib=await import(config.module);lib.GlobalWorkerOptions.workerSrc=config.worker;
  const pdfDoc=await lib.getDocument({data:bytes,isEvalSupported:false,useWasm:false,disableFontFace:true}).promise;
  if(!slot.isConnected){await pdfDoc.destroy();return}
  slot.cleanup=()=>pdfDoc.destroy().catch(()=>{});
  const canvas=document.createElement('canvas'),prev=document.createElement('button'),next=document.createElement('button'),label=document.createElement('span');
  prev.textContent='Previous page';next.textContent='Next page';slot.append(canvas,prev,label,next);
  let number=1;
  async function render(){
   prev.disabled=next.disabled=true;
   try{
    const page=await pdfDoc.getPage(number),base=page.getViewport({scale:1});
    const viewport=page.getViewport({scale:Math.min(2,1000/base.width,1400/base.height)});
    canvas.width=Math.ceil(viewport.width);canvas.height=Math.ceil(viewport.height);
    await page.render({canvasContext:canvas.getContext('2d'),viewport}).promise;
    label.textContent=' Page '+number+' of '+pdfDoc.numPages+' ';
    prev.disabled=number===1;next.disabled=number===pdfDoc.numPages;page.cleanup();
   }catch{if(slot.isConnected)slot.textContent='Page preview unavailable'}
  }
  prev.onclick=()=>{number--;render()};next.onclick=()=>{number++;render()};await render();
 }catch{if(slot.isConnected)slot.textContent='PDF preview unavailable'}
}
function showFile(filename,preview){
 for(const slot of previews.get(filename)||[]){
  if(!slot.isConnected || slot.dataset.loaded)continue;slot.dataset.loaded='1';
  if(preview.kind==='text'){const code=document.createElement('pre');code.textContent=preview.text+(preview.truncated?'…':'');slot.append(code);continue}
  if(!['image','video','audio','pdf'].includes(preview.kind)){slot.textContent=preview.kind==='missing'?'Attachment expired':'Preview unavailable for this file type';continue}
  const tag={image:'img',video:'video',audio:'audio',pdf:'iframe'}[preview.kind],node=document.createElement(tag);
  const raw=atob(preview.data),bytes=Uint8Array.from(raw,c=>c.charCodeAt(0));
  if(preview.kind==='pdf'){pdfPreview(slot,bytes);continue}
  const url=URL.createObjectURL(new Blob([bytes],{type:preview.mime}));slot.dataset.url=url;
  if(tag==='img'){node.alt='Generated image';node.loading='lazy'}
  if(tag==='video'||tag==='audio'){node.controls=true;node.preload='metadata'}
  if(tag==='iframe'){node.title='PDF preview';node.setAttribute('sandbox','')}
  node.onerror=()=>{slot.textContent='Preview unavailable for this encoding'};
  node.src=url;slot.append(node);
 }
}
function formatCode(body,text){
 const fence=String.fromCharCode(96).repeat(3),parts=text.split(fence);
 if(parts.length<3)return;
 body.replaceChildren();
 parts.forEach((part,i)=>{
  if(i%2===0){body.append(document.createTextNode(part));return}
  const pos=part.indexOf(String.fromCharCode(10)),pre=document.createElement('pre'),code=document.createElement('code');
  pre.className='replyCode';code.textContent=pos<0?part:part.slice(pos+1);pre.append(code);body.append(pre);
  const save=document.createElement('button');save.textContent='Download code';
  save.onclick=()=>api.postMessage({op:'exportCode',language:pos<0?'':part.slice(0,pos).trim(),text:code.textContent});body.append(save);
 });
}
function row(role,text,model=activeModel,streaming=false,replyId,vote,metrics,createdAtMs,activity=[],summary,files=[]){
 const item=document.createElement('article'),label=document.createElement('b'),body=document.createElement('div');
 item.className=role;label.textContent=role==='user'?'You':'Meshlit'+(model?' · '+model:'');if(Number.isFinite(createdAtMs)){const clock=document.createElement('time'),d=new Date(createdAtMs);clock.dateTime=d.toISOString();clock.title=d.toLocaleString();clock.textContent=' · '+d.toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'});label.append(clock)}body.className='body';body.textContent=text;item.append(label,body);if(role==='assistant'&&!streaming){formatCode(body,text);for(const filename of files)attachment(item,filename)}
 if(role==='assistant'&&!streaming){
  const details=document.createElement('details'),heading=document.createElement('summary');details.className='replyActivity';heading.textContent='Activity';details.append(heading);
  const list=document.createElement('ol');for(const step of activity){const li=document.createElement('li');li.textContent=step;list.append(li)}details.append(list);
  if(summary){const title=document.createElement('strong'),description=document.createElement('p');title.textContent='Model summary';description.textContent=summary;details.append(title,description)}
  const explain=document.createElement('button');explain.textContent='Explain answer';explain.onclick=()=>{const input=el('prompt');input.value=[input.value,'Briefly explain the assumptions and evidence behind this answer.',text.slice(0,2400)].filter(Boolean).join(String.fromCharCode(10,10));resize();input.focus()};details.append(explain);item.insertBefore(details,body);
 }
 if(role==='assistant'&&metrics){const stats=document.createElement('small');stats.className='replyStats';stats.textContent=metrics;item.append(stats);}
 if(role==='assistant'&&!streaming){
  const actions=document.createElement('div');actions.className='replyActions';
  if(replyId){
   item.dataset.replyId=replyId;
   for(const [value,title] of [['up','Like reply'],['down','Dislike reply']]){
    const button=document.createElement('button');button.className='rate '+value;button.dataset.vote=value;
    button.setAttribute('aria-label',title);button.title=title;button.setAttribute('aria-pressed',String(vote===value));
    button.innerHTML=${JSON.stringify(icon('thumb'))};
    button.onclick=()=>api.postMessage({op:'rateReply',id:replyId,vote:value});actions.append(button);
   }
   const preference=document.createElement('button');preference.textContent='Reply preference…';
   preference.title='Tell local models how to respond';preference.onclick=()=>api.postMessage({op:'replyPreference'});actions.append(preference);
  }
  for(const [id,title] of [['copy','Copy'],['quote','Reply'],['otherModel','Ask another model…'],['export','Download reply'],['speak','Read aloud'],['silence','Stop reading']]){
   const button=document.createElement('button');button.textContent=title;button.dataset.action=id;if(id==='speak')setAvailability(button,access.speak===false?'Open Permissions and allow Read replies aloud.':'');
   button.onclick=()=>{if(id==='otherModel'){api.postMessage({op:'otherModel',text,draft:el('prompt').value});return}if(id==='quote'){el('prompt').value='> '+text.split(String.fromCharCode(10)).join(String.fromCharCode(10)+'> ')+String.fromCharCode(10,10);el('prompt').focus();resize()}else api.postMessage({op:id,text})};actions.append(button);
  }item.append(actions);
 }
 el('log').append(item);if(role==='user')followEnd=true;if(!streaming && (role==='user'||role==='assistant'))addAnchor(item,role,text);el('welcome').hidden=true;el('content').classList.remove('empty');if(followEnd)el('log').scrollTop=el('log').scrollHeight;return item;
}
function resize(){const t=el('prompt');t.style.height='auto';t.style.height=Math.min(140,Math.max(46,t.scrollHeight))+'px'}
function tick(){if(pendingSince)el('feedback').textContent=phase+' · '+Math.floor((Date.now()-pendingSince)/1000)+' s'}
setInterval(tick,1000);
function busy(on){
 for(const id of ['new','models','model','send','attach','web','guard','voice','attached'])setAvailability(el(id),on?waitForReply:'');
 el('liveActivity').hidden=true;if(on){el('liveActivity').open=false;el('liveActivity').querySelector('ol').replaceChildren()}
 generating=on;applyAccess();el('stop').hidden=!on;el('send').hidden=on;
 pendingSince=on?Date.now():0;
 if(on){phase='Preparing request…';el('metrics').textContent='';tick()}
}
function clear(){dictation.stop();submittedVoice=undefined;for(const group of previews.values())for(const slot of group){if(slot.dataset.url)URL.revokeObjectURL(slot.dataset.url);slot.cleanup?.()}previews.clear();el('anchors').replaceChildren();el('anchors').hidden=true;followEnd=true;el('log').replaceChildren();el('welcome').hidden=false;el('content').classList.add('empty');el('feedback').textContent='';el('prompt').value='';el('metrics').textContent='';resize();busy(false)}
for(const id of ['useSuggestion','startLocal','startProvider','startLibrary','skipSetup','new','models','management','tools','cloud','settings','ide','pair','wallet','control','stop','back','web','voice','guard','permissions','attach','screenFrame'])el(id).onclick=()=>api.postMessage({op:id});el('model').onclick=()=>api.postMessage({op:'pickModel'});function setRail(compact){
 document.body.classList.toggle('collapsed',compact);
 const label=compact?'Expand sidebar':'Collapse sidebar';el('menu').title=label;el('menu').setAttribute('aria-label',label);el('menu').setAttribute('aria-expanded',String(!compact));
}
function chooseRail(compact){setRail(compact);api.postMessage({op:'sidebar',compact});}
el('menu').onclick=()=>chooseRail(!document.body.classList.contains('collapsed'));
el('historyOptions').onclick=()=>api.postMessage({op:'historyOptions'});el('historyToggle').onclick=()=>{chooseRail(false);el('recent').querySelector('button')?.focus()};
const menuPairs=[['addMenu','addPanel'],['accessMenu','accessPanel']];
function closeMenus(focus=false){for(const [id,target] of menuPairs){if(focus&&!el(target).hidden)el(id).focus();el(target).hidden=true;el(id).setAttribute('aria-expanded','false');}}
for(const [id,target] of menuPairs){el(id).onclick=()=>{const open=el(target).hidden;closeMenus();if(open){el(target).hidden=false;el(id).setAttribute('aria-expanded','true');el(target).querySelector('button:not(:disabled)')?.focus();}};}
document.addEventListener('click',e=>{if(!e.target.closest('.composerGroup')||e.target.closest('.composerMenu button'))closeMenus();});
document.addEventListener('keydown',e=>{if(e.key==='Escape')closeMenus(true);});
el('replyPreference').onclick=()=>api.postMessage({op:'replyPreference'});el('editAccess').onclick=()=>api.postMessage({op:'permissions'});el('tuneChat').onclick=()=>api.postMessage({op:'tuneChat'});
el('finishSetup').onclick=()=>api.postMessage({op:'finishSetup',login:el('loginService').checked,server:el('openServer').checked});el('advancedBack').onclick=()=>{el('advancedSetup').hidden=true;el('firstModel').hidden=false;};el('getStarted').onclick=()=>{el('setupIntro').hidden=true;el('firstModel').hidden=false};el('setupBack').onclick=()=>{el('setupIntro').hidden=false;el('firstModel').hidden=true};el('prompt').oninput=resize;el('attached').onclick=()=>api.postMessage({op:'dropAttachment'});el('send').onclick=()=>{const text=el('prompt').value.trim();if(!text)return;lastPrompt=text;row('user',text,activeModel,false,undefined,undefined,undefined,Date.now());el('prompt').value='';resize();busy(true);api.postMessage({op:'send',text})};el('prompt').onkeydown=e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.isComposing&&!el('send').disabled){e.preventDefault();el('send').click()}};
window.addEventListener('message',({data:m})=>{if(m.type==='setup'){el('setup').hidden=!m.visible;el('content').classList.toggle('firstRun',m.visible);el('hostSummary').textContent=m.host||'';if(m.recommendation){el('suggestedName').textContent='Recommended · '+m.recommendation.name;el('suggestedDetail').textContent=m.recommendation.detail;}if(!m.visible)el('prompt').focus()}
if(m.type==='armVoice'){
 voiceField.focus();
 if(dictation.arm(m.id)){
   voiceId=m.id;el('voice').setAttribute('aria-pressed','true');
   el('feedback').textContent=m.instruction+' Say “Ask Gemini to …” or “Ask ChatGPT to …”, then “send task now”. Esc cancels.';
 }else el('feedback').textContent='Review or clear your draft before dictating a named task.';
}
if(m.type==='disarmVoice' && m.id===voiceId)dictation.stop();
if(m.type==='systemVoiceResult' && m.id===submittedVoice?.id){
 if(m.ok && voiceField.value===submittedVoice.text){voiceField.value='';resize();}
 submittedVoice=undefined;busy(false);el('feedback').textContent=m.error||'';
}
if(['draft','restore','clear'].includes(m.type) || (m.type==='attachment' && m.name))dictation.stop();
if(m.type==='restartSetup'){el('setupIntro').hidden=false;el('firstModel').hidden=true;el('advancedSetup').hidden=true;}if(m.type==='advancedSetup'){el('firstModel').hidden=true;el('setupIntro').hidden=true;el('advancedSetup').hidden=false;el('loginService').checked=m.login;}
if(m.type==='sidebar')setRail(m.compact);
if(m.type==='navigation')setAvailability(el('back'),m.back?'':'This is the first screen. Open another Studio page to use Back.');
if(m.type==='metrics')el('metrics').textContent=m.text;
if(m.type==='activity'){const view=el('liveActivity');view.hidden=false;const list=view.querySelector('ol');list.replaceChildren(...m.steps.map(step=>{const li=document.createElement('li');li.textContent=step;return li}))}
if(m.type==='automaticTurn'){
 const r=m.reply;if(document.querySelector('[data-reply-id="'+r.id+'"]'))return;row('user',m.request);row('assistant',r.text,r.model,false,r.id,undefined,r.metrics,r.createdAtMs,r.activity,r.summary,r.files);
}
if(m.type==='progress'){phase=m.text;tick()}
if(m.type==='focus')el('prompt').focus();
if(m.type==='draft' && m.files?.length){
 const output=row('tool',m.title);
 for(const filename of m.files)attachment(output,filename);
}
if(m.type==='filePreview')showFile(m.filename,m.preview);
if(m.type==='draft'){el('prompt').value=m.replace?m.text:[el('prompt').value,m.text].filter(Boolean).join(String.fromCharCode(10));el('feedback').textContent='';el('prompt').focus();resize()}
if(m.type==='attachment'){el('attached').textContent=m.name+' ×';el('attached').hidden=!m.name}
if(m.type==='error'&&!el('prompt').value&&lastPrompt){el('prompt').value=lastPrompt;resize()}
if(m.type==='status'){
 document.body.classList.toggle('activityHidden',m.showActivity===false);el('toggleActivity').setAttribute('aria-pressed',String(m.showActivity!==false));el('toggleActivity').textContent='Show reply activity · '+(m.showActivity===false?'off':'on');
 activeModel=m.model;el('model').textContent=m.text;el('model').title='Choose model · '+m.text;
 const names={attach:'attachments',web:'internet search',voice:'dictation',speak:'read aloud'};
 voiceHelp=m.voiceHelp||voiceHelp;access=m.actions;applyAccess();
 if(m.systemDictation){el('voice').title='Dictate a named task';el('voice').setAttribute('aria-label','Dictate a named task');}
 if(!m.actions.voice)dictation.stop();
 const actions=Object.keys(names).filter(id=>m.actions[id]).map(id=>names[id]);
 el('composerPins').replaceChildren(...(m.pins||[]).map(p=>{const b=document.createElement('button');b.textContent=p.title;b.onclick=()=>api.postMessage({op:'composerPin',command:p.command,draft:el('prompt').value});return b}));
 el('composerInternet').textContent='Internet search · '+(m.actions.web?'on':'off');
 el('composerAuto').textContent='Autopilot · '+(m.automaticReads?'on':'off');
 el('accessMenu').textContent=m.automaticReads?'Autopilot · web reads':'Chat access';
 el('toolAccess').textContent='Your chat actions: '+(actions.join(', ')||'none enabled')+'.';
 el('accessMenu').title=el('toolAccess').textContent;
 el('guard').textContent='Reply filter '+(m.filter?'on':'off');
}if(m.type==='clear')clear();if(m.type==='restore'){clear();for(const x of m.messages)row(x.role,x.content,x.model||'',false,x.id,x.vote,x.metrics,x.createdAtMs,x.activity,x.summary,x.files)}if(m.type==='threads'){el('rail').classList.toggle('hasRecents',!!m.items.length);el('recentTitle').hidden=!m.items.length;el('recent').replaceChildren();for(const x of m.items){const b=document.createElement('button');b.className='thread';b.textContent=x.title;b.setAttribute('aria-current',String(x.id===m.current));b.onclick=()=>api.postMessage({op:'thread',id:x.id});el('recent').append(b)}}if(m.type==='partial'){if(!partial)partial=row('assistant','',activeModel,true);partial.querySelector('.body').textContent=m.text;if(followEnd)el('log').scrollTop=el('log').scrollHeight;}if(['answer','error','clear','restore'].includes(m.type))clearPartial();if(m.type==='answer')row('assistant',m.text,m.model,false,m.id,undefined,m.metrics,m.createdAtMs,m.activity,m.summary,m.files);if(m.type==='rated'){for(const item of el('log').querySelectorAll('[data-reply-id]'))if(item.dataset.replyId===m.id)for(const button of item.querySelectorAll('[data-vote]'))button.setAttribute('aria-pressed',String(button.dataset.vote===m.vote));}if(['answer','notice','error'].includes(m.type)){busy(false);el('feedback').textContent=m.type==='answer'?'':m.text}});setRail(innerWidth<560);api.postMessage({op:'ready'});</script></body></html>`;
}
module.exports={page};
