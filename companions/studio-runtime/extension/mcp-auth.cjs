const TOKEN = /^omb_sess_[A-Za-z0-9_-]{16,512}$/;
const REF = /^meshlit\.mcp\.openmausbot\.[0-9a-f-]{36}$/;

function validOpenMausToken(token) { return typeof token === 'string' && TOKEN.test(token); }
function configuredConnector(input) {
  if (!input || typeof input !== 'object' || !/^[A-Za-z0-9_-]{1,64}$/.test(input.name)) throw Error('Invalid MCP connector');
  if (input.env !== undefined) throw Error('Saved MCP environment values are not supported; use a bound secret reference');
  let output = {name: input.name, type: input.type};
  if (input.type === 'stdio') {
    if (typeof input.command !== 'string' || !require('node:path').isAbsolute(input.command)) throw Error('MCP requires an absolute installed executable');
    if (!Array.isArray(input.args) || input.args.length > 32 || input.args.some(item => typeof item !== 'string' || item.length > 2048 || item.includes('\0'))) throw Error('Invalid MCP arguments');
    output = {...output, command: input.command, args: [...input.args]};
  } else if (input.type === 'http') {
    let url; try { url = new URL(input.url); } catch { throw Error('Invalid MCP URL'); }
    if (url.username || url.password || url.search || url.hash || !(url.protocol === 'https:' || url.protocol === 'http:' && ['127.0.0.1', '[::1]'].includes(url.hostname))) throw Error('Unsupported MCP URL');
    output.url = url.toString();
  } else throw Error('Unsupported MCP transport');
  if (input.auth !== undefined) {
    const auth = input.auth;
    if (output.type !== 'stdio' || output.args.length !== 1 || output.args[0] !== 'mcp' || !auth || auth.kind !== 'openmausbot-session' || !REF.test(auth.tokenRef) || !Number.isInteger(auth.port) || auth.port < 1024 || auth.port > 65535) throw Error('Invalid OpenMausBot credential binding');
    output.auth = {kind: 'openmausbot-session', port: auth.port, tokenRef: auth.tokenRef};
  }
  return output;
}

// Call only after native user selection/approval. The returned values are kept
// in process memory and private host IPC, never in webview state or settings.
async function resolveConnector(context, input) {
  const config = configuredConnector(input);
  if (!config.auth) return config;
  let token;
  try { token = await context.secrets.get(config.auth.tokenRef); }
  catch { throw Error('Could not read the saved OpenMausBot pairing secret'); }
  if (!validOpenMausToken(token)) throw Error('The OpenMausBot pairing secret is missing or invalid; pair the connector again');
  const {auth, ...output} = config;
  // The CLI checks OPENMAUSBOT_URL before OMB_PORT. Pin both so an inherited
  // environment value cannot redirect this session credential elsewhere.
  return {...output, auth: {kind: auth.kind, port: auth.port}, env: {OMB_PORT: String(auth.port), OPENMAUSBOT_URL: `http://127.0.0.1:${auth.port}`, OPENMAUSBOT_TOKEN: token}};
}

function connectorPreview(input) {
  const config = configuredConnector(input);
  return config.name + ': ' + (config.url || config.command + ' ' + JSON.stringify(config.args)) + (config.auth ? ` · paired OpenMausBot on 127.0.0.1:${config.auth.port} · saved secret` : '');
}

module.exports = {validOpenMausToken, configuredConnector, resolveConnector, connectorPreview};
