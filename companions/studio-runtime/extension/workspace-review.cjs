const path=require('node:path');
const fs=require('node:fs/promises');
const crypto=require('node:crypto');
const tools=require('./workspace-tools.cjs');
const {archive}=require('./workspace-review-archive.cjs');
const MAX_FILES=128, MAX_TURN_FILES=48, MAX_TEXT=1024*1024;

function fileReferences(text) {
 const seen=new Set(), result=[];
 for(const match of String(text).slice(0,96000).matchAll(/\[([^\]\n]{1,120})\]\((<[^>\n]{1,1024}>|[^)\s]{1,1024})\)/g)) {
  const target=match[2].replace(/^<|>$/g,'');
  const withoutLine=target.replace(/(?::|#L)\d+$/,'');
  if(/^[a-z][a-z0-9+.-]*:/i.test(withoutLine)&&! /^[a-z]:[\\/]/i.test(withoutLine))continue;
  if(!/(?:\.[a-z0-9_+-]{1,18}|\b(?:Dockerfile|Makefile|README|LICENSE))(?::\d+|#L\d+)?$/i.test(target)||seen.has(target))continue;
  seen.add(target);result.push({label:match[1],reference:target});if(result.length===16)break;
 }
 return result;
}

function relativeTo(root,file) {
 const relative=path.relative(root,file);
 if(!relative || relative==='..' || relative.startsWith('..'+path.sep) || path.isAbsolute(relative))throw Error('Choose a file in this workspace');
 return relative;
}

function fileReference(root,reference) {
 if(typeof reference!=='string'||reference.length>1024||/[\u0000-\u001f\u007f]/.test(reference))throw Error('Invalid file reference');
 const match=reference.match(/(?::|#L)([1-9][0-9]{0,6})$/);
 const filename=match?reference.slice(0,match.index):reference;
 if(/^[a-z][a-z0-9+.-]*:/i.test(filename)&&!path.isAbsolute(filename))throw Error('Only workspace file references are supported');
 if(filename.split(/[\\/]/).includes('..'))throw Error('Use a path within the workspace');
 return {relative:relativeTo(root,path.resolve(root,filename)),line:match?Number(match[1]):undefined};
}

class TurnChanges {
 constructor(){this.turns=new Map();}
 begin(id,root){this.turns.set(id,{root,files:new Map(),limited:false});}
 remove(id){this.turns.delete(id);}
 record(id,file,before,after){
  const turn=this.turns.get(id);if(!turn||!turn.root)return;
  let relative;try{relative=relativeTo(turn.root,file);}catch{return;}
  if(!turn.files.has(file)&&turn.files.size>=MAX_TURN_FILES){turn.limited=true;return;}
  const original=turn.files.get(file)?.before??before;
  if(original===after){turn.files.delete(file);return;}
  turn.files.set(file,{file,relative,before:original,after,kind:'turn',session:id});
 }
 get(id){const turn=this.turns.get(id);return {rows:[...turn?.files.values()||[]],limited:turn?.limited===true};}
}

function gitRows(repositories,folderFor) {
 const rows=new Map();let limited=false;
 for(const repository of repositories) {
  for(const group of [repository.state.indexChanges,repository.state.workingTreeChanges,repository.state.untrackedChanges,repository.state.mergeChanges]) {
   for(const change of group||[]) {
    const uri=change.renameUri||change.uri, folder=uri&&folderFor(uri);
    if(!folder||uri.scheme!=='file')continue;
    let relative;try{relative=relativeTo(folder.uri.fsPath,uri.fsPath);}catch{continue;}
    if(rows.size>=MAX_FILES&&!rows.has(uri.fsPath)){limited=true;continue;}
    const previous=rows.get(uri.fsPath), status=change.status;
    // Built-in Git API Status codes: index add=1, delete=2, rename=3, copy=4;
    // worktree delete=6, untracked=7, intent-to-add=9, conflict=12..18.
    const original=previous?.original||change.originalUri||uri;
    const added=!repository.state.HEAD?.commit||previous?.added===true||(!previous&&[1,7,9].includes(status));
    const deleted=[2,6,17].includes(status);
    if(added&&deleted){rows.delete(uri.fsPath);continue;}
    const label=status>=12?'Conflict':deleted?'Deleted':added?'Added':status===3?'Renamed':status===4?'Copied':previous?.label==='Renamed'?'Renamed':'Modified';
    rows.set(uri.fsPath,{uri,original,relative,file:uri.fsPath,root:folder.uri.fsPath,added,deleted,label,kind:'git'});
   }
  }
 }
 return {rows:[...rows.values()].sort((a,b)=>a.relative.localeCompare(b.relative)),limited};
}

function create(context,vscode,chatFor,{persistent=true}={}) {
 const turns=new TurnChanges(), virtual=new Map(), changes=new Map();
 const savedReview=archive(context);
 let panel, session, range='turn', refreshRevision=0, git;
 const trusted=()=>{if(!vscode.workspace.isTrusted)throw Error('Trust the workspace before reviewing files');};
 const notify=error=>vscode.window.showWarningMessage(error instanceof Error?error.message:'Workspace action failed.');
 const safe=fn=>(...args)=>Promise.resolve().then(()=>fn(...args)).catch(notify);
 const getGit=async()=>{
  const extension=vscode.extensions.getExtension('vscode.git');
  if(!extension)throw Error('Git integration is unavailable. Open a local Git workspace to review its changes.');
  const api=await extension.activate();
  return api.getAPI(1);
 };
 const folderFor=uri=>vscode.workspace.getWorkspaceFolder(uri);
 function putVirtual(filename,content) {
  const uri=vscode.Uri.from({scheme:'meshlit-review',path:`/${crypto.randomUUID()}/${path.basename(filename)}`});
  virtual.set(uri.toString(),content);
  while(virtual.size>MAX_TURN_FILES*4)virtual.delete(virtual.keys().next().value);
  return uri;
 }
 const openReview=async row=>{
  trusted();const uri=row.uri||vscode.Uri.file(row.file), folder=folderFor(uri);
  if(!folder)throw Error('This file is no longer in the open workspace');
  relativeTo(folder.uri.fsPath,row.file);
  let before,after,label;
  if(row.kind==='turn'||row.kind==='saved'){
   before=putVirtual(row.file,row.before);after=putVirtual(row.file,row.after);
   label=`${row.relative} · ${row.kind==='turn'?'Last turn':'Saved review'}`;
  }else{
   const api=git||await getGit();
   if(!row.added)relativeTo(folder.uri.fsPath,row.original.fsPath);
   if(!row.deleted)relativeTo(await fs.realpath(folder.uri.fsPath),await fs.realpath(uri.fsPath));
   before=row.added?putVirtual(row.file,''):api.toGitUri(row.original,'HEAD');
   after=row.deleted?putVirtual(row.file,''):uri;label=`${row.relative} · Working tree`;
  }
  await vscode.commands.executeCommand('vscode.diff',before,after,label,{viewColumn:panel?.viewColumn||vscode.ViewColumn.Beside});
 };
 async function loadSavedReview() {
  const saved=await savedReview.load();
  if(!saved)return {rows:[],limited:false};
  const folder=vscode.workspace.workspaceFolders?.find(folder=>folder.uri.scheme==='file' && folder.uri.fsPath===saved.root);
  if(!folder)throw Error('Open the workspace folder where this review was saved.');
  return {rows:saved.rows.map(row=>{
   const file=path.resolve(saved.root,row.relative);
   if(relativeTo(saved.root,file)!==row.relative)throw Error('Saved review refers to an invalid workspace path.');
   return {...row,file,kind:'saved',label:'Saved edit'};
  }),limited:false};
 }
 async function refresh(){
  if(!panel)return;
  const target=panel, serial=++refreshRevision, selectedRange=range;
  try {
   trusted();
   const result=selectedRange==='turn'?turns.get(session):selectedRange==='saved'?await loadSavedReview():
    gitRows((git||=await getGit()).repositories,folderFor);
   if(selectedRange==='git'&&!git.repositories.length)throw Error('Open a local Git repository to review its working tree.');
   if(target!==panel||serial!==refreshRevision)return;
   changes.clear();
   const rows=result.rows.map(row=>{const id=crypto.randomUUID();changes.set(id,row);return {id,path:row.relative,label:row.label||'Applied edit',canUndo:row.kind==='turn'};});
   target.webview.postMessage({type:'changes',range:selectedRange,rows,limited:result.limited,
    empty:selectedRange==='turn'?'No applied file changes in this chat’s latest turn.':
     selectedRange==='saved'?'No saved review available from the past seven days.':'No Git changes in the open workspace.'});
  } catch(error){if(target===panel&&serial===refreshRevision){changes.clear();target.webview.postMessage({type:'changes',range:selectedRange,rows:[],empty:error.message});}}
 }
 async function show(id){
  trusted();
  if(id&&chatFor(id)){session=id;range='turn';}else if(!session||!chatFor(session)){session=undefined;range='git';}
  if(!panel){
   const next=require('./shell.cjs').createPanel(vscode,'meshlit.workspaceChanges','Workspace changes',vscode.ViewColumn.Beside,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});
   panel=next;context.subscriptions.push(next);
   next.webview.html=require('./workspace-review-view.cjs').render(persistent);
   next.webview.onDidReceiveMessage(safe(async message=>{
    if(next!==panel)return;
    if(message?.op==='range'&&['turn','git',...(persistent?['saved']:[])].includes(message.range)){range=message.range;await refresh();}
    else if(['ready','refresh'].includes(message?.op))await refresh();
    else if(message?.op==='open'&&typeof message.id==='string'){const row=changes.get(message.id);if(row)await openReview(row);}
    else if(message?.op==='saveReview'&&persistent&&range==='turn'){
     trusted();
     const root=chatFor(session)?.root;
     const result=turns.get(session);
     if(!root||!result.rows.length||result.limited)throw Error('Open a complete last-turn review before saving.');
     await savedReview.save(root,result.rows);
     range='saved';await refresh();
    }
    else if(message?.op==='forgetReview'&&persistent&&range==='saved'){
     trusted();await savedReview.forget();await refresh();
    }
   }));
   next.onDidDispose(()=>{if(panel===next){panel=undefined;changes.clear();++refreshRevision;}});
  }else{panel.reveal();await refresh();}
 }
 async function target(arg){
  trusted();
  if(typeof arg?.meshlitReviewId==='string') {
   const row=changes.get(arg.meshlitReviewId);if(!row)throw Error('Refresh the changes list first');
   return {uri:row.uri||vscode.Uri.file(row.file),row};
  }
  if(typeof arg?.meshlitSession==='string') {
   const chat=chatFor(arg.meshlitSession), root=chat?.root||chat?.memoryRoot;
   if(!root)throw Error('Open a local workspace to use file links');
   const {relative,line}=fileReference(root,arg.meshlitReference);
   return {uri:vscode.Uri.file(await tools.scoped(root,relative)),line,chat:arg.meshlitSession};
  }
  const uri=arg?.scheme==='file'?arg:vscode.window.activeTextEditor?.document.uri;
  if(!uri||uri.scheme!=='file'||!folderFor(uri))throw Error('Select a file in the open workspace');
  return {uri};
 }
 async function fileAction(action,arg){
  const {uri,line,row,chat}=await target(arg), folder=folderFor(uri);
  if(!folder)throw Error('File is outside the workspace');
  if(action==='review'){
   if(row)return openReview(row);
   const tracked=turns.get(chat||session).rows.find(item=>item.file===uri.fsPath);
   if(tracked)return openReview(tracked);
   const result=gitRows((git||=await getGit()).repositories,folderFor).rows.find(item=>item.file===uri.fsPath);
   if(result)return openReview(result);
   return vscode.window.showInformationMessage('No changes to this file.');
  }
  if(action==='undo'){
   if(row?.kind!=='turn' || !turns.get(row.session).rows.includes(row))throw Error('Undo is available for a current last-turn edit.');
   const scoped=await tools.scoped(folder.uri.fsPath,row.relative);
   if(scoped!==uri.fsPath)throw Error('The file changed location. Refresh the review.');
   const document=await vscode.workspace.openTextDocument(uri);
   const version=document.version;
   if(document.getText()!==row.after)throw Error('The file has newer edits. Review the changes before undoing manually.');
   const before=putVirtual(row.file,row.after), after=putVirtual(row.file,row.before);
   await vscode.commands.executeCommand('vscode.diff',before,after,`Undo proposal · ${row.relative}`);
   const choice=await vscode.window.showWarningMessage(`Undo the last Meshlit edit to ${row.relative}?`,{modal:true},'Undo edit');
   if(choice!=='Undo edit')return;
   trusted();
   if(await tools.scoped(folder.uri.fsPath,row.relative)!==scoped || document.version!==version ||
      document.getText()!==row.after || !turns.get(row.session).rows.includes(row))throw Error('The file or turn changed during undo review.');
   const edit=new vscode.WorkspaceEdit();
   edit.replace(uri,new vscode.Range(document.positionAt(0),document.positionAt(row.after.length)),row.before);
   if(!await vscode.workspace.applyEdit(edit))throw Error('The editor refused the undo.');
   turns.record(row.session,row.file,row.after,row.before);
   if(panel)await refresh();
   return;
  }
  if(action==='copyPath')return vscode.env.clipboard.writeText(uri.fsPath);
  const real=await fs.realpath(uri.fsPath), base=await fs.realpath(folder.uri.fsPath);
  relativeTo(base,real); // Existing-file actions may not follow a link out of the workspace.
  if(!(await fs.stat(real)).isFile())throw Error('Select a workspace file');
  if(action==='external')return vscode.env.openExternal(uri);
  if(action==='reveal')return vscode.commands.executeCommand('revealFileInOS',uri);
  if(action==='copyText'&&(await fs.stat(real)).size>MAX_TEXT)throw Error('Copy file contents supports text up to 1 MiB');
  const document=await vscode.workspace.openTextDocument(uri);
  if(action==='copyText'){
   const text=document.getText();if(Buffer.byteLength(text)>MAX_TEXT||text.includes('\0'))throw Error('Copy file contents supports text up to 1 MiB');
   return vscode.env.clipboard.writeText(text);
  }
  const selection=line?new vscode.Range(Math.min(line-1,document.lineCount-1),0,Math.min(line-1,document.lineCount-1),0):undefined;
  await vscode.window.showTextDocument(document,{viewColumn:vscode.ViewColumn.Beside,selection});
  if(action==='saveAs')await vscode.commands.executeCommand('workbench.action.files.saveAs');
 }
 context.subscriptions.push(vscode.workspace.registerTextDocumentContentProvider('meshlit-review',{provideTextDocumentContent:uri=>virtual.get(uri.toString())??''}));
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.workspaceChanges',safe(show)));
 for(const action of ['open','review','undo','external','saveAs','copyPath','copyText','reveal'])
  context.subscriptions.push(vscode.commands.registerCommand(`meshlit.file.${action}`,safe(arg=>fileAction(action,arg))));
 return {show:safe(show), openFile:safe((id,reference)=>fileAction('open',{meshlitSession:id,meshlitReference:reference})),
  begin:(id,root)=>{turns.begin(id,root);if(panel&&range==='turn'&&session===id)void refresh();},
  record:(id,file,before,after)=>{turns.record(id,file,before,after);if(panel&&range==='turn'&&session===id)void refresh();},
  remove:id=>{turns.remove(id);if(session===id){session=undefined;if(panel)void refresh();}}};
}
module.exports={create,TurnChanges,fileReferences,fileReference,gitRows};
