import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from prepare_shell import (BOOTSTRAP_ORIGINAL, BOOTSTRAP_PREFIX,
                           ZEN_ORIGINAL, ZEN_REPLACEMENT, SHELL_STYLE,
                           MENU_RENDERER_ANCHOR, MENU_RENDERER_PATCH,
                           MENU_NATIVE_ANCHOR, MENU_NATIVE_PATCH, STUDIO_MENU_ITEMS,
                           configure_chat_shell)


class ChatShellPackagingTest(unittest.TestCase):
    def fixture(self, root):
        bootstrap = root / 'out/vs/code/electron-browser/workbench/workbench.js'
        main = root / 'out/vs/workbench/workbench.desktop.main.js'
        css = root / 'out/vs/workbench/workbench.desktop.main.css'
        for file in (bootstrap, main, css):
            file.parent.mkdir(parents=True, exist_ok=True)
        bootstrap.write_text('({'+BOOTSTRAP_ORIGINAL+'doSomething()}})')
        main.write_text(ZEN_ORIGINAL + MENU_RENDERER_ANCHOR)
        (root / 'out/mainImpl.js').write_text(MENU_NATIVE_ANCHOR)
        css.write_text('body { margin: 0; }')
        return bootstrap, main, css

    def test_patch_is_idempotent_and_does_not_hide_native_window_controls(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = (*self.fixture(root), root / 'out/mainImpl.js')
            configure_chat_shell(root)
            first = [p.read_text() for p in files]
            configure_chat_shell(root)
            self.assertEqual(first, [p.read_text() for p in files])
            self.assertNotIn('.window-controls-container', SHELL_STYLE)
            self.assertNotIn('.notifications', SHELL_STYLE)
            self.assertNotIn('.dialog', SHELL_STYLE)

    def test_unknown_pinned_layout_aborts_without_partial_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bootstrap, main, css = self.fixture(root)
            main.write_text('unknown upstream workbench')
            before = [p.read_text() for p in (bootstrap, main, css)]
            with self.assertRaisesRegex(ValueError, 'Unexpected pinned'):
                configure_chat_shell(root)
            self.assertEqual(before, [p.read_text() for p in (bootstrap, main, css)])

    def test_unknown_native_menu_aborts_all_files_without_partial_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = list(self.fixture(root)) + [root / 'out/mainImpl.js']
            files[-1].write_text('new upstream native menu layout')
            before = [p.read_text() for p in files]
            with self.assertRaisesRegex(ValueError, 'Unexpected pinned workbench menu'):
                configure_chat_shell(root)
            self.assertEqual(before, [p.read_text() for p in files])

    def test_menu_commands_are_existing_public_extension_commands(self):
        manifest = json.loads((Path(__file__).parent / 'extension/package.json').read_text())
        registered = {item['command'] for item in manifest['contributes']['commands']}
        commands = [item[2] for item in STUDIO_MENU_ITEMS]
        self.assertEqual(len(commands), len(set(commands)))
        self.assertTrue(set(commands) <= registered)

    @unittest.skipUnless(shutil.which('node'), 'requires Node to execute menu registry patch')
    def test_renderer_and_native_menu_preserve_command_routing_and_standard_menus(self):
        script = '''
const assert=require('node:assert/strict'),vm=require('node:vm');
class F {constructor(id){this.id=id;}} F.MenubarMainMenu=new F('Main');
const registry=[],ctx={F,De:{appendMenuItem:(menu,item)=>registry.push({menu:menu.id,item})}};
vm.runInNewContext(RENDERER,ctx);
assert.equal(registry[0].menu,'Main');
assert.equal(registry[0].item.title.original,'Studio');
assert.equal(registry[0].item.order,7.5);
assert.equal(registry.length,20);
assert(registry.slice(1).every(row=>row.menu==='MeshlitStudioMenu'&&row.item.command.id.startsWith('meshlit.')));
for(const available of [true,false]){
 const top={items:['File','Edit','Selection','View','Go','Run','Terminal'],append(item){this.items.push(item);}};
 class Menu {constructor(){this.items=[];}} class Item {constructor(options){Object.assign(this,options);}}
 const host={shouldDrawMenu:id=>available&&id==='Studio',mnemonicLabel:s=>s.replace('&&',''),setMenuById(menu,id){assert.equal(id,'Studio');menu.items=registry.slice(1).map(row=>row.item.command);}};
 vm.runInNewContext('(function(){'+NATIVE+'}).call(host)',{hn:Menu,ct:Item,t:top,host});
 assert.deepEqual(top.items.slice(0,7),['File','Edit','Selection','View','Go','Run','Terminal']);
 assert.equal(top.items.length,available?8:7);
 if(available){assert.equal(top.items[7].label,'Studio');assert.equal(top.items[7].submenu.items[0].id,'meshlit.chat');}
}
'''
        subprocess.run(['node', '-e', 'const RENDERER='+json.dumps(MENU_RENDERER_PATCH)+';const NATIVE='+json.dumps(MENU_NATIVE_PATCH)+';'+script], check=True)

    @unittest.skipUnless(shutil.which('node'), 'requires Node to execute bootstrap')
    def test_project_windows_keep_user_layout_and_empty_windows_keep_notifications(self):
        script = '''
const assert = require('node:assert/strict');
const vm = require('node:vm');
for (const workspace of [undefined, {id:'project'}, {configPath:'saved.code-workspace'}]) {
 const original = {fullScreen:true,centerLayout:true,showTabs:'multiple',silentNotifications:true,restore:true};
 const splash = {layoutInfo:{width:123},colorInfo:{background:'#123'}};
 const classes = new Set();
 const ctx = {a:{workspace,partsSplash:splash}, document:{body:{classList:{toggle:(name,on)=>on?classes.add(name):classes.delete(name)}}}};
 vm.createContext(ctx);
 vm.runInContext(PREFIX+ZEN+';result=frr({getValue:()=>('+JSON.stringify(original)+')})',ctx);
 assert.equal(classes.has('meshlit-chat-shell'),!workspace);
 if (workspace) {
  assert.equal(ctx.result.fullScreen,true);
  assert.equal(ctx.result.restore,true);
  assert.equal(ctx.result.showTabs,'multiple');
  assert.equal(splash.layoutInfo.width,123);
 } else {
  assert.equal(ctx.result.fullScreen,false);
  assert.equal(ctx.result.centerLayout,false);
  assert.equal(ctx.result.showTabs,'none');
  assert.equal(ctx.result.hideStatusBar,true);
  assert.equal(ctx.result.hideActivityBar,true);
  assert.equal(ctx.result.restore,false);
  assert.equal(ctx.result.silentNotifications,false);
  assert.equal(splash.layoutInfo,undefined);
  assert.equal(splash.colorInfo.background,'#123');
 }
}
'''
        subprocess.run(['node', '-e', 'const PREFIX='+json.dumps(BOOTSTRAP_PREFIX)+';const ZEN='+json.dumps(ZEN_REPLACEMENT)+';'+script], check=True)


if __name__ == '__main__':
    unittest.main()
