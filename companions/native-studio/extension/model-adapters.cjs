// SPDX-License-Identifier: Apache-2.0
// Shared with HyperL. Inspection reads local metadata and never starts a runtime.
'use strict';
const fs = require('node:fs/promises');
const path = require('node:path');
const {constants} = require('node:fs');
const contract = 'meshlit.model-adapters/1';
const families = {
    gguf: ['GGUF', 'llama.cpp', ['gguf']],
    safetensors: ['SafeTensors', 'Transformers or Ollama', ['safetensors']],
    pytorch: ['PyTorch checkpoint', 'Trusted PyTorch conversion', ['pt', 'pth']],
    flax: ['Flax MessagePack', 'Flax/JAX', ['msgpack']],
    tensorflow: ['TensorFlow graph or SavedModel', 'TensorFlow', ['pb']],
    keras: ['Keras / HDF5', 'Keras', ['h5', 'hdf5', 'keras']],
    numpy: ['NumPy array or archive', 'Architecture-specific loader', ['npy', 'npz']],
    ggml: ['Legacy GGML', 'Matching legacy runtime or conversion', ['ggml']],
    llamafile: ['llamafile executable', 'Trusted llamafile deployment', ['llamafile']],
    binary: ['Unidentified binary', 'Identify exporter (e.g. PyTorch, RWKV, QNN or another custom runtime)', ['bin']],
    rwkv: ['RWKV checkpoint', 'Matching RWKV implementation', []],
    mlx: ['MLX checkpoint', 'MLX-LM on supported hardware', []],
    tensorrt: ['TensorRT engine', 'Matching TensorRT-LLM runtime and GPU', ['engine', 'plan']],
    onnx: ['ONNX graph', 'ONNX Runtime GenAI for decoder packages', ['onnx']],
    openvino: ['OpenVINO IR', 'OpenVINO GenAI for decoder packages', ['xml']],
    coreml: ['Core ML', 'Core ML on a compatible Apple device', ['mlmodel', 'mlmodelc', 'mlpackage']],
    litert: ['LiteRT', 'Compatible LiteRT interpreter', ['tflite']],
    executorch: ['ExecuTorch', 'ExecuTorch with matching operators', ['pte']],
    mlc: ['MLC-LLM package', 'MLC-LLM with a matching compiled library', []],
    library: ['Compiled library', 'Identify exporter and target architecture', ['so', 'dylib', 'dll', 'wasm']],
    rkllm: ['RKLLM', 'Rockchip RKLLM on a compatible NPU', ['rkllm']],
    qnn: ['Qualcomm QNN / SNPE', 'Qualcomm SNPE or compatible QNN loader', ['dlc']]
};
const formats = Object.fromEntries(Object.entries(families).map(([id,[label,runtime,extensions]]) => [id,{label,runtime,extensions}]));
const methods = ['AWQ','GPTQ','EXL2','EXL3','HQQ','AQLM','QuIP#','bitsandbytes','NF4','INT8','FP8','NVFP4','Marlin'];
const extensions = new Map(Object.entries(formats).flatMap(([id,v])=>v.extensions.map(ext=>[ext,id])));
async function regular(file) {
    const h = await fs.open(file, constants.O_RDONLY | (constants.O_NOFOLLOW || 0) | (constants.O_NONBLOCK || 0));
    try {
        const stat = await h.stat();
        if (!stat.isFile() || !stat.size || !Number.isSafeInteger(stat.size)) throw Error('Expected a nonempty regular file');
        return {h,size:stat.size};
    } catch (err) { await h.close(); throw err; }
}
async function readPart(h, count, offset=0) {
    const bytes = Buffer.alloc(count);
    let done = 0;
    while (done < count) {
        const r = await h.read(bytes,done,count-done,offset+done);
        if (!r.bytesRead) throw Error('Truncated model metadata');
        done += r.bytesRead;
    }
    return bytes;
}
async function metadata(file) {
    const {h,size}=await regular(file);
    try {
        if(size>8*1024*1024) throw Error('Model configuration exceeds 8 MiB');
        const obj=JSON.parse((await readPart(h,size)).toString('utf8'));
        if(!obj || typeof obj !== 'object' || Array.isArray(obj)) throw Error('Expected object metadata');
        return obj;
    } finally { await h.close(); }
}
function quantization(config={}) {
    const q=config.quantization_config || config.quantization || config;
    const declared=[q.quant_method,q.method,q.quant_algo,q.quantization_method,q.bnb_4bit_quant_type,q.checkpoint_format,q.format,config.torch_dtype,config.dtype];
    if (q.load_in_8bit || q._load_in_8bit) declared.push('bitsandbytes','int8');
    if (q.load_in_4bit || q._load_in_4bit) declared.push('bitsandbytes');
    const tags=new Set();
    // Some exporters put the numerical scheme in nested compression groups.
    for(const group of Object.values(q.config_groups || {})){
        const w=group?.weights;
        if(w?.type==='float' && w.num_bits===8) tags.add('FP8');
    }
    for(const v of declared.filter(v=>typeof v==='string')){
        for(const m of methods) if(v.toLowerCase()===m.toLowerCase()) tags.add(m);
        if(/^float8_|^fp8_/i.test(v)) tags.add('FP8');
        if(/^nvfp4(?:_|$)/i.test(v)) tags.add('NVFP4');
        if(/^quip(?:_sharp|sharp)$/i.test(v)) tags.add('QuIP#');
        if(/(?:^|[_-])marlin(?:$|[_-])/i.test(v)) tags.add('Marlin');
    }
    return [...tags];
}
function result(file,id,evidence,options={}) {
    return {contract,file,format:id,label:formats[id].label,runtime:formats[id].runtime,evidence,quantization:[],adapters:[],...options};
}
async function inspectFile(file) {
    const name=path.basename(file).toLowerCase(),ext=path.extname(name).slice(1);
    let id=extensions.get(ext),evidence='filename hint';
    const {h,size}=await regular(file);
    try {
        const prefix=await readPart(h, Math.min(size,96));
        if(prefix.toString('ascii',0,4)==='GGUF'){
            if(size<24 || ![2,3].includes(prefix.readUInt32LE(4))) throw Error('Unsupported or incomplete GGUF header');
            return result(file,'gguf','GGUF header',{bytes:size,adapters:ext==='gguf'&&!/-\d{5}-of-\d{5}\.gguf$/.test(name)?['llama.cpp']:[]});
        }
        if(id==='gguf') throw Error('GGUF header missing');
        if(['ggml','ggmf','ggjt','lmgg','fmgg','tjgg'].includes(prefix.toString('ascii',0,4))) {id='ggml'; evidence='GGML family header';}
        if(name==='pytorch_model.bin') id='pytorch';
        if(id==='numpy'){
            if(ext==='npy' && !prefix.subarray(0,6).equals(Buffer.from([147,78,85,77,80,89]))) throw Error('NumPy signature missing');
            if(ext==='npz' && !prefix.subarray(0,4).equals(Buffer.from([80,75,3,4]))) throw Error('Archive signature missing');
            evidence=ext==='npy'?'NumPy signature':'ZIP signature and NPZ filename';
        }
        if(id==='litert') {
            if(prefix.toString('ascii',4,8)!=='TFL3') throw Error('LiteRT identifier missing');
            evidence='TFL3 identifier';
        }
        if(id==='safetensors'){
            const len=Number(prefix.readBigUInt64LE());
            if(!Number.isSafeInteger(len)||len<2||len>16*1024*1024||8+len>size)throw Error('Invalid SafeTensors header length');
            const header=JSON.parse((await readPart(h,len,8)).toString('utf8'));
            const entries=Object.entries(header||{}).filter(([k])=>k!=='__metadata__');
            if(!entries.length || entries.some(([,t])=>!Array.isArray(t?.shape)||typeof t.dtype!=='string'||!Array.isArray(t.data_offsets)||t.data_offsets.length!==2||!t.data_offsets.every(n=>Number.isSafeInteger(n)&&n>=0&&n<=size-8-len)||t.data_offsets[0]>t.data_offsets[1]))throw Error('Invalid SafeTensors tensor directory');
            return result(file,id,'tensor header',{bytes:size,quantization:entries.some(([,t])=>/^F8_/.test(t.dtype))?['FP8']:[]});
        }
        if(id==='openvino') {
            const xml=(await readPart(h,Math.min(size,8192))).toString('utf8');
            if(!/<net\s/.test(xml)) throw Error('OpenVINO IR network header missing');
            const weights=await regular(file.slice(0,-4)+'.bin'); await weights.h.close();
            evidence='IR network header and companion BIN';
        }
        if(!id) throw Error('Unrecognized model file. Select the model directory if this is a package.');
        return result(file,id,evidence,{bytes:size});
    } finally {await h.close();}
}
function checkName(name) {
    if(typeof name!=='string'||!name||name.length>240||/[\\/:\u0000-\u001f]/.test(name)||name==='.'||name==='..') throw Error('Invalid model package member');
    return name;
}
const binaryHints={binary:['pytorch','rwkv','qnn'],library:['mlc']};
function declareFormat(description,hint){
    if(!hint)return description;
    if(!binaryHints[description.format]?.includes(hint))throw Error('The format override does not apply to this artifact');
    return result(description.file,hint,'format declared by user',{bytes:description.bytes});
}
async function inspect(source,hint) {
    const input=path.resolve(source), stat=await fs.lstat(input);
    if(stat.isSymbolicLink())throw Error('Select the original model file or directory');
    if(!stat.isDirectory() && !input.endsWith('.safetensors.index.json'))return declareFormat(await inspectFile(input),hint);
    if(hint)throw Error('A format override applies only to an ambiguous file');
    const indexed=!stat.isDirectory(),dir=indexed?path.dirname(input):input;
    const entries=new Set();
    const listing=await fs.opendir(dir);
    let count=0;
    for await(const entry of listing){
        if(++count>4096)throw Error('Model directory exceeds 4096 entries');
        if(entry.isFile())entries.add(entry.name);
    }
    if(!indexed&&/\.ml(package|modelc)$/i.test(dir)){
        if(/\.mlpackage$/i.test(dir)&&!entries.has('Manifest.json'))throw Error('Missing Core ML Manifest.json');
        return result(input,'coreml','package name');
    }
    const config=entries.has('config.json')?await metadata(path.join(dir,'config.json')):{};
    const tags=quantization(config);
    for(const aux of ['quantize_config.json','quant_config.json','hf_quant_config.json']) {
        if(entries.has(aux))tags.push(...quantization(await metadata(path.join(dir,aux))));
    }
    const data={quantization:[...new Set(tags)]};
    const tokenizerPresent = entries.has('tokenizer.json') || entries.has('tokenizer.model');
    const configured = entries.has('config.json') && tokenizerPresent && !config.auto_map;
    const markers=[['mlc-chat-config.json','mlc'],['genai_config.json','onnx'],['openvino_model.xml','openvino'],['saved_model.pb','tensorflow'],['pytorch_model.bin','pytorch'],['pytorch_model.bin.index.json','pytorch']];
    if(!indexed) for(const [name,id] of markers) if(entries.has(name)){
        if(id==='openvino')await inspectFile(path.join(dir,name));
        if(id==='onnx' && ![...entries].some(n=>n.endsWith('.onnx')))throw Error('ONNX GenAI package has no top-level ONNX graph');
        const adapters=id==='onnx'?['onnx-genai']:id==='openvino'&&entries.has('openvino_tokenizer.xml')&&entries.has('openvino_detokenizer.xml')?['openvino-genai']:[];
        return result(input,id,'package marker: '+name,{...data,adapters});
    }
    const index=indexed?path.basename(input):'model.safetensors.index.json';
    const weights=[...entries].filter(n=>n.endsWith('.safetensors')).sort();
    if(indexed || entries.has(index)){
        const mapping=(await metadata(path.join(dir,index))).weight_map;
        if(!mapping||typeof mapping!=='object'||Array.isArray(mapping)||!Object.keys(mapping).length)throw Error('Missing SafeTensors shard map');
        const shards=new Set(Object.values(mapping).map(checkName));
        if(shards.size>128||[...shards].some(n=>!n.endsWith('.safetensors')||!entries.has(n)))throw Error('Missing or unsupported SafeTensors shard');
    }
    if(weights.length){
        const mlx=!!(config.quantization?.group_size && config.quantization?.bits && !config.quantization_config);
        const rwkv=/^rwkv/i.test(config.model_type||'');
        const prep=!indexed && configured && (!mlx || config.model_file == null);
        return result(input,mlx?'mlx':rwkv?'rwkv':'safetensors','weight filenames and package metadata',{...data,adapters:prep?[mlx?'mlx-lm':'transformers']:[]});
    }
    if(entries.has('flax_model.msgpack')){
        return result(input,'flax','Flax MessagePack checkpoint and package metadata',{...data,adapters:configured?['flax-transformers']:[]});
    }
    if([...entries].some(n=>n.endsWith('.engine')))return result(input,'tensorrt','engine filename in package',data);
    throw Error('Unrecognized model directory. Select a specific model file.');
}
module.exports={contract,formats,methods,inspect,quantization,binaryHints,declareFormat};

