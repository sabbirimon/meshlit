'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {entries,shieldIntent,composer}=require('./composer-tools.cjs');
test('pins name known targets, HTTPS services or registered tools; import grants no commands',()=>{
 const tool={command:'my-pin',title:'My pin',target:'devices'};
 for(const target of ['devices','models','agents','https://example.org/x','mcp:ext.demo','studio:inventory'])assert.equal(entries({schema:1,tools:[{...tool,target}]})[0].target,target);
 for(const target of ['file:///tmp/a','https://me:password@example.org','studio:invented','javascript:alert(1)'])assert.throws(()=>entries({schema:1,tools:[{...tool,target}]}));
 assert.throws(()=>entries({schema:1,tools:[tool,tool]}));
});
test('only explicit Shirudo phrases activate from the composer',()=>{
 assert.equal(shieldIntent('Please scan my Shirudo report.'),'import');
 assert.equal(shieldIntent('/shirudo scan'),'scan');assert.equal(shieldIntent('What does /shirudo scan do?'),undefined);
});
test('a saved shortcut dispatches to its intended host',async()=>{
 const store=[{command:'pair',title:'Pair',target:'devices'}];let target;
 const c=composer({}, {globalState:{get:()=>store}}, {destinations:async()=>[],activate:async t=>{target=t}});
 await c.shortcut('pair');assert.equal(target,'devices');await assert.rejects(c.shortcut('absent'),/Unknown tool/);
});

test('tool result download writes the selected bytes and only resolves cached files',async()=>{
 const data=Uint8Array.from([0,255,128,13,10]);
 let attached,saved;
 const api={
  lm:{tools:[{name:'render_image'}],invokeTool:async()=>({content:[{data,mimeType:'image/png'}]})},
  window:{showInputBox:async()=>'{}',showWarningMessage:async()=>'Run tool',
   withProgress:(_,cb)=>cb({},{}),showSaveDialog:async()=>({fsPath:'output.png'})},
  ProgressLocation:{Notification:1},Uri:{file:fsPath=>({fsPath})},
  workspace:{fs:{writeFile:async(_,bytes)=>{saved=bytes}}}
 };
 const host=composer(api,{globalState:{get:()=>[]}},{
  destinations:async()=>[],arguments:()=>'{}',result:(_,text,files)=>{attached=files[0];assert.ok(text.includes(attached))}
 });
 await host.shortcut('mcp:render_image');
 assert.match(attached,/^meshlit-.*\.png$/);
 await host.download(attached);
 assert.deepEqual(saved,Buffer.from(data));
 await assert.rejects(host.download('../outside'),/expired/);
});
test('media from a model uses the same preview and download path as a tool',()=>{
 const host=composer({}, {globalState:{get:()=>[]}}, {});
 const [file]=host.replyFiles({images:[{image_url:{url:'data:image/png;base64,AQID'}}]});
 assert.deepEqual(host.preview(file),{kind:'image',mime:'image/png',data:'AQID'});
 assert.deepEqual(host.replyFiles({images:[{image_url:{url:'https://example.org/private.png'}}]}),[]);
 assert.deepEqual(host.preview('missing'),{kind:'missing'});
});
test('source and unsupported attachments stay inert in previews',()=>{
 const {preview,extension}=require('./attachment-preview.cjs');
 assert.equal(extension('audio/mpeg'),'mp3');
 assert.deepEqual(preview('sample.html',Buffer.from('<script>alert(1)</script>')),{kind:'text',text:'<script>alert(1)</script>',truncated:false});
 assert.equal(preview('large.py',Buffer.alloc(18000)).text.length,16000);
 assert.deepEqual(preview('archive.bin',Buffer.alloc(0)),{kind:'file'});
 assert.deepEqual(preview('broken.docx',Buffer.from('invalid')),{kind:'file'});
});
