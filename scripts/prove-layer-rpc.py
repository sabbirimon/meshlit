#!/usr/bin/env python3
"""Reproducible host CPU versus two native RPC worker equivalence proof.
Requires an operator-supplied real GGUF. Uses loopback and a temporary API key.
This does not prove Android, physical-phone or oversized-model execution.
"""
import argparse
import hashlib
import json
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import time
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
def free_port(used):
    while True:
        with socket.socket() as listener:
            listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
        if port not in used:used.add(port);return port

def request(port,key,path,body=None):
    req=urllib.request.Request(f'http://127.0.0.1:{port}/{path}',headers={'Authorization':f'Bearer {key}','Content-Type':'application/json'},
        data=json.dumps(body).encode() if body is not None else None)
    with urllib.request.urlopen(req,timeout=30) as response:return json.load(response)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--bin',type=Path,default=ROOT/'build/pipeline-host/bin')
    args=parser.parse_args();model=args.model.resolve();out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
    with model.open('rb') as check:
        if check.read(4)!=b'GGUF':parser.error('Supply a real GGUF model')
    used=set();worker_ports=[free_port(used),free_port(used)];remote_port=free_port(used);baseline_port=free_port(used)
    processes=[];logs=[]
    with tempfile.TemporaryDirectory() as scratch:
        key=secrets.token_hex(32);keyfile=Path(scratch)/'api-key';keyfile.write_text(key);keyfile.chmod(0o600)
        def launch(name,argv):
            log=(out/(name+'.log')).open('w');logs.append(log)
            process=subprocess.Popen([str(args.bin/argv[0])]+argv[1:],stdout=log,stderr=subprocess.STDOUT)
            processes.append(process);return process
        try:
            for index,port in enumerate(worker_ports):launch(f'worker{index+1}',['ggml-rpc-server','-H','127.0.0.1','-p',str(port),'--device','CPU','-t','2'])
            time.sleep(.5)
            common=['llama-server','-m',str(model),'-c','512','-t','2','--parallel','1','--host','127.0.0.1',
                '--api-key-file',str(keyfile),'--no-webui','--no-warmup','--fit','off']
            remote=launch('distributed',common+['--port',str(remote_port),'--rpc',','.join(f'127.0.0.1:{p}' for p in worker_ports),
                '--device','RPC0,RPC1','--split-mode','layer','--tensor-split','1,1','-ngl','99','--verbose'])
            baseline=launch('baseline',common+['--port',str(baseline_port),'-ngl','0'])
            for port,process in [(remote_port,remote),(baseline_port,baseline)]:
                deadline=time.monotonic()+120
                while True:
                    if process.poll() is not None:raise RuntimeError('Native server exited; see log')
                    try:request(port,key,'health');break
                    except (OSError,ValueError):
                        if time.monotonic()>deadline:raise TimeoutError('Native server readiness timeout')
                        time.sleep(.2)
            payload={'prompt':'Once upon a time','n_predict':32,'temperature':0,'seed':42,'cache_prompt':False}
            distributed=request(remote_port,key,'completion',payload);single=request(baseline_port,key,'completion',payload)
            (out/'distributed-response.json').write_text(json.dumps(distributed,indent=2))
            (out/'baseline-response.json').write_text(json.dumps(single,indent=2))
            assert distributed['content']==single['content'],'Native outputs differ'
            assert distributed['tokens_predicted']==32 and single['tokens_predicted']==32,'Generation incomplete'
            for log in logs:log.flush()
            placement=(out/'distributed.log').read_text()
            assert 'assigned to device RPC0' in placement and 'assigned to device RPC1' in placement,'Missing placement on both workers'
            assert all(f'127.0.0.1:{port}] model buffer size' in placement for port in worker_ports),'Missing remote weight buffers'
            digest=hashlib.sha256()
            with model.open('rb') as stream:
                for block in iter(lambda:stream.read(1024*1024),b''):digest.update(block)
            proof={'model_sha256':digest.hexdigest(),'model_bytes':model.stat().st_size,'prompt':payload['prompt'],'seed':42,
                'tokens':32,'identical_output':True,'two_remote_layer_placements':True,'physical_phone_proof':False,'oversized_model_proof':False,
                'native_revision':'4df29be4f4c3673f428170fda944a5b19f743bb8'}
            (out/'proof.json').write_text(json.dumps(proof,indent=2)+'\n');print(json.dumps(proof))
        finally:
            for process in reversed(processes):
                process.terminate()
                try:process.wait(timeout=5)
                except subprocess.TimeoutExpired:process.kill();process.wait()
            for log in logs:log.close()
if __name__=='__main__':main()