const {execFile} = require('node:child_process');
async function worker(python, adapter, file, messages, signal, maxTokens=128) {
    if(!path.isAbsolute(python))throw Error('Choose the Python executable from an installed runtime environment');
    const descriptor=await inspect(file);
    if(!descriptor.adapters.includes(adapter)||adapter==='llama.cpp')throw Error('This model package cannot use the selected adapter');
    const payload=JSON.stringify({operation:messages?'generate':'probe',adapter,file:descriptor.file,messages,max_tokens:maxTokens});
    return new Promise((resolve,reject)=>{
        const child=execFile(python,['-I',path.join(__dirname,'model_worker.py')],{
            cwd:__dirname,encoding:'utf8',windowsHide:true,signal,timeout:messages?240000:15000,
            killSignal:'SIGKILL',maxBuffer:1024*1024
        },(err,stdout)=>{
            let response;
            try {response=JSON.parse(stdout);} catch { /* Runtime may be unavailable or stopped. */ }
            if(signal?.aborted)return reject(signal.reason);
            if(err||response?.error||!response){
                const failure=err?.signal?'Local runtime stopped ('+err.signal+')':err?.message;
                return reject(Error(response?.error||failure||'Invalid local runtime response'));
            }
            resolve(response);
        });
        child.stdin.on('error',()=>{}); // Process launch/exit is reported by execFile.
        child.stdin.end(payload);
    });
}
module.exports.worker=worker;
if(require.main===module){
    (async()=>{
        const [op,file,adapter,python]=process.argv.slice(2);
        if(op==='list')return {contract,formats,methods};
        if(op==='inspect')return inspect(file,adapter);
        if(op==='probe')return worker(python,adapter,file);
        if(op==='generate'){
            const chunks=[];let length=0;
            for await(const chunk of process.stdin){
                length+=chunk.length;if(length>1024*1024)throw Error('Request too large');
                chunks.push(chunk);
            }
            const req=JSON.parse(Buffer.concat(chunks));
            return worker(python,adapter,file,req.messages,undefined,req.max_tokens);
        }
        throw Error('Expected list, inspect, probe or generate');
    })().then(v=>console.log(JSON.stringify(v))).catch(err=>{console.error(err.message);process.exitCode=1;});
}
