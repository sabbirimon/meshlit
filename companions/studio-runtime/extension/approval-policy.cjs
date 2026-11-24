const policies=[
 {id:'ask',label:'Ask each time',description:'Review each workspace tool request'},
 {id:'read-only',label:'Auto workspace reads',description:'Bounded list/read inside this workspace; ask before edit preparation'},
 {id:'workspace',label:'Autonomous workspace edits',description:'Read and apply bounded edits in this project without per-action prompts; review and save changes yourself'},
 {id:'selected',label:'Selected workspace tools',description:'Skip initial prompts only for workspace tools you select; applying edits still needs review'}
];
const names=['list_files','read_file','propose_edit'];
function normalize(value){
 if(!value)return {mode:'ask',tools:[]};
 if(!policies.some(p=>p.id===value.mode)||!Array.isArray(value.tools)||value.tools.some(t=>!names.includes(t)))throw Error('Invalid workspace approval policy');
 return {mode:value.mode,tools:[...new Set(value.tools)]};
}
function automatic(policy,tool){
 const p=normalize(policy);
 return p.mode==='workspace'&&names.includes(tool)||p.mode==='read-only'&&['list_files','read_file'].includes(tool)||p.mode==='selected'&&names.includes(tool)&&p.tools.includes(tool);
}
async function choose(current,vscode){
 const p=normalize(current),selected=await vscode.window.showQuickPick(policies,{title:'Workspace tool approvals',placeHolder:'MCP, app access, credentials and policy changes keep their separate approvals'});
 if(!selected)return;
 let tools=[];
 if(selected.id==='selected'){
  const picked=await vscode.window.showQuickPick([
   {id:'list_files',label:'List workspace files',description:'At most 200 non-hidden entries; workspace boundary enforced',picked:p.tools.includes('list_files')},
   {id:'read_file',label:'Read workspace text',description:'At most 32 KiB; private paths and symbolic links blocked',picked:p.tools.includes('read_file')},
   {id:'propose_edit',label:'Prepare an edit diff',description:'Read first; unique replacement; human must Apply and Save',picked:p.tools.includes('propose_edit')}
  ],{canPickMany:true,title:'Tools allowed without the initial request prompt'});
  if(!picked)return;tools=picked.map(x=>x.id);
 }
 const next=normalize({mode:selected.id,tools});
 if(next.mode!=='ask'){
  const result=await vscode.window.showWarningMessage('Use '+selected.label+' in this chat? Approved workspace content can be sent to this chat’s selected model. Existing path, size, mode and Stop limits remain. In Autonomous workspace edits, bounded edits apply without another prompt and remain unsaved for review. Other presets still require Apply. MCP and app access still ask separately. This does not grant unrestricted filesystem, shell, root, credential or policy access.',{modal:true},'Use workspace policy');
  if(result!=='Use workspace policy')return;
 }
 return next;
}
module.exports={policies,normalize,automatic,choose};
