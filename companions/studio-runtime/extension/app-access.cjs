const vscode=require('vscode'),fs=require('node:fs'),path=require('node:path'),{spawn}=require('node:child_process'),readline=require('node:readline');
const {AppAccessPolicy}=require('./app-access-policy.cjs');
const policy=new AppAccessPolicy();let child,sequence=0;const pending=new Map();let helper;
function stop(){policy.stop();if(child){child.kill();child=undefined;}for(const p of pending.values())p.reject(Error('App access stopped'));pending.clear();}
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
async function execute(args,active){
 const generation=policy.validate(args);
 const status=await call({action:'status'});if(!status.trusted)throw Error('Enable Meshlit App Access in macOS Accessibility settings first');
 const summary=`${args.action} in ${args.bundle_id}`+(args.action==='set_value'?`\nText to enter:\n${args.text}`:'');
 const choice=await vscode.window.showWarningMessage(`Allow app access?\n${summary}\n\nVisible app content will be returned to this chat’s selected model. Check the target and model before allowing.`,{modal:true},'Allow once');
 if(choice!=='Allow once'||!active()||generation!==policy.validate(args))throw Error('App access denied or revoked');
 return JSON.stringify(await call(args));
}
function register(context){
 helper=path.join(context.extensionPath,'native','Meshlit App Access.app','Contents','MacOS','Meshlit App Access');
 context.subscriptions.push({dispose:stop},vscode.commands.registerCommand('meshlit.stopAppAccess',()=>{stop();vscode.window.showInformationMessage('App access stopped. Existing OS permission remains under your control.');}),
 vscode.commands.registerCommand('meshlit.appAccess',async()=>{try{
  const status=await call({action:'status'});
  const choice=await vscode.window.showQuickPick([
   {id:'scope',label:'Choose apps for this Studio session',detail:policy.apps.size+' selected · each action needs approval'},
   {id:'permission',label:'Open macOS permission setup',detail:status.trusted?'Accessibility permission is enabled':'Accessibility permission is missing'},
   {id:'stop',label:'Stop all app access'}
  ],{title:'Agent app access · '+(status.trusted?'OS permission enabled':'OS permission required')});
  if(choice?.id==='stop'){stop();return;}
  if(choice?.id==='permission'){await call({action:'request_permission'});await vscode.env.openExternal(vscode.Uri.parse('x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility'));return;}
  if(choice?.id==='scope'){
   const result=await call({action:'apps'});
   const selected=await vscode.window.showQuickPick(result.apps.map(a=>({label:a.name,description:a.id,id:a.id,picked:policy.apps.has(a.id)})),{canPickMany:true,title:'Apps agents may request access to · select at most ten'});
   if(!selected)return;
   const ok=await vscode.window.showWarningMessage('Allow agents to request access to these apps for this Studio session? Every snapshot and action still requires Allow once. App content can be sent to the selected model. Credentials and protected system surfaces stay human-controlled.',{modal:true},'Enable selected apps');
   if(ok==='Enable selected apps'){policy.allow(selected.map(a=>a.id));await call({action:'stop'});}
  }
 }catch(error){vscode.window.showErrorMessage(error.message);}}));
}
module.exports={register,execute,stop};
