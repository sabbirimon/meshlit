const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
function fixture(){
 const requests=[],messages=[],elements=new Map(),listeners={};let receive;
 const element=()=>({value:'',checked:false,hidden:false,disabled:false,textContent:'',children:[],append(e){this.children.push(e);},replaceChildren(){this.children=[];}});
 const get=id=>{if(!elements.has(id))elements.set(id,element());return elements.get(id);};
 const panel={webview:{onDidReceiveMessage:f=>receive=f,postMessage:m=>messages.push(m)},onDidDispose(){},reveal(){}};
 const exports={};const vscode={ViewColumn:{Active:1},window:{createWebviewPanel:()=>panel},commands:{executeCommand:async name=>requests.push(name)}};
 const module={exports};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'usage.cjs'),'utf8'),{module,require:n=>n==='vscode'?vscode:require(n)});
 const controller=module.exports.create({subscriptions:[]},m=>requests.push(m));controller.open();
 const script=panel.webview.html.match(/<script nonce="[^"]+">([\s\S]*?)<\/script>/)[1];
 vm.runInNewContext(script,{acquireVsCodeApi:()=>({postMessage:receive}),document:{getElementById:get,createElement:element},window:{addEventListener:(k,f)=>listeners[k]=f},Date});
 return {controller,get,requests,messages,receive,deliver:d=>listeners.message({data:d})};
}
test('usage displays real unknown values safely and filters recorded history',()=>{
 const f=fixture();f.deliver({type:'usage',month:'2026-10',requests:2,inputTokens:5,outputTokens:0,unknownUsageRequests:2,freeOnly:true,budgets:[],history:[{model:'<script>bad</script>',startedAt:1,status:'failed',input:5,output:null,cost:null,gateway:true},{model:'Local',startedAt:2,status:'complete',input:null,output:null,cost:'0',currency:'USD',local:true}]});
 assert.equal(f.get('unknown').textContent,'2');assert.equal(f.get('history').children[0].children[1].textContent,'<script>bad</script> · Gateway');assert.equal(f.get('history').children[0].children[4].textContent,'Unknown');assert.equal(f.get('history').children[0].children[5].textContent,'Unknown');
 f.get('filter').value='complete';f.get('filter').onchange();assert.equal(f.get('history').children.length,1);
 f.get('search').value='missing';f.get('search').oninput();assert.equal(f.get('history').children.length,0);
 f.deliver({type:'error',message:'Ledger locked'});assert.equal(f.get('unlock').hidden,false);assert.equal(f.get('status').textContent,'Ledger locked');
});
test('budget controller preserves decimal strings and rejects malformed messages',async()=>{
 const f=fixture();await f.receive({op:'budget',freeOnly:false,currency:'USD',monthly:'12.01',perRequest:'0.001'});
 assert.equal(f.requests.at(-1).monthly,'12.01');const count=f.requests.length;
 for(const bad of [{freeOnly:'false'},{currency:'bad'},{monthly:42},{perRequest:'1'.repeat(31)}])await f.receive({op:'budget',freeOnly:true,currency:'USD',monthly:'10',perRequest:'1',...bad});
 assert.equal(f.requests.length,count);assert.equal(f.messages.at(-1).type,'error');
 await f.receive({op:'unlock'});assert.equal(f.requests.at(-1),'meshlit.unlockUsage');
});
