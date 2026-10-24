#!/usr/bin/env python3
"""Measure an operator-owned node's authenticated application RTT, not NIC or HFT latency."""
import argparse, http.client, ipaddress, json, math, os, platform, statistics, time
from pathlib import Path
from urllib.parse import urlsplit

def benchmark(endpoint,token,samples=100,warmup=10):
    parsed=urlsplit(endpoint)
    if parsed.scheme not in ('https','http') or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path!='/node':raise ValueError('Exact node endpoint required')
    if parsed.scheme=='http' and not ipaddress.ip_address(parsed.hostname).is_loopback:raise ValueError('Plain HTTP requires literal loopback')
    if not 1<=samples<=1000 or not 0<=warmup<=100 or len(token)<32 or any(c.isspace() for c in token):raise ValueError('Bounded sample count and private token required')
    cls=http.client.HTTPSConnection if parsed.scheme=='https' else http.client.HTTPConnection
    connection=cls(parsed.hostname,parsed.port,timeout=5)
    values=[];keepalive=True;deadline=time.monotonic()+60
    try:
        for index in range(warmup+samples):
            if time.monotonic()>deadline:raise TimeoutError('Benchmark deadline')
            start=time.perf_counter_ns()
            connection.request('POST','/node',b'{"operation":"probe"}',{'Content-Type':'application/json','Authorization':'Bearer '+token})
            response=connection.getresponse();body=response.read(65537)
            elapsed=(time.perf_counter_ns()-start)/1000
            if response.status!=200 or len(body)>65536 or json.loads(body).get('schema')!=1:raise ValueError('Node probe failed')
            keepalive=keepalive and not response.will_close
            if index>=warmup:values.append(elapsed)
    finally:connection.close()
    ordered=sorted(values)
    def quantile(p):return ordered[max(0,math.ceil(p*len(ordered))-1)]
    return {'schema':1,'measured_at_unix_ms':time.time_ns()//1_000_000,'client_os':platform.system(),'client_architecture':platform.machine(),'samples':samples,'warmup':warmup,'clock':'perf_counter_ns','units':'microseconds','http_keepalive_observed':keepalive,'tls':parsed.scheme=='https','rtt':{'min':min(values),'p50':quantile(.5),'p95':quantile(.95),'p99':quantile(.99),'max':max(values),'population_stddev':statistics.pstdev(values)},'qualification':'sequential client application RTT including auth, JSON and node OS/storage probe; not one-way latency, line rate, zero-copy, NIC/PTP, load, GPU/RDMA or HFT qualification'}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--endpoint',required=True);parser.add_argument('--token-file',type=Path,required=True);parser.add_argument('--samples',type=int,default=100);parser.add_argument('--warmup',type=int,default=10);args=parser.parse_args()
    path=args.token_file
    if path.is_symlink() or not path.is_file() or path.stat().st_size>4096 or os.name!='nt' and path.stat().st_mode&0o077:raise ValueError('Private token file required')
    print(json.dumps(benchmark(args.endpoint,path.read_text().strip(),args.samples,args.warmup),indent=2))
if __name__=='__main__':main()
