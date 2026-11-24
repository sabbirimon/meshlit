'use strict';
const {spawn}=require('node:child_process');
const {helper}=require('./control.cjs');
const applications={
    darwin:{textedit:['com.apple.TextEdit','/usr/bin/open',['-b','com.apple.TextEdit']],calculator:['com.apple.calculator','/usr/bin/open',['-b','com.apple.calculator']]},
    win32:{notepad:['notepad','notepad.exe',[]],mspaint:['mspaint','mspaint.exe',[]]},
    linux:Object.fromEntries(['gedit','gnome-text-editor','kate','kwrite','mousepad','xed','leafpad','pluma'].map(name=>[name,[name,name,[]]]))
};
function intent(text){
    const match=/^(?:please\s+)?(?:use|operate|open|control)\s+([\w.-]{1,80})\s+(?:to|and then|and)\s+(.{1,2000})$/i.exec(text.trim());
    return match && Object.hasOwn(applications[process.platform]||{},match[1].toLowerCase()) ? {app:match[1].toLowerCase(),task:match[2]} : null;
}
function launch(command,args){
    return new Promise((resolve,reject)=>{
        const child=spawn(command,args,{detached:true,stdio:'ignore'});
        child.once('error',reject);
        child.once('spawn',()=>{child.unref();resolve();});
    });
}
async function perform({client,appId,plan,task,check,progress}){
    let count=0;
    for(let round=0; round<=8; round++){
        check();
        const {nodes}=await client.request({action:'snapshot',bundle_id:appId});
        check();
        const rows=nodes.filter(n=>n.editable || n.press).slice(0,80);
        const controls=rows.map((n,index)=>({index,role:n.role,label:String(n.title||n.description||'').slice(0,120),
            value:String(n.value||'').slice(0,180),press:!!n.press,editable:!!n.editable}));
        const next=await plan(task,controls,count);
        check();
        if(next.action==='done'){
            if(!count)throw Error('The model ended the task before operating a control.');
            return count;
        }
        const node=Number.isInteger(next.index) && rows[next.index];
        if(!node || count>=8)throw Error('No current control matched the plan, or the eight-action limit was reached.');
        const req={action:next.action,bundle_id:appId,node_id:node.id};
        if(next.action==='set_value' && node.editable && typeof next.text==='string' && Buffer.byteLength(next.text)<=8192)req.text=next.text;
        else if(next.action!=='press' || !node.press)throw Error('The selected control does not accept this action.');
        check();
        const result=await client.request(req);
        if(result?.ok!==true)throw Error('The app declined the requested action.');
        progress(++count,next.action);
    }
}
async function run({root,command,signal,check,choose,plan,progress}){
    const spec=applications[process.platform]?.[command.app];
    if(!spec)throw Error('Choose a supported app: '+Object.keys(applications[process.platform]||{}).join(', '));
    const client=helper(root);
    const stop=()=>client.stop();
    signal.addEventListener('abort',stop,{once:true});
    function allowed(){signal.throwIfAborted();check();}
    try{
        allowed();
        if(!(await client.request({action:'status'})).trusted)throw Error('Allow Meshlit App Access in system Accessibility settings and retry.');
        const find=async()=>(await client.request({action:'apps'})).apps.filter(a=>process.platform==='darwin'?
            a.id.toLowerCase()===spec[0].toLowerCase():a.name.toLowerCase().split(' · ')[0]===spec[0]);
        let matches=await find();
        if(!matches.length){
            allowed();await launch(spec[1],spec[2]);
            for(let i=0;i<12 && !matches.length;i++){await new Promise(r=>setTimeout(r,250));allowed();matches=await find();}
        }
        if(!matches.length)throw Error('The app did not expose an accessible window.');
        const target=matches.length===1?matches[0]:await choose(matches);
        if(!target)throw Error('Window selection cancelled.');
        return await perform({client,appId:target.id,plan,task:command.task,check:allowed,progress});
    }finally{signal.removeEventListener('abort',stop);stop();}
}
function textEntry(task,controls,steps){
    const match=/^(?:type|enter|write)\s+"([^"\n]{1,2000})"$/i.exec(task);
    if(!match)return undefined;
    if(steps)return {action:'done'};
    const fields=controls.filter(n=>n.editable);
    if(fields.length===1)return {action:'set_value',index:fields[0].index,text:match[1]};
}
module.exports={intent,perform,run,textEntry};
