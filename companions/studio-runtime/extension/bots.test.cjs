const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {render,renderDetails,validBot,MOVE_EDITOR}=require('./bots.cjs');
const bot={id:'saved-bot',name:'Research helper',instructions:'Explain the evidence plainly.',modelId:'local'};
function fixture(){
 const values=new Map(),commands=new Map(),inputs=[],picks=[],confirmations=[],opened=[],calls=[],info=[],errors=[],panels=[];let failSave=false,pickHook,failChat=false,failCommand=false,availableCommands=[MOVE_EDITOR];
 const context={subscriptions:[],globalState:{get:(key,fallback)=>values.has(key)?values.get(key):fallback,update:async(key,value)=>{if(failSave)throw Error('private storage detail');values.set(key,value);}}};
 const vscode={ViewColumn:{One:1},commands:{registerCommand:(id,fn)=>{commands.set(id,fn);return {dispose(){}};},getCommands:async()=>availableCommands,executeCommand:async id=>{calls.push(id);if(failCommand)throw Error('private runtime detail');}},window:{showInputBox:async()=>inputs.shift(),showQuickPick:async()=>{pickHook?.();return picks.shift();},showWarningMessage:async()=>confirmations.shift(),showInformationMessage:async message=>info.push(message),showErrorMessage:async message=>errors.push(message)}};
 const shell={createPanel:(_vscode,type,title)=>{const panel={type,title,messages:[],webview:{postMessage:async value=>panel.messages.push(value),onDidReceiveMessage:handler=>panel.receive=handler},reveal(){panel.revealed=true;},onDidDispose:handler=>panel.close=handler};panels.push(panel);return panel;}};
 const module={exports:{}};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'bots.cjs'),'utf8'),{module,URL,require:name=>name==='./shell.cjs'?shell:require(name)});
 module.exports.register(context,vscode,async value=>{if(failChat)throw Error('private runtime detail');opened.push(value);});
 return {vscode,values,commands,inputs,picks,confirmations,opened,calls,info,errors,panels,setFailure:value=>failSave=value,setCommands:value=>availableCommands=value,setChatFailure:value=>failChat=value,setCommandFailure:value=>failCommand=value,setPickHook:hook=>pickHook=hook,open:()=>{commands.get('meshlit.botDashboard')();return panels.at(-1);}};
}
test('dashboard escapes saved profile content and labels actual capabilities without fake bots',()=>{
 const html=render([{...bot,name:'<script>evil()</script>',instructions:'</pre><script>attack()</script>',modelId:'missing'}],[]);
 assert(!html.includes('<script>evil()'));assert(!html.includes('<script>attack()'));
 assert(html.includes('&lt;/pre&gt;&lt;script&gt;attack()&lt;/script&gt;'));
 assert(html.includes('Model connection missing'));assert(html.includes('data-op="start" data-id="saved-bot" disabled'));
 assert(html.includes('No workspace or device tools'));assert(html.includes('they do not train themselves'));
 assert(render([],[]).includes('No saved bots yet'));
 const nonce=html.match(/<script nonce="([^"]+)"/)[1];assert(html.includes("script-src 'nonce-"+nonce+"'"));
 assert(validBot(bot));assert(!validBot({...bot,instructions:'x'.repeat(2001)}));assert(!validBot({...bot,name:'bad\0name'}));
});
test('dashboard creates a bounded reviewed profile and starts only the saved profile',async()=>{
 const f=fixture(),panel=f.open();f.inputs.push('My helper','Give concise explanations.');f.picks.push({id:'local'});
 await panel.receive({botsPage:true,op:'create'});const saved=f.values.get('meshlit.bots')[0];
 assert.equal(saved.name,'My helper');assert.equal(saved.modelId,'local');assert.equal(f.info.length,1);assert.match(panel.webview.html,/My helper/);
 await panel.receive({botsPage:true,op:'start',id:saved.id,instructions:'Injected ignored text',command:'shell'});
 assert.equal(f.opened.length,1);assert.equal(f.opened[0].instructions,'Give concise explanations.');
 await panel.receive({botsPage:true,op:'workbench.action.terminal.new'});await panel.receive({botsPage:true,op:'constructor'});assert.equal(f.calls.length,0);
 await panel.receive({botsPage:true,op:'teams'});await panel.receive({botsPage:true,op:'companions'});assert.deepEqual(f.calls,['meshlit.agentTeams','meshlit.companions']);
});
test('missing or removed providers cannot start a bot and editing preserves its identifier',async()=>{
 const f=fixture();f.values.set('meshlit.bots',[{...bot,modelId:'remote'}]);f.values.set('meshlit.connections',[{id:'remote',service:'Owner server',model:'Qwen'}]);const panel=f.open();
 f.values.set('meshlit.connections',[]);await panel.receive({botsPage:true,op:'start',id:bot.id});assert.equal(f.opened.length,0);assert(panel.messages.at(-1).error);
 f.inputs.push('Renamed helper','New reviewed instructions.');f.picks.push({id:'local'});await panel.receive({botsPage:true,op:'edit',id:bot.id});
 const saved=f.values.get('meshlit.bots')[0];assert.equal(saved.id,bot.id);assert.equal(saved.name,'Renamed helper');assert.equal(saved.modelId,'local');
});
test('failed saves and unconfirmed removals preserve saved profiles without claiming success',async()=>{
 const f=fixture();f.values.set('meshlit.bots',[bot]);const panel=f.open();f.inputs.push('New name','New instructions.');f.picks.push({id:'local'});f.setFailure(true);
 await panel.receive({botsPage:true,op:'edit',id:bot.id});assert.equal(f.values.get('meshlit.bots')[0].name,bot.name);assert.equal(f.info.length,0);assert(panel.messages.at(-1).error);assert(!panel.messages.at(-1).error.includes('private storage'));
 f.setFailure(false);await panel.receive({botsPage:true,op:'remove',id:bot.id});assert.equal(f.values.get('meshlit.bots').length,1);
 f.confirmations.push('Remove');await panel.receive({botsPage:true,op:'remove',id:bot.id});assert.equal(f.values.get('meshlit.bots').length,0);
 panel.close();f.inputs.push('Ignored','Ignored');f.picks.push({id:'local'});await panel.receive({botsPage:true,op:'create'});assert.equal(f.values.get('meshlit.bots').length,0);
});
test('an edit cannot overwrite a concurrently changed profile',async()=>{
 const f=fixture();f.values.set('meshlit.bots',[bot]);const panel=f.open();f.inputs.push('Changed','Reviewed instructions.');f.picks.push({id:'local'});
 f.setPickHook(()=>f.values.set('meshlit.bots',[{...bot,instructions:'Another reviewed update.'}]));
 await panel.receive({botsPage:true,op:'edit',id:bot.id});assert.equal(f.values.get('meshlit.bots')[0].instructions,'Another reviewed update.');assert(panel.messages.at(-1).error);assert.equal(f.info.length,0);
});
test('legacy picker and manager alias remain compatible while the dashboard reuses its panel',async()=>{
 const f=fixture();f.values.set('meshlit.bots',[bot]);f.picks.push({bot},'Start bot chat');await f.commands.get('meshlit.bots')();assert.equal(f.opened.length,1);
 f.picks.push({action:'connection'});await f.commands.get('meshlit.manageBots')();assert.equal(f.calls[0],'meshlit.addModelSource');
 const panel=f.open();f.open();assert.equal(f.panels.length,1);assert(panel.revealed);
});

