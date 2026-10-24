"""Bounded read-only OS topology observations; no advertised peak speeds or inferred RDMA."""
import platform,re,time
from pathlib import Path

def read(path,limit=32768):
    try:
        with Path(path).open('rb') as stream:data=stream.read(limit+1)
        if len(data)>limit:return None
        return data.decode('utf-8',errors='replace').strip()
    except (OSError,ValueError):return None

def observe():
    memory=[];fabrics=[];now=int(time.time()*1000)
    if platform.system()=='Linux':
        raw=read('/proc/meminfo');fields={}
        if raw:
            for name,value in re.findall(r'^(MemTotal|MemAvailable):\s+(\d+) kB$',raw,re.M):fields[name]=int(value)*1024
            memory.append({'pool_id':'system','sharing_domain':'system-ram','kind':'SYSTEM_RAM','capacity_bytes':fields.get('MemTotal'),'allocatable_bytes':fields.get('MemAvailable'),'bus_width_bits':None,'transfer_rate_mts':None,'measured_bandwidth_bytes_per_second':None,'source':'/proc/meminfo','observed_at_ms':now})
        for directory in sorted(Path('/sys/devices/system/node').glob('node[0-9]*'))[:128]:
            memory.append({'numa_node':directory.name,'source':str(directory),'meminfo':read(directory/'meminfo'),'distance':read(directory/'distance'),'kind':'UNKNOWN','observed_at_ms':now,'note':'NUMA locality is not measured bandwidth; node views overlap the global system pool'})
        for directory in sorted(Path('/sys/class/net').glob('*'))[:128]:
            speed=read(directory/'speed',64);speed=int(speed)*1000000 if speed and speed.isdecimal() and int(speed)>0 else None
            fabrics.append({'interface':directory.name,'source':str(directory),'carrier':read(directory/'carrier',16),'operstate':read(directory/'operstate',64),'reported_link_bits_per_second':speed,'physical_medium':None,'rdma_qualified':False,'ip_reachability_verified':False,'observed_at_ms':now})
    return {'schema':1,'observed_at_ms':now,'host_os':platform.system(),'abi':platform.machine(),'memory':memory,'fabrics':fabrics,'coverage':'OS-exposed fields only; no DDR/HBM inference from chip name, bus-width guessing, measured bandwidth, optical-module discovery or native fabric execution'}
