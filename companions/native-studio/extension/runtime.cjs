'use strict';
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const os = require('node:os');
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

async function digest(file,signal) {
    const hash = crypto.createHash('sha256');
    for await (const block of fs.createReadStream(file,{signal})) hash.update(block);
    return hash.digest('hex');
}

class LocalServer {
    constructor(root, onStateChange = () => {}) { this.root = root; this.onStateChange = onStateChange; }
    async start(signal, configuredThreads = 0, imported, progress=()=>{}) {
        if (!this.pending) {
            const startup = new AbortController();
            this.startup = startup;
            this.pending = this.launch(AbortSignal.any([signal,startup.signal]), configuredThreads, imported, progress).then(route=>{
                startup.signal.throwIfAborted();
                this.route=route; this.onStateChange();
                return route;
            }).catch(err => { if(this.startup===startup)this.stop(); throw err; });
            this.onStateChange();
        }
        return this.pending;
    }
    async launch(signal, configuredThreads, imported, progress) {
        const available = os.availableParallelism();
        if (!Number.isInteger(configuredThreads) || configuredThreads < 0 || configuredThreads > Math.min(64, available)) {
            throw Error('CPU thread limit exceeds available processors');
        }
        const threads = String(configuredThreads || Math.min(4, available));
        const meta = JSON.parse(fs.readFileSync(path.join(this.root, 'inference/engine.json')));
        const model = JSON.parse(fs.readFileSync(path.join(this.root, 'inference/model.json')));
        const server = path.join(this.root, 'inference', process.platform === 'win32' ? 'llama-server.exe' : 'llama-server');
        if (path.basename(model.filename) !== model.filename) throw Error('Invalid model manifest');
        const weights = imported ? imported.file : path.join(this.root, 'models', model.filename);
        progress('Verifying local model and engine…');
        if (await digest(server,signal) !== meta.sha256 || await digest(weights,signal) !== (imported?.sha256 || model.sha256)) throw Error('Installed inference files failed verification');
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
        signal.throwIfAborted();
        progress('Loading model into memory…');
        const child = spawn(server, ['--host','127.0.0.1','--port',String(port),'--model',weights,'--alias','meshlit-local','--ctx-size','4096','--parallel','1','--threads',threads,'--threads-batch',threads,
            '--fit','off','--device','none','--n-gpu-layers','0','--no-warmup','--no-agent','--no-webui','--offline'], {env,stdio:'ignore', windowsHide:true});
        this.child = child;
        let failed;
        child.once('error', err => { failed = err; });
        child.once('exit', () => { if (this.child === child) { this.child = undefined; this.pending = undefined; this.route = undefined; this.onStateChange(); } });
        const deadline = Date.now() + 180000;
        while (Date.now() < deadline) {
            signal.throwIfAborted();
            if (failed || child.exitCode !== null || child.signalCode) throw Error('Local model server stopped during startup'+(failed?' · '+failed.message:' · exit '+child.exitCode)+'. Check available RAM and extract the full Studio package.');
            try {
                const inventory = await request(base, '/models', token, null, AbortSignal.any([signal,AbortSignal.timeout(1000)]));
                if (inventory.data?.some(m => m.id === 'meshlit-local')) return {base, token, model:'meshlit-local'};
            } catch { signal.throwIfAborted(); }
            await sleep(250, undefined, {signal});
        }
        throw Error('Local model did not become ready');
    }
    stop() { this.startup?.abort(); this.startup=undefined; this.route=undefined; this.child?.kill(); this.child = undefined; this.pending = undefined; this.onStateChange(); }
}
module.exports = {LocalServer, endpoint, request, digest};
