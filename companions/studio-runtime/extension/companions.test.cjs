const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const actual = require('./companion-client.cjs');

function fixture() {
  const values = new Map(), secrets = new Map(), commands = new Map(), sent = [], opened = [], executed = [], inputs = [];
  let receive, dispose, probes = 0, name = 'memory-local', picked;
  const webview = {postMessage: value => sent.push(value), onDidReceiveMessage: fn => { receive = fn; }};
  const vscode = {ViewColumn: {One: 1}, Uri: {parse: value => value}, env: {openExternal: async value => { opened.push(value); return true; }}, commands: {registerCommand: (id, fn) => { commands.set(id, fn); return {dispose() {}}; }, executeCommand: async id => executed.push(id)}, window: {createWebviewPanel: () => ({webview, onDidDispose: fn => { dispose = fn; }, reveal() {}}), showInputBox: async () => inputs.length ? inputs.shift() : name, showOpenDialog: async () => [{fsPath: __filename}], showQuickPick: async items => picked ? items[0] : undefined, showInformationMessage: async () => undefined}};
  const module = {exports: {}};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'companions.cjs'), 'utf8'), {module, URL, AbortController, require: id => id === 'vscode' ? vscode : id === './companion-client.cjs' ? {...actual, checkCompanion: async () => { probes++; return {status: 'endpoint-responding', generationVerified: false}; }} : id === 'node:os' ? {userInfo: () => ({uid: 501})} : require(id)});
  module.exports.register({subscriptions: [], secrets: {store: async (key, value) => secrets.set(key, value), delete: async key => secrets.delete(key)}, globalState: {get: (key, fallback) => values.has(key) ? values.get(key) : fallback, update: async (key, value) => values.set(key, value)}});
  commands.get('meshlit.companions')();
  return {values, secrets, inputs, sent, opened, executed, webview, receive: value => receive(value), dispose: () => dispose(), probes: () => probes, setName: value => { name = value; }, pickFirst: () => { picked = true; }, key: module.exports.KEY};
}

test('dashboard saves locally; only an explicit check or open contacts the companion', async () => {
  const f = fixture(); await f.receive({op: 'ready'});
  assert.equal(f.sent.at(-1).rows[0].config.apiOrigin, 'http://127.0.0.1:37701');
  await f.receive({op: 'save', id: 'openbot', config: actual.defaults('openbot')});
  assert.equal(f.probes(), 0); assert.equal(f.opened.length, 0);
  await f.receive({op: 'check', id: 'openbot'}); assert.equal(f.probes(), 1);
  await f.receive({op: 'open', id: 'openbot'}); assert.deepEqual(f.opened, ['http://127.0.0.1:3010/']);
  await f.receive({op: 'remove', id: 'openbot'}); assert.equal(f.values.get(f.key).openbot, undefined);
  await f.receive({op: 'open', id: 'openbot'}); assert.equal(f.opened.length, 1); assert.match(f.sent.at(-1).message, /Save/);
});

test('invalid endpoint cannot overwrite a valid configuration or open a remote destination', async () => {
  const f = fixture(); const config = actual.defaults('openmausbot');
  await f.receive({op: 'save', id: 'openmausbot', config});
  await f.receive({op: 'save', id: 'openmausbot', config: {...config, uiOrigin: 'https://attacker.test'}});
  assert.match(f.sent.at(-1).message, /loopback/);
  assert.equal(f.values.get(f.key).openmausbot.uiOrigin, config.uiOrigin);
  await f.receive({op: 'open', id: 'openmausbot'}); assert.equal(f.opened[0], config.uiOrigin + '/');
  await f.receive({op: 'source', id: 'attacker'}); assert.equal(f.opened.length, 1);
});

test('memory MCP joins the existing disabled registry and refuses duplicate names', async () => {
  const f = fixture(); await f.receive({op: 'save', id: 'claude-mem', config: actual.defaults('claude-mem', 501)});
  await f.receive({op: 'mcp', id: 'claude-mem'});
  const registry = f.values.get('meshlit.mcp');
  assert.equal(registry.length, 1); assert.equal(registry[0].url, 'http://127.0.0.1:37790/mcp'); assert.equal(registry[0].enabled, undefined);
  assert.equal(f.probes(), 0); assert.equal(f.executed.length, 0);
  await f.receive({op: 'mcp', id: 'claude-mem'}); assert.equal(f.values.get('meshlit.mcp').length, 1); assert.match(f.sent.at(-1).message, /already exists/);
  await f.receive({op: 'remove', id: 'claude-mem'}); assert.equal(f.values.get('meshlit.mcp').length, 1);
});

test('webview has no external assets, strong CSP and keyboard-labelled native controls', () => {
  const f = fixture(); const html = f.webview.html;
  assert.match(html, /default-src 'none'/); assert.match(html, /form-action 'none'/);
  assert(!html.includes('unsafe-inline')); assert(!/<iframe|<img|<link/i.test(html));
  assert.match(html, /<label>API origin<input/); assert.match(html, /aria-live="polite"/);
  assert.match(html, /:focus-visible/); assert.match(html, /No native AG-UI adapter/);
});

test('paired connector stores only a secret reference, and removal closes chats before erasing it', async () => {
  const f = fixture(), token = 'omb_sess_fixture_000000000000000000000000';
  await f.receive({op: 'save', id: 'openmausbot', config: actual.defaults('openmausbot')});
  f.inputs.push('paired-maus', token); await f.receive({op: 'mcp', id: 'openmausbot'});
  const connector = f.values.get('meshlit.mcp')[0];
  assert.equal(connector.auth.port, 8799); assert.equal(f.secrets.get(connector.auth.tokenRef), token);
  assert(!JSON.stringify([...f.values]).includes(token)); assert(!JSON.stringify(f.sent).includes(token));
  assert.equal(f.probes(), 0); assert.equal(f.executed.length, 0);
  f.pickFirst(); await f.receive({op: 'removeMcp', id: 'openmausbot'});
  assert(f.executed.includes('meshlit.revokeMcp')); assert.equal(f.secrets.size, 0); assert.equal(f.values.get('meshlit.mcp').length, 0);
});
