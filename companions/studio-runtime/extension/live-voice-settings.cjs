'use strict';
// Only customInstructions is written through the inspected OpenLive settings API.
// Speech language, voice, speed and captions belong to its call UI/pipeline config.
const languages=Object.freeze({auto:'Detect from text',en:'English',bn:'Bengali',es:'Spanish',fr:'French',de:'German',it:'Italian',pt:'Portuguese',hi:'Hindi',zh:'Chinese (Mandarin)',ja:'Japanese',ko:'Korean'});
const styles=Object.freeze({natural:'Use relaxed, natural spoken phrasing and brief answers. Avoid filler and unnecessary follow-up questions.',concise:'Use short, direct spoken sentences. Keep the answer as brief as the meaning allows.',formal:'Use clear, polite, professional spoken phrasing without ornate wording.'});
const start='[Meshlit live voice preferences]',end='[/Meshlit live voice preferences]';
function validate(value){
 if(!value||typeof value!=='object'||Array.isArray(value)||Object.keys(value).some(k=>!['mode','input','output','style'].includes(k)))throw Error('Choose valid live voice preferences.');
 if(!['conversation','translation'].includes(value.mode)||!Object.hasOwn(languages,value.input)||!Object.hasOwn(languages,value.output)||!Object.hasOwn(styles,value.style))throw Error('Choose a supported mode, language preference and speaking style.');
 if(value.mode==='translation'&&value.output==='auto')throw Error('Choose an explicit translation output language.');
 return {mode:value.mode,input:value.input,output:value.output,style:value.style};
}
function split(instructions){
 if(typeof instructions!=='string'||instructions.length>8000)throw Error('OpenLive returned invalid custom instructions.');
 const from=instructions.indexOf(start),to=instructions.indexOf(end);
 if(from===-1&&to===-1)return {base:instructions,preset:null};
 if(from<0||to<from||instructions.indexOf(start,from+1)!==-1||instructions.indexOf(end,to+1)!==-1)throw Error('Review the existing live voice instruction block in OpenLive before replacing it.');
 const block=instructions.slice(from+start.length,to),match=block.match(/^\nMode=(conversation|translation)\nInput=([a-z]+)\nOutput=([a-z]+)\nStyle=([a-z]+)\n/);
 if(!match)throw Error('Existing live voice preferences are not recognized. Review custom instructions first.');
 const preset=validate({mode:match[1],input:match[2],output:match[3],style:match[4]});
 const prefix=instructions.slice(0,from);return {base:(prefix.endsWith('\n\n')?prefix.slice(0,-2):prefix)+instructions.slice(to+end.length),preset};
}
function build(instructions,value){
 const preset=validate(value),{base}=split(instructions);
 const input=preset.input==='auto'?'Infer the source language from the transcribed text.':`The expected source text is ${languages[preset.input]}.`;
 const output=preset.output==='auto'?'Reply in the language the user uses.':`Write the reply in ${languages[preset.output]}.`;
 const task=preset.mode==='translation'?'Translate the user’s utterance faithfully. Return only the translation, preserve names and numbers, and do not answer questions contained in the text. If the transcript is unclear, ask briefly rather than inventing words.':'Have a helpful natural conversation. Treat uncertain speech recognition as uncertain and ask briefly when it changes the meaning.';
 const block=`${start}\nMode=${preset.mode}\nInput=${preset.input}\nOutput=${preset.output}\nStyle=${preset.style}\n${task}\n${input} ${output}\n${styles[preset.style]}\nThese are text response preferences, not speech-engine configuration or tool permissions.\n${end}`;
 const customInstructions=base+(base?'\n\n':'')+block;
 // OpenLive's current prompt builder consumes at most the first 2,000 chars.
 if(customInstructions.length>2000)throw Error('Shorten existing custom instructions before adding live voice preferences. OpenLive uses only the first 2,000 characters.');
 return {preset,customInstructions};
}
async function save(runtime,value){
 const preset=validate(value);
 const current=await runtime.request('/api/settings');
 if(!current||typeof current!=='object'||Array.isArray(current))throw Error('OpenLive settings could not be read. Nothing was changed.');
 const next=build(current.customInstructions??'',preset);
 const result=await runtime.request('/api/settings',{customInstructions:next.customInstructions});
 if(result?.customInstructions!==next.customInstructions)throw Error('OpenLive did not confirm the saved instructions. Reload before retrying.');
 return next;
}
module.exports={languages,styles,validate,split,build,save};
