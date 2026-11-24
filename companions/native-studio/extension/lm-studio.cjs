// SPDX-License-Identifier: Apache-2.0
'use strict';
const {endpoint,request} = require('./runtime.cjs');

async function manage(vscode, route, token) {
    if (route.kind !== 'LM Studio') throw Error('Select a saved LM Studio connection first');
    const base = endpoint(route.base);
    if (!base.endsWith('/v1')) throw Error('Use the OpenAI-compatible /v1 base URL for this LM Studio connection');
    const native = base.slice(0,-3)+'/api/v1';
    const listing = await request(native,'/models',token,null,AbortSignal.timeout(12000));
    if (!Array.isArray(listing.models)) throw Error('This server did not return an LM Studio v1 inventory');
    const model = listing.models.find(m=>m.type==='llm' && (m.key===route.model || m.loaded_instances?.some(i=>i.id===route.model)));
    if (!model) throw Error('The selected model is no longer in this LM Studio inventory');
    const instances = Array.isArray(model.loaded_instances)?model.loaded_instances:[];
    const options = [{label:'Load a model instance',body:{model:model.key},op:'load'}];
    for (const instance of instances) {
        if (typeof instance.id === 'string') options.push({label:'Unload '+instance.id,body:{instance_id:instance.id},op:'unload'});
    }
    const choice = await vscode.window.showQuickPick(options,{title:model.key+' on '+new URL(base).host,placeHolder:instances.length+' loaded · '+[model.format,model.params_string].filter(Boolean).join(' · ')});
    if (!choice) return;
    const result = await request(native,'/models/'+choice.op,token,choice.body,AbortSignal.timeout(180000));
    if (typeof result.instance_id !== 'string') throw Error('Server response did not confirm the model instance; refresh its inventory');
    vscode.window.showInformationMessage('LM Studio '+(choice.op==='load'?'loaded ':'unloaded ')+result.instance_id);
}
module.exports={manage};
