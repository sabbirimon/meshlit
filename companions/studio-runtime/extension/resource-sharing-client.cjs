const LIMIT = 64 * 1024;

function resourceUrl(value) {
  if (typeof value !== 'string' || value.length > 2048 || /[\u0000-\u0020\\]/.test(value)) throw Error('Enter an HTTPS address without spaces or credentials.');
  let url;
  try { url = new URL(value); } catch { throw Error('Enter a valid resource address.'); }
  const literalLoopback = /^http:\/\/(?:127\.0\.0\.1|\[::1\])(?::[0-9]{1,5})?(?:\/|$)/.test(value);
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && literalLoopback)) throw Error('Use HTTPS, or literal loopback HTTP at 127.0.0.1 or [::1].');
  if (url.username || url.password || url.search || url.hash) throw Error('The address must not contain credentials, a query or a fragment.');
  const path = url.pathname.replace(/\/+$/, '');
  url.pathname = path.endsWith('/v1/resources') ? path : path + '/v1/resources';
  return url.toString();
}

function validToken(token) { return typeof token === 'string' && /^[\x21-\x7e]{1,4096}$/.test(token); }
function catalog(data, token) {
  if (!data || data.schema !== 'meshlit.resources.v1' || !Array.isArray(data.resources) || data.resources.length > 128 || data.commercial?.payments !== false) throw Error('unsupported-catalog');
  const text = (value, max) => {
    if (typeof value !== 'string' || value.length > max || value.includes(token)) throw Error('invalid-catalog');
    return value.replace(/[\u0000-\u001f\u007f\u202a-\u202e\u2066-\u2069]/g, ' ');
  };
  const resources = data.resources.map(item => {
    if (!item || item.kind !== 'text_inference' || item.price !== null || item.measured_capacity !== null ||
        !Number.isSafeInteger(item.max_output_tokens) || item.max_output_tokens < 1 || item.max_output_tokens > 1_000_000) throw Error('unsupported-resource');
    return {kind: 'text_inference', model: text(item.model, 256), endpoint: text(item.endpoint, 512),
      state: text(item.state, 64), maxOutputTokens: item.max_output_tokens, price: null, measuredCapacity: null};
  });
  return {resources, payments: false, generationVerified: false, capacityVerified: false};
}

async function inspectResources(endpoint, token, {request = fetch, signal, timeoutMs = 5000} = {}) {
  let address;
  try { address = resourceUrl(endpoint); } catch { return {status: 'invalid-address'}; }
  if (!validToken(token)) return {status: 'credential-required'};
  const abort = new AbortController();
  let expired = false, interrupt;
  const interrupted = new Promise(resolve => { interrupt = resolve; });
  const cancel = () => { abort.abort(); interrupt({status: expired ? 'timed-out' : 'cancelled'}); };
  const timer = setTimeout(() => { expired = true; cancel(); }, Math.min(5000, Math.max(1, timeoutMs)));
  signal?.addEventListener('abort', cancel, {once: true});
  if (signal?.aborted) cancel();
  const work = async () => {
    if (abort.signal.aborted) return {status: 'cancelled'};
    const response = await request(address, {method: 'GET', redirect: 'manual', credentials: 'omit', cache: 'no-store',
      headers: {Accept: 'application/json', Authorization: 'Bearer ' + token}, signal: abort.signal});
    const reject = async status => { await response.body?.cancel(); return {status}; };
    if (abort.signal.aborted) return reject(expired ? 'timed-out' : 'cancelled');
    if (response.redirected || response.status >= 300 && response.status < 400) return reject('redirect-rejected');
    if (!response.ok) return reject(({401: 'authentication-failed', 403: 'access-denied', 404: 'resources-unavailable', 429: 'allowance-exhausted'})[response.status] || 'http-error');
    if (Number(response.headers.get('content-length')) > LIMIT) return reject('response-too-large');
    if (!/^application\/json(?:\s*;|$)/i.test(response.headers.get('content-type') || '')) return reject('invalid-catalog');
    const reader = response.body?.getReader();
    if (!reader) return {status: 'invalid-catalog'};
    const cancelBody = () => { reader.cancel().catch(() => {}); };
    abort.signal.addEventListener('abort', cancelBody, {once: true});
    const chunks = []; let size = 0;
    try {
      while (true) {
        const {done, value} = await reader.read();
        if (abort.signal.aborted) return {status: expired ? 'timed-out' : 'cancelled'};
        if (done) break;
        size += value.byteLength;
        if (size > LIMIT) return {status: 'response-too-large'};
        chunks.push(Buffer.from(value));
      }
    } finally { abort.signal.removeEventListener('abort', cancelBody); await reader.cancel(); }
    try { return {status: 'catalog-received', checkedAt: new Date().toISOString(), ...catalog(JSON.parse(Buffer.concat(chunks).toString('utf8')), token)}; }
    catch { return {status: 'invalid-catalog'}; }
  };
  try { return await Promise.race([work(), interrupted]); }
  catch { return {status: expired ? 'timed-out' : signal?.aborted ? 'cancelled' : 'connection-failed'}; }
  finally { clearTimeout(timer); signal?.removeEventListener('abort', cancel); abort.abort(); }
}

module.exports = {resourceUrl, validToken, inspectResources};
