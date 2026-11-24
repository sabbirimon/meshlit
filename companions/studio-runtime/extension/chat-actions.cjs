'use strict';
const MAX_TEXT=128*1024,MAX_TOTAL=1024*1024;
function messageText(value){
 if(typeof value!=='string'||!value.trim()||Buffer.byteLength(value,'utf8')>MAX_TEXT)throw Error('Message is empty or too large. Select a smaller section to copy.');
 return value;
}
function transcript(rows){
 if(!Array.isArray(rows)||!rows.length||rows.length>200)throw Error('Export supports 1–200 messages.');
 let total=0;
 return rows.map(row=>{
  if(!row||!['user','assistant'].includes(row.role))throw Error('Invalid message role.');
  const text=messageText(row.text);total+=Buffer.byteLength(text,'utf8');
  if(total>MAX_TOTAL)throw Error('Chat export exceeds 1 MiB.');
  return `## ${row.role==='user'?'You':'Meshlit'}\n\n${text}`;
 }).join('\n\n')+'\n';
}
async function handle(message,{vscode,voice,owner,post,speechState={},active=()=>true}){
 const reply=value=>{if(active())void post(value);};
 try{
  if(message.op==='copyMessage'){
   const text=messageText(message.text);await vscode.env.clipboard.writeText(text);
   reply({type:'messageAction',message:'Message copied.'});return true;
  }
  if(message.op==='exportChat'){
   const text=transcript(message.messages);
   const uri=await vscode.window.showSaveDialog({title:'Export chat locally',saveLabel:'Export chat',filters:{Markdown:['md'],Text:['txt']}});
   if(!uri||!active()){reply({type:'messageAction',message:'Export cancelled.'});return true;}
   await vscode.workspace.fs.writeFile(uri,Buffer.from(text,'utf8'));
   reply({type:'messageAction',message:'Chat exported to the selected file.'});return true;
  }
  if(message.op==='readAloud'){
   const text=messageText(message.text),token=Symbol('speech');speechState.token=token;reply({type:'speech',speaking:true});
   try{await voice.speak(text,owner);}finally{if(speechState.token===token)reply({type:'speech',speaking:false});}
   return true;
  }
  if(message.op==='stopSpeech'){speechState.token=undefined;voice.stop(owner);reply({type:'speech',speaking:false});return true;}
  return false;
 }catch(error){reply({type:'messageAction',error:['copyMessage','exportChat'].includes(message.op)?'Copy/export did not complete. Check message size and destination access.':'Speech did not complete. Check Chat voice settings.'});return true;}
}
module.exports={messageText,transcript,handle};
