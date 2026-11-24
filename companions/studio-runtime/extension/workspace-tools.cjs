const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const digest = text => crypto.createHash('sha256').update(text).digest('hex');
async function scoped(root, relative) {
  if (typeof relative !== 'string' || relative.length > 1024 || path.isAbsolute(relative) || relative.includes('\0')) throw Error('Use a workspace-relative path');
  const parts = relative.split(/[\\/]/);
  if (parts.some(p => p === '..' || (p.startsWith('.') && p !== '.')) || /(?:secret|credential|id_rsa)|\.(?:pem|key|p12)$/i.test(relative)) throw Error('Private or traversing paths are blocked');
  const base = await fs.realpath(root);
  let current = base;
  for (const part of parts.filter(p => p && p !== '.')) {
    current = path.join(current, part);
    if ((await fs.lstat(current)).isSymbolicLink()) throw Error('Symbolic links are not allowed');
  }
  const real = await fs.realpath(current);
  if (real !== base && !real.startsWith(base + path.sep)) throw Error('Outside workspace');
  return real;
}
async function read(root, relative) {
  const file = await scoped(root, relative);
  const stat = await fs.stat(file);
  if (!stat.isFile() || stat.size > 32768) throw Error('Choose a text file smaller than 32 KiB');
  const bytes = await fs.readFile(file);
  const text = new TextDecoder('utf-8', {fatal:true}).decode(bytes);
  if (text.includes('\0')) throw Error('Binary file');
  return {file, text, hash:digest(text)};
}
async function listing(root, relative) {
  const folder = await scoped(root, relative);
  const entries = await fs.readdir(folder, {withFileTypes:true});
  return entries.filter(e => !e.name.startsWith('.') && !e.isSymbolicLink()).slice(0,200)
    .map(e => e.name + (e.isDirectory() ? '/' : '')).join('\n');
}
function replacement(text, old, next) {
  if (typeof old !== 'string' || !old || typeof next !== 'string' || next.length > 32768) throw Error('Invalid edit');
  const index = text.indexOf(old);
  if (index < 0 || text.indexOf(old,index+old.length) >= 0) throw Error('The old text must match exactly once');
  const result = text.slice(0,index)+next+text.slice(index+old.length);
  if (Buffer.byteLength(result) > 32768) throw Error('Result exceeds 32 KiB');
  return result;
}
module.exports = {scoped, read, listing, replacement, digest};
