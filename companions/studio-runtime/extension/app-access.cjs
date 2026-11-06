const vscode=require('vscode'),fs=require('node:fs'),path=require('node:path'),{spawn}=require('node:child_process'),readline=require('node:readline');
const {AppAccessPolicy,TASK_OPERATIONS}=require('./app-access-policy.cjs');
const policy=new AppAccessPolicy();let child,sequence=0;const pending=new Map();let helper;const appNames=new Map();
function stop(){policy.stop();appNames.clear();if(child){child.kill();child=undefined;}for(const p of pending.values())p.reject(Error('App access stopped'));pending.clear();}
function call(args){return new Promise((resolve,reject)=>{
 if(pending.size>=8)return reject(Error('Too many pending app-access requests'));
 if(process.platform!=='darwin'||!helper||!fs.existsSync(helper))return reject(Error('Native Mac App Access helper is unavailable in this build'));
 if(!child){
  child=spawn(helper,[],{shell:false,stdio:['pipe','pipe','ignore'],env:Object.fromEntries(['HOME','PATH','TMPDIR','LANG'].filter(k=>process.env[k]).map(k=>[k,process.env[k]]))});
  const owned=child;
  owned.stdin.on('error',()=>{if(child===owned)stop();});
  readline.createInterface({input:owned.stdout}).on('line',line=>{try{const e=JSON.parse(line),p=pending.get(e.id);if(p){pending.delete(e.id);e.error?p.reject(Error(e.error)):p.resolve(e.result);}}catch{}});
  owned.on('error',()=>{if(child===owned)stop();});owned.on('exit',()=>{if(child===owned)stop();});
 }
 const id=++sequence;const timer=setTimeout(()=>{pending.delete(id);reject(Error('App access timed out'));stop();},12000);
 pending.set(id,{resolve:v=>{clearTimeout(timer);resolve(v);},reject:e=>{clearTimeout(timer);reject(e);}});
 child.stdin.write(JSON.stringify({...args,id})+'\n');
});}
function endTask(owner){policy.endTask(owner);}
async function execute(args,active,binding){
 const generation=policy.validate(args),scope=policy.taskScope(binding);
 const current=()=>{if(!active()||generation!==policy.validate(args))throw Error('App access denied or revoked');if(scope)policy.validateTask(scope);};
 current();
 const status=await call({action:'status'});if(!status.trusted)throw Error('Enable Meshlit App Access in macOS Accessibility settings first');
 current();
 if(scope&&policy.consumeTask(args,scope))return JSON.stringify(await call(args));
 const target=appNames.get(args.bundle_id)||args.bundle_id;
 const summary=`${args.action} in ${target} (${args.bundle_id})`+(args.action==='set_value'?`\nText to enter:\n${args.text}`:'');
 const task=scope?`\n\nSelected model: ${scope.model}\nHuman task: ${scope.task.slice(0,1200)}${scope.task.length>1200?'…':''}\n\nAllow this task permits up to ${TASK_OPERATIONS} snapshots and actions in this app for 10 minutes, ending when this task stops or finishes. Other apps require their own approval.`:'';
 const choice=await vscode.window.showWarningMessage(`Allow app access?\n${summary}${task}\n\nVisible app content will be returned to this chat’s selected model. Credentials and protected system surfaces stay human-controlled.`,{modal:true},'Allow once',...(scope?['Allow this task']:[]));
 current();
 if(choice==='Allow this task'&&scope){policy.grantTask(args,scope);policy.consumeTask(args,scope);}
 else if(choice!=='Allow once')throw Error('App access denied or revoked');
 return JSON.stringify(await call(args));
}
function register(context){
 helper=path.join(context.extensionPath,'native','Meshlit App Access.app','Contents','MacOS','Meshlit App Access');
 context.subscriptions.push({dispose:stop},vscode.commands.registerCommand('meshlit.stopAppAccess',()=>{stop();vscode.window.showInformationMessage('App access stopped. Existing OS permission remains under your control.');}),
 vscode.commands.registerCommand('meshlit.appAccess',async()=>{try{
  const status=await call({action:'status'});
  const choice=await vscode.window.showQuickPick([
   {id:'scope',label:'Choose apps for this Studio session',detail:policy.apps.size+' selected · allow once or approve a bounded task'},
   {id:'permission',label:'Open macOS permission setup',detail:status.trusted?'Accessibility permission is enabled':'Accessibility permission is missing'},
   {id:'stop',label:'Stop all app access'}
  ],{title:'Agent app access · '+(status.trusted?'OS permission enabled':'OS permission required')});
  if(choice?.id==='stop'){stop();return;}
  if(choice?.id==='permission'){await call({action:'request_permission'});await vscode.env.openExternal(vscode.Uri.parse('x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility'));return;}
  if(choice?.id==='scope'){
   const result=await call({action:'apps'});
   const selected=await vscode.window.showQuickPick(result.apps.map(a=>({label:a.name,description:a.id,id:a.id,picked:policy.apps.has(a.id)})),{canPickMany:true,title:'Apps agents may request access to · select at most ten'});
   if(!selected)return;
   const ok=await vscode.window.showWarningMessage('Allow agents to request access to these apps for this Studio session? The first action asks for Allow once or an optional task grant of up to 25 operations for 10 minutes in one app. App content can be sent to the selected model. Credentials and protected system surfaces stay human-controlled.',{modal:true},'Enable selected apps');
   if(ok==='Enable selected apps'){policy.allow(selected.map(a=>a.id));appNames.clear();for(const app of selected)appNames.set(app.id,app.label);await call({action:'stop'});}
  }
 }catch(error){vscode.window.showErrorMessage(error.message);}}));
}
module.exports={register,execute,stop,endTask};
