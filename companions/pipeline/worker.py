#!/usr/bin/env python3
"""Owner-enabled native layer worker for a host without the Android app.
Native RPC is loopback only; the peer transport requires TLS pinning + a token.
Python standard library only. No auto-install, remote shell or public default.
"""
import argparse
import hashlib
import json
import os
import platform
import shutil
from pathlib import Path
import secrets
import socket
import ssl
import struct
import subprocess
import threading
import time
import uuid

REVISION='4df29be4f4c3673f428170fda944a5b19f743bb8'
MAGIC=b'MESHLIT_LAYER_RPC/1'
CAPS=b'MESHLIT_LAYER_CAPS/1'

def frame(value):
    data=value if isinstance(value,bytes) else value.encode('ascii')
    if not 1<=len(data)<=16000: raise ValueError('Frame size invalid')
    return struct.pack('>H',len(data))+data

def read_exact(stream,size):
    data=bytearray()
    while len(data)<size:
        part=stream.recv(size-len(data))
        if not part: raise EOFError('Closed frame')
        data.extend(part)
    return bytes(data)

def read_auth(stream):
    size=struct.unpack('>H',read_exact(stream,2))[0]
    if not 1<=size<=256: raise ValueError('Authentication frame too large')
    data=read_exact(stream,size)
    if any(b<32 or b>126 for b in data): raise ValueError('Authentication encoding invalid')
    return data

def free_memory():
    try:
        import psutil
        return psutil.virtual_memory().available
    except ImportError:
        pass
    try:
        fields={line.split(':')[0]:int(line.split()[1])*1024 for line in Path('/proc/meminfo').read_text().splitlines()}
        return fields.get('MemAvailable',fields.get('MemFree',0))
    except (OSError,ValueError):
        return 0

