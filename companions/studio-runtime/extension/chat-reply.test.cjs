const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm');
const {render,quoteReply}=require('./chat-view.cjs');
test('reply quotes preserve the current draft and stay inside the input limit',()=>{
 assert.equal(quoteReply('Earlier answer\nsecond line','My draft','assistant'),'> Assistant:\n> Earlier answer\n> second line\n\nMy draft');
 const draft='x'.repeat(15900),result=quoteReply('q'.repeat(5000),draft,'user');assert(result.length<=16000);assert(result.endsWith(draft));
 assert.equal(quoteReply('Answer','x'.repeat(15999),'assistant'),undefined);
});
test('reply UI retains safe text rendering and compiles as an actual webview script',()=>{
 const html=render('Chat','chat');assert.match(html,/Reply to message/);assert.match(html,/prompt.focus\(\)/);assert.doesNotMatch(html,/innerHTML/);
 new vm.Script(html.match(/<script nonce="[^"]+">([\s\S]*)<\/script>/)[1]);
});
