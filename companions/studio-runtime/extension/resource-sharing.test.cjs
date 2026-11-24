const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const actual = require('./resource-sharing-client.cjs');
const token = 'resource_test_key_1234567890';
function fixture() {
  const values = new Map(), secrets = new Map(), commands = new Map(), sent = [], inputs = [], prompts = [];
  let receive, dispose, secretChanged, resolveCheck, calls = 0, signal;
  const webview = {postMessage: value => sent.push(value), onDidReceiveMessage: fn => { receive = fn; }};
  const vscode = {ViewColumn: {One: 1}, workspace: {isTrusted: true}, Uri: {joinPath: (...parts) => parts.join('/')},
    commands: {registerCommand: (id, fn) => { commands.set(id, fn); return {dispose() {}}; }, executeCommand: async () => {}},
    window: {createWebviewPanel: () => ({webview, onDidDispose: fn => { dispose = fn; }, reveal() {}}), showInputBox: async options => { prompts.push(options); return inputs.shift(); }}};
  const module = {exports: {}};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'resource-sharing.cjs'), 'utf8'), {module, AbortController, require: id => id === 'vscode' ? vscode : id === './resource-sharing-client.cjs' ? {...actual, inspectResources: async (_url, _token, options) => { calls++; signal = options.signal; return new Promise(resolve => { resolveCheck = resolve; }); }} : require(id)});
  const context = {subscriptions: [], extensionUri: 'extension', globalState: {get: key => values.get(key), update: async (key, value) => { values.set(key, value); }},
    secrets: {store: async (key, value) => { secrets.set(key, value); }, get: async key => secrets.get(key), delete: async key => { secrets.delete(key); }, onDidChange: fn => { secretChanged = fn; return {dispose() {}}; }}};
  module.exports.register(context); commands.get('meshlit.resourceSharing')();
  return {values, secrets, sent, inputs, prompts, webview, vscode, key: module.exports.KEY, send: message => receive(message), dispose: () => dispose(), calls: () => calls, signal: () => signal,
    finish: () => resolveCheck({status: 'catalog-received', checkedAt: 'now', resources: [], payments: false}), revoke: () => secretChanged({key: values.get(module.exports.KEY).tokenRef})};
}
const flush = () => new Promise(resolve => setImmediate(resolve));
async function configure(f) { f.inputs.push('https://owner.example', token); await f.send({op: 'setup'}); }

test('setup saves only endpoint and secret reference; opening and saving never connect', async () => {
  const f = fixture(); await f.send({op: 'ready'}); await configure(f);
  assert.equal(f.calls(), 0); assert.equal(f.prompts[1].password, true);
  assert.equal(f.values.get(f.key).endpoint, 'https://owner.example/v1/resources');
  assert.equal(f.secrets.get(f.values.get(f.key).tokenRef), token);
  assert(!JSON.stringify([...f.values]).includes(token)); assert(!JSON.stringify(f.sent).includes(token));
  assert.match(f.sent.at(-1).message, /Destination saved/);
  const checking = f.send({op: 'check'}); await flush(); assert.equal(f.calls(), 1);
  f.finish(); await checking; assert.equal(f.sent.at(-1).result.status, 'catalog-received');
});

test('Stop, clear, credential revocation and disposal discard late responses', async () => {
  for (const action of ['stop', 'clear', 'revoke', 'dispose']) {
    const f = fixture(); await configure(f);
    const checking = f.send({op: 'check'}); await flush();
    if (action === 'revoke') f.revoke(); else if (action === 'dispose') f.dispose(); else await f.send({op: action});
    assert.equal(f.signal().aborted, true); f.finish(); await checking;
    assert(!f.sent.some(value => value.result?.status === 'catalog-received'));
    if (action === 'clear') { assert.equal(f.values.get(f.key), undefined); assert.equal(f.secrets.size, 0); }
  }
});

test('concurrent check clicks dispatch once and trust loss suppresses pending results', async () => {
  const f = fixture(); await configure(f);
  const one = f.send({op: 'check'}), two = f.send({op: 'check'}); await flush();
  assert.equal(f.calls(), 1); f.vscode.workspace.isTrusted = false; f.finish(); await Promise.all([one, two]);
  assert(!f.sent.some(value => value.result?.status === 'catalog-received'));
});

test('inspector uses separate bordered sections, strict CSP and text-only resource output', () => {
  const f = fixture(), html = f.webview.html;
  assert.match(html, /default-src 'none'/); assert.match(html, /form-action 'none'/);
  assert(!html.includes('unsafe-inline')); assert(!/<iframe|<img|<link/i.test(html));
  assert.match(html, /<h2>Inspect resources/); assert.match(html, /<h2>Setup and access/);
  assert.match(html, /<h2>Unavailable and unverified/); assert.match(html, /aria-live="polite"/);
  assert(!html.includes('innerHTML')); assert(!html.includes('type="password"'));
});
