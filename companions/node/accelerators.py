"""Read-only installed vendor SDK/driver/profiler observations. No installation or clocks/root."""
import importlib.util, platform, shutil, time
from pathlib import Path
spec=importlib.util.spec_from_file_location('vendor_bounds',Path(__file__).parents[1]/'cyber/meshlit_cyber.py');bounds=importlib.util.module_from_spec(spec);spec.loader.exec_module(bounds)
TOOLS={
    'nvidia':{'driver':('nvidia-smi',['--query-gpu=name,driver_version','--format=csv,noheader']),'sdk':('nvcc',['--version']),'profiler':('nsys',['--version'])},
    'amd':{'driver':('amd-smi',['static','--json']),'sdk':('hipcc',['--version']),'profiler':('rocprofv3',['--version'])},
    'huawei_ascend':{'driver':('npu-smi',['info']),'profiler':('msprof',['--version'])},
    'qualcomm':{'sdk':('qnn-net-run',['--version']),'profiler':('qnn-profile-viewer',['--help'])},
    'arm_graphics':{'driver':('vulkaninfo',['--summary'])},
}
def probe():
    observations=[]
    for vendor,tools in TOOLS.items():
        records={}
        for role,(name,args) in tools.items():
            executable=shutil.which(name)
            record={'tool':name,'installed':executable is not None,'status':'unavailable','output':None}
            if executable:
                try:
                    data=bounds.bounded_process([executable]+args,limit=32768,timeout=5)
                    record.update(status='command_completed',output=data.decode('utf-8',errors='replace'))
                except Exception:record['status']='probe_failed_or_unsupported'
            records[role]=record
        observations.append({'vendor':vendor,'observations':records,'inference_qualified':False})
    return {'schema':1,'observed_at_ms':int(time.time()*1000),'host_os':platform.system(),'abi':platform.machine(),'vendors':observations,'inference_qualified':False,'coverage':'installed tool/version/driver observations only; no model execution, profiler capture, clock changes, shared-memory budget or accelerator admission'}
