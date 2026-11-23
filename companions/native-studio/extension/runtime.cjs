'use strict';
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const crypto = require('node:crypto');
const {spawn} = require('node:child_process');
const {once} = require('node:events');
const {setTimeout: sleep} = require('node:timers/promises');

function endpoint(input) {
    const url = new URL(input);
    if (url.username || url.password || url.search || url.hash ||
        !(url.protocol === 'https:' || (url.protocol === 'http:' && ['127.0.0.1','[::1]'].includes(url.hostname)))) {
        throw Error('Use HTTPS, or HTTP on a literal loopback address.');
    }
    return url.href.replace(/\/$/, '');
}

async function request(base, route, token, body, signal) {
    const res = await fetch(endpoint(base) + route, {
        method: body ? 'POST' : 'GET', redirect: 'error', signal,
        headers: {'Content-Type': 'application/json', ...(token ? {Authorization: 'Bearer ' + token} : {})},
        body: body ? JSON.stringify(body) : undefined
    });
    if (!res.ok) throw Error('Model server returned HTTP ' + res.status);
    const chunks = [];
    let bytes = 0;
    for await (const chunk of res.body) {
        bytes += chunk.length;
        if (bytes > 4 * 1024 * 1024) throw Error('Response exceeds the Studio limit');
        chunks.push(chunk);
    }
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

async function digest(file) {
    const hash = crypto.createHash('sha256');
    for await (const block of fs.createReadStream(file)) hash.update(block);
    return hash.digest('hex');
}

class LocalServer {
    constructor(root) { this.root = root; }
    async start(signal) {
        if (!this.pending) {
            this.pending = this.launch(signal).catch(err => { this.stop(); throw err; });
        }
        return this.pending;
    }
    async launch(signal) {
        const meta = JSON.parse(fs.readFileSync(path.join(this.root, 'inference/engine.json')));
        const model = JSON.parse(fs.readFileSync(path.join(this.root, 'inference/model.json')));
        const server = path.join(this.root, 'inference', process.platform === 'win32' ? 'llama-server.exe' : 'llama-server');
        if (path.basename(model.filename) !== model.filename) throw Error('Invalid model manifest');
        const weights = path.join(this.root, 'models', model.filename);
        if (await digest(server) !== meta.sha256 || await digest(weights) !== model.sha256) throw Error('Installed inference files failed verification');
        signal.throwIfAborted();
        const reservation = net.createServer();
        reservation.listen(0, '127.0.0.1');
        await once(reservation, 'listening');
        const port = reservation.address().port;
        await new Promise(resolve => reservation.close(resolve));
        const token = crypto.randomBytes(32).toString('hex');
        const base = 'http://127.0.0.1:' + port + '/v1';
        const env = Object.fromEntries(Object.entries(process.env).filter(([k]) => !/^(LLAMA_|GGML_)/i.test(k)));
        env.LLAMA_API_KEY = token;
        const child = spawn(server, ['--host','127.0.0.1','--port',String(port),'--model',weights,'--alias','meshlit-local','--ctx-size','4096','--parallel','1'], {env,stdio:'ignore', windowsHide:true});
        this.child = child;
        let failed;
        child.once('error', err => { failed = err; });
        child.once('exit', () => { if (this.child === child) { this.child = undefined; this.pending = undefined; } });
        for (let i = 0; i < 240; i++) {
            signal.throwIfAborted();
            if (failed || child.exitCode !== null || child.signalCode) throw Error('Local model server stopped during startup');
            try {
                const inventory = await request(base, '/models', token, null, AbortSignal.any([signal,AbortSignal.timeout(1000)]));
                if (inventory.data?.some(m => m.id === 'meshlit-local')) return {base, token, model:'meshlit-local'};
            } catch { signal.throwIfAborted(); }
            await sleep(250, undefined, {signal});
        }
        throw Error('Local model did not become ready');
    }
    stop() { this.child?.kill(); this.child = undefined; this.pending = undefined; }
}
module.exports = {LocalServer, endpoint, request};
