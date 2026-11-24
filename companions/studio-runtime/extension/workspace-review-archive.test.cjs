const test = require('node:test');
const assert = require('node:assert/strict');
const {archive} = require('./workspace-review-archive.cjs');

function fixture() {
  const state = new Map();
  const keys = new Map();
  const context = {
    workspaceState: {get: id => state.get(id), async update(id, value) { state.set(id, value); }},
    secrets: {async get(id) { return keys.get(id); }, async store(id, value) { keys.set(id, value); },
      async delete(id) { keys.delete(id); }},
  };
  return {state, keys, context};
}

test('saved review survives a new controller without storing plaintext in workspace state', async () => {
  const {state, keys, context} = fixture();
  const root = '/example/workspace';
  const rows = [{relative: 'readme.md', before: 'private previous text', after: 'private changed text'}];
  await archive(context, () => 1000).save(root, rows);
  assert.doesNotMatch(JSON.stringify([...state.values()]), /private|workspace|readme/);
  assert.deepEqual(await archive(context, () => 1100).load(), {root, savedAt: 1000, rows});
  assert.equal(await archive(context, () => 8 * 86400 * 1000).load(), undefined);
  await archive(context).forget();
  assert.equal(await archive(context).load(), undefined);
  assert.equal(keys.size, 0);
});

test('tampering and oversize data cannot overwrite or reopen a saved review', async () => {
  const {state, context} = fixture();
  const review = archive(context, () => 1000);
  const rows = [{relative: 'small.txt', before: 'before', after: 'after'}];
  await review.save('/example/workspace', rows);
  await assert.rejects(review.save('/example/workspace', [{...rows[0], after: 'x'.repeat(256 * 1024)}]), /too large/);
  assert.deepEqual((await review.load()).rows, rows);
  const value = [...state.values()][0];
  const bytes = Buffer.from(value.body, 'base64');
  bytes[bytes.length - 1] ^= 1;
  value.body = bytes.toString('base64');
  await assert.rejects(review.load(), /could not be verified/);
});
