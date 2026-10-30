const vscode = require('vscode');
const crypto = require('node:crypto');
const os = require('node:os');
const client = require('./companion-client.cjs');
const mcpAuth = require('./mcp-auth.cjs');
const fs = require('node:fs');
const path = require('node:path');
const KEY = 'meshlit.companions.v1';
const labels = {
  'not-configured': 'Not configured', 'not-checked': 'Saved · not checked',
  'checking': 'Checking local endpoint…', 'invalid-address': 'Invalid saved address',
  'worker-ready': 'Memory worker initialized', 'worker-starting': 'Memory worker is starting',
  'worker-degraded': 'Memory worker reports degraded service',
  'service-reachable': 'OpenMausBot harness responds',
  'endpoint-responding': 'Endpoint responds · identity not verified',
  'identity-unverified': 'Response does not match the expected service',
  'authentication-required': 'Authentication required · open the companion to sign in',
  'http-error': 'Endpoint returned an HTTP error', 'response-too-large': 'Health response exceeds 64 KiB',
  'unexpected-response': 'Unexpected health response', 'redirect-rejected': 'Redirect refused',
  'timed-out': 'Health check timed out', 'cancelled': 'Health check cancelled',
  'connection-failed': 'Could not connect · check the address, running service and TLS'
};

function register(context) {
  let panel, queue = Promise.resolve();
  const probes = new Map(), health = new Map();
  let uid; try { uid = os.userInfo().uid; } catch { /* Explicitly handled by defaults. */ }
  const saved = () => {
    const value = context.globalState.get(KEY, {});
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
  };
  const configuration = id => {
    const value = saved()[id];
    if (!value) throw Error('Save the companion addresses first');
    return client.validateConfiguration(id, value);
  };
  const stopProbe = id => { probes.get(id)?.abort(); probes.delete(id); };
  const mutate = fn => { const task = queue.then(fn); queue = task.catch(() => {}); return task; };
  const message = text => panel?.webview.postMessage({message: text});
  const rows = id => client.sources.filter(source => !id || source.id === id).map(source => {
    let config = client.defaults(source.id, uid), status = 'not-configured';
    if (saved()[source.id]) {
      try { config = configuration(source.id); status = 'not-checked'; }
      catch { status = 'invalid-address'; }
    }
    return {id: source.id, config, configured: !!saved()[source.id], health: health.get(source.id) || {status}};
  });
  const update = id => panel?.webview.postMessage({rows: rows(id)});
  const publishHealth = (id, value) => {
    health.set(id, value);
    panel?.webview.postMessage({health: {id, ...value}});
  };
  context.subscriptions.push({dispose() { for (const id of probes.keys()) stopProbe(id); }});
  context.subscriptions.push(vscode.commands.registerCommand('meshlit.companions', () => {
    if (panel) { panel.reveal(); return; }
    const currentPanel = vscode.window.createWebviewPanel('meshlit.companions', 'Companion services', vscode.ViewColumn.One, {enableScripts: true, localResourceRoots: []});
    panel = currentPanel;
    panel.webview.html = render();
    panel.onDidDispose(() => {
      for (const id of probes.keys()) stopProbe(id);
      if (panel === currentPanel) panel = undefined;
    });
    context.subscriptions.push(panel);
    panel.webview.onDidReceiveMessage(async input => {
      try {
        if (!input || typeof input !== 'object') return;
        if (input.op === 'ready') { update(); return; }
        if (!client.sources.some(source => source.id === input.id)) throw Error('Unknown companion');
        const id = input.id;
        if (input.op === 'save') {
          const config = client.validateConfiguration(id, input.config);
          stopProbe(id);
          await mutate(() => context.globalState.update(KEY, {...saved(), [id]: config}));
          health.delete(id); update(id);
          message('Addresses saved. Use Check endpoint to contact the service; nothing was installed or started.');
        } else if (input.op === 'remove') {
          stopProbe(id);
          await mutate(async () => { const next = {...saved()}; delete next[id]; await context.globalState.update(KEY, next); });
          health.delete(id); update(id);
          message('Saved addresses removed. Companion data and any MCP connector remain unchanged.');
        } else if (input.op === 'check') {
          const config = configuration(id);
          stopProbe(id);
          const abort = new AbortController(); probes.set(id, abort);
          publishHealth(id, {status: 'checking'});
          const result = await client.checkCompanion(id, config, {signal: abort.signal});
          if (probes.get(id) === abort) { probes.delete(id); publishHealth(id, result); }
        } else if (input.op === 'cancel') {
          stopProbe(id); publishHealth(id, {status: 'cancelled'});
        } else if (input.op === 'open') {
          const opened = await vscode.env.openExternal(vscode.Uri.parse(client.uiUrl(id, configuration(id))));
          message(opened ? 'Opened the companion in your browser. Its own permissions, credentials and billing apply.' : 'The browser could not open this address.');
        } else if (input.op === 'source' || input.op === 'docs') {
          await vscode.env.openExternal(vscode.Uri.parse(client.sourceUrl(id, input.op === 'docs')));
        } else if (input.op === 'mcp' && id === 'claude-mem') {
          const config = configuration(id);
          const name = await vscode.window.showInputBox({title: 'Claude-Mem connector name', value: 'claude-mem', prompt: 'The separate MCP HTTP server must already be running. Saved disabled; select it for an agent when needed.', validateInput: text => /^[A-Za-z0-9_-]{1,64}$/.test(text) ? undefined : 'Use letters, digits, hyphens and underscores'});
          if (!name) return;
          await mutate(async () => {
            if (JSON.stringify(configuration(id)) !== JSON.stringify(config)) throw Error('Addresses changed; try adding the connector again');
            const connector = client.memoryConnector(config, name);
            const previous = context.globalState.get('meshlit.mcp', []);
            if (!Array.isArray(previous) || previous.length >= 64) throw Error('The connector registry is invalid or full');
            if (previous.some(item => item.name === name)) throw Error('A connector with that name already exists');
            await context.globalState.update('meshlit.mcp', [...previous, connector]);
          });
          message('MCP connector saved, disabled by default. Select it for an Edit or Autopilot agent. Connection and tool calls are reviewed by the existing MCP flow. Worker health does not verify this separate MCP port.');
        } else if (input.op === 'mcp' && id === 'openmausbot') {
          const config = configuration(id), origin = new URL(config.apiOrigin);
          const port = Number(origin.port);
          if (origin.protocol !== 'http:' || origin.hostname !== '127.0.0.1' || !Number.isInteger(port) || port < 1024 || port > 65535) throw Error('Paired MCP currently requires an explicit http://127.0.0.1:PORT API origin, port 1024–65535');
          const name = await vscode.window.showInputBox({title: 'OpenMausBot connector name', value: 'openmausbot', validateInput: text => /^[A-Za-z0-9_-]{1,64}$/.test(text) ? undefined : 'Use letters, digits, hyphens and underscores'});
          if (!name) return;
          const selected = await vscode.window.showOpenDialog({title: 'Choose your installed openmausbot CLI executable', canSelectMany: false});
          if (!selected) return;
          const command = selected[0].fsPath;
          if (!path.isAbsolute(command) || !fs.statSync(command).isFile()) throw Error('Choose the installed openmausbot CLI executable');
          const token = await vscode.window.showInputBox({title: 'OpenMausBot paired session token', password: true, prompt: `Pair this client in OpenMausBot first. The token is bound to 127.0.0.1:${port}, stored in SecretStorage, and supplied only to this executable after connector approval.`});
          if (token === undefined) return;
          if (!mcpAuth.validOpenMausToken(token)) throw Error('Enter a valid omb_sess_ paired session token');
          const tokenRef = 'meshlit.mcp.openmausbot.' + crypto.randomUUID();
          const connector = mcpAuth.configuredConnector({name, type: 'stdio', command, args: ['mcp'], auth: {kind: 'openmausbot-session', port, tokenRef}});
          await mutate(async () => {
            if (JSON.stringify(configuration(id)) !== JSON.stringify(config)) throw Error('Addresses changed during pairing; start again');
            const previous = context.globalState.get('meshlit.mcp', []);
            if (!Array.isArray(previous) || previous.length >= 64) throw Error('The connector registry is invalid or full');
            if (previous.some(item => item.name === name)) throw Error('A connector with that name already exists');
            try { await context.secrets.store(tokenRef, token); }
            catch { throw Error('Could not save the pairing secret; connector was not added'); }
            try { await context.globalState.update('meshlit.mcp', [...previous, connector]); }
            catch { await context.secrets.delete(tokenRef).catch(() => {}); throw Error('Could not save the connector; pairing setup did not finish'); }
          });
          message('Paired MCP connector saved, disabled by default. Select it for an Edit or Autopilot agent. Each tool call still requires review; upstream session and bot permissions also apply.');
        } else if (input.op === 'removeMcp' && id === 'openmausbot') {
          const entries = context.globalState.get('meshlit.mcp', []).filter(item => item.auth?.kind === 'openmausbot-session');
          const picked = await vscode.window.showQuickPick(entries.map(item => ({label: item.name, description: '127.0.0.1:' + item.auth.port, item})), {title: 'Remove paired MCP connector and stop its Meshlit chats'});
          if (!picked) return;
          await vscode.commands.executeCommand('meshlit.revokeMcp', picked.item.name);
          await mutate(async () => {
            const previous = context.globalState.get('meshlit.mcp', []);
            const current = previous.find(item => item.name === picked.item.name);
            if (!current || current.auth?.tokenRef !== picked.item.auth.tokenRef) throw Error('The connector changed; select it again');
            await context.globalState.update('meshlit.mcp', previous.filter(item => item !== current));
            try { await context.secrets.delete(current.auth.tokenRef); }
            catch { throw Error('Connector removed, but the saved secret could not be erased. Revoke this client in OpenMausBot Settings → Phone.'); }
          });
          message('Paired MCP connector and saved secret removed. Revoke the paired client in OpenMausBot Settings → Phone to invalidate the upstream session too.');
        }
      } catch (error) {
        message(error?.message || 'The companion operation failed');
      }
    });
  }));
}

