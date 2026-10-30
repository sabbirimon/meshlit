const test = require('node:test'), assert = require('node:assert/strict');
const {resourceUrl, inspectResources} = require('./resource-sharing-client.cjs');
const token = 'resource_test_key_1234567890';
const document = {schema: 'meshlit.resources.v1', resources: [{kind: 'text_inference', model: 'owner/model', endpoint: '/v1/chat/completions', state: 'configured', max_output_tokens: 256, price: null, measured_capacity: null}], commercial: {payments: false}};
const response = value => new Response(JSON.stringify(value), {headers: {'content-type': 'application/json'}});

test('public HTTPS and exact literal loopback endpoints are accepted without credential ambiguity', () => {
  assert.equal(resourceUrl('https://owner.example/mesh'), 'https://owner.example/mesh/v1/resources');
  assert.equal(resourceUrl('https://owner.example/mesh/v1/resources/'), 'https://owner.example/mesh/v1/resources');
  assert.equal(resourceUrl('http://127.0.0.1:9191'), 'http://127.0.0.1:9191/v1/resources');
  assert.equal(resourceUrl('http://[::1]:9191'), 'http://[::1]:9191/v1/resources');
  for (const value of ['http://localhost:9191', 'http://127.1', 'http://2130706433', 'http://192.168.1.5', 'http://owner.example', 'https://key@owner.example', 'https://owner.example?token=secret', 'https://owner.example#fragment', 'https://owner.example\\@other.test', 'file:///tmp/secret']) assert.throws(() => resourceUrl(value));
});

test('explicit check uses one scoped GET and returns only bounded advertised data', async () => {
  let calls = 0;
  const result = await inspectResources('https://owner.example', token, {request: async (url, options) => {
    calls++; assert.equal(url, 'https://owner.example/v1/resources'); assert.equal(options.method, 'GET');
    assert.equal(options.headers.Authorization, 'Bearer ' + token); assert.equal(options.redirect, 'manual');
    return response({...document, debug: token});
  }});
  assert.equal(calls, 1); assert.equal(result.status, 'catalog-received');
  assert.equal(result.resources[0].model, 'owner/model'); assert.equal(result.capacityVerified, false);
  assert.equal(result.generationVerified, false); assert.equal(result.payments, false);
  assert(!JSON.stringify(result).includes(token));
});

test('redirects, rejected credentials and malformed catalogs never become resource success', async () => {
  for (const [code, status] of [[302, 'redirect-rejected'], [401, 'authentication-failed'], [403, 'access-denied'], [404, 'resources-unavailable'], [429, 'allowance-exhausted']]) {
    assert.equal((await inspectResources('https://owner.example', token, {request: async () => new Response(token, {status: code})})).status, status);
  }
  for (const value of [{...document, schema: 'other'}, {...document, commercial: {payments: true}}, {...document, resources: [{...document.resources[0], model: token}]}, {...document, resources: [{...document.resources[0], price: 4}]}]) {
    assert.equal((await inspectResources('https://owner.example', token, {request: async () => response(value)})).status, 'invalid-catalog');
  }
});

test('response size, full-body deadline and cancellation bound the request', async () => {
  assert.equal((await inspectResources('https://owner.example', token, {request: async () => response({padding: 'x'.repeat(65536)})})).status, 'response-too-large');
  let requestSignal;
  const slow = async (_url, options) => { requestSignal = options.signal; return new Response(new ReadableStream({start() {}}), {headers: {'content-type': 'application/json'}}); };
  assert.equal((await inspectResources('https://owner.example', token, {request: slow, timeoutMs: 5})).status, 'timed-out');
  assert.equal(requestSignal.aborted, true);
  const stop = new AbortController(); stop.abort(); let calls = 0;
  assert.equal((await inspectResources('https://owner.example', token, {signal: stop.signal, request: async () => { calls++; }})).status, 'cancelled');
  assert.equal(calls, 0);
});
