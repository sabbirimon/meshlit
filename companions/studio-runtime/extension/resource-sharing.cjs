const vscode = require('vscode'), crypto = require('node:crypto');
const {resourceUrl, validToken, inspectResources} = require('./resource-sharing-client.cjs');
const KEY = 'meshlit.resourceSharing.endpoint';
const messages = {
  'catalog-received': 'Resource catalog received. Configured models are not proof of available capacity or successful generation.',
  'invalid-address': 'Configure an exact HTTPS or literal loopback address first.',
  'credential-required': 'Configure a valid client key from the resource owner.',
  'authentication-failed': 'The host rejected this key. Ask its owner for a current scoped client key.',
  'access-denied': 'This key has no access to the resource catalog.',
  'resources-unavailable': 'This host does not expose /v1/resources.',
  'allowance-exhausted': 'The host allowance or request limit was reached. Review it with the owner.',
  'redirect-rejected': 'The host redirected this request. Review and configure the exact destination.',
  'response-too-large': 'The catalog exceeded the 64 KiB response limit.',
  'invalid-catalog': 'The host returned an unsupported or invalid resource catalog.',
  'http-error': 'The host rejected the resource request.',
  'timed-out': 'The check exceeded five seconds. No automatic retry was made.',
  'cancelled': 'Check stopped. An already accepted request may still count toward the host allowance.',
  'connection-failed': 'Could not connect securely. Check the exact address, host availability and TLS certificate.'
};

function register(context) {
  let panel, pending, revision = 0, configuration = 0, settingUp = false, mutation = Promise.resolve(), lastMessage = '';
  const saved = () => context.globalState.get(KEY);
  const update = (message, result = null, target = panel) => {
    if (target && panel === target) {
      if (message !== undefined) lastMessage = message;
      target.webview.postMessage({endpoint: saved()?.endpoint || '', busy: !!pending, settingUp, message: lastMessage, result});
    }
  };
  const stop = () => { revision++; pending?.abort(); pending = undefined; };
  const serial = action => { const next = mutation.then(action); mutation = next.catch(() => {}); return next; };
  context.subscriptions.push({dispose() { configuration++; stop(); }});
  if (context.secrets.onDidChange) context.subscriptions.push(context.secrets.onDidChange(event => {
    if (event.key === saved()?.tokenRef) { stop(); update('Credential changed or revoked. Check again only after reviewing access.'); }
  }));
  context.subscriptions.push(vscode.commands.registerCommand('meshlit.resourceSharing', () => {
    if (panel) { panel.reveal(); return; }
    lastMessage = '';
    const view = panel = vscode.window.createWebviewPanel('meshlit.resourceSharing', 'Resource sharing', vscode.ViewColumn.One,
      {enableScripts: true, localResourceRoots: []});
    view.webview.html = render(); context.subscriptions.push(view);
    view.onDidDispose(() => { configuration++; stop(); if (panel === view) panel = undefined; });
    view.webview.onDidReceiveMessage(async message => {
      if (panel !== view) return;
      try {
        if (message?.op === 'ready') { update(); return; }
        if (message?.op === 'stop') { stop(); update(messages.cancelled); return; }
        if (message?.op === 'clear') {
          configuration++; stop();
          await serial(async () => {
            const previous = saved();
            try { await context.globalState.update(KEY, undefined); }
            finally { if (previous?.tokenRef) await context.secrets.delete(previous.tokenRef); }
          });
          update('Saved endpoint and local credential cleared. Revoke the key on the owner host to end its remote access.', null, view); return;
        }
        if (message?.op === 'setup') {
          if (settingUp) return;
          stop(); settingUp = true; const generation = ++configuration;
          const current = () => panel === view && generation === configuration && vscode.workspace.isTrusted;
          update('Configure the exact destination. Saving does not contact or start a host.');
          try {
            if (!vscode.workspace.isTrusted) throw Error('Trust this workspace first.');
            const input = await vscode.window.showInputBox({title: 'Resource host', prompt: 'HTTPS base URL or exact /v1/resources endpoint. HTTP is allowed only on literal loopback.', value: saved()?.endpoint || '', ignoreFocusOut: true,
              validateInput(value) { try { resourceUrl(value); } catch (error) { return error.message; } }});
            if (input === undefined || !current()) return;
            const endpoint = resourceUrl(input);
            const token = await vscode.window.showInputBox({title: 'Resource client key', prompt: 'Use a scoped key issued by this exact host. Stored only in the system secret store.', password: true, ignoreFocusOut: true,
              validateInput(value) { return validToken(value) ? undefined : 'Enter a nonempty key without spaces or control characters.'; }});
            if (token === undefined || !current()) return;
            if (!validToken(token)) throw Error('Invalid client key.');
            await serial(async () => {
              if (!current()) return;
              const previous = saved(), tokenRef = 'meshlit.resourceSharing.key.' + crypto.randomUUID();
              await context.secrets.store(tokenRef, token);
              try {
                if (!current()) { await context.secrets.delete(tokenRef); return; }
                await context.globalState.update(KEY, {endpoint, tokenRef});
              } catch (error) { await context.secrets.delete(tokenRef); throw error; }
              if (previous?.tokenRef) await context.secrets.delete(previous.tokenRef);
            });
            if (current()) update('Destination saved. Check resources sends one authenticated catalog request; it does not start hosting.');
          } finally { settingUp = false; update(undefined, null, view); }
          return;
        }
        if (message?.op === 'check') {
          if (pending || settingUp) return;
          if (!vscode.workspace.isTrusted) throw Error('Trust this workspace first.');
          const before = revision;
          await mutation;
          if (before !== revision || panel !== view || pending || settingUp) return;
          const config = saved(); if (!config?.endpoint || !config?.tokenRef) throw Error('Configure a resource host and client key first.');
          stop(); const generation = revision, abort = pending = new AbortController();
          update('Checking resources. This request consumes the host client allowance.');
          const token = await context.secrets.get(config.tokenRef);
          if (generation !== revision || panel !== view) return;
          const result = await inspectResources(config.endpoint, token, {signal: abort.signal});
          if (generation !== revision || panel !== view) return;
          if (!vscode.workspace.isTrusted) { stop(); update('Workspace trust changed. Check stopped.', null, view); return; }
          pending = undefined; update(messages[result.status], result, view); return;
        }
        if (message?.op === 'help') {
          const file = vscode.Uri.joinPath(context.extensionUri, 'OPEN_RESOURCE_NETWORK.md');
          await vscode.commands.executeCommand('markdown.showPreview', file);
        }
      } catch { stop(); update('The action could not complete. Check the saved destination, client key and local storage. No automatic retry was made.', null, view); }
    });
  }));
}

