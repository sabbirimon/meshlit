import importlib.util, json, math, struct, tempfile, threading, unittest, urllib.request, urllib.error
from pathlib import Path
spec=importlib.util.spec_from_file_location('node',Path(__file__).parents[1]/'meshlit_node.py');node=importlib.util.module_from_spec(spec);spec.loader.exec_module(node)
class NodeTests(unittest.TestCase):
    def test_vendor_and_topology_probes_never_invent_qualification(self):
        value=node.accelerator_probe();self.assertFalse(value["inference_qualified"]);self.assertFalse(any(v["inference_qualified"] for v in value["vendors"]));self.assertEqual(1,value["topology"]["schema"])
    def test_stage_range_reassembly_and_digest_rejection(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);path=root/'owned.bin';path.write_bytes(bytes(range(256))*32);objects=node.Objects(root/'objects');sha=node.digest(path)
            objects.stage(path,sha);a=objects.read(sha,0,3000);b=objects.read(sha,3000,10000)
            self.assertEqual(node.base64.b64decode(a['base64'])+node.base64.b64decode(b['base64']),path.read_bytes())
            with self.assertRaises(ValueError):objects.stage(path,'0'*64)
            with self.assertRaises(ValueError):objects.read('../escape',0,1)
            self.assertFalse(any(p.name.startswith('.stage-') for p in (root/'objects').iterdir()))
    def test_owned_iq_fft_known_frequency_and_truncation(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'iq.f32';n=256;values=[]
            for i in range(n):values.extend([math.cos(2*math.pi*32*i/n),math.sin(2*math.pi*32*i/n)])
            path.write_bytes(struct.pack('<'+'f'*len(values),*values))
            result=node.iq_spectrum(path,node.digest(path),256000,n)
            self.assertEqual(32000,result['peaks'][0]['offset_hz']);self.assertFalse(result['calibrated'])
            with self.assertRaises(ValueError):node.iq_spectrum(path,node.digest(path),256000,512)
    def test_probe_reports_actual_machine_without_invented_execution(self):
        with tempfile.TemporaryDirectory() as temp:
            value=node.probe(temp);self.assertGreater(value['storage_free_bytes'],0);self.assertFalse(value['transformer_execution']);self.assertFalse(value['radio_tx'])
    def test_authenticated_real_socket_read_and_origin_denial(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);objects=node.Objects(root/'objects');path=root/'blob';path.write_bytes(b'owned storage proof');sha=node.digest(path);objects.stage(path,sha)
            # Select an unused loopback port; production validation refuses privileged ports.
            import socket
            with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
            server=node.server(objects,'x'*40,port);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
            try:
                def req(headers):return urllib.request.Request('http://127.0.0.1:'+str(port)+'/node',data=json.dumps({'operation':'read','sha256':sha,'offset':0,'length':100}).encode(),headers={'Content-Type':'application/json',**headers})
                with urllib.request.urlopen(req({'Authorization':'Bearer '+'x'*40}),timeout=5) as reply:self.assertEqual('owned storage proof',node.base64.b64decode(json.load(reply)['base64']).decode())
                import http.client
                connection=http.client.HTTPConnection('127.0.0.1',port,timeout=5)
                try:
                    for _ in range(2):
                        connection.request('POST','/node',json.dumps({'operation':'probe'}),{'Content-Type':'application/json','Authorization':'Bearer '+'x'*40})
                        response=connection.getresponse();self.assertEqual(200,response.status);self.assertFalse(response.will_close);self.assertEqual(1,json.loads(response.read())['schema'])
                    connection.request('POST','/node',json.dumps({'operation':'accelerators'}),{'Content-Type':'application/json','Authorization':'Bearer '+'x'*40})
                    response=connection.getresponse();self.assertEqual(400,response.status);response.read()
                finally:connection.close()
                with self.assertRaises(urllib.error.HTTPError):urllib.request.urlopen(req({'Authorization':'Bearer '+'x'*40,'Origin':'https://untrusted.example'}),timeout=5)
            finally:server.shutdown();server.server_close();thread.join(timeout=5)
