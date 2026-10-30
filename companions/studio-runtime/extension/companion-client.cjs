const sources = require('./companion-sources.lock.json').sources;
const LIMIT = 64 * 1024;

function sourceFor(id) {
  const source = sources.find(item => item.id === id);
  if (!source) throw Error('Unknown companion');
  return source;
}

// Literal addresses avoid DNS/rebinding and make the destination visible. No
// credentials, remote hosts, URL queries or custom API paths are accepted.
function localUrl(value, allowedPath = '/') {
  if (typeof value !== 'string' || value.length > 2048) throw Error('Enter a local HTTP(S) address');
  const input = value.trim();
  if (!/^https?:\/\/(?:127\.0\.0\.1|\[::1\])(?::[0-9]{1,5})?(?:\/|$)/.test(input)) {
    throw Error('Use the literal loopback address 127.0.0.1 or [::1]');
  }
  let url;
  try { url = new URL(input); } catch { throw Error('Invalid local address'); }
  if (url.username || url.password || url.search || url.hash || !['127.0.0.1', '[::1]'].includes(url.hostname)) {
    throw Error('Local addresses must not contain credentials, queries or fragments');
  }
  const path = url.pathname.replace(/\/$/, '') || '/';
  if (path !== allowedPath) throw Error(allowedPath === '/' ? 'Enter the origin only, without an API path' : 'Use the /mcp Streamable HTTP endpoint');
  url.pathname = allowedPath;
  return allowedPath === '/' ? url.origin : url.toString();
}

function defaults(id, uid) {
  sourceFor(id);
  const safeUid = Number.isInteger(uid) && uid >= 0 ? uid : 77;
  if (id === 'claude-mem') return {apiOrigin: `http://127.0.0.1:${37700 + safeUid % 100}`, uiOrigin: `http://127.0.0.1:${37700 + safeUid % 100}`, mcpUrl: 'http://127.0.0.1:37790/mcp'};
  if (id === 'openmausbot') return {apiOrigin: 'http://127.0.0.1:8799', uiOrigin: 'http://127.0.0.1:8799'};
  return {apiOrigin: 'http://127.0.0.1:3001', uiOrigin: 'http://127.0.0.1:3010'};
}

function validateConfiguration(id, input) {
  sourceFor(id);
  if (!input || typeof input !== 'object') throw Error('Missing companion addresses');
  const config = {apiOrigin: localUrl(input.apiOrigin), uiOrigin: localUrl(input.uiOrigin)};
  if (id === 'claude-mem' && input.mcpUrl) config.mcpUrl = localUrl(input.mcpUrl, '/mcp');
  return config;
}

function uiUrl(id, config) {
  return validateConfiguration(id, config).uiOrigin + sourceFor(id).uiPath;
}

function sourceUrl(id, documentation = false) {
  const source = sourceFor(id);
  return source.repository + (documentation ? '/blob/' + source.revision + '/' + source.documentationPath : '/tree/' + source.revision);
}

function memoryConnector(config, name = 'claude-mem') {
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(name)) throw Error('Use letters, digits, hyphens and underscores for the connector name');
  const validated = validateConfiguration('claude-mem', config);
  if (!validated.mcpUrl) throw Error('Save a Claude-Mem MCP HTTP endpoint first');
  // Existing connector selection and per-tool review remain authoritative. No
  // worker admin endpoint, provider secret, or capture hook is registered here.
  return {name, type: 'http', url: validated.mcpUrl};
}

async function checkCompanion(id, config, {request = fetch, signal, timeoutMs = 5000} = {}) {
  const checkedAt = new Date().toISOString();
  const result = (status, extra = {}) => ({status, checkedAt, identityVerified: false, generationVerified: false, ...extra});
  let source, address;
  try { source = sourceFor(id); address = validateConfiguration(id, config).apiOrigin + source.healthPath; }
  catch { return result('invalid-address'); }
  const abort = new AbortController();
  let expired = false;
  const cancel = () => abort.abort();
  const timer = setTimeout(() => { expired = true; abort.abort(); }, Math.min(15000, Math.max(1, timeoutMs)));
  signal?.addEventListener('abort', cancel, {once: true});
  if (signal?.aborted) abort.abort();
  try {
    if (abort.signal.aborted) return result('cancelled');
    const response = await request(address, {method: 'GET', headers: {Accept: 'application/json'}, credentials: 'omit', redirect: 'error', signal: abort.signal});
    if (response.redirected || response.status >= 300 && response.status < 400) {
      await response.body?.cancel(); return result('redirect-rejected');
    }
    if (!response.ok && !(id === 'claude-mem' && response.status === 503)) {
      await response.body?.cancel();
      return result(response.status === 401 || response.status === 403 ? 'authentication-required' : 'http-error', {httpStatus: response.status});
    }
    const length = response.headers.get('content-length');
    if (length !== null && Number(length) > LIMIT) { await response.body?.cancel(); return result('response-too-large'); }
    if (!/^application\/(?:json|[a-z0-9.+-]+\+json)(?:\s*;|$)/i.test(response.headers.get('content-type') || '')) {
      await response.body?.cancel(); return result('unexpected-response');
    }
    const reader = response.body?.getReader();
    if (!reader) return result('unexpected-response');
    const chunks = []; let size = 0;
    try {
      while (true) {
        const {done, value} = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > LIMIT) return result('response-too-large');
        chunks.push(Buffer.from(value));
      }
    } finally { await reader.cancel(); }
    if (abort.signal.aborted) return result(expired ? 'timed-out' : 'cancelled');
    let data;
    try { data = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { return result('unexpected-response'); }
    if (!data || typeof data !== 'object' || Array.isArray(data)) return result('unexpected-response');
    if (id === 'openmausbot') {
      if (data.app !== 'openmausbot' || !Number.isInteger(data.pid) || data.pid <= 0 || typeof data.static !== 'boolean') return result('identity-unverified');
      return result('service-reachable', {identityVerified: true, servesUi: data.static});
    }
    if (id === 'claude-mem') {
      if (typeof data.version !== 'string' || !/^[A-Za-z0-9._+-]{1,64}$/.test(data.version) || typeof data.initialized !== 'boolean' || typeof data.mcpReady !== 'boolean' || !['ok', 'degraded'].includes(data.status)) return result('identity-unverified');
      // This is a recognizable protocol shape, not cryptographic attestation.
      if (!response.ok || data.status === 'degraded') return result('worker-degraded', {identityVerified: true});
      return result(data.initialized && data.mcpReady ? 'worker-ready' : 'worker-starting', {identityVerified: true, version: data.version, initialized: data.initialized, mcpReady: data.mcpReady});
    }
    return data.status === 'ok' ? result('endpoint-responding') : result('unexpected-response');
  } catch (error) {
    return result(expired || error?.name === 'TimeoutError' ? 'timed-out' : signal?.aborted ? 'cancelled' : 'connection-failed');
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', cancel);
  }
}

module.exports = {sources, defaults, localUrl, validateConfiguration, uiUrl, sourceUrl, memoryConnector, checkCompanion};
