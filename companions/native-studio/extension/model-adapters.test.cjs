const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const os=require('node:os');
const path=require('node:path');
const {contract,formats,methods,inspect,quantization,worker}=require('./model-adapters.cjs');

async function temp(t){
    const dir=await fs.mkdtemp(path.join(os.tmpdir(),'studio-formats-'));
    t.after(()=>fs.rm(dir,{force:true,recursive:true}));
    return dir;
}
test('opaque artifacts stay inert, and generic BIN has no guessed runtime',async t=>{
    const dir=await temp(t);
    for(const [filename,id] of Object.entries({'raw.bin':'binary','rwkv.pth':'pytorch','flax_model.msgpack':'flax','saved_model.pb':'tensorflow','network.h5':'keras','old.ggml':'ggml','run.llamafile':'llamafile','model.engine':'tensorrt','model.onnx':'onnx','model.mlmodel':'coreml','model.pte':'executorch','model.wasm':'library','llm.rkllm':'rkllm','llm.dlc':'qnn'})){
        const file=path.join(dir,filename);await fs.writeFile(file,'opaque');
        const description=await inspect(file);
        assert.equal(description.format,id);
        assert.equal(description.contract,contract);
        assert.deepEqual(description.adapters,[]);
        if(id==='binary'){
            assert.equal((await inspect(file,'qnn')).evidence,'format declared by user');
            await assert.rejects(inspect(file,'mlx'));
        }
    }
});
test('quantization and kernel tags are explicit and independent of filenames',()=>{
    for(const method of methods)assert.ok(quantization({quantization_config:{quant_method:method}}).includes(method));
    assert.deepEqual(quantization({quantization_config:{quant_method:'gptq',checkpoint_format:'gptq_marlin'}}),['GPTQ','Marlin']);
    assert.deepEqual(quantization({quantization_config:{load_in_4bit:true,bnb_4bit_quant_type:'nf4'}}),['NF4','bitsandbytes']);
    assert.deepEqual(quantization({model_type:'fp8-in-the-name'}),[]);
});
test('package markers identify runtime candidates and reject missing companions',async t=>{
    const dir=await temp(t);
    await fs.writeFile(path.join(dir,'genai_config.json'),'{}');
    await assert.rejects(inspect(dir),/ONNX/);
    await fs.writeFile(path.join(dir,'decoder.onnx'),'graph');
    assert.deepEqual((await inspect(dir)).adapters,['onnx-genai']);
    await fs.rm(path.join(dir,'genai_config.json'));
    await fs.writeFile(path.join(dir,'openvino_model.xml'),'<net name="m"/>');
    await assert.rejects(inspect(dir),/ENOENT/);
    for(const f of ['openvino_model.bin','openvino_tokenizer.xml','openvino_detokenizer.xml'])await fs.writeFile(path.join(dir,f),'data');
    assert.deepEqual((await inspect(dir)).adapters,['openvino-genai']);
    await fs.writeFile(path.join(dir,'mlc-chat-config.json'),'{}');
    assert.equal((await inspect(dir)).format,'mlc');
});
test('SafeTensors shard references must be local files and a single shard is not a chat package',async t=>{
    const dir=await temp(t),config=path.join(dir,'config.json'),index=path.join(dir,'model.safetensors.index.json'),shard='weights.safetensors';
    const header=Buffer.from(JSON.stringify({x:{shape:[1],dtype:'F32',data_offsets:[0,4]}}));
    const len=Buffer.alloc(8);len.writeBigUInt64LE(BigInt(header.length));
    await fs.writeFile(path.join(dir,shard),Buffer.concat([len,header,Buffer.alloc(4)]));
    assert.deepEqual((await inspect(path.join(dir,shard))).adapters,[]);
    await fs.writeFile(config,'{"model_type":"llama"}');
    await fs.writeFile(path.join(dir,'tokenizer.json'),'{}');
    for(const bad of ['../secret','/secret','C:secret','absent.safetensors']){
        await fs.writeFile(index,JSON.stringify({weight_map:{x:bad}}));
        await assert.rejects(inspect(dir));
    }
    await fs.writeFile(index,JSON.stringify({weight_map:{x:shard}}));
    assert.deepEqual((await inspect(dir)).adapters,['transformers']);
    await fs.writeFile(config,'{"quantization":{"bits":4,"group_size":64}}');
    assert.deepEqual((await inspect(dir)).adapters,['mlx-lm']);
    await fs.writeFile(config,JSON.stringify({quantization:{bits:4,group_size:64},model_file:'custom.py'}));
    assert.deepEqual((await inspect(dir)).adapters,[]);
    await fs.writeFile(config,'{"model_type":"rwkv6","auto_map":{"AutoModel":"pkg.custom"}}');
    assert.equal((await inspect(dir)).format,'rwkv');
    assert.deepEqual((await inspect(dir)).adapters,[]);
    await assert.rejects(worker(process.execPath,'transformers',dir),/cannot use/);
});
test('GGUF signature, LiteRT identifier and NumPy magic are checked',async t=>{
    const dir=await temp(t);
    for(const name of ['x.gguf','x.tflite','x.npy','x.npz']){
        const file=path.join(dir,name);await fs.writeFile(file,'invalid');await assert.rejects(inspect(file));
    }
    const gguf=Buffer.alloc(24);gguf.write('GGUF');gguf.writeUInt32LE(3,4);
    const file=path.join(dir,'x.gguf');await fs.writeFile(file,gguf);
    assert.deepEqual((await inspect(file)).adapters,['llama.cpp']);
    const tflite=path.join(dir,'model.tflite');await fs.writeFile(tflite,Buffer.from('1234TFL3'));
    assert.equal((await inspect(tflite)).format,'litert');
    assert.equal(formats.numpy.runtime,'Architecture-specific loader');
});

test('Flax folder requires a local config and tokenizer and excludes custom code',async t=>{
    const dir=await temp(t);
    await fs.writeFile(path.join(dir,'flax_model.msgpack'),'weights');
    assert.deepEqual((await inspect(dir)).adapters,[]);
    await fs.writeFile(path.join(dir,'config.json'),'{"model_type":"gpt2"}');
    await fs.writeFile(path.join(dir,'tokenizer.json'),'{}');
    assert.deepEqual((await inspect(dir)).adapters,['flax-transformers']);
    await fs.writeFile(path.join(dir,'config.json'),'{"auto_map":{"AutoModel":"custom"}}');
    assert.deepEqual((await inspect(dir)).adapters,[]);
});
