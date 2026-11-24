'use strict';
const fs=require('node:fs/promises');
const catalogIds=new Set(Object.values(require('./operations.cjs').groups).flat().flatMap(([,ids])=>ids));
const builtins={document:'Create a document',research:'Research a topic',image:'Generate an image',video:'Generate a video',downloads:'Download tool output',project:'Create a multi-file project',build:'Build a local project',ssh:'SSH project build',ide:'Coding workspace',app:'Create a web app',devices:'Pair devices',models:'Models & providers',agents:'Agents and model tools',files:'Browse device files',mcp:'Manage MCP servers',shirudo:'Shirudo shields',internet:'Internet permission',autopilot:'Autopilot permission'};
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
    const files=new Map();
    const byteLimit=32*1024*1024;
    function cacheFile(part) {
        const extensions={'image/png':'png','image/jpeg':'jpg','image/webp':'webp','image/gif':'gif','audio/wav':'wav',
            'video/mp4':'mp4','video/webm':'webm','application/pdf':'pdf','text/markdown':'md','text/plain':'txt'};
        const extension=extensions[part.mimeType]||'bin';
        let total=part.data.length;
        for(const bytes of files.values())total+=bytes.length;
        while(files.size && (files.size>=8 || total>byteLimit)){
            const oldest=files.keys().next().value;
            total-=files.get(oldest).length;
            files.delete(oldest);
        }
        const filename='meshlit-'+require('node:crypto').randomUUID()+'.'+extension;
        files.set(filename,Buffer.from(part.data));
        return filename;
    }
    async function download(filename){
        if(filename===undefined){
            if(!files.size)throw Error('Run a registered tool that returns a file first.');
            const items=[...files].map(([label,bytes])=>({label,description:bytes.length+' bytes'}));
            const pick=await vscode.window.showQuickPick(items,{title:'Download a tool result'});
            if(!pick)return;
            filename=pick.label;
        }
        const bytes=files.get(filename);
        if(!bytes)throw Error('This file has expired. Run the tool again to regenerate it.');
        const path=require('node:path').join(require('node:os').homedir(),filename);
        const dest=await vscode.window.showSaveDialog({saveLabel:'Download file',defaultUri:vscode.Uri.file(path)});
        if(dest)await vscode.workspace.fs.writeFile(dest,bytes);
    }
    async function runTool(name){
        const tool=vscode.lm?.tools?.find(t=>t.name===name);
        if(!tool)throw Error('Connect or enable this tool in MCP or the IDE extension manager first.');
        const input=await vscode.window.showInputBox({title:tool.name+' · JSON arguments',value:hooks.arguments(),prompt:JSON.stringify(tool.inputSchema||{}).slice(0,700)});
        if(input===undefined)return;
        const args=JSON.parse(input);
        if(!args||typeof args!=='object'||Array.isArray(args))throw Error('Enter a JSON object.');
        if(!await vscode.window.showWarningMessage('Run '+name+' with these arguments?',{modal:true,detail:input.slice(0,4000)},'Run tool'))return;
        const result=await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:name,cancellable:true},(_,token)=>vscode.lm.invokeTool(name,{input:args,toolInvocationToken:undefined},token));
        const attachments=[];
        const output=(result.content||[]).map(part=>{
            if(typeof part.value==='string')return part.value;
            if(part.data instanceof Uint8Array){
                const mime=part.mimeType||'application/octet-stream';
                if(part.data.length>byteLimit)return '['+mime+' exceeds the 32 MiB download limit]';
                const filename=cacheFile(part);
                attachments.push(filename);
                return filename+' · '+mime+' · '+part.data.length+' bytes';
            }
            return JSON.stringify(part);
        }).join('\n');
        hooks.result(name,output || 'Tool finished with no output.',attachments.filter(name=>files.has(name)));
    }
    async function activate(target){
        if(target==='downloads')return download();
        if(target==='image'||target==='video'){
            const list=(vscode.lm?.tools||[]).filter(t=>(t.name+' '+(t.description||'')).toLowerCase().includes(target));
            if(!list.length){await vscode.window.showInformationMessage('Connect a local '+target+' generator in MCP servers to use it from chat.');return hooks.activate('mcp');}
            const pick=await vscode.window.showQuickPick(list.map(t=>({label:t.name,description:t.description})),{title:'Choose your '+target+' generation tool'});
            if(pick)return runTool(pick.label);return;
        }
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
    async function catalog(){
        return [
            ...list().map(t=>({label:t.title,description:'Pinned · /tool '+t.command,target:t.target,command:t.command})),
            ...Object.entries(builtins).map(([target,label])=>({label,target})),
            ...await hooks.destinations(),
            ...(vscode.lm?.tools||[]).map(tool=>({label:tool.name,description:tool.description,target:'mcp:'+tool.name})),
            {label:'IDE command palette',description:'Browse all commands supplied by installed IDE extensions',target:'ide-commands'},
            {label:'Customize composer…',target:'manage'}
        ];
    }
    async function launch(target){
        if(target==='manage')return manage();
        if(target==='ide-commands')return vscode.commands.executeCommand('workbench.action.showCommands');
        return activate(target);
    }
    async function menu(query=''){
        const words=query.toLowerCase().split(/[^a-z0-9]+/).filter(w=>w.length>1);
        const items=await catalog();
        function rank(item){const text=(item.label+' '+item.target+' '+(item.description||'')).toLowerCase();return words.filter(w=>text.includes(w)).length;}
        if(words.length)items.sort((a,b)=>rank(b)-rank(a));
        const pick=await vscode.window.showQuickPick(items,{title:query?'Tools for '+query.slice(0,120):'All chat tools and services',matchOnDescription:true,placeHolder:'Search tools, services, or IDE commands'});
        if(pick)return launch(pick.target);
    }
    async function shortcut(id){
        const pin=list().find(p=>p.command===id);if(pin)return activate(pin.target);
        const items=await catalog();
        const pick=items.find(t=>t.command===id || t.target===id || t.target==='studio:'+id || t.target==='mcp:'+id);
        if(pick)return launch(pick.target);
        throw Error('Unknown tool. Use /tools to search the catalog.');
    }
    return {menu,shortcut,activate,download,pins:()=>list().map(({title,command})=>({title,command}))};
}
module.exports={entries,shieldIntent,composer};
