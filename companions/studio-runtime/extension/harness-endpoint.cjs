// Login URLs are process credentials. Persist only endpoint, never launchUrl/token.
function parseHarnessEndpoint(value) {
  if(typeof value!=='string'||value.length>2048||/[\s\\]/.test(value))throw Error('Enter a complete Harness URL without spaces');
  if(!/^https?:\/\//.test(value))throw Error('Use a lowercase HTTP(S) scheme');
  const u=new URL(value);
  const authority=value.match(/^https?:\/\/([^/?#]+)/)?.[1];
  if(u.protocol==='http:'&&!/^(127\.0\.0\.1|\[::1\])(:[0-9]+)?$/.test(authority))throw Error('Use literal loopback');
  if(u.username||u.password||u.hash||!u.hostname||u.port==='0')throw Error('Invalid Harness address');
  if(u.protocol!=='https:' && !(u.protocol==='http:'&&['127.0.0.1','[::1]'].includes(u.hostname)))throw Error('Use HTTPS, or HTTP on literal loopback');
  // Accept only a clean application root and the upstream one-time login field.
  const rawPath=value.match(/^https?:\/\/[^/?#]+([^?#]*)/)?.[1]||'/';
  if(!/^\/(?:[A-Za-z0-9_-]+\/)*$/.test(rawPath))throw Error('Use the application root, ending in /');
  const keys=[...u.searchParams.keys()];
  if(keys.some(k=>k!=='token')||keys.length>1)throw Error('Only the Harness login token is allowed');
  const token=u.searchParams.get('token');
  if(token!==null&&!/^[A-Za-z0-9_-]{16,512}$/.test(token))throw Error('Invalid Harness login token');
  u.search='';const endpoint=u.toString();
  if(token!==null)u.searchParams.set('token',token);
  return {endpoint,origin:u.origin,launchUrl:u.toString()};
}
module.exports={parseHarnessEndpoint};
