'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {validate,checkIdentity,availability,argumentsFor,checkConfiguration,render,register}=require('./ssh-dashboard.cjs');
const profile={id:'fixture',name:'Test server',host:'example.test',user:'alice',port:22,identity:''};
const fakeFs={statSync:()=>({isFile:()=>true}),lstatSync:()=>({isFile:()=>true,isSymbolicLink:()=>false,size:100}),accessSync(){}};
function fixture(t,overrides={}){
 const saved=new Map(),inputs=[],picks=[],files=[],answers=[],warnings=[],terminals=[],checks=[],posts=[],prompts=[],commands=new Map();let handler,panel,writeError=false;
 const context={subscriptions:[],globalState:{get:(key,fallback)=>saved.get(key)??fallback,update:async(key,value)=>{if(writeError)throw Error('Storage unavailable');saved.set(key,value);}}};
 const vscode={ViewColumn:{One:1},workspace:{isTrusted:true},commands:{registerCommand(name,run){commands.set(name,run);return {dispose(){}};}},window:{
  createWebviewPanel(){const closed=[];panel={webview:{html:'',postMessage:async message=>posts.push(message),onDidReceiveMessage(run){handler=run;return {dispose(){}};}},onDidDispose(run){closed.push(run);},reveal(){},dispose(){for(const run of closed)run();}};return panel;},
  showInputBox:async options=>{prompts.push(options);const next=inputs.shift();return typeof next==='function'?next(options):next;},
  showQuickPick:async()=>picks.shift(),showOpenDialog:async()=>files.shift(),
  showInformationMessage:async(...args)=>{prompts.push(args);const next=answers.shift();return typeof next==='function'?next():next;},
  showWarningMessage:async()=>warnings.shift(),
  createTerminal(options){const terminal={options,shown:false,show(){this.shown=true;}};terminals.push(terminal);return terminal;}
 }};
 register(context,vscode,{platform:'darwin',fileSystem:fakeFs,run:(...args)=>{checks.push(args);args.at(-1)(null,'ignored');},...overrides});
 t.after(()=>{for(const item of context.subscriptions.reverse())item.dispose?.();});
 return {saved,inputs,picks,files,answers,warnings,terminals,checks,posts,prompts,vscode,open:()=>commands.get('meshlit.sshDashboard')(),send:(op,id=profile.id)=>handler({sshPage:true,op,id}),raw:message=>handler(message),get panel(){return panel;},get profiles(){return saved.get('meshlit.sshProfiles')||[];},seed(value=[profile]){saved.set('meshlit.sshProfiles',value);},failSave(){writeError=true;}};
}
test('profile validation rejects options, command injection, secrets and invalid boundaries',()=>{
 for(const host of ['-oProxyCommand=evil','example;touch /tmp/no','`whoami`','a$(id)','ssh://example.test','alice@example.test','host\nother','a..b','a'.repeat(254)])assert.throws(()=>validate({...profile,host}));
 for(const user of ['-o','a b','a;id','a\n'])assert.throws(()=>validate({...profile,user}));
 for(const port of [0,65536,22.5,'22',NaN])assert.throws(()=>validate({...profile,port}));
 for(const identity of ['relative','/tmp/key\nfile'])assert.throws(()=>validate({...profile,identity}));
 for(const field of ['password','privateKey','command','proxyCommand'])assert.throws(()=>validate({...profile,[field]:'secret'}));
 assert.equal(validate({...profile,host:'[2001:db8::1]'}).host,'2001:db8::1');
});
test('SSH arguments remain separate, disable ambient config and forwarding, and ask before trusting new hosts',()=>{
 const identity='/Users/example/My keys/key $(id)';const args=argumentsFor({...profile,identity});
 assert.deepEqual(args.slice(0,2),['-F','/dev/null']);assert.equal(args[args.indexOf('-i')+1],identity);
 for(const option of ['StrictHostKeyChecking=ask','ForwardAgent=no','ClearAllForwardings=yes','PermitLocalCommand=no','ProxyCommand=none','ProxyJump=none','IdentityAgent=none','UpdateHostKeys=no','ControlPath=none'])assert.ok(args.includes(option));
 assert.deepEqual(args.slice(-5),['-p','22','-l','alice','example.test']);assert.ok(!args.includes('-G'));
});
test('identity checks never read secrets and reject missing, nonregular, symlink and oversized files',()=>{
 checkIdentity({...profile,identity:'/keys/id'},fakeFs);
 for(const stat of [{isFile:()=>false,size:100},{isFile:()=>true,isSymbolicLink:()=>true,size:100},{isFile:()=>true,size:1048577}])assert.throws(()=>checkIdentity({...profile,identity:'/keys/id'},{lstatSync:()=>stat}));
 assert.throws(()=>checkIdentity({...profile,identity:'/keys/id'},{lstatSync(){throw Error();}}));
});
test('local configuration check uses only -G with a timeout and never reports authentication success',async()=>{
 let called;const result=await checkConfiguration(profile,{platform:'darwin',fileSystem:fakeFs,run:(...args)=>{called=args;args.at(-1)(null,'username secret');}});
 assert.equal(called[0],'/usr/bin/ssh');assert.equal(called[1][0],'-G');assert.equal(called[2].shell,false);assert.equal(called[2].timeout,5000);assert.equal(called[2].env.SSH_AUTH_SOCK,undefined);
 assert.match(result,/No network contact, host-key handshake or authentication was attempted/);assert.doesNotMatch(result,/secret/);
 await assert.rejects(checkConfiguration(profile,{platform:'darwin',fileSystem:fakeFs,run:(...args)=>args.at(-1)(Error('private stderr'))}),/configuration check failed/);
});
test('page escapes profile text, disables unavailable runtime and keeps final status on idle acknowledgement',()=>{
 const html=render([{...profile,name:'<script>bad</script>',identity:'/keys/" onclick="bad'}],{available:false,reason:'Not installed'});
 assert.match(html,/&lt;script&gt;bad&lt;\/script&gt;/);assert.doesNotMatch(html,/<script>bad/);assert.match(html,/data-op="connect"[^>]*disabled/);assert.match(html,/data-op="check"[^>]*disabled/);assert.match(html,/if\(data.idleOnly\)return/);
 assert.equal(availability({platform:'win32',fileSystem:fakeFs}).available,false);
});
test('opening, refreshing and saving a profile never connects or runs a configuration check',async t=>{
 const f=fixture(t);f.open();await f.send('refresh');f.inputs.push('Server','example.test','alice','2222');f.picks.push({id:'keep'});await f.send('add');
 assert.equal(f.profiles.length,1);assert.equal(f.profiles[0].port,2222);assert.deepEqual(Object.keys(f.profiles[0]).sort(),['host','id','identity','name','port','user']);assert.equal(f.terminals.length,0);assert.equal(f.checks.length,0);assert.ok(f.posts.some(item=>/No connection started/.test(item.message)));
});
test('cancelled edits and failed persistence preserve the saved profile and never connect',async t=>{
 const f=fixture(t);f.seed();f.open();await f.send('edit');assert.deepEqual(f.profiles,[profile]);
 f.inputs.push('Changed','example.test','alice','22');f.picks.push({id:'keep'});f.failSave();await f.send('edit');assert.deepEqual(f.profiles,[profile]);assert.ok(f.posts.some(item=>item.error&&item.message==='Storage unavailable'));assert.equal(f.terminals.length,0);
});
test('key picker cancellation and page disposal cannot save an unfinished profile',async t=>{
 const f=fixture(t);f.open();f.inputs.push('Server','example.test','alice','22');f.picks.push({id:'choose'});await f.send('add');assert.equal(f.profiles.length,0);
 f.inputs.push(()=>{f.panel.dispose();return 'Name';});await f.send('add');assert.equal(f.profiles.length,0);assert.equal(f.terminals.length,0);
});
test('connection requires explicit review, then uses argv and terminal prompts without claiming login',async t=>{
 const f=fixture(t);f.seed();f.open();await f.send('connect');assert.equal(f.terminals.length,0);
 f.answers.push('Open SSH terminal');await f.send('connect');assert.equal(f.terminals.length,1);const terminal=f.terminals[0];assert.equal(terminal.shown,true);assert.equal(terminal.options.shellPath,'/usr/bin/ssh');assert.deepEqual(terminal.options.shellArgs,argumentsFor(profile));assert.equal(terminal.options.env.SSH_AUTH_SOCK,null);assert.equal(terminal.options.env.SSH_ASKPASS_REQUIRE,'never');assert.equal(terminal.options.isTransient,true);assert.ok(f.posts.some(item=>/login has not been verified/.test(item.message)));assert.equal(f.checks.length,0);
});
test('missing executable, untrusted workspace and a changed profile block terminal creation',async t=>{
 const unavailable=fixture(t,{fileSystem:{statSync(){throw Error();}}});unavailable.seed();unavailable.open();unavailable.answers.push('Open SSH terminal');await unavailable.send('connect');assert.equal(unavailable.terminals.length,0);assert.ok(unavailable.posts.some(item=>item.error&&/not found/.test(item.message)));
 const f=fixture(t);f.seed();f.open();f.vscode.workspace.isTrusted=false;await f.send('connect');assert.equal(f.terminals.length,0);
 f.vscode.workspace.isTrusted=true;f.answers.push(()=>{f.seed([{...profile,host:'changed.test'}]);return 'Open SSH terminal';});await f.send('connect');assert.equal(f.terminals.length,0);assert.ok(f.posts.some(item=>item.error&&/changed/.test(item.message)));
});
test('checks and removal are explicit; cancellation and unsupported operations do not mutate',async t=>{
 const f=fixture(t);f.seed();f.open();await f.raw({sshPage:true,op:'run',command:'id'});await f.raw({op:'remove',id:profile.id});assert.deepEqual(f.profiles,[profile]);
 await f.send('check');assert.equal(f.checks.length,1);assert.equal(f.terminals.length,0);await f.send('remove');assert.deepEqual(f.profiles,[profile]);f.warnings.push('Remove profile');await f.send('remove');assert.deepEqual(f.profiles,[]);
});

