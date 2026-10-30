const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const auth = require('./mcp-auth.cjs');
const token = 'omb_sess_fixture_000000000000000000000000';
const reference = 'meshlit.mcp.openmausbot.11111111-1111-1111-1111-111111111111';
const config = {name: 'maus', type: 'stdio', command: '/installed/openmausbot', args: ['mcp'], auth: {kind: 'openmausbot-session', port: 8799, tokenRef: reference}};

test('paired credentials resolve to a fixed loopback port with no generic environment access', async () => {
  const calls = [];
  const resolved = await auth.resolveConnector({secrets: {get: async key => { calls.push(key); return token; }}}, config);
  assert.deepEqual(calls, [reference]);
  assert.deepEqual(resolved.env, {OMB_PORT: '8799', OPENMAUSBOT_URL: 'http://127.0.0.1:8799', OPENMAUSBOT_TOKEN: token});
  assert.equal(resolved.auth.tokenRef, undefined);
  assert(!auth.connectorPreview(config).includes(token)); assert(!auth.connectorPreview(config).includes(reference));
  assert.throws(() => auth.configuredConnector({...config, env: {PATH: '/evil'}}));
  assert.throws(() => auth.configuredConnector({...config, args: ['mcp', '--remote']}));
  assert.throws(() => auth.configuredConnector({...config, auth: {...config.auth, tokenRef: 'meshlit.model.secret'}}));
  await assert.rejects(auth.resolveConnector({secrets: {get: async () => undefined}}, config), /missing/);
});

function selectionFixture() {
  let choices, approval, reads = 0; const previews = []; const registry = [config];
  const vscode = {window: {showQuickPick: async items => choices === 'all' ? items : choices, showWarningMessage: async message => { previews.push(message); return approval; }}};
  const module = {exports: {}};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'connections.cjs'), 'utf8'), {module, URL, require: id => id === 'vscode' ? vscode : id === './mcp-auth.cjs' ? auth : require(id)});
  const context = {globalState: {get: () => registry}, secrets: {get: async () => { reads++; return token; }}};
  return {choose: options => module.exports.chooseMcp(context, options), set: (selected, answer) => { choices = selected; approval = answer; }, reads: () => reads, previews, registry};
}

test('editing cancellation preserves selection, empty selection clears, denial never reads secrets', async () => {
  const f = selectionFixture();
  assert.equal(await f.choose({editing: true}), undefined);
  f.set([]); assert.equal((await f.choose({editing: true})).length, 0);
  f.set('all', undefined); assert.equal(await f.choose({editing: true}), undefined); assert.equal(f.reads(), 0);
  assert.equal((await f.choose()).length, 0);
  f.set('all', 'Start selected servers'); const selected = await f.choose({editing: true});
  assert.equal(selected[0].env.OPENMAUSBOT_TOKEN, token); assert.equal(f.reads(), 1);
  assert(!f.previews.join('\n').includes(token)); assert(!JSON.stringify(f.registry).includes(token));
});
