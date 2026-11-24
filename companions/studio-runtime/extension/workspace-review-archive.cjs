const crypto = require('node:crypto');

const MAX_BYTES = 256 * 1024;
const MAX_AGE = 7 * 24 * 60 * 60 * 1000;
const STATE = 'meshlit.workspaceReview.v1';
const secretName = id => 'meshlit.workspaceReviewKey.v1.' + id;
const AAD = Buffer.from('meshlit workspace review v1');

function keyBytes(value) {
  if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) {
    throw Error('The saved review key is unavailable. Forget the saved review before saving another.');
  }
  return Buffer.from(value, 'hex');
}

function seal(review, key) {
  const plain = Buffer.from(JSON.stringify(review));
  if (plain.length > MAX_BYTES) throw Error('This review is too large to save. The limit is 256 KiB.');
  const nonce = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', keyBytes(key), nonce);
  cipher.setAAD(AAD);
  const encrypted = Buffer.concat([cipher.update(plain), cipher.final()]);
  return Buffer.concat([nonce, cipher.getAuthTag(), encrypted]).toString('base64');
}

function unseal(encoded, key, now) {
  if (typeof encoded !== 'string' || encoded.length > Math.ceil((MAX_BYTES + 28) / 3) * 4) {
    throw Error('Saved review exceeds the size limit.');
  }
  let review;
  try {
    const bytes = Buffer.from(encoded, 'base64');
    if (bytes.length < 29 || bytes.length > MAX_BYTES + 28) throw Error();
    const cipher = crypto.createDecipheriv('aes-256-gcm', keyBytes(key), bytes.subarray(0, 12));
    cipher.setAAD(AAD);
    cipher.setAuthTag(bytes.subarray(12, 28));
    review = JSON.parse(Buffer.concat([cipher.update(bytes.subarray(28)), cipher.final()]).toString('utf8'));
    if (!Number.isSafeInteger(review.savedAt) || typeof review.root !== 'string' ||
        !Array.isArray(review.rows) || review.rows.length < 1 || review.rows.length > 48 ||
        !review.rows.every(row => typeof row.relative === 'string' && typeof row.before === 'string' && typeof row.after === 'string')) throw Error();
  } catch {
    throw Error('Saved review could not be verified. Forget it to clear the unreadable copy.');
  }
  return review.savedAt <= now && now - review.savedAt <= MAX_AGE ? review : undefined;
}

function archive(context, clock = Date.now) {
  let busy = false;
  function current() {
    const value = context.workspaceState.get(STATE);
    if (value && (typeof value.id !== 'string' || !/^[a-f0-9-]{36}$/.test(value.id))) {
      throw Error('Saved review could not be verified. Forget it to clear the unreadable copy.');
    }
    return value;
  }
  return {
    async save(root, rows) {
      if (busy) throw Error('Wait for the current review save to finish.');
      busy = true;
      try {
        const review = {root, savedAt: clock(), rows: rows.map(({relative, before, after}) => ({relative, before, after}))};
        const prior = current();
        const id = crypto.randomUUID();
        const key = crypto.randomBytes(32).toString('hex');
        const body = seal(review, key);
        await context.secrets.store(secretName(id), key);
        await context.workspaceState.update(STATE, {id, body});
        if (prior) await context.secrets.delete(secretName(prior.id)).catch(() => {});
      } finally { busy = false; }
    },
    async load() {
      const saved = current();
      if (!saved) return undefined;
      return unseal(saved.body, await context.secrets.get(secretName(saved.id)), clock());
    },
    async forget() {
      if (busy) throw Error('Wait for the current review save to finish.');
      const prior = context.workspaceState.get(STATE);
      await context.workspaceState.update(STATE, undefined);
      if (typeof prior?.id === 'string' && /^[a-f0-9-]{36}$/.test(prior.id)) {
        await context.secrets.delete(secretName(prior.id)).catch(() => {});
      }
    },
  };
}

module.exports = {archive};