test('system Terminal handoff quotes every argument, uses private files, removes them before execution and sanitizes environment',async t=>{
 const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),{spawnSync}=require('node:child_process');
 const {openSystemTerminal}=require('./ssh-dashboard.cjs');const root=fs.mkdtempSync(path.join(os.tmpdir(),"meshlit-terminal-test-'"));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 const marker=path.join(root,'should-not-exist'),identity=path.join(root,"key ' $(touch should-not-exist) `id`");fs.writeFileSync(identity,'test fixture only');
 const fileSystem={...fs,statSync:file=>file==='/System/Applications/Utilities/Terminal.app'?{isDirectory:()=>true}:file==='/usr/bin/open'||file==='/usr/bin/ssh'?{isFile:()=>true}:fs.statSync(file),accessSync(file){if(file!=='/usr/bin/open'&&file!=='/usr/bin/ssh')fs.accessSync(file);}};
 let commandFile,script,launch,expiry;
 const result=await openSystemTerminal({...profile,identity},{fileSystem,platform:'darwin',tempRoot:root,environment:{HOME:root,USER:'test',SSH_AUTH_SOCK:'secret-agent',SECRET_TOKEN:'not-exported'},schedule:fn=>{expiry=fn;return {unref(){}};},run:(...args)=>{launch=args;commandFile=args[1][2];script=fs.readFileSync(commandFile,'utf8');assert.equal(fs.statSync(commandFile).mode&0o777,0o700);assert.equal(fs.statSync(path.dirname(commandFile)).mode&0o777,0o700);args.at(-1)(null);}});
 assert.equal(launch[0],'/usr/bin/open');assert.deepEqual(launch[1].slice(0,2),['-a','/System/Applications/Utilities/Terminal.app']);assert.equal(launch[2].shell,false);assert.equal(launch[2].timeout,5000);assert.match(result,/authentication has not been verified/);assert.doesNotMatch(script,/not-exported|secret-agent/);assert.match(script,/StrictHostKeyChecking=ask/);
 assert.equal(spawnSync('/bin/sh',['-n',commandFile]).status,0);
 // Replace only the fixed SSH executable with printf to exercise actual POSIX
 // quoting and cleanup without ever starting SSH or making a network request.
 fs.writeFileSync(commandFile,script.replace("'/usr/bin/ssh'", "'/usr/bin/printf' '%s\\n'"));
 const parsed=spawnSync('/bin/sh',[commandFile],{encoding:'utf8',cwd:root});assert.equal(parsed.status,0,parsed.stderr);assert.ok(parsed.stdout.split('\n').includes(identity));assert.equal(fs.existsSync(marker),false);assert.equal(fs.existsSync(commandFile),false);assert.equal(fs.existsSync(path.dirname(commandFile)),false);expiry();
});

