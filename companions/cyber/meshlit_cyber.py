#!/usr/bin/env python3
"""Original read-only assessment companion. No third-party tools bundled."""
import argparse, hashlib, json, os, shutil, struct, subprocess, sys, zipfile, selectors, signal, time, sqlite3, urllib.parse
from pathlib import Path
from contextlib import closing
MAX_INPUT=512*1024*1024
TOOLS={'androguard':'androguard','quark':'quark','tshark':'tshark','objection':'objection','frida':'frida','drozer':'drozer','metasploit':'msfconsole','sleuthkit':'fsstat','autopsy':'autopsy'}
def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b''): h.update(chunk)
    return h.hexdigest()
def bounded_process(argv,limit=8192,timeout=40):
    """Capture bounded metadata; kill the isolated process group on every failure."""
    proc=subprocess.Popen(argv,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,start_new_session=True)
    deadline=time.monotonic()+timeout
    data=bytearray()
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(proc.stdout,selectors.EVENT_READ)
            while selector.get_map():
                remaining=deadline-time.monotonic()
                if remaining<=0: raise TimeoutError('Tool deadline exceeded')
                for key,_ in selector.select(min(remaining,1)):
                    chunk=os.read(key.fileobj.fileno(),65536)
                    if not chunk: selector.unregister(key.fileobj);continue
                    data.extend(chunk)
                    if len(data)>limit: raise ValueError('Tool output budget exceeded')
        if proc.wait(timeout=max(0.001,deadline-time.monotonic()))!=0: raise ValueError('Tool failed')
        return bytes(data)
    except BaseException:
        try: os.killpg(proc.pid,signal.SIGKILL)
        except ProcessLookupError: pass
        proc.wait();raise
    finally: proc.stdout.close()

