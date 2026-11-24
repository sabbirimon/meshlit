'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const {parseReport,importReport,applySigned} = require('./shirudo.cjs');
const report = {schema:1,host:'lab',is_target:true,status:'tampered',conditions:[{type:'ChainIntact',status:'ALERT'}],upatches_loaded:[{}]};

test('preserves an unhealthy verdict and reads the selected file', async t=>{
    const dir=await fs.mkdtemp(path.join(os.tmpdir(),'meshlit-report-'));
    t.after(()=>fs.rm(dir,{force:true,recursive:true}));
    const file=path.join(dir,'report.json');
    await fs.writeFile(file,JSON.stringify(report));
    assert.deepEqual(await importReport(file),{host:'lab',target:true,verdict:'tampered',conditions:[['ChainIntact','ALERT']],loaded:1});
    await fs.writeFile(file,Buffer.alloc(1024*1024+1));
    await assert.rejects(importReport(file),/1 MiB/);
});
test('malformed, unknown and ambiguous reports fail closed',()=>{
    for(const bad of [{schema:2},{conditions:[...report.conditions,...report.conditions]},{status:'verified'},{conditions:[{type:'Unknown',status:'OK'}]}])
        assert.throws(()=>parseReport(JSON.stringify({...report,...bad})));
    assert.throws(()=>parseReport('null'));
});
test('cancelled operator review never starts a load',async t=>{
    const dir=await fs.mkdtemp(path.join(os.tmpdir(),'meshlit-shield-'));
    t.after(()=>fs.rm(dir,{force:true,recursive:true}));
    const file=path.join(dir,'sample.upatch');
    await fs.writeFile(file,'');
    let reviewed=false;
    await applySigned({window:{showOpenDialog:async()=>[{fsPath:file}],showWarningMessage:async()=>{reviewed=true;}}});
    assert.equal(reviewed,true);
});
