const test = require('node:test');
const assert = require('node:assert/strict');
const {sources, defaults, localUrl, validateConfiguration, uiUrl, sourceUrl, memoryConnector, checkCompanion} = require('./companion-client.cjs');
const json = (body, status = 200) => new Response(JSON.stringify(body), {status, headers: {'content-type': 'application/json'}});
const worker = {status: 'ok', version: '1.2.3', initialized: true, mcpReady: true, secret: 'never-forward-this'};

test('companion destinations only accept explicit literal loopback origins', () => {
  assert.equal(localUrl('http://127.0.0.1:8799/'), 'http://127.0.0.1:8799');
  assert.equal(localUrl('https://[::1]:8443'), 'https://[::1]:8443');
  for (const value of ['http://localhost:8799', 'http://127.0.0.2:8799', 'http://2130706433', 'http://0x7f000001', 'https://example.com', 'http://127.0.0.1.evil.test', 'http://user:secret@127.0.0.1:8799', 'http://127.0.0.1:8799?token=secret', 'http://127.0.0.1:8799/#token', 'file:///tmp/app', 'http://127.0.0.1:8799/api']) assert.throws(() => localUrl(value), value);
  assert.throws(() => localUrl('http://127.0.0.1:37790/anything', '/mcp'));
  assert.equal(defaults('claude-mem', 501).apiOrigin, 'http://127.0.0.1:37701');
  assert.equal(defaults('claude-mem', -1).apiOrigin, 'http://127.0.0.1:37777');
});

test('health is read-only, bounded evidence and never forwards unknown data', async () => {
  const config = defaults('claude-mem', 501);
  const result = await checkCompanion('claude-mem', config, {request: async (url, options) => {
    assert.equal(url, config.apiOrigin + '/api/health'); assert.equal(options.method, 'GET');
    assert.equal(options.redirect, 'error'); assert.equal(options.credentials, 'omit');
    assert.deepEqual(options.headers, {Accept: 'application/json'});
    return json(worker);
  }});
  assert.equal(result.status, 'worker-ready'); assert.equal(result.generationVerified, false);
  assert.equal(result.identityVerified, true); assert(!JSON.stringify(result).includes('never-forward-this'));
  assert.equal((await checkCompanion('claude-mem', config, {request: async () => json({...worker, initialized: false})})).status, 'worker-starting');
  assert.equal((await checkCompanion('claude-mem', config, {request: async () => json({...worker, status: 'degraded'}, 503)})).status, 'worker-degraded');
});

test('service-specific identity checks distinguish generic responses and model readiness', async () => {
  const maus = await checkCompanion('openmausbot', defaults('openmausbot'), {request: async () => json({app: 'openmausbot', pid: 12, static: false, token: 'never-forward'})});
  assert.equal(maus.status, 'service-reachable'); assert.equal(maus.servesUi, false); assert.equal(maus.generationVerified, false);
  const bot = await checkCompanion('openbot', defaults('openbot'), {request: async () => json({status: 'ok'})});
  assert.equal(bot.status, 'endpoint-responding'); assert.equal(bot.identityVerified, false);
  for (const id of ['claude-mem', 'openmausbot']) assert.equal((await checkCompanion(id, defaults(id), {request: async () => json({status: 'ok'})})).status, 'identity-unverified');
});

test('invalid addresses, HTTP failures, redirects and oversized responses stay unavailable', async () => {
  let calls = 0;
  assert.equal((await checkCompanion('openbot', {apiOrigin: 'https://remote.test', uiOrigin: 'https://remote.test'}, {request: async () => { calls++; }})).status, 'invalid-address');
  assert.equal(calls, 0);
  for (const [response, status] of [[json({}, 401), 'authentication-required'], [json({}, 403), 'authentication-required'], [json({}, 500), 'http-error'], [new Response('', {status: 302, headers: {location: 'https://remote.test'}}), 'redirect-rejected'], [new Response('<html>login</html>'), 'unexpected-response'], [json({text: 'x'.repeat(65537)}), 'response-too-large'], [new Response('{}', {headers: {'content-type': 'application/json', 'content-length': '65537'}}), 'response-too-large']]) {
    assert.equal((await checkCompanion('openbot', defaults('openbot'), {request: async () => response})).status, status);
  }
});

test('deadline and owner cancellation abort outstanding HTTP', async () => {
  const pending = async (_url, options) => new Promise((_resolve, reject) => options.signal.addEventListener('abort', () => reject(new DOMException('cancelled', 'AbortError')), {once: true}));
  assert.equal((await checkCompanion('openbot', defaults('openbot'), {request: pending, timeoutMs: 10})).status, 'timed-out');
  const abort = new AbortController();
  const task = checkCompanion('openbot', defaults('openbot'), {request: pending, signal: abort.signal});
  abort.abort(); assert.equal((await task).status, 'cancelled');
  let calls = 0;
  assert.equal((await checkCompanion('openbot', defaults('openbot'), {request: async () => { calls++; }, signal: abort.signal})).status, 'cancelled');
  assert.equal(calls, 0);
});

test('MCP registration uses separate transport, no secrets or inferred grants', () => {
  const config = defaults('claude-mem', 501);
  assert.deepEqual(memoryConnector(config), {name: 'claude-mem', type: 'http', url: 'http://127.0.0.1:37790/mcp'});
  assert.throws(() => memoryConnector(config, 'bad name'));
  assert.throws(() => memoryConnector({...config, mcpUrl: ''}));
  assert.throws(() => memoryConnector({...config, mcpUrl: 'https://remote.test/mcp'}));
  assert.deepEqual(validateConfiguration('openbot', {...defaults('openbot'), token: 'excluded', enabled: true}), defaults('openbot'));
});

test('workspace and documentation destinations are pinned and validated', () => {
  assert.equal(uiUrl('claude-mem', defaults('claude-mem', 501)), 'http://127.0.0.1:37701/viewer');
  assert.equal(uiUrl('openbot', defaults('openbot')), 'http://127.0.0.1:3010/');
  for (const source of sources) {
    assert.match(source.revision, /^[0-9a-f]{40}$/);
    assert.equal(new URL(sourceUrl(source.id)).hostname, 'github.com');
    assert(sourceUrl(source.id, true).includes(source.revision));
  }
  assert.throws(() => sourceUrl('../../bad'));
});
