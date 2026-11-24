'use strict';
const path=require('node:path'),fs=require('node:fs'),{spawn}=require('node:child_process'),readline=require('node:readline');
function helper(root) {
    const folder=path.join(root,'automation');
    const [binary,args]=process.platform==='win32'?[path.join(process.env.SystemRoot,'System32/WindowsPowerShell/v1.0/powershell.exe'),['-NoProfile','-NonInteractive','-File',path.join(folder,'windows.ps1')]]:
        process.platform==='linux'?['/usr/bin/python3',['-I',path.join(folder,'linux.py')]]:[path.join(folder,'native/Meshlit App Access.app/Contents/MacOS/Meshlit App Access'),[]];
    if(!fs.existsSync(binary))throw Error('Platform accessibility helper is unavailable');
    const child=spawn(binary,args,{stdio:['pipe','pipe','ignore'],windowsHide:true});
    let sequence=0,pending;
    const lines=readline.createInterface({input:child.stdout});
    const fail=()=>{if(pending){const p=pending;pending=undefined;p.reject(Error('Accessibility helper stopped. On Linux install python3-gi and gir1.2-atspi-2.0.'));}};
    child.on('error',fail);child.on('exit',fail);child.stdin.on('error',fail);
    lines.on('line',line=>{if(!pending)return;try {const obj=JSON.parse(line);if(obj.id!==pending.id)return;const p=pending;pending=undefined;if(obj.error)p.reject(Error(obj.error));else p.resolve(obj.result);}catch{fail();}});
    return {request(fields){return new Promise((resolve,reject)=>{
        const id=++sequence,timer=setTimeout(()=>{child.kill();fail();},15000);
        pending={id,resolve:x=>{clearTimeout(timer);resolve(x);},reject:e=>{clearTimeout(timer);reject(e);}};
        child.stdin.write(JSON.stringify({...fields,id})+'\n');
    });},stop(){child.kill();lines.close();}};
}
function register(vscode,context,root,stopAutopilot=()=>{}) {
    let session;
    const stop=()=>{session?.stop();session=undefined;};
    context.subscriptions.push({dispose:stop},vscode.commands.registerCommand('meshlit.studio.stopControl',()=>{stop();stopAutopilot();}));
    return async function control(hint='') {
        stop();const current=helper(root);session=current;
        try {
            const status=await current.request({action:'status'});
            if(!status.trusted){
                if(process.platform==='darwin' && await vscode.window.showWarningMessage('Allow Meshlit App Access in macOS Accessibility settings?',{modal:true},'Open settings')==='Open settings'){
                    await current.request({action:'request_permission'});
                    await vscode.env.openExternal(vscode.Uri.parse('x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility'));
                }else throw Error('Enable the platform accessibility service to continue');return;
            }
            const {apps}=await current.request({action:'apps'});
            if(!apps?.length)throw Error('Open a supported graphical editor first, such as Notepad, gedit or TextEdit.');
            const app=await vscode.window.showQuickPick(apps.map(a=>({label:a.name,description:a.id,id:a.id})),{title:'Choose the local application to control'});
            if(!app)return;
            const {nodes}=await current.request({action:'snapshot',bundle_id:app.id});
            const choices=nodes.flatMap(n=>[...(n.press?[{label:'Press · '+(n.title||n.role),action:'press',n}]:[]),...(n.editable?[{label:'Set text · '+(n.title||n.role),action:'set_value',n}]:[])]);
            if(!choices.length)throw Error('The app exposes no supported editable fields or buttons');
            const query=hint.toLowerCase().trim();
            if(query)choices.sort((a,b)=>Number(b.label.toLowerCase().includes(query))-Number(a.label.toLowerCase().includes(query)));
            const target=await vscode.window.showQuickPick(choices,{title:'Choose a control in '+app.label});
            if(!target)return;
            const text=target.action==='set_value'?await vscode.window.showInputBox({title:'Text to enter',ignoreFocusOut:true,validateInput:v=>Buffer.byteLength(v)>8192?'Maximum 8 KiB':undefined}):undefined;
            if(target.action==='set_value'&&text===undefined)return;
            const choice=await vscode.window.showWarningMessage(app.label+'\n'+target.label+(text!==undefined?'\n'+text:''),{modal:true},'Allow once');
            if(choice!=='Allow once'||current!==session)return;
            await current.request({action:target.action,bundle_id:app.id,node_id:target.n.id,text});
            vscode.window.showInformationMessage('Application accepted the action.');
        }finally{current.stop();if(current===session)session=undefined;}
    };
}
module.exports={helper,register};