class Worker:
    def __init__(self,options):
        self.options=options
        self.identity=options.node_id
        self.limit=threading.BoundedSemaphore(4)
        self.connections=set()
        self.lock=threading.Lock()
        self.closed=False
        self.native=None
        self.listener=None
    def offer(self):
        available=free_memory()
        if self.options.memory_budget_mb is not None:
            limit=self.options.memory_budget_mb*1024*1024
            available=min(available,limit) if available else limit
        return {'nodeId':self.identity,'abi':platform.machine(),'freeMemoryBytes':available,
            'freeDiskBytes':shutil.disk_usage(self.options.workdir).free,
            'runtimeRevision':REVISION,'workerAllowed':True,'coordinatorAllowed':False,
            'thermalStatus':0,'observedAtMs':int(time.time()*1000),'deviceClass':self.options.device_class,
            'backend':self.options.native_device,'cpuThreads':self.options.threads}
    def handle(self,raw):
        tls=None;native=None
        try:
            raw.settimeout(10)
            tls=self.tls.wrap_socket(raw,server_side=True)
            protocol=read_auth(tls)
            if protocol not in (MAGIC,CAPS): raise ValueError('Protocol mismatch')
            if not secrets.compare_digest(read_auth(tls),self.options.token.encode('ascii')): raise ValueError('Pairing rejected')
            tls.sendall(frame('OK'))
            if protocol==CAPS:
                tls.sendall(frame(json.dumps(self.offer(),separators=(',',':'),ensure_ascii=True)))
                return
            native=socket.create_connection(('127.0.0.1',self.native_port),timeout=5)
            tls.settimeout(120);native.settimeout(120)
            with self.lock: self.connections.update((tls,native))
            def copy(source,target):
                try:
                    while not self.closed:
                        data=source.recv(65536)
                        if not data: break
                        target.sendall(data)
                except (OSError,ssl.SSLError): pass
                finally:
                    for item in (source,target):
                        try:item.shutdown(socket.SHUT_RDWR)
                        except OSError:pass
            reverse=threading.Thread(target=copy,args=(native,tls),daemon=True)
            reverse.start();copy(tls,native);reverse.join(timeout=2)
        except (OSError,ValueError,EOFError,ssl.SSLError):
            # Do not log credentials or arbitrary peer input.
            print('Peer channel closed or rejected',flush=True)
        finally:
            for item in (raw,tls,native):
                if item:
                    with self.lock:self.connections.discard(item)
                    try:item.close()
                    except OSError:pass
            self.limit.release()
    def run(self):
        self.tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        self.tls.minimum_version=ssl.TLSVersion.TLSv1_2
        self.tls.load_cert_chain(self.options.certificate,self.options.key)
        der=ssl.PEM_cert_to_DER_cert(Path(self.options.certificate).read_text())
        print('Certificate SHA-256:',hashlib.sha256(der).hexdigest(),flush=True)
        with socket.socket() as allocate:
            allocate.bind(('127.0.0.1',0));self.native_port=allocate.getsockname()[1]
        self.native=subprocess.Popen([str(self.options.native),'-H','127.0.0.1','-p',str(self.native_port),
            '--device',self.options.native_device,'-t',str(self.options.threads)],cwd=self.options.workdir)
        try:
            self.listener=socket.socket()
            self.listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
            self.listener.bind((self.options.bind,self.options.port));self.listener.listen(4);self.listener.settimeout(1)
            print('TLS worker:',self.options.bind,self.options.port,'node:',self.identity,flush=True)
            while self.native.poll() is None:
                try: raw,_=self.listener.accept()
                except socket.timeout: continue
                if not self.limit.acquire(blocking=False):raw.close();continue
                threading.Thread(target=self.handle,args=(raw,),daemon=True).start()
            raise RuntimeError('Native RPC worker exited')
        finally:
            self.closed=True
            if self.listener:self.listener.close()
            with self.lock:
                for item in self.connections:
                    try:item.close()
                    except OSError:pass
            if self.native:
                self.native.terminate()
                try:self.native.wait(timeout=5)
                except subprocess.TimeoutExpired:self.native.kill();self.native.wait()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native',type=Path,required=True)
    parser.add_argument('--certificate',type=Path,required=True)
    parser.add_argument('--key',type=Path,required=True)
    parser.add_argument('--token-file',type=Path,required=True)
    parser.add_argument('--node-id',required=True,help='Stable operator-assigned UUID')
    parser.add_argument('--bind',default='127.0.0.1',help='Explicitly select a private interface to enroll from phones')
    parser.add_argument('--port',type=int,default=50551)
    parser.add_argument('--threads',type=int,default=2)
    parser.add_argument('--native-device',default='CPU',help='One installed/probed backend device; invalid names fail native startup')
    parser.add_argument('--memory-budget-mb',type=int,help='Operator limit; required when platform memory probe is unavailable')
    parser.add_argument('--device-class',choices=['server','desktop','laptop','nas','router'],default='server')
    parser.add_argument('--workdir',type=Path,default=Path.cwd())
    options=parser.parse_args()
    options.native=options.native.resolve()
    options.token=options.token_file.read_text().strip()
    if not options.native.is_file() or not os.access(options.native,os.X_OK):parser.error('Native worker must be installed and executable')
    if not 32<=len(options.token)<=128 or any(ord(c)<33 or ord(c)>126 for c in options.token):parser.error('Invalid pairing token')
    if not 1024<=options.port<=65535 or not 1<=options.threads<=32:parser.error('Port/threads out of range')
    if options.memory_budget_mb is not None and options.memory_budget_mb<512:parser.error('Memory budget must be at least 512 MiB')
    if not free_memory() and options.memory_budget_mb is None:parser.error('Memory probe unavailable: supply an operator-verified --memory-budget-mb')
    if not 1<=len(options.native_device)<=64 or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_:-.' for c in options.native_device):parser.error('Invalid device name')
    uuid.UUID(options.node_id)
    try:Worker(options).run()
    except KeyboardInterrupt:pass
if __name__=='__main__':main()
