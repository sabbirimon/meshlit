'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {exportOptions} = require('./chat-export.cjs');

test('agents get ordered visible turns and code export preserves complete reply fences', () => {
    const code = '~~~~js\nif (a < b) return "✓";\n~~~~';
    const turns = [
        {role:'system',content:'hidden'},
        {role:'user',content:'~~~\nuser code\n~~~',key:'hidden'},
        {role:'assistant',content:'Hello\n'+code+'\n~~~\nunfinished'}
    ];
    const [markdown, agent, blocks] = exportOptions(turns);
    const records = agent.text.trimEnd().split('\n').map(line=>JSON.parse(line));
    assert.deepEqual(records, turns.slice(1).map((m,i)=>({schema:'meshlit.chat.turn.v1',turn:i+1,role:m.role,text:m.content})));
    assert.equal(blocks.text,code+'\n');
    assert.equal(markdown.text.includes('hidden'),false);
    assert.deepEqual(exportOptions([]),[]);
    assert.equal(exportOptions([turns[1]]).length,2);
});
