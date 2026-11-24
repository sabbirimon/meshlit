'use strict';
const fs=require('node:fs/promises');
const catalogIds=new Set(Object.values(require('./operations.cjs').groups).flat().flatMap(([,ids])=>ids));
const builtins={project:'Create a multi-file project',build:'Build a local project',ssh:'SSH project build',ide:'Coding workspace',app:'Create a web app',devices:'Pair devices',models:'Models & providers',agents:'Agents and model tools',files:'Browse device files',mcp:'Manage MCP servers',shirudo:'Shirudo shields',internet:'Internet permission',autopilot:'Autopilot permission'};
function entries(doc) {
    if(doc?.schema!==1||!Array.isArray(doc.tools)||doc.tools.length>32)throw Error('Choose a schema 1 composer file with up to 32 tools.');
    const seen=new Set();
    return doc.tools.map(({command,title,target})=>{
        if(typeof command!=='string'||!/^[a-z][a-z0-9-]{0,31}$/.test(command)||seen.has(command)||
           typeof title!=='string'||!title.trim()||title.length>64||typeof target!=='string'||target.length>2048)throw Error('Invalid shortcut name, command or target.');
        seen.add(command);
        if(!Object.hasOwn(builtins,target)&&!(target.startsWith('studio:')&&catalogIds.has(target.slice(7)))&&!/^mcp:[a-zA-Z0-9_.-]{1,128}$/.test(target)){
            const url=new URL(target);
            if(url.protocol!=='https:'||url.username||url.password)throw Error('Choose an HTTPS service URL without inline credentials.');
        }
        return {command,title:title.trim(),target};
    });
}
function shieldIntent(text) {
    const s=text.trim().toLowerCase().replace(/[.!?]+$/,'').replace(/^please /,'').replace(/ please$/,'');
    if(['/shirudo','/shirudo status','show shirudo status'].includes(s))return 'status';
    if(['/shirudo scan','scan local shirudo target'].includes(s))return 'scan';
    if(['/shirudo import','scan my shirudo report','scan shirudo report','import shirudo report'].includes(s))return 'import';
    if(['/shirudo explain','explain my shirudo report','explain shirudo report'].includes(s))return 'explain';
    if(['/shirudo export','export shirudo report','export my shirudo report'].includes(s))return 'export';
}
function composer(vscode,context,hooks){
    const key='composer-shortcuts-v1';
    const list=()=>context.globalState.get(key,[]);
    async function save(items){await context.globalState.update(key,entries({schema:1,tools:items}));hooks.refresh();}
    async function runTool(name){
        const tool=vscode.lm?.tools?.find(t=>t.name===name);
        if(!tool)throw Error('Connect or enable this tool in MCP or the IDE extension manager first.');
        const input=await vscode.window.showInputBox({title:tool.name+' · JSON arguments',value:hooks.arguments(),prompt:JSON.stringify(tool.inputSchema||{}).slice(0,700)});
        if(input===undefined)return;
        const args=JSON.parse(input);
        if(!args||typeof args!=='object'||Array.isArray(args))throw Error('Enter a JSON object.');
        if(!await vscode.window.showWarningMessage('Run '+name+' with these arguments?',{modal:true,detail:input.slice(0,4000)},'Run tool'))return;
        const result=await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:name,cancellable:true},(_,token)=>vscode.lm.invokeTool(name,{input:args,toolInvocationToken:undefined},token));
        const output=(result.content||[]).map(part=>{
            if(typeof part.value==='string')return part.value;
            if(part.data instanceof Uint8Array)return '['+(part.mimeType||'Binary output')+' · '+part.data.byteLength+' bytes]';
            return JSON.stringify(part);
        }).join('\n');
        hooks.result(name,output || 'Tool finished with no output.');
    }
    async function activate(target){
        if(target.startsWith('mcp:'))return runTool(target.slice(4));
        if(target.startsWith('studio:'))return hooks.dashboard(target.slice(7));
        if(target==='agents'){
            const choices=[{label:'Open agent chat in the IDE',agent:true},...(vscode.lm?.tools||[]).map(t=>({label:t.name,description:t.description}))];
            const p=await vscode.window.showQuickPick(choices,{title:'Agents and registered tools'});
            if(p)return p.agent?hooks.agentChat():runTool(p.label);return;
        }
        if(Object.hasOwn(builtins,target))return hooks.activate(target);
        const url=new URL(target);
        if(await vscode.window.showInformationMessage('Open linked service?',{modal:true,detail:url.href},'Open service'))await vscode.env.openExternal(vscode.Uri.parse(url.href));
    }
    async function manage(){
        const action=await vscode.window.showQuickPick(['Pin a Meshlit tool','Pin a registered tool','Link an HTTPS service','Import shortcut JSON','Remove a shortcut'],{title:'Customize chat composer'});
        if(!action)return;
        if(action==='Remove a shortcut'){
            const pick=await vscode.window.showQuickPick(list().map(t=>({label:t.title,t})),{title:'Remove shortcut'});
            if(pick)await save(list().filter(t=>t.command!==pick.t.command));return;
        }
        if(action==='Import shortcut JSON'){
            const [uri]=await vscode.window.showOpenDialog({canSelectMany:false,filters:{'Composer shortcuts':['json']}})||[];
            if(!uri)return;
            const handle=await fs.open(uri.fsPath,require('node:fs').constants.O_RDONLY|require('node:fs').constants.O_NONBLOCK);
            let bytes;
            try{if(!(await handle.stat()).isFile())throw Error('Select a regular JSON file.');bytes=Buffer.alloc(65537);const {bytesRead}=await handle.read(bytes);if(bytesRead===bytes.length)throw Error('Maximum import size is 64 KiB.');bytes=bytes.subarray(0,bytesRead);}finally{await handle.close();}
            const added=entries(JSON.parse(bytes.toString('utf8')));
            if(added.length&&await vscode.window.showInformationMessage('Add these shortcuts?',{modal:true,detail:added.map(t=>t.title+' → '+t.target).join('\n')},'Import'))await save([...list(),...added]);return;
        }
        let target,title;
        if(action==='Pin a Meshlit tool'){
            const p=await vscode.window.showQuickPick([...Object.entries(builtins).map(([target,label])=>({label,target})),...await hooks.destinations()]);
            if(!p)return;target=p.target;title=p.label;
        }else if(action==='Pin a registered tool'){
            const p=await vscode.window.showQuickPick((vscode.lm?.tools||[]).map(t=>t.name));if(!p)return;target='mcp:'+p;title=p;
        }else{
            target=await vscode.window.showInputBox({title:'HTTPS service URL'});if(!target)return;
            title=await vscode.window.showInputBox({title:'Shortcut title'});if(!title)return;
        }
        const command=await vscode.window.showInputBox({title:'Command name · use /tool name',value:title.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'').slice(0,32)});
        if(command)await save([...list(),{command,title,target}]);
    }
    async function menu(){
        const choice=await vscode.window.showQuickPick([...list().map(t=>({label:t.title,description:'/tool '+t.command,target:t.target})),
            ...Object.entries(builtins).map(([target,label])=>({label,target})),{label:'Customize composer…',target:'manage'}],{title:'Chat tools'});
        if(choice)return choice.target==='manage'?manage():activate(choice.target);
    }
    async function shortcut(id){const item=list().find(t=>t.command===id);if(!item)throw Error('Unknown shortcut. Open More tools → Customize composer.');return activate(item.target);}
    return {menu,shortcut,activate,pins:()=>list().map(({title,command})=>({title,command}))};
}
module.exports={entries,shieldIntent,composer};