function escape(text) { return text.replace(/[&<>"']/g, char => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[char])); }
function render() {
  const nonce = crypto.randomBytes(20).toString('hex');
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}'; base-uri 'none'; form-action 'none'"><style nonce="${nonce}">
body{font:14px var(--vscode-font-family);color:var(--vscode-foreground);background:var(--vscode-editor-background);margin:0;padding:28px;line-height:1.55}main{max-width:1000px;margin:auto}h1{font-size:28px;line-height:1.2;margin:0 0 12px}h2{font-size:19px;margin:0}p{margin:10px 0}.intro,.hint{color:var(--vscode-descriptionForeground)}.service{border-top:1px solid var(--vscode-panel-border);padding:24px 0}.heading{display:flex;justify-content:space-between;align-items:baseline;gap:16px;flex-wrap:wrap}.license{font-size:12px;color:var(--vscode-descriptionForeground)}.addresses{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin:16px 0}label{display:block}input,button{font:inherit}input{box-sizing:border-box;display:block;width:100%;margin-top:6px;padding:9px 10px;color:var(--vscode-input-foreground);background:var(--vscode-input-background);border:1px solid var(--vscode-input-border,var(--vscode-panel-border));border-radius:3px}button{padding:7px 12px;cursor:pointer;border:1px solid var(--vscode-button-border,transparent);border-radius:3px;background:var(--vscode-button-secondaryBackground);color:var(--vscode-button-secondaryForeground)}button:hover{background:var(--vscode-button-secondaryHoverBackground)}button.primary{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}button:focus-visible,input:focus-visible,summary:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:2px}button:disabled{opacity:.5;cursor:default}.actions{display:flex;flex-wrap:wrap;gap:8px}.status{font-weight:600;margin-bottom:0}.checked{font-size:12px;color:var(--vscode-descriptionForeground);min-height:18px}.message{white-space:pre-wrap;padding:10px 0;color:var(--vscode-descriptionForeground);min-height:22px}details{margin-top:14px}summary{cursor:pointer}code{overflow-wrap:anywhere}small{display:block;margin-top:8px;color:var(--vscode-descriptionForeground)}@media(max-width:650px){body{padding:18px}.addresses{grid-template-columns:1fr}}@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto}}
</style></head><body><main><h1>Companion services</h1><p class="intro">Connect a service you run on this computer. Keep its complete workspace, settings and permissions in the companion; use Meshlit to find it and check its endpoint.</p><p class="hint">Checks read only a local health endpoint. They do not test a model, sign in, enable capture, or authorize agent actions. Setup, accounts and charges remain with each companion.</p><div id="message" class="message" role="status" aria-live="polite"></div>
${client.sources.map(source => `<section class="service" data-id="${source.id}" aria-labelledby="${source.id}-title"><div class="heading"><h2 id="${source.id}-title">${escape(source.name)}</h2><span class="license">${source.license} · optional host service</span></div><p>${escape(source.description)}</p><p id="${source.id}-status" class="status" role="status" aria-live="polite">Not configured</p><p id="${source.id}-checked" class="checked"></p><div class="addresses"><label>API origin<input id="${source.id}-apiOrigin" aria-describedby="${source.id}-hint" spellcheck="false" autocomplete="off" maxlength="2048"></label><label>Workspace origin<input id="${source.id}-uiOrigin" aria-describedby="${source.id}-hint" spellcheck="false" autocomplete="off" maxlength="2048"></label>${source.id === 'claude-mem' ? `<label>Separate MCP HTTP endpoint<input id="${source.id}-mcpUrl" spellcheck="false" autocomplete="off" maxlength="2048"></label>` : ''}</div><p id="${source.id}-hint" class="hint">Use <code>http://127.0.0.1:PORT</code> or <code>http://[::1]:PORT</code>. No URL credentials. ${escape(source.prerequisites)}</p><div class="actions"><button data-op="save" class="primary">Save addresses</button><button data-op="check" data-needs-config>Check endpoint</button><button data-op="cancel">Cancel check</button><button data-op="open" data-needs-config>Open workspace</button><button data-op="remove" data-needs-config>Remove addresses</button></div><details><summary>Integration and source</summary><p>Reviewed source <code>${source.revision}</code>. A running service may use a different version; protocol changes need a new review.</p><div class="actions">${source.id !== 'openbot' ? '<button data-op="mcp">' + (source.id === 'claude-mem' ? 'Add memory MCP connector' : 'Add paired MCP connector') + '</button>' : ''}${source.id === 'openmausbot' ? '<button data-op="removeMcp">Remove paired MCP connector</button>' : ''}<button data-op="docs">Upstream setup</button><button data-op="source">Reviewed source</button></div><small>${source.id === 'openbot' ? 'Full workspace handoff is supported. No native AG-UI adapter is installed by this dashboard.' : source.id === 'openmausbot' ? 'MCP coordinates teams through a separately approved paired session. Tokens stay in SecretStorage; approvals and computer lifecycle stay in OpenMausBot.' : 'The memory MCP connector is saved disabled. Capturing Meshlit chats, automatic recall and cloud sync are not enabled by adding it.'}</small></details></section>`).join('')}
</main><script nonce="${nonce}">
const api=acquireVsCodeApi(),labels=${JSON.stringify(labels)};
function health(id,value){document.getElementById(id+'-status').textContent=labels[value.status]||'Unknown state';document.getElementById(id+'-checked').textContent=value.checkedAt?'Checked '+new Date(value.checkedAt).toLocaleString()+(value.version?' · worker '+value.version:'')+(value.httpStatus?' · HTTP '+value.httpStatus:'')+(value.servesUi===false?' · API has no bundled UI; set the source UI origin':''):'';}
document.querySelectorAll('.service').forEach(section=>{section.querySelectorAll('[data-op]').forEach(button=>{button.onclick=()=>{const id=section.dataset.id,op=button.dataset.op,config={};if(op==='save')for(const key of ['apiOrigin','uiOrigin','mcpUrl']){const input=document.getElementById(id+'-'+key);if(input)config[key]=input.value;}api.postMessage({id,op,config});};});});
window.addEventListener('message',({data})=>{if(data.message!==undefined)document.getElementById('message').textContent=data.message;if(data.rows)for(const row of data.rows){for(const key of ['apiOrigin','uiOrigin','mcpUrl']){const input=document.getElementById(row.id+'-'+key);if(input)input.value=row.config[key]||'';}document.querySelectorAll('[data-id="'+row.id+'"] [data-needs-config]').forEach(button=>button.disabled=!row.configured);health(row.id,row.health);}if(data.health)health(data.health.id,data.health);});api.postMessage({op:'ready'});
</script></body></html>`;
}

module.exports = {register, render, KEY};
