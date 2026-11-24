#!/usr/bin/env python3
"""Repeat real CPU-vs-two-RPC-worker generation on one host. Not a phone/oversized proof."""
import argparse, hashlib, json, os, signal, socket, subprocess, time, urllib.request, uuid
from pathlib import Path

def port():
    with socket.socket() as sock:sock.bind(('127.0.0.1',0));return sock.getsockname()[1]
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--model',type=Path,required=True);parser.add_argument('--sha256',required=True);parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--binary-dir',type=Path,help='Existing reviewed host build; no download or rebuild')
    parser.add_argument('--reference-kernels',action='store_true',help='Disable repacking and flash attention on both paths for a controlled CPU comparison')
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1];binary=(args.binary_dir or root/'build/pipeline-host/bin').resolve();model=args.model.resolve()
    def sha256(path):
        digest=hashlib.sha256()
        with path.open('rb') as stream:
            for chunk in iter(lambda:stream.read(1024*1024),b''):digest.update(chunk)
        return digest.hexdigest()
    for name in ('llama-server','ggml-rpc-server'):
        if not (binary/name).is_file():raise ValueError('Missing installed native binary: '+name)
    digest=sha256(model)
    if digest!=args.sha256:raise ValueError('Model digest mismatch')
    args.output.mkdir(parents=True,exist_ok=True);processes=[];logs=[];token=uuid.uuid4().hex+uuid.uuid4().hex
    key=args.output/'temporary-api-key';key.write_text(token);os.chmod(key,0o600)
    def launch(name,argv):
        stream=(args.output/(name+'.log')).open('w');logs.append(stream)
        proc=subprocess.Popen(argv,stdout=stream,stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL,start_new_session=True);processes.append(proc);return proc
    def request(number,path,data=None):
        req=urllib.request.Request('http://127.0.0.1:'+str(number)+path,data=None if data is None else json.dumps(data).encode(),headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=20) as response:
            raw=response.read(1024*1024+1)
            if len(raw)>1024*1024:raise ValueError('Response budget exceeded')
            return json.loads(raw)
    def server(name,rpc=None):
        number=port();argv=[str(binary/'llama-server'),'-m',str(model),'--host','127.0.0.1','--port',str(number),'--api-key-file',str(key),'--no-ui','-v','-t','2','-c','256','--parallel','1']
        if args.reference_kernels:argv+=['--no-repack','--flash-attn','off']
        argv+=['--device','none','-ngl','0'] if rpc is None else ['--rpc',rpc,'--device','RPC0,RPC1','-ngl','99','--split-mode','layer','--tensor-split','1,1']
        proc=launch(name,argv);deadline=time.monotonic()+90
        while time.monotonic()<deadline:
            if proc.poll() is not None:raise ValueError('Native server stopped; inspect log')
            try:
                if request(number,'/health').get('status')=='ok':return number,proc
            except Exception:pass
            time.sleep(.2)
        raise TimeoutError('Native server readiness deadline')
    def stop(proc):
        if proc.poll() is None:
            os.killpg(proc.pid,signal.SIGTERM)
            try:proc.wait(timeout=5)
            except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait()
    try:
        options={'prompt':'Once upon a time','seed':42,'temperature':0,'n_predict':32,'stream':False,'cache_prompt':False}
        number,proc=server('baseline');baseline=request(number,'/completion',options);stop(proc)
        workers=[port(),port()]
        for i,p in enumerate(workers):launch('worker'+str(i),[str(binary/'ggml-rpc-server'),'-H','127.0.0.1','-p',str(p),'-t','2','-d','CPU'])
        time.sleep(.5);number,proc=server('distributed',','.join('127.0.0.1:'+str(p) for p in workers));distributed=request(number,'/completion',options)
        logs[-1].flush();text=(args.output/'distributed.log').read_text()
        identical=baseline['content']==distributed['content'];placement=all(('assigned to device RPC'+str(i)) in text and ('127.0.0.1:'+str(workers[i])+'] model buffer size') in text for i in range(2))
        for name,value in [('baseline-response',baseline),('distributed-response',distributed)]:(args.output/(name+'.json')).write_text(json.dumps(value,indent=2))
        def measurements(response):
            return {'prompt_tokens':response.get('tokens_evaluated'), 'output_tokens':response.get('tokens_predicted'),
                'timings':response.get('timings'), 'peak_memory_bytes':None, 'first_token_ms':None,
                'inter_token_ms':None}
        result={'model_sha256':digest,'model_bytes':model.stat().st_size,'identical_output':identical,'two_remote_buffers':placement,'physical_phone_proof':False,'oversized_model_proof':False,'tls_transport_proof':False,'runtime':'existing pinned native CPU host binaries',
            'binary_sha256':{name:sha256(binary/name) for name in ('llama-server','ggml-rpc-server')},
            'baseline':measurements(baseline),'two_workers':measurements(distributed),
            'reference_kernels':args.reference_kernels,
            'measurement_scope':'Non-streaming CPU generation on one host; memory and streaming latency unmeasured'}
        (args.output/'proof.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
        if not identical or not placement:raise ValueError('Native output/placement check failed')
    finally:
        for proc in reversed(processes):stop(proc)
        for stream in logs:stream.close()
        key.unlink(missing_ok=True)
if __name__=='__main__':main()
