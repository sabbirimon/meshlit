'use strict';

const styles = `
h1,.brand,.top{color:var(--vscode-textLink-foreground,#68a8e6)}
h2,h3,.setupTitle{color:color-mix(in srgb,var(--vscode-textLink-foreground,#68a8e6) 68%,var(--vscode-foreground,#ddd))}
#disabledHelp{position:fixed;z-index:1000;max-width:min(320px,90vw);padding:8px 11px;border:1px solid var(--vscode-editorHoverWidget-border,var(--vscode-panel-border));border-radius:6px;background:var(--vscode-editorHoverWidget-background,var(--vscode-editor-background,#242424));color:var(--vscode-editorHoverWidget-foreground,var(--vscode-foreground,#eee));font:13px/1.45 var(--vscode-font-family,system-ui);box-shadow:0 3px 12px #0004;pointer-events:none}
#disabledHelp[hidden]{display:none}
`;

function installHelp() {
    const tip=document.createElement('div');
    tip.id='disabledHelp';
    tip.setAttribute('role','tooltip');
    tip.hidden=true;
    document.body.append(tip);
    let target;
    function hide(){tip.hidden=true;target=undefined;}
    function show(event){
        const button=event.target.closest?.('button:disabled,select:disabled,input:disabled');
        if(!button||!button.dataset.reason){hide();return;}
        if(target===button&&!tip.hidden)return;
        target=button;
        tip.textContent=button.dataset.reason;
        tip.hidden=false;
        const rect=button.getBoundingClientRect();
        tip.style.left=Math.max(4,Math.min(rect.left,innerWidth-tip.offsetWidth-4))+'px';
        tip.style.top=(rect.bottom+tip.offsetHeight+8<innerHeight?rect.bottom+4:Math.max(4,rect.top-tip.offsetHeight-4))+'px';
    }
    document.addEventListener('pointerover',show,true);
    document.addEventListener('pointerout',event=>{if(!target?.contains(event.relatedTarget))hide();},true);
    document.addEventListener('scroll',hide,true);
    window.addEventListener('blur',hide);
    document.addEventListener('keydown',event=>{if(event.key==='Escape')hide();});
    new MutationObserver(()=>{if(target&&(!target.disabled||!target.isConnected))hide();}).observe(document.body,{subtree:true,childList:true,attributes:true,attributeFilter:['disabled']});
}
function setAvailability(button,reason){
    button.disabled=Boolean(reason);
    if(reason){button.dataset.reason=reason;button.setAttribute('aria-description',reason);}
    else{delete button.dataset.reason;button.removeAttribute('aria-description');}
}
module.exports={styles,script:installHelp.toString()+'\n'+setAvailability.toString()+'\ninstallHelp();'};
