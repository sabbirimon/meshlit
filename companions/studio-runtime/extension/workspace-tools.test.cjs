const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const os=require('node:os');
const path=require('node:path');
const tools=require('./workspace-tools.cjs');
test('workspace tools reject escapes, symlinks, private files, ambiguous and oversized edits',async()=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'meshlit-tools-'));
 try {
  await fs.writeFile(path.join(root,'code.txt'),'before\n');
  const file=await tools.read(root,'code.txt');assert.equal(file.text,'before\n');
  assert.equal(tools.replacement(file.text,'before','after'),'after\n');
  await fs.symlink('/tmp',path.join(root,'link'));
  await assert.rejects(()=>tools.scoped(root,'link'));
  await assert.rejects(()=>tools.scoped(root,'../escape'));
  await assert.rejects(()=>tools.scoped(root,'.env'));
  await assert.rejects(()=>tools.scoped(root,'credentials.json'));
  assert.throws(()=>tools.replacement('xx','x','y'));
  assert.throws(()=>tools.replacement('x','x','z'.repeat(32769)));
  assert.equal(await tools.listing(root,'.'),'code.txt');
 } finally {await fs.rm(root,{recursive:true,force:true});}
});
