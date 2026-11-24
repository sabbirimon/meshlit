'use strict';

function fencedBlocks(text) {
    const blocks = [];
    let fence = '', lines = [];
    for (const line of text.split(/\r\n?|\n/)) {
        const match = /^ {0,3}(\x60{3,}|~{3,})(.*)$/.exec(line);
        if (!fence) {
            if (!match || (match[1][0] === '\x60' && match[2].includes('\x60'))) continue;
            fence = match[1];
            lines = [line];
        } else {
            lines.push(line);
            if (match && match[1].startsWith(fence) && !match[2].trim()) {
                blocks.push(lines.join('\n'));
                fence = '';
            }
        }
    }
    return blocks;
}

function exportOptions(history) {
    const turns = history.filter(m => m.role === 'user' || m.role === 'assistant');
    if (!turns.length) return [];
    const markdown = turns.map(m => '## ' + (m.role === 'user' ? 'You' : 'Meshlit') + '\n\n' + m.content);
    const records = turns.map((m, i) => JSON.stringify({schema:'meshlit.chat.turn.v1', turn:i+1, role:m.role, text:m.content}));
    const blocks = turns.filter(m => m.role === 'assistant').flatMap(m => fencedBlocks(m.content));
    const detail = turns.length + ' messages';
    const choices = [
        {label:'Conversation as Markdown', detail, extension:'md', text:'# Meshlit chat\n\n' + markdown.join('\n\n') + '\n'},
        {label:'Conversation for agents (JSON Lines)', detail, extension:'jsonl', text:records.join('\n') + '\n'}
    ];
    if (blocks.length) choices.push({label:'Reply code blocks', detail:blocks.length+' fenced blocks', extension:'md', text:blocks.join('\n\n')+'\n'});
    return choices;
}

module.exports = {exportOptions};
