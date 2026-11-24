'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {systemDictation}=require('./system-dictation.cjs');
function composer(){
    const handlers=new Map(),timers=new Map(),sent=[],cancelled=[];
    let seq=0;
    const field={value:'',addEventListener:(name,fn)=>handlers.set(name,fn)};
    const control=systemDictation(field,{
        submit:(id,text)=>sent.push({id,text}),cancel:id=>cancelled.push(id),
        schedule:(fn,delay)=>{timers.set(++seq,{fn,delay});return seq;},unschedule:id=>timers.delete(id)
    });
    function emit(name,details={}){handlers.get(name)(details);}
    function settle(delay=850){
        for(const [id,timer] of [...timers])if(timer.delay===delay){timers.delete(id);timer.fn();}
    }
    return {field,control,sent,cancelled,emit,settle};
}
test('macOS composition sends the final explicitly terminated named task once',()=>{
    const c=composer();c.control.arm('mac');
    c.emit('compositionstart');
    c.field.value='Ask Gemini to write a poem, send task now.';
    c.emit('input',{inputType:'insertCompositionText',isComposing:true});c.settle();
    assert.equal(c.sent.length,0);
    c.emit('compositionend');c.emit('input',{inputType:'insertText'});c.settle();c.settle();
    assert.deepEqual(c.sent,[{id:'mac',text:'Ask Gemini to write a poem'}]);
    assert.equal(c.field.value,'Ask Gemini to write a poem');
});
test('Linux committed text settles after the last edit and normal composer never auto-sends',()=>{
    const c=composer(),text='Ask ChatGPT to list two birds. Send task now!';
    c.field.value=text;c.emit('input',{inputType:'insertText'});c.settle();assert.equal(c.sent.length,0);
    assert.equal(c.control.arm('nonempty'),false);
    c.field.value='';c.control.arm('linux');
    c.field.value=text;c.emit('input',{inputType:'insertText'});
    c.field.value+=' Later.';c.emit('input',{inputType:'insertReplacementText'});c.settle();
    assert.equal(c.sent.length,0);
    c.field.value='Ask ChatGPT to list two fish send task now';
    c.emit('input',{inputType:'insertText'});c.settle();
    assert.deepEqual(c.sent,[{id:'linux',text:'Ask ChatGPT to list two fish'}]);
});
test('editing, Escape, leaving the field, revocation and expiry cancel a pending voice submission',()=>{
    for(const cancel of [c=>c.emit('keydown',{key:'Escape'}),c=>c.emit('keydown',{key:'x'}),c=>c.emit('paste'),c=>c.emit('blur'),c=>c.control.stop(),c=>c.settle(120000)]){
        const c=composer();c.control.arm('one');
        c.field.value='Ask Gemini to count send task now';c.emit('input',{inputType:'insertText'});
        cancel(c);c.settle();assert.equal(c.sent.length,0);assert.deepEqual(c.cancelled,['one']);
        assert.ok(c.field.value.includes('count'));
    }
});
