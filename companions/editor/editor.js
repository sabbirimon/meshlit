import {EditorView,basicSetup} from 'codemirror';
import {javascript} from '@codemirror/lang-javascript';
import {python} from '@codemirror/lang-python';
import {json} from '@codemirror/lang-json';
import {StreamLanguage} from '@codemirror/language';
import {kotlin} from '@codemirror/legacy-modes/mode/clike';
let view;
window.setEditor=(text,filename)=>{
 if(view) view.destroy();
 const ext=filename.endsWith('.py')?python():filename.endsWith('.json')?json():filename.endsWith('.kt')||filename.endsWith('.kts')?StreamLanguage.define(kotlin):javascript({typescript:filename.endsWith('.ts')});
 view=new EditorView({doc:text,parent:document.querySelector('#editor'),extensions:[basicSetup,ext,EditorView.lineWrapping,
  EditorView.theme({'&':{height:'100%',backgroundColor:'#111827',color:'#e5e7eb'},'.cm-content':{fontFamily:'monospace',fontSize:'14px'},'.cm-scroller':{overflow:'auto'},'.cm-gutters':{backgroundColor:'#1f2937',color:'#9ca3af',border:'none'},'.cm-cursor':{borderLeftColor:'#14b8a6'},'&.cm-focused .cm-selectionBackground,.cm-selectionBackground':{backgroundColor:'#334155'}},{dark:true})]});
};
window.saveEditor=()=>{if(view) NativeEditor.save(view.state.doc.toString());};
window.addEventListener('DOMContentLoaded',()=>NativeEditor.ready());
