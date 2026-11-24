const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path'),vm=require('node:vm');
const {TurnChanges,fileReference,fileReferences,gitRows}=require('./workspace-review.cjs');
const {render}=require('./workspace-review-view.cjs');

const root=path.resolve('project'),uri=fsPath=>({scheme:'file',fsPath});
const file=name=>uri(path.join(root,name));

test('local Markdown references preserve spaces and line targets without accepting URI schemes or traversal',()=>{
 const references=fileReferences('Read [guide](<docs/My Guide.md:12>), [code](main.ts#L3), [site](https://example.test/main.ts), [action](command:secret.ts).');
 assert.deepEqual(references,[{label:'guide',reference:'docs/My Guide.md:12'},{label:'code',reference:'main.ts#L3'}]);
 assert.deepEqual(fileReference(root,references[0].reference),{relative:path.join('docs','My Guide.md'),line:12});
 assert.equal(fileReference(root,references[1].reference).line,3);
 for(const target of ['../secret.md','docs/../secret.md',path.resolve('external.md'),'file:/secret.md','a\0.md'])
  assert.throws(()=>fileReference(root,target));
});

test('latest turn keeps the original text across applied edits and removes reverted changes',()=>{
 const changes=new TurnChanges(),main=file('main.ts').fsPath,other=file('other.ts').fsPath;
 changes.begin('chat',root);
 changes.record('chat',main,'original','first');
 changes.record('chat',main,'first','latest');
 changes.record('chat',other,'a','b');
 changes.record('chat',other,'b','a');
 changes.record('chat',path.resolve('outside.txt'),'no','scope');
 assert.deepEqual(changes.get('chat'),{rows:[{file:main,relative:'main.ts',before:'original',after:'latest',kind:'turn',session:'chat'}],limited:false});
 changes.begin('other',root);
 assert.equal(changes.get('other').rows.length,0);
 changes.begin('chat',root);
 assert.equal(changes.get('chat').rows.length,0);
});

test('working tree rows compare to HEAD across staging, renames, deletes and untracked additions',()=>{
 const modification=file('edit.ts'), renamed=file('new.ts'), original=file('old.ts'), deleted=file('gone.ts'), fresh=file('new.txt'), recreated=file('recreated.ts'), outside=uri(path.resolve('external.ts'));
 const change=(uri,status)=>({uri,originalUri:uri,status});
 const state={HEAD:{commit:'HEAD'},indexChanges:[
  change(modification,0),{uri:original,renameUri:renamed,originalUri:original,status:3},change(recreated,2)
 ],workingTreeChanges:[change(modification,5),change(renamed,5),change(deleted,6),change(outside,5)],untrackedChanges:[change(fresh,7),change(recreated,7)],mergeChanges:[]};
 const rows=gitRows([{state}],u=>u!==outside?{uri:uri(root)}:undefined).rows;
 const byName=Object.fromEntries(rows.map(row=>[row.relative,row]));
 assert.deepEqual(Object.keys(byName).sort(),['edit.ts','gone.ts','new.ts','new.txt','recreated.ts']);
 assert.equal(byName['edit.ts'].label,'Modified');
 assert.equal(byName['new.ts'].label,'Renamed');assert.equal(byName['new.ts'].original,original);
 assert.equal(byName['gone.ts'].deleted,true);assert.equal(byName['new.txt'].added,true);
 assert.equal(byName['recreated.ts'].added,false);assert.equal(byName['recreated.ts'].deleted,false);
 assert.equal(gitRows([{state:{indexChanges:[change(fresh,1)]}}],()=>({uri:uri(root)})).rows[0].added,true);
});

test('changes view filters real rows and sends only the opaque selected row id',()=>{
 const els=new Map(),sent=[],listeners={};
 const element=()=>({children:[],dataset:{},value:'',append(...values){this.children.push(...values);},replaceChildren(){this.children=[];}});
 const get=id=>{if(!els.has(id))els.set(id,element());return els.get(id);};
 const script=render().match(/<script nonce="[^"]+">([\s\S]*?)<\/script>/)[1];
 vm.runInNewContext(script,{acquireVsCodeApi:()=>({postMessage:m=>sent.push(m)}),document:{getElementById:get,createElement:element},window:{addEventListener:(name,listener)=>listeners[name]=listener}});
 listeners.message({data:{type:'changes',range:'git',rows:[{id:'first',path:'src/app.js',label:'Modified'},{id:'second',path:'Guide.md',label:'Added'}]}});
 get('filter').value='guide';get('filter').oninput();
 assert.equal(get('info').textContent.startsWith('1 of 2 files'),true);
 const row=get('rows').children[0];
 assert.equal(row.children[0].textContent,'Guide.md');
 assert.equal(JSON.parse(row.dataset.vscodeContext).meshlitReviewId,'second');
 row.onclick();assert.equal(sent.at(-1).op,'open');assert.equal(sent.at(-1).id,'second');
 get('range').value='turn';get('range').onchange();assert.equal(sent.at(-1).range,'turn');
});

test('reviewed undo preserves newer editor changes and only reverses its exact last-turn snapshot', async()=>{
 const fs=require('node:fs/promises'),os=require('node:os');
 const {create}=require('./workspace-review.cjs');
 const directory=await fs.realpath(await fs.mkdtemp(path.join(os.tmpdir(),'meshlit-undo-')));
 try {
  const filename=path.join(directory,'review.txt');
  await fs.writeFile(filename,'original');
  const commands=new Map(),posted=[],warnings=[];
  let receive, applied=0, changedDuringReview=true;
  const document={version:1,text:'agent edit',getText(){return this.text;},positionAt(i){return i;}};
  const webview={postMessage(message){posted.push(message);},onDidReceiveMessage(handler){receive=handler;}};
  const folder={uri:{scheme:'file',fsPath:directory}};
  const vscode={
   ViewColumn:{Beside:2},
   Uri:{file(fsPath){return {scheme:'file',fsPath};},from({scheme,path}){return {toString:()=>scheme+path};}},
   Range:class{constructor(start,end){this.start=start;this.end=end;}},
   WorkspaceEdit:class{replace(_uri,_range,after){this.after=after;}},
   workspace:{isTrusted:true,getWorkspaceFolder:()=>folder,registerTextDocumentContentProvider(){},
    async openTextDocument(){return document;},
    async applyEdit(edit){applied++;document.text=edit.after;document.version++;return true;}},
   window:{createWebviewPanel(){return {webview,onDidDispose(){}};},
    async showWarningMessage(message,options){
     if(!options?.modal){warnings.push(message);return;}
     if(changedDuringReview){document.text='new work';document.version++;}
     return 'Undo edit';
    }},
   commands:{registerCommand(id,fn){commands.set(id,fn);},async executeCommand(){}},
  };
  const review=create({subscriptions:[]},vscode,()=>({root:directory}));
  review.begin('chat',directory);
  review.record('chat',filename,'original','agent edit');
  await review.show('chat');await receive({op:'ready'});
  const rowId=posted.at(-1).rows[0].id;
  await commands.get('meshlit.file.undo')({meshlitReviewId:rowId});
  assert.equal(applied,0);assert.equal(document.text,'new work');assert.match(warnings[0],/changed during undo/);
  document.text='agent edit';document.version++;changedDuringReview=false;
  await commands.get('meshlit.file.undo')({meshlitReviewId:rowId});
  assert.equal(applied,1);assert.equal(document.text,'original');assert.equal(posted.at(-1).rows.length,0);
 } finally {await fs.rm(directory,{recursive:true,force:true});}
});
