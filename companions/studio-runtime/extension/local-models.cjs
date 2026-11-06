'use strict';
const STARTER='qwen25-1.5b';
const known=new Set([STARTER,'qwen3-4b']);
let catalog=[{id:STARTER,label:'Qwen2.5 1.5B Instruct · Q4_K_M',thinkingSupported:false,present:true}];
function accept(models){if(!Array.isArray(models))return;const next=models.filter(m=>m&&known.has(m.id)&&typeof m.label==='string'&&m.label.length<150&&typeof m.present==='boolean'&&typeof m.thinkingSupported==='boolean');if(next.length&&new Set(next.map(m=>m.id)).size===next.length)catalog=next.map(m=>({...m}));}
function list(){return catalog.map(m=>({...m}));}
function id(route){return route?.modelId||STARTER;}
function route(profileId){const modelId=profileId==='local'?STARTER:profileId.replace(/^local:/,'');const model=catalog.find(m=>m.id===modelId);if(!model||!model.present)throw Error('This bundled model is missing from the installation.');return {kind:'local',modelId:model.id,label:model.label,thinkingSupported:model.thinkingSupported};}
function thinking(route){return route?.kind==='local'&&catalog.some(m=>m.id===id(route)&&m.thinkingSupported);}
function state(route,current){const modelId=id(route);if((current.modelId||STARTER)===modelId)return current;const model=catalog.find(m=>m.id===modelId);return {modelId,label:model?.label,state:model?.present?'standby':'error',message:'This model is missing from the installation.'};}
module.exports={STARTER,accept,list,id,route,thinking,state};
