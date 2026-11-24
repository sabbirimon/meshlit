const test=require('node:test'),assert=require('node:assert/strict'),http=require('node:http'),vm=require('node:vm'),fs=require('node:fs/promises'),path=require('node:path'),os=require('node:os');
test('late Artemis status cannot resurrect a task after acknowledged Stop',async()=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'meshlit-services-test-')),secrets=new Map(),messages=[];let receive,releaseStatus,statusRequested;
 const waiting=new Promise(resolve=>statusRequested=resolve),host=http.createServer(async(req,res)=>{
  let raw='';for await(const part of req)raw+=part;res.setHeader('Content-Type','application/json');
  if(req.url==='/api/run'){const body=JSON.parse(raw);res.end(JSON.stringify({status:'accepted',tasks:[{session_id:body.session_id,device_serial:body.device_serial}]}));}
  else if(req.url==='/api/stop')res.end(JSON.stringify({status:'stopped'}));
  else if(req.url.startsWith('/api/sessions/')){statusRequested();releaseStatus=()=>res.end(JSON.stringify({status:'running'}));}
  else {res.writeHead(404);res.end('{}');}
 });
 await new Promise(resolve=>host.listen(0,'127.0.0.1',resolve));const context={subscriptions:[],secrets:{get:async k=>secrets.get(k),store:async(k,v)=>secrets.set(k,v),delete:async k=>secrets.delete(k)}};
 const vscode={workspace:{isTrusted:true,workspaceFolders:[{uri:{scheme:'file',fsPath:root}}]},ViewColumn:{Active:1},window:{showWarningMessage:async()=> 'Run reviewed task',showErrorMessage:m=>{throw Error(m);},createWebviewPanel:()=>({reveal(){},onDidDispose(){},webview:{postMessage:m=>messages.push(m),onDidReceiveMessage:fn=>receive=fn}})},commands:{registerCommand:(name,fn)=>({dispose(){}})}};
 const mod={exports:{}};vm.runInNewContext(await fs.readFile(path.join(__dirname,'connected-services.cjs'),'utf8'),{module:mod,AbortController,require:n=>n==='vscode'?vscode:require(n.startsWith('.')?path.join(__dirname,n):n)});
 try {
  await mod.exports.register(context).open();
  await receive({op:'save',kind:'artemis',config:{address:'http://127.0.0.1:'+host.address().port,token:'',enabled:true,localRecall:false,remoteRecall:false,agents:false}});
  await receive({op:'run',kind:'artemis',task:{goal:'Inspect fixture app',serial:'fixture',appPackage:'com.meshlit'}});
  await waiting;
  const id=messages.filter(m=>m.type==='services').at(-1).jobs[0].id;
  await receive({op:'stop',kind:'artemis',id});releaseStatus();
  await new Promise(resolve=>setTimeout(resolve,30));
  await receive({op:'refresh'});
  assert.equal(messages.filter(m=>m.type==='services').at(-1).jobs[0].phase,'STOPPED');
 }finally{for(const item of context.subscriptions)item.dispose?.();host.closeAllConnections();await new Promise(resolve=>host.close(resolve));await fs.rm(root,{recursive:true,force:true});}
});
