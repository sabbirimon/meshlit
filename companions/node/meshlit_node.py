#!/usr/bin/env python3
"""Original portable storage/probe and offline IQ analysis companion. No arbitrary exec or RF TX."""
import argparse, base64, hashlib, hmac, http.server, json, math, os, platform, shutil, struct, sys, socket, tempfile, threading
from pathlib import Path
SHA_CHARS=set('0123456789abcdef');MAX_OBJECT=2*1024**3;QUOTA=8*1024**3

def digest(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for data in iter(lambda:stream.read(1024*1024),b''):h.update(data)
    return h.hexdigest()
def valid_sha(value):return isinstance(value,str) and len(value)==64 and set(value)<=SHA_CHARS
def probe(root):
    usage=shutil.disk_usage(root)
    return {'schema':1,'os':platform.system(),'architecture':platform.machine(),'python':platform.python_version(),'logical_cpus':os.cpu_count(),'storage_free_bytes':usage.free,'storage_quota_bytes':QUOTA,'capabilities':['content_addressed_storage','offline_iq_fft'],'transformer_execution':False,'radio_rx':False,'radio_tx':False,'gpu_npu_execution':False}

class Objects:
    def __init__(self,root):
        self.root=Path(root)
        if not self.root.is_absolute() or self.root.is_symlink():raise ValueError('Owned absolute object directory required')
        self.root.mkdir(mode=0o700,parents=True,exist_ok=True)
        if os.name!='nt' and self.root.stat().st_mode&0o077:raise ValueError('Object directory must be private')
        self.lock=threading.Lock()
    def target(self,sha):
        if not valid_sha(sha):raise ValueError('SHA-256 object identity required')
        path=self.root/sha
        if path.is_symlink():raise ValueError('Object symlink rejected')
        return path
    def stage(self,source,expected):
        source=Path(source);target=self.target(expected)
        if not source.is_absolute() or not source.is_file() or not 0<source.stat().st_size<=MAX_OBJECT:raise ValueError('Object input invalid')
        with self.lock:
            if target.exists():
                if digest(target)!=expected:raise ValueError('Stored object corrupt')
                return {'sha256':expected,'bytes':target.stat().st_size,'already_present':True}
            total=sum(p.stat().st_size for p in self.root.iterdir() if p.is_file())
            if total+source.stat().st_size>QUOTA or shutil.disk_usage(self.root).free<source.stat().st_size+64*1024**2:raise ValueError('Storage budget insufficient')
            fd,name=tempfile.mkstemp(prefix='.stage-',dir=self.root);h=hashlib.sha256();count=0
            try:
                with os.fdopen(fd,'wb') as output,source.open('rb') as stream:
                    for data in iter(lambda:stream.read(1024*1024),b''):
                        count+=len(data)
                        if count>MAX_OBJECT or total+count>QUOTA:raise ValueError('Object grew beyond quota')
                        h.update(data);output.write(data)
                    output.flush();os.fsync(output.fileno())
                if h.hexdigest()!=expected:raise ValueError('Object digest mismatch')
                os.replace(name,target)
                return {'sha256':expected,'bytes':count,'already_present':False}
            finally:Path(name).unlink(missing_ok=True)
    def read(self,sha,offset,length):
        if type(offset)!=int or type(length)!=int or offset<0 or not 1<=length<=1024*1024:raise ValueError('Chunk range invalid')
        with self.lock:
            path=self.target(sha)
            if not path.is_file() or path.stat().st_size>MAX_OBJECT or offset>path.stat().st_size:raise ValueError('Object unavailable or range invalid')
            with path.open('rb') as stream:stream.seek(offset);data=stream.read(length)
            return {'object_sha256':sha,'object_bytes':path.stat().st_size,'offset':offset,'bytes':len(data),'chunk_sha256':hashlib.sha256(data).hexdigest(),'base64':base64.b64encode(data).decode(),'verification':'verify full assembled object SHA-256 before use; chunk integrity alone is insufficient'}

def fft(values):
    n=len(values)
    if n==1:return values
    even=fft(values[::2]);odd=fft(values[1::2]);result=[0j]*n
    for k in range(n//2):
        angle=-2*math.pi*k/n;term=complex(math.cos(angle),math.sin(angle))*odd[k]
        result[k]=even[k]+term;result[k+n//2]=even[k]-term
    return result
def iq_spectrum(path,expected,sample_rate,samples=1024):
    path=Path(path)
    if not path.is_absolute() or not path.is_file() or path.stat().st_size>128*1024**2 or digest(path)!=expected:raise ValueError('Owned IQ artifact digest required')
    if not math.isfinite(sample_rate) or not 0<sample_rate<=1e9 or samples not in (256,512,1024,2048,4096):raise ValueError('IQ parameters invalid')
    with path.open('rb') as stream:data=stream.read(samples*8)
    if len(data)!=samples*8:raise ValueError('IQ recording truncated')
    raw=struct.unpack('<'+'f'*samples*2,data)
    if any(not math.isfinite(v) for v in raw):raise ValueError('Nonfinite samples')
    spectrum=fft([complex(raw[i*2],raw[i*2+1]) for i in range(samples)])
    peaks=sorted(enumerate(spectrum),key=lambda item:abs(item[1]),reverse=True)[:16]
    if digest(path)!=expected:raise ValueError('IQ input changed')
    return {'schema':1,'input_sha256':expected,'sample_rate_hz':sample_rate,'samples_analyzed':samples,'format':'interleaved little-endian float32 I/Q','window':'rectangular','calibrated':False,'coverage':'offline spectral peaks only; no live hardware, demodulation, absolute dBm, center-frequency inference or transmission','peaks':[{'offset_hz':(i if i<samples//2 else i-samples)*sample_rate/samples,'magnitude':abs(v)/samples} for i,v in peaks]}

def accelerator_probe():
    import importlib.util
    spec=importlib.util.spec_from_file_location('meshlit_vendor_probe',Path(__file__).with_name('accelerators.py'));module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    value=module.probe()
    topology_spec=importlib.util.spec_from_file_location("meshlit_topology",Path(__file__).with_name("topology.py"));topology=importlib.util.module_from_spec(topology_spec);topology_spec.loader.exec_module(topology)
    value["topology"]=topology.observe();return value
def server(objects,token,port,allow_accelerators=False):
    if not 1024<=port<=65535 or len(token)<32 or any(c.isspace() for c in token):raise ValueError('Port/token invalid')
    admission=threading.BoundedSemaphore(2)
    class Handler(http.server.BaseHTTPRequestHandler):
        protocol_version="HTTP/1.1"
        def log_message(self,*args):pass
        def do_POST(self):
            self.connection.settimeout(5)
            if self.headers.get('Origin') is not None or self.path!='/node' or not hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+token):self.send_error(403);return
            if not admission.acquire(blocking=False):self.send_error(429);return
            try:
                size=int(self.headers.get('Content-Length','0'))
                if not 0<size<=4096 or self.headers.get('Transfer-Encoding') or self.headers.get_content_type()!='application/json':raise ValueError()
                raw=self.rfile.read(size)
                if len(raw)!=size:raise ValueError()
                request=json.loads(raw)
                if request['operation']=='probe':value=probe(objects.root)
                elif request['operation']=='accelerators' and allow_accelerators:value=accelerator_probe()
                elif request['operation']=='read':value=objects.read(request['sha256'],request['offset'],request['length'])
                else:raise ValueError()
                encoded=json.dumps(value).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(encoded)));self.end_headers();self.wfile.write(encoded)
            except Exception:self.send_error(400,'Invalid or unavailable node operation')
            finally:admission.release()
    class Server(http.server.ThreadingHTTPServer):
        def get_request(self):
            connection,address=super().get_request();connection.settimeout(5);connection.setsockopt(socket.IPPROTO_TCP,socket.TCP_NODELAY,1);return connection,address
    return Server(('127.0.0.1',port),Handler)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['probe','stage','serve','iq','accelerators']);parser.add_argument('--objects',type=Path);parser.add_argument('--file',type=Path);parser.add_argument('--sha256');parser.add_argument('--token-file',type=Path);parser.add_argument('--port',type=int,default=18895);parser.add_argument('--sample-rate',type=float);parser.add_argument('--samples',type=int,default=1024);parser.add_argument('--allow-accelerator-probe',action='store_true');args=parser.parse_args()
    try:
        if args.action=='accelerators':value=accelerator_probe()
        elif args.action=='iq':value=iq_spectrum(args.file,args.sha256,args.sample_rate,args.samples)
        else:
            objects=Objects(args.objects)
            if args.action=='serve':
                if args.token_file.is_symlink() or args.token_file.stat().st_size>4096 or os.name!='nt' and args.token_file.stat().st_mode&0o077:raise ValueError('Private token file required')
                server(objects,args.token_file.read_text().strip(),args.port,args.allow_accelerator_probe).serve_forever();return 0
            value=probe(objects.root) if args.action=='probe' else objects.stage(args.file,args.sha256)
        print(json.dumps(value));return 0
    except Exception:print(json.dumps({'status':'failed','error':'Owned artifact, token, range or capability check failed'}));return 1
if __name__=='__main__':sys.exit(main())