test('system Terminal launch failures and unconsumed handoffs clean temporary files without remote connections',async t=>{
 const fs=require('node:fs'),os=require('node:os'),path=require('node:path');const {openSystemTerminal,systemTerminalAvailable}=require('./ssh-dashboard.cjs');
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'meshlit-terminal-fail-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 const fileSystem={...fs,statSync:file=>file.endsWith('Terminal.app')?{isDirectory:()=>true}:{isFile:()=>true},accessSync(){}};
 await assert.rejects(openSystemTerminal(profile,{fileSystem,platform:'darwin',tempRoot:root,run:(...args)=>args.at(-1)(Error('failed'))}),/could not be opened/);assert.deepEqual(fs.readdirSync(root),[]);
 let cleanup;await openSystemTerminal(profile,{fileSystem,platform:'darwin',tempRoot:root,run:(...args)=>args.at(-1)(null),schedule:fn=>{cleanup=fn;return {unref(){}};}});assert.equal(fs.readdirSync(root).length,1);cleanup();assert.deepEqual(fs.readdirSync(root),[]);
 assert.equal(systemTerminalAvailable({platform:'linux',fileSystem}),false);await assert.rejects(openSystemTerminal(profile,{platform:'linux',fileSystem}),/unavailable/);
});

test('system Terminal remains explicit, review cancellation creates no handoff, and unavailable platforms reject it',async t=>{
 const f=fixture(t,{fileSystem:{...fakeFs,statSync:file=>file.endsWith('Terminal.app')?{isDirectory:()=>true}:{isFile:()=>true}}});f.seed();f.open();assert.match(f.panel.webview.html,/System Terminal…/);assert.equal(f.checks.length,0);await f.send('connectSystem');assert.equal(f.checks.length,0);assert.equal(f.terminals.length,0);assert.ok(f.posts.some(item=>item.message==='Connection cancelled.'));
 const missing=fixture(t,{platform:'linux'});missing.seed();missing.open();await missing.send('connectSystem');assert.ok(missing.posts.some(item=>item.error&&/System Terminal is unavailable/.test(item.message)));assert.equal(missing.checks.length,0);
});
