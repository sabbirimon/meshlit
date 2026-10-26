const vscode = require('vscode');
const fs = require('node:fs');
const { spawn } = require('node:child_process');
const { launchArguments } = require('./launcher.cjs');
function activate(context) {
  context.subscriptions.push(vscode.commands.registerCommand('meshlit.openWorkspace', async () => {
    if (!vscode.workspace.isTrusted) return vscode.window.showWarningMessage('Trust this workspace before opening it in Meshlit.');
    const folders = vscode.workspace.workspaceFolders || [];
    const folder = folders.length === 1 ? folders[0] : await vscode.window.showWorkspaceFolderPick();
    if (!folder) return vscode.window.showInformationMessage('Open a local project folder first.');
    if (folder.uri.scheme !== 'file') return vscode.window.showWarningMessage('This initial integration supports local folders only.');
    // Never accept repository-controlled launcher settings.
    let executable = vscode.workspace.getConfiguration('meshlitStudio').inspect('launcher')?.globalValue;
    if (!executable) {
      const selected = await vscode.window.showOpenDialog({canSelectFiles:true, canSelectFolders:process.platform==='darwin', canSelectMany:false, title:'Choose your trusted Meshlit app or launcher'});
      if (!selected?.length) return;
      executable = selected[0].fsPath;
      await vscode.workspace.getConfiguration('meshlitStudio').update('launcher', executable, vscode.ConfigurationTarget.Global);
    }
    try {
      if (!fs.existsSync(executable)) throw new Error('Launcher not found');
      const [command,args] = launchArguments(process.platform, executable, folder.uri.fsPath);
      const child = spawn(command,args,{shell:false,detached:true,stdio:'ignore'});
      child.on('error', () => vscode.window.showErrorMessage('Meshlit could not start. Check the configured launcher.'));
      child.unref();
    } catch (_) { vscode.window.showErrorMessage('Choose a valid absolute Meshlit launcher in user settings.'); }
  }));
}
module.exports = { activate };
