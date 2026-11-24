'use strict';

// System dictation can deliver composition or plain input events in the webview.
function systemDictation(field, {cancel, submit, schedule=setTimeout, unschedule=clearTimeout}) {
    let session, settle, expiry, composing=false;
    function take() {
        const id=session;
        session=undefined;
        unschedule(settle);unschedule(expiry);
        composing=false;
        return id;
    }
    function stop() {
        const id=take();
        if(id)cancel(id);
    }
    function arm(id) {
        stop();
        if(field.value.trim()){cancel(id);return false;}
        session=id;
        expiry=schedule(stop,120000);
        return true;
    }
    function changed(event={}) {
        unschedule(settle);
        if(!session || composing || event.isComposing)return;
        if(event.inputType && !['insertText','insertReplacementText','insertCompositionText','insertFromComposition','insertFromDictation'].includes(event.inputType)){stop();return;}
        const snapshot=field.value;
        const ending=/[\s,;.!?]+send task now[.!?\s]*$/i.exec(snapshot);
        if(!ending)return;
        const task=snapshot.slice(0,ending.index).trim();
        if(!task)return;
        settle=schedule(()=>{
            if(!session || composing || field.value!==snapshot)return;
            const id=take();
            field.value=task;
            submit(id,task);
        },850);
    }
    field.addEventListener('input',changed);
    field.addEventListener('compositionstart',()=>{unschedule(settle);composing=true;});
    field.addEventListener('compositionend',()=>{composing=false;changed();});
    field.addEventListener('keydown',event=>{
        if(event.isComposing || ['Process','Unidentified','Dead','Fn','Control','Alt','Meta','Shift'].includes(event.key))return;
        stop();
    });
    for(const name of ['paste','cut','drop','pointerdown','blur'])field.addEventListener(name,stop);
    return {arm,stop};
}
module.exports={systemDictation};