function render() {
  const nonce = crypto.randomBytes(24).toString('hex');
  return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'nonce-${nonce}';script-src 'nonce-${nonce}';form-action 'none';base-uri 'none'"><style nonce="${nonce}">
*{box-sizing:border-box}body{font:14px/1.6 var(--vscode-font-family);color:var(--vscode-foreground);background:var(--vscode-editor-background);margin:0}main{max-width:900px;padding:28px;margin:auto}h1{line-height:1.25}h2{color:var(--vscode-textLink-foreground);font-size:18px;margin:0 0 12px}section{border:1px solid var(--vscode-panel-border);border-radius:10px;background:var(--vscode-sideBar-background);padding:22px;margin:24px 0}p{max-width:75ch}button{font:inherit;cursor:pointer;padding:9px 14px;margin:4px 8px 4px 0;background:var(--vscode-button-secondaryBackground);color:var(--vscode-button-secondaryForeground);border:1px solid var(--vscode-button-border,var(--vscode-panel-border));border-radius:4px}button:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:3px}button:disabled{opacity:.6;cursor:default}.resource{padding:16px 0;border-top:1px solid var(--vscode-panel-border)}h3{font-size:15px;margin:0}.resource p{margin:6px 0}#endpoint,.resource{overflow-wrap:anywhere}#status{min-height:1.6em;color:var(--vscode-descriptionForeground)}@media(max-width:560px){main{padding:18px}section{padding:16px}}
</style></head><body><main><h1>Resource sharing</h1><p>Inspect resources offered by a host you choose. Individuals, teams and organizations use the same explicit access controls.</p>
<section><h2>Inspect resources</h2><p id="endpoint">No endpoint configured</p><p>Check reads one resource catalog and consumes the host client allowance. It does not generate text, reserve capacity or start a service.</p><button id="check" data-op="check" disabled>Check resources</button><button id="stop" data-op="stop" disabled>Stop check</button><p id="status" role="status" aria-live="polite"></p><div id="results"></div></section>
<section><h2>Setup and access</h2><p>Ask the owner for an exact HTTPS resource address and scoped client key. A reviewed HTTPS tunnel can connect LAN or internet hosts. Literal loopback HTTP is available for a local host or an owner-configured local tunnel. A compatible external host does not need the Meshlit app.</p><button id="setup" data-op="setup">Configure host and key</button><button data-op="clear">Clear local access</button><button data-op="help">Read setup guide</button><p>Keys stay in the system secret store and are never sent to this page. Clearing local access cancels this check and forgets this endpoint; revoke the key on the owner host to end remote access.</p></section>
<section><h2>Unavailable and unverified</h2><p>Configured model names are advertisements, not capacity measurements. Catalog checks do not verify generation, model quality, availability or pricing. Payments, raw compute rental, shared cache/storage, bandwidth resale, sensor markets and automatic WAN layer scheduling are unavailable here.</p><p>Meshlit does not provide automatic NAT traversal or a public hosting service. Host permissions, network setup and request limits remain under the owner's control.</p></section>
</main><script nonce="${nonce}">const api=acquireVsCodeApi();document.querySelectorAll('[data-op]').forEach(button=>button.onclick=()=>api.postMessage({op:button.dataset.op}));window.addEventListener('message',({data})=>{document.getElementById('endpoint').textContent=data.endpoint||'No endpoint configured';document.getElementById('check').disabled=!data.endpoint||data.busy||data.settingUp;document.getElementById('stop').disabled=!data.busy;document.getElementById('setup').disabled=data.settingUp;document.getElementById('status').textContent=data.message||'';const results=document.getElementById('results');results.replaceChildren();if(data.result?.status==='catalog-received'){const stamp=document.createElement('p');stamp.textContent='Catalog checked '+data.result.checkedAt+' · generation and capacity unverified';results.append(stamp);if(!data.result.resources.length){const empty=document.createElement('p');empty.textContent='No text resources were advertised for this key.';results.append(empty);}for(const resource of data.result.resources){const row=document.createElement('div');row.className='resource';const title=document.createElement('h3');title.textContent=resource.model;const detail=document.createElement('p');detail.textContent='Text inference · host state: '+resource.state+' · output limit: '+resource.maxOutputTokens;const endpoint=document.createElement('p');endpoint.textContent='Advertised path: '+resource.endpoint+' · price unknown · measured capacity unknown';row.append(title,detail,endpoint);results.append(row);}}});api.postMessage({op:'ready'});</script></body></html>`;
}
module.exports = {register, render, KEY};
