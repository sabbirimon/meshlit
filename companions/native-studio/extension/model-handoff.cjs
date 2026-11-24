'use strict';

const names={gemini:'Gemini',chatgpt:'ChatGPT',openai:'ChatGPT'};
const defaults={Gemini:'https://generativelanguage.googleapis.com/v1beta/openai',ChatGPT:'https://api.openai.com/v1'};
const prefix='named-model-handoff:';

function intent(text){
    const input=text.trim().replace(/^(?:please|plz)\s+/i,'');
    let m=/^(?:connect(?:\s+(?:me\s+)?to)?|set\s+up)\s+(gemini|chatgpt|openai)[.!]?$/i.exec(input);
    if(m)return {name:names[m[1].toLowerCase()],connect:true};
    m=/^(?:ask|tell|use|delegate(?: (?:this|a) task)? to|send(?: (?:this|a) task)? to|give(?:(?: a| this)? task to)?)\s+(gemini|chatgpt|openai)\b(?:\s+(?:a task|this task))?(?:\s+to\s+|\s*[:,]\s*|\s+)([\s\S]+)$/i.exec(input);
    if(m && m[2].trim() && !/^(?:to|a task|this task)$/i.test(m[2].trim()))return {name:names[m[1].toLowerCase()],task:m[2].trim()};
}

function candidates(name,routes){
    return routes.filter(r=>{
        if(!r.base||!r.model||r.kind==='peer')return false;
        const id=r.model.split('/').pop();
        if(r.kind===name)return true;
        return (name==='Gemini'?/^gemini(?:[-.:]|$)/i:/^(?:gpt[-.]|chatgpt[-.]|o[1-9](?:-|$))/i).test(id);
    });
}

// A remembered grant applies only to one provider spelling and the selected endpoint/model.
function handoffs(vscode,globalState,{routes,modelId,configure}){
    function entry(name){return globalState.get(prefix+name);}
    async function forget(name){await globalState.update(prefix+name,undefined);}
    function automaticRoute(name,source){
        const state=entry(name);
        if(!state?.automatic || !['voice','agents'].includes(source) || !state[source])return;
        return routes().find(route=>modelId(route)===state.id);
    }
    async function prepare(intent){
        const {name,connect}=intent;
        const previous=entry(name);
        let route=routes().find(r=>previous && modelId(r)===previous.id);
        if(!route){
            const matching=candidates(name,routes());
            if(matching.length===1)route=matching[0];
            else {
                const choices=matching.map(r=>({label:r.model,detail:r.base,route:r}));
                choices.push({label:'Connect '+name+' API…',detail:defaults[name]});
                const selected=await vscode.window.showQuickPick(choices,{title:'Choose a '+name+' connection'});
                if(!selected)return;
                route=selected.route;
                if(!route){
                    route=await configure(name,defaults[name]);
                    if(!route)return;
                }
            }
        }
        const id=modelId(route);
        const automatic=previous?.id===id && previous.automatic===true;
        if(!connect && automatic)return {route,automatic:true};
        const detail=route.model+' at '+route.base+' receives the text addressed to '+name+'. Automatic access includes typed tasks and explicitly dictated named tasks. Choose the agent option to also accept tasks from installed IDE extensions in trusted workspaces. The provider may meter API use.';
        const once=connect?'Ask each time':'Send once',always='Always send named tasks',agents='Always allow, including agents';
        const choice=await vscode.window.showInformationMessage('Delegate text tasks to '+name+'?',{modal:true,detail},once,always,agents);
        if(!choice)return;
        const remember=choice===always || choice===agents;
        await globalState.update(prefix+name,{id,automatic:remember,voice:remember,agents:choice===agents});
        return {route,automatic:remember};
    }
    async function manage(){
        const choices=['Gemini','ChatGPT'].map(name=>{
            const state=entry(name),route=state && routes().find(r=>modelId(r)===state.id);
            return {label:name,name,detail:route?route.model+' · '+route.base+' · '+(state.agents?'Automatic · includes agents':state.automatic?'Automatic':'Ask each time'):'Choose connection'};
        });
        const p=await vscode.window.showQuickPick(choices,{title:'Automatic named model tasks'});
        if(!p)return;
        if(!entry(p.name))return prepare({name:p.name,connect:true});
        const action=await vscode.window.showQuickPick(['Change permission','Forget connection and automatic permission'],{title:p.name+' task access'});
        if(action==='Change permission')return prepare({name:p.name,connect:true});
        if(action)await forget(p.name);
    }
    async function removed(id){
        for(const name of ['Gemini','ChatGPT'])if(entry(name)?.id===id)await forget(name);
    }
    return {prepare,manage,removed,automaticRoute};
}
function checkTask(text){
    if(typeof text!=='string' || !text.trim() || text.length>16000)throw Error('Provide a named model task of up to 16,000 characters.');
    const command=intent(text);
    if(!command?.task)throw Error('Use “Ask Gemini to …” or “Ask ChatGPT to …” with the full task text.');
    if(/^(?:summari[sz]e|explain|review|translate|rewrite|analy[sz]e)\s+(?:(?:this|that|the)(?:\s+(?:reply|text|message|answer|document|file))?|it)[.?!]?$/i.test(command.task))throw Error('Paste the text to send after the named request.');
    return command;
}
function inputMessages(prompt,history,named){
    const prior=named?[]:history.slice(-8).map(({role,content})=>({role,content}));
    return [...prior,{role:'user',content:prompt}];
}
module.exports={intent,candidates,handoffs,inputMessages,checkTask};