test('bot details escape content and show source truth without credential-bearing addresses',()=>{
 const html=renderDetails({...bot,name:'<img src=x>',instructions:'<script>secret()</script>',modelId:'remote'},[{id:'remote',model:'qwen',service:'Owner server',endpoint:'https://user:password@example.com/v1?token=secret'}]);
 assert(html.includes('&lt;img src=x&gt;'));assert(!html.includes('<script>secret()'));
 assert(!html.includes('user:password'));assert(!html.includes('token=secret'));assert(html.includes('Endpoint needs review'));
 assert(html.includes('Check a saved connection…'));assert(html.includes('Workspace files, terminal and edits'));assert(html.includes('Not attached'));assert(html.includes('Not implemented'));
 assert(renderDetails(undefined).includes('This bot is no longer saved'));
});
test('details command validates IDs/options, reuses profiles and exposes General Chat',async()=>{
 const f=fixture();f.values.set('meshlit.bots',[bot]);const command=f.commands.get('meshlit.openBotDetails');
 await command('missing');await command(bot.id,{separate:'yes'});await command(bot.id,{command:'terminal'});assert.equal(f.panels.length,0);assert.equal(f.errors.length,3);
 const page=await command(bot.id);assert.equal(page.type,'meshlit.botDetails');assert.match(page.webview.html,/Research helper/);
 assert.equal(await command(bot.id),page);assert.equal(f.panels.length,1);
 await page.receive({botDetailsPage:true,op:'general'});assert.deepEqual(f.calls,['meshlit.generalChat']);
 await page.receive({botDetailsPage:true,op:'constructor'});await page.receive({botDetailsPage:true,op:'terminal'});assert.equal(f.calls.length,1);
 await page.receive({botDetailsPage:true,op:'start',id:'injected',instructions:'injected'});assert.equal(f.opened[0].id,bot.id);assert.equal(f.opened[0].instructions,bot.instructions);
});
test('separate details require the real workbench command and report unavailable/runtime failures',async()=>{
 const f=fixture();f.values.set('meshlit.bots',[bot]);const dashboard=f.open();
 await dashboard.receive({botsPage:true,op:'detailsSeparate',id:bot.id});const page=f.panels.at(-1);assert.equal(page.type,'meshlit.botDetails');assert(page.revealed);assert.deepEqual(f.calls,[MOVE_EDITOR]);
 f.setCommands([]);await page.receive({botDetailsPage:true,op:'separate'});assert.match(page.messages.at(-1).error,/unavailable/);assert.equal(f.calls.length,1);
 f.setCommands([MOVE_EDITOR]);f.setCommandFailure(true);await page.receive({botDetailsPage:true,op:'separate'});assert.match(page.messages.at(-1).error,/unavailable/);assert(!page.messages.at(-1).error.includes('private runtime'));
});
test('closed or removed bot details cannot start and mutations refresh every live page',async()=>{
 const f=fixture();f.values.set('meshlit.bots',[bot]);const dashboard=f.open(),page=await f.commands.get('meshlit.openBotDetails')(bot.id);
 f.inputs.push('Updated bot','Updated reviewed instructions');f.picks.push({id:'local'});await page.receive({botDetailsPage:true,op:'edit'});
 assert.match(dashboard.webview.html,/Updated bot/);assert.match(page.webview.html,/Updated reviewed instructions/);assert.equal(page.title,'Bot · Updated bot');
 f.setChatFailure(true);await page.receive({botDetailsPage:true,op:'start'});assert(page.messages.at(-1).error);assert(!page.messages.at(-1).error.includes('private runtime'));
 f.setChatFailure(false);f.confirmations.push('Remove');await page.receive({botDetailsPage:true,op:'remove'});assert.match(page.webview.html,/no longer saved/);assert.match(dashboard.webview.html,/No saved bots/);
 await page.receive({botDetailsPage:true,op:'start'});assert.equal(f.opened.length,0);assert(page.messages.at(-1).error);
 f.values.set('meshlit.bots',[bot]);page.close();const count=page.messages.length;await page.receive({botDetailsPage:true,op:'start'});assert.equal(f.opened.length,0);assert.equal(page.messages.length,count);
 const replacement=await f.commands.get('meshlit.openBotDetails')(bot.id);assert.notEqual(replacement,page);
});

test('saved notification does not block immediate details or chat actions',async()=>{
 const f=fixture(),panel=f.open();let dismiss;
 f.vscode.window.showInformationMessage=()=>new Promise(resolve=>{dismiss=resolve;});
 f.inputs.push('Quick helper','Explain clearly.');f.picks.push({id:'local'});
 const completed=await Promise.race([panel.receive({botsPage:true,op:'create'}).then(()=>true),new Promise(resolve=>setTimeout(()=>resolve(false),100))]);
 try {
  assert.equal(completed,true,'Saving must complete while the notification remains open');
  const saved=f.values.get('meshlit.bots')[0];
  await panel.receive({botsPage:true,op:'details',id:saved.id});
  assert.equal(f.panels.at(-1).type,'meshlit.botDetails');
  await f.panels.at(-1).receive({botDetailsPage:true,op:'start'});
  assert.equal(f.opened[0].id,saved.id);
 } finally { dismiss?.(); }
});
