'use strict';
const fs = require('node:fs/promises');
const { constants } = require('node:fs');
const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const path = require('node:path');
const run = promisify(execFile);
const {randomUUID} = require('node:crypto');
const runtime = '/run/shirudo/runtime/usr/bin/shirudo';
const limit = 1024 * 1024;
const verdicts = new Set(['ok','tampered','lsm-off','init-pending','reboot-pending','degraded']);
const conditionNames = new Set(['LsmAttached','KeyringSealed','ChainIntact','UpatchesMeasured','TpmMeasured','ConfigActive','XdpCovered']);

function parseReport(text) {
    if (Buffer.byteLength(text) > limit) throw Error('Shirudo reports must be at most 1 MiB.');
    const doc = JSON.parse(text);
    if (doc?.schema !== 1 || !verdicts.has(doc.status) || typeof doc.host !== 'string' ||
        typeof doc.is_target !== 'boolean' || !Array.isArray(doc.conditions) || !Array.isArray(doc.upatches_loaded))
        throw Error('Choose a schema 1 report from shirudo status --json.');
    const conditions = doc.conditions.map(c => {
        if (!conditionNames.has(c?.type) || !['OK','WARN','ALERT'].includes(c.status)) throw Error('Unknown Shirudo condition.');
        return [c.type, c.status];
    });
    if (new Set(conditions.map(([name])=>name)).size !== conditions.length) throw Error('Duplicate Shirudo condition.');
    const missing = [...conditionNames].filter(name=>!conditions.some(([type])=>type===name));
    const scan = {ok:0,warnings:0,alerts:0,missing};
    for (const [,status] of conditions) scan[{OK:'ok',WARN:'warnings',ALERT:'alerts'}[status]]++;
    return { raw:text, scan, host:doc.host.replace(/[\p{C}]/gu,'').slice(0,128), target:doc.is_target, verdict:doc.status,
        conditions, loaded:doc.upatches_loaded.length };
}

async function importReport(file) {
    const handle = await fs.open(file, constants.O_RDONLY | (constants.O_NONBLOCK || 0));
    try {
        const stat = await handle.stat();
        if (!stat.isFile() || stat.size > limit) throw Error('Select a regular JSON file of at most 1 MiB.');
        const chunks = [];
        for await (const chunk of handle.createReadStream({autoClose:false, end:limit})) chunks.push(chunk);
        return parseReport(new TextDecoder('utf-8',{fatal:true,ignoreBOM:true}).decode(Buffer.concat(chunks)));
    } finally { await handle.close(); }
}

async function onTarget(args, timeout, execute=run) {
    if (process.platform !== 'linux') throw Error('This operation requires a Shirudo Linux target.');
    try {
        return await execute('/usr/bin/pkexec', [runtime, ...args], {timeout, maxBuffer:limit, cwd:'/'});
    } catch (error) {
        if (error.code === 126 || error.code === 127) throw Error('Authorization cancelled or denied.');
        if (error.code === 'ENOENT') throw Error('Install polkit and configure the Shirudo target runtime first.');
        throw Error('Shirudo did not complete. Check target status before retrying. ' + String(error.stderr || error.message).slice(0,350));
    }
}

async function inspect(vscode, chooseLocal) {
    if (chooseLocal) return parseReport((await onTarget(['status','--json'],45000)).stdout);
    const [uri] = await vscode.window.showOpenDialog({openLabel:'Inspect Shirudo report',filters:{'Shirudo status':['json']},canSelectMany:false}) || [];
    if (uri) return importReport(uri.fsPath);
}

async function exportReport(vscode, report) {
    if (!report) throw Error('Open or scan a Shirudo report first.');
    const uri=await vscode.window.showSaveDialog({saveLabel:'Export original JSON',filters:{'Shirudo report':['json']}});
    if (!uri) return;
    if (uri.scheme!=='file') throw Error('Select a local file destination.');
    const tmp=path.join(path.dirname(uri.fsPath),'.meshlit-shirudo-'+randomUUID()+'.tmp');
    try {
        await fs.writeFile(tmp,report.raw,{flag:'wx',mode:0o600});
        await fs.rename(tmp,uri.fsPath);
    } finally { await fs.rm(tmp,{force:true}); }
}

async function applySigned(vscode) {
    const [uri] = await vscode.window.showOpenDialog({openLabel:'Review signed shield',filters:{'Signed Shirudo shield':['upatch']},canSelectMany:false}) || [];
    if (!uri) return;
    const file = uri.fsPath;
    if (!path.isAbsolute(file) || path.extname(file) !== '.upatch' || !(await fs.lstat(file)).isFile()) throw Error('Select a local signed .upatch file.');
    const choice = await vscode.window.showWarningMessage('Apply this shield to the local Linux kernel?', {
        modal:true, detail:file + '\nShirudo verifies the signature and records the load. The system will request operator authorization.'
    }, 'Apply signed shield');
    if (choice !== 'Apply signed shield') return;
    await onTarget(['upatch','load',file],120000);
    await vscode.window.showInformationMessage('Shirudo loaded the signed shield. Read live status to inspect the target.');
}
module.exports = { parseReport, importReport, inspect, exportReport, applySigned, runtime };
