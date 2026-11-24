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
 const c=composer({}, {globalState:{get:()=>store}}, {activate:async t=>{target=t}});
 await c.shortcut('pair');assert.equal(target,'devices');await assert.rejects(c.shortcut('absent'));
});