def analyze(tool,path,expected):
    path=Path(path)
    if not path.is_absolute() or not path.is_file() or path.stat().st_size>MAX_INPUT: raise ValueError('Input missing or exceeds 512 MiB')
    actual=digest(path)
    if actual.lower()!=expected.lower(): raise ValueError('Input digest mismatch')
    if tool=='apk_inventory':
        with zipfile.ZipFile(path) as archive:
            entries=archive.infolist()
            if len(entries)>20000: raise ValueError('ZIP entry budget exceeded')
            names=[item.filename for item in entries]
            if any(name.startswith('/') or '..' in Path(name).parts for name in names): raise ValueError('Unsafe archive paths')
            if 'AndroidManifest.xml' not in names: raise ValueError('Not an APK')
            if sum(item.file_size for item in entries)>2*1024**3: raise ValueError('Expanded archive budget exceeded')
            findings={'entry_count':len(entries),'manifest_present':True,'dex_files':[n for n in names if n.endswith('.dex')][:100],'native_libraries':[n for n in names if n.startswith('lib/') and n.endswith('.so')][:500],'signature_entries':[n for n in names if n.startswith('META-INF/')][:100]}
            coverage='ZIP metadata only; no binary manifest decoding, signature verification or malware verdict'
    elif tool=='elf_inventory':
        with path.open('rb') as stream: header=stream.read(64)
        if len(header)<52 or header[:4]!=b'\x7fELF' or header[4] not in (1,2) or header[5] not in (1,2) or header[6]!=1: raise ValueError('Invalid ELF header')
        endian='<' if header[5]==1 else '>'
        bits=32 if header[4]==1 else 64
        fmt=endian+('HHIIIIIHHHHHH' if bits==32 else 'HHIQQQIHHHHHH')
        values=struct.unpack_from(fmt,header,16)
        kind,machine,version,entry,phoff,shoff,flags,ehsize,phentsize,phnum,shentsize,shnum,shstr=values
        size=path.stat().st_size
        if version!=1 or ehsize!=(52 if bits==32 else 64): raise ValueError('Invalid ELF version/size')
        if phnum==65535 or shnum==0 and shoff!=0 or shstr==65535: raise ValueError('Extended ELF counts unsupported')
        if phnum and (phentsize!=(32 if bits==32 else 56) or phoff<ehsize or phoff+phnum*phentsize>size): raise ValueError('Invalid program table')
        if shnum and (shentsize!=(40 if bits==32 else 64) or shoff<ehsize or shoff+shnum*shentsize>size): raise ValueError('Invalid section table')
        findings={'bits':bits,'byte_order':'little' if endian=='<' else 'big','machine':machine,'elf_type':kind,'program_headers':phnum,'section_headers':shnum}
        coverage='ELF header/table bounds and architecture metadata only; no execution, disassembly or malware verdict'
    elif tool=='sqlite_metadata':
        with path.open('rb') as stream:
            if stream.read(16)!=b'SQLite format 3\x00': raise ValueError('Not SQLite')
        uri='file:'+urllib.parse.quote(str(path),safe='/')+'?mode=ro&immutable=1'
        deadline=time.monotonic()+10
        with closing(sqlite3.connect(uri,uri=True,timeout=2)) as database:
            if hasattr(database,'enable_load_extension'):database.enable_load_extension(False)
            database.set_progress_handler(lambda: int(time.monotonic()>deadline),1000)
            database.execute('PRAGMA query_only=ON')
            objects=database.execute('SELECT type,name FROM sqlite_master ORDER BY name LIMIT 201').fetchall()
            if len(objects)>200 or any(len(name)>256 for _,name in objects): raise ValueError('Schema metadata budget exceeded')
            findings={'page_size':database.execute('PRAGMA page_size').fetchone()[0],'pages':database.execute('PRAGMA page_count').fetchone()[0],'free_pages':database.execute('PRAGMA freelist_count').fetchone()[0],'objects':[{'type':kind,'name':name} for kind,name in objects]}
        coverage='Immutable read-only SQLite schema/page metadata; no row contents, WAL recovery or forensic completeness claim'
    elif tool=='pcap_summary':
        magic={b'\xd4\xc3\xb2\xa1':('<',1000000),b'\xa1\xb2\xc3\xd4':('>',1000000),b'\x4d\x3c\xb2\xa1':('<',1000000000),b'\xa1\xb2\x3c\x4d':('>',1000000000)}
        with path.open('rb') as stream:
            header=stream.read(24)
            if len(header)!=24 or header[:4] not in magic: raise ValueError('Only classic PCAP supported')
            endian,scale=magic[header[:4]];major,minor,_,_,snap,link=struct.unpack(endian+'HHiIII',header[4:])
            if (major,minor)!=(2,4) or not 1<=snap<=16*1024*1024: raise ValueError('Invalid PCAP header')
            packets=total=0
            while True:
                record=stream.read(16)
                if not record: break
                if len(record)!=16: raise ValueError('Truncated packet header')
                seconds,fraction,captured,original=struct.unpack(endian+'IIII',record)
                if fraction>=scale or captured>snap or captured>original: raise ValueError('Invalid packet limits')
                if len(stream.read(captured))!=captured: raise ValueError('Truncated packet')
                packets+=1;total+=captured
                if packets>1000000: raise ValueError('Packet budget exceeded')
            findings={'packets':packets,'captured_bytes':total,'link_type':link,'timestamp_resolution':scale};coverage='Record structure only; no payload export, decryption or protocol verdict'
    elif tool=='tshark':
        binary=shutil.which('tshark')
        if not binary: raise ValueError('tshark is not installed')
        data=bounded_process([binary,'-n','-r',str(path),'-c','10000','-T','fields','-e','frame.number','-e','frame.len'],limit=1024*1024)
        lines=data.decode().splitlines();findings={'packets_inspected':len(lines),'packet_limit':10000};coverage='Bounded count-only tshark analysis; capture may contain more packets'
    elif tool=='sleuthkit':
        binary=shutil.which('fsstat')
        if not binary: raise ValueError('Sleuth Kit fsstat is not installed')
        data=bounded_process([binary,'-i','raw',str(path)])
        findings={'filesystem_metadata':data.decode('utf-8',errors='replace')}
        coverage='Read-only fsstat raw filesystem-image metadata only; no partition offset selection, file recovery or Autopsy integration'
    else: raise ValueError('Adapter not implemented')
    if digest(path)!=actual: raise ValueError('Input changed during analysis')
    return {'schema':1,'tool':tool,'status':'completed','input_sha256':actual,'findings':findings,'coverage':coverage}
def main():
    parser=argparse.ArgumentParser();sub=parser.add_subparsers(dest='action',required=True)
    sub.add_parser('probe');run=sub.add_parser('run');run.add_argument('--tool',choices=['apk_inventory','pcap_summary','tshark','sleuthkit','elf_inventory','sqlite_metadata'],required=True);run.add_argument('--input',required=True);run.add_argument('--sha256',required=True)
    args=parser.parse_args()
    try:
        value={'tools':{name:{'installed':shutil.which(binary) is not None,'adapter_implemented':name in {'tshark','sleuthkit'}} for name,binary in TOOLS.items()}} if args.action=='probe' else analyze(args.tool,args.input,args.sha256)
        print(json.dumps(value));return 0
    except Exception:
        print(json.dumps({'status':'failed','error':'Input, digest, installed tool or bounded analysis check failed'}));return 1
if __name__=='__main__': sys.exit(main())
