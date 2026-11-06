"""Add the empty-window Meshlit shell to the explicitly pinned workbench.

Project windows retain the upstream workbench layout. The extension enters Zen
layout in the empty window so the editor services allocate the entire content
area to the main application webview. This patch supplies the early window
identity, removes the saved IDE splash, and keeps native window controls.
"""
from pathlib import Path
import json


BOOTSTRAP_ORIGINAL = 'beforeImport:function(a){L(a),'
BOOTSTRAP_PREFIX = (
    'globalThis.__MESHLIT_CHAT_SHELL=!a.workspace;'
    'document.body.classList.toggle("meshlit-chat-shell",globalThis.__MESHLIT_CHAT_SHELL);'
    'if(globalThis.__MESHLIT_CHAT_SHELL&&a.partsSplash)a.partsSplash.layoutInfo=void 0;'
)
BOOTSTRAP_REPLACEMENT = 'beforeImport:function(a){' + BOOTSTRAP_PREFIX + 'L(a),'

ZEN_ORIGINAL = 'function frr(s){return s.getValue("zenMode")}'
ZEN_REPLACEMENT = (
    'function frr(s){const z=s.getValue("zenMode");'
    'return globalThis.__MESHLIT_CHAT_SHELL?{...z,fullScreen:false,centerLayout:false,'
    'showTabs:"none",hideStatusBar:true,hideActivityBar:true,restore:false,'
    'silentNotifications:false}:z}'
)

# Both sides are necessary: the renderer sends named menu data, while the native
# Electron main process installs an explicit list of top-level menus. Keep the
# upstream menus and command dispatch/enablement handling intact.
MENU_RENDERER_ANCHOR = 'De.appendMenuItem(F.MenubarMainMenu,{submenu:F.MenubarHelpMenu,title:{value:"Help",original:"Help",mnemonicTitle:d(8636,null)},order:8});'
MENU_NATIVE_ANCHOR = 'let o;if(this.shouldDrawMenu("Window")){let s=new hn;'
STUDIO_MENU_ITEMS = (
    ('1_chat', 'New Chat', 'meshlit.chat'),
    ('1_chat', 'General Chat', 'meshlit.generalChat'),
    ('1_chat', 'Workspaces…', 'meshlit.workspaces'),
    ('1_chat', 'Open IDE…', 'meshlit.openIde'),
    ('2_models', 'Management', 'meshlit.management'),
    ('2_models', 'Models and Sources', 'meshlit.models'),
    ('2_models', 'LLM Settings', 'meshlit.llmSettings'),
    ('3_agents', 'Bots', 'meshlit.botDashboard'),
    ('3_agents', 'Workspace Agents', 'meshlit.workspaceAgents'),
    ('3_agents', 'Agent Teams', 'meshlit.agentTeams'),
    ('4_connections', 'Devices and Connections', 'meshlit.deviceConnections'),
    ('4_connections', 'MCP Connectors', 'meshlit.connectors'),
    ('4_connections', 'SSH and Terminals', 'meshlit.sshDashboard'),
    ('5_preferences', 'Chat Personality', 'meshlit.chatPersonality'),
    ('5_preferences', 'Voice and Vision', 'meshlit.voice'),
    ('5_preferences', 'Usage and Budgets', 'meshlit.usage'),
    ('5_preferences', 'Credentials', 'meshlit.credentialsDashboard'),
    ('6_help', 'Settings', 'meshlit.settings'),
    ('6_help', 'User Manual', 'meshlit.manual'),
)
MENU_RENDERER_PATCH = (
    '/* Meshlit native Studio menu */(()=>{'
    'const menu=new F("MeshlitStudioMenu");'
    'De.appendMenuItem(F.MenubarMainMenu,{submenu:menu,title:{value:"Studio",'
    'original:"Studio",mnemonicTitle:"&&Studio"},order:7.5});'
    'for(const [order,item]of ' + json.dumps(STUDIO_MENU_ITEMS, ensure_ascii=True) + '.entries())'
    'De.appendMenuItem(menu,{group:item[0],order,command:{id:item[2],title:item[1]}});'
    '})();'
)
MENU_NATIVE_PATCH = (
    '/* Meshlit native Studio menu */if(this.shouldDrawMenu("Studio")){'
    'let s=new hn,a=new ct({label:this.mnemonicLabel("&&Studio"),submenu:s});'
    'this.setMenuById(s,"Studio"),t.append(a)}'
)


def _insert_pinned(source, anchor, addition, name):
    replacement = addition + anchor
    if source.count(replacement) == 1 and source.count(anchor) == 1:
        return source
    if source.count(anchor) != 1 or '/* Meshlit native Studio menu */' in source:
        raise ValueError('Unexpected pinned workbench menu code: ' + name)
    return source.replace(anchor, replacement)

STYLE_MARKER = '/* Meshlit empty-window shell chrome */'
SHELL_STYLE = '\n' + STYLE_MARKER + '''
body.meshlit-chat-shell .monaco-workbench .part.titlebar>.titlebar-container>.titlebar-center,
body.meshlit-chat-shell .monaco-workbench .part.titlebar>.titlebar-container>.titlebar-right>.action-toolbar-container,
body.meshlit-chat-shell .monaco-workbench .part.titlebar>.titlebar-container>.titlebar-right>.center-adjacent-toolbar-container,
body.meshlit-chat-shell .monaco-workbench .part.titlebar>.titlebar-container>.titlebar-right>.update-toolbar-container {
    display: none !important;
}
body.meshlit-chat-shell .monaco-workbench .part.titlebar {
    background-color: var(--vscode-sideBar-background) !important;
    border-bottom: none !important;
}
'''


def _replace_pinned(source, original, replacement, name):
    if source.count(replacement) == 1 and original not in source:
        return source
    if source.count(original) != 1 or replacement in source:
        raise ValueError('Unexpected pinned workbench shell code: ' + name)
    return source.replace(original, replacement)


def configure_chat_shell(resources):
    """Patch a staging bundle; refresh product checksums before signing it."""
    out = Path(resources) / 'out'
    bootstrap = out / 'vs/code/electron-browser/workbench/workbench.js'
    workbench = out / 'vs/workbench/workbench.desktop.main.js'
    css = out / 'vs/workbench/workbench.desktop.main.css'
    native = out / 'mainImpl.js'
    # Validate every pinned anchor before changing any file.
    boot_source = _replace_pinned(bootstrap.read_text(), BOOTSTRAP_ORIGINAL,
                                  BOOTSTRAP_REPLACEMENT, bootstrap.name)
    main_source = _replace_pinned(workbench.read_text(), ZEN_ORIGINAL,
                                  ZEN_REPLACEMENT, workbench.name)
    main_source = _insert_pinned(main_source, MENU_RENDERER_ANCHOR,
                                 MENU_RENDERER_PATCH, workbench.name)
    native_source = _insert_pinned(native.read_text(), MENU_NATIVE_ANCHOR,
                                   MENU_NATIVE_PATCH, native.name)
    css_source = css.read_text()
    if STYLE_MARKER in css_source:
        if css_source.count(SHELL_STYLE) != 1:
            raise ValueError('Unexpected existing Meshlit shell style')
    else:
        css_source += SHELL_STYLE
    bootstrap.write_text(boot_source)
    workbench.write_text(main_source)
    native.write_text(native_source)
    css.write_text(css_source)
