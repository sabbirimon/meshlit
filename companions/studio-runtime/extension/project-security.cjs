'use strict';
const fs=require('node:fs');
const path=require('node:path');
const key='meshlit.projectSecurity';
const modes=new Set(['local','standard','protected']);
const approvalModes=new Set(['ask','read-only','workspace']);
function folder(value){
 if(typeof value!=='string'||!path.isAbsolute(value)||value.length>4096||/[\x00-\x1f]/.test(value))return;
 try{const root=fs.realpathSync(value);if(fs.statSync(root).isDirectory())return root;}catch{}
}
/** Project-scoped presets never approve external tools, credentials or providers. */
function create(context,vscode,{unlockProtected,isProtected}){
 function records(){
  const saved=context.globalState.get(key,[]);
  return Array.isArray(saved)?saved.filter(item=>item&&typeof item.root==='string'&&modes.has(item.mode)&&Number.isSafeInteger(item.reviewedAt)).slice(0,128).map(item=>({root:item.root,mode:item.mode,reviewedAt:item.reviewedAt,approval:{mode:approvalModes.has(item.approval?.mode)?item.approval.mode:'ask',tools:[]}})):[];
 }
 function status(value){const root=folder(value);if(!root)return null;const entry=records().find(item=>item.root===root);return entry?{...entry}:null;}
 async function review(uri,{force=false}={}){
  if(uri?.scheme!=='file')return false;
  const root=folder(uri.fsPath);if(!root)return false;
  if(!force&&status(root))return true;
  const previous=status(root);
  const choice=await vscode.window.showQuickPick([
   {label:'Local autonomous · no password',description:'Bounded reads and edits in this selected project.',detail:'Unsaved editor changes stay available for review, with Stop during agent work. No shell, device, MCP, credential, or provider grants are enabled automatically.',mode:'local'},
   {label:'Local workspace · choose permissions',description:'No password. Choose Ask, read-only, or autonomous editing next.',detail:'Choose a reusable project action preset on the next screen. Shell, device, MCP, credential, and provider grants remain separate.',mode:'standard'},
   {label:'Protected workspace',description:'Unlock encrypted Usage for provider requests in the coding window.',detail:'The project opens separately. Its coding window must unlock before starting an agent. Files themselves are not encrypted by this setting.',mode:'protected'}
  ],{title:`${previous?'Review':'Set up'} project security · ${path.basename(root)}`,placeHolder:'Local autonomy needs no password and is limited to this project. Protected storage is optional.',ignoreFocusOut:true});
  if(!choice||!modes.has(choice.mode))return false;
  const permission=choice.mode==='local'?{mode:'workspace'}:await vscode.window.showQuickPick([
   {label:'Ask each action',description:'Review every workspace read and edit.',detail:'Choose this for individual action approval. External services still require their own grants.',mode:'ask'},
   {label:'Auto workspace reads',description:'Read and list files in this selected project without repeated prompts.',detail:'Edits remain reviewed. No shell, device, MCP, credential, or provider grants.',mode:'read-only'},
   {label:'Autonomous workspace edits',description:'Bounded reads and edits in this selected project.',detail:'Unsaved editor changes stay available for review. No shell, device, MCP, credential, or provider grants.',mode:'workspace'}
  ],{title:`Project action permissions · ${path.basename(root)}`,placeHolder:'This saved preset applies only to workspace actions in this project.',ignoreFocusOut:true});
  if(!permission||!approvalModes.has(permission.mode))return false;
  const entry={root,mode:choice.mode,reviewedAt:Date.now(),approval:{mode:permission.mode,tools:[]}};
  await context.globalState.update(key,[entry,...records().filter(item=>item.root!==root)].slice(0,128));
  return true;
 }
 async function ensure(value){
  const root=folder(value);if(!root)return false;
  let selected=status(root);
  if(!selected){
   if(!await review(vscode.Uri.file(root)))return false;
   selected=status(root);
  }
  if(selected?.mode==='standard'||selected?.mode==='local')return true;
  if(selected?.mode!=='protected')return false;
  if(isProtected())return true;
  try{await unlockProtected();return isProtected()===true;}catch{return false;}
 }
 return {ensure,review,status};
}
module.exports={create};
