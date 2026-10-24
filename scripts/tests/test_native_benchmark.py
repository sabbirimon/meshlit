import importlib.util
import json
from pathlib import Path
import time
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location("native_benchmark",Path(__file__).parents[1]/"native_benchmark.py")
bench=importlib.util.module_from_spec(spec)
spec.loader.exec_module(bench)

def wire(*items):
    data="".join(f"event: {kind}\r\ndata: {json.dumps(payload)}\r\n\r\n" for kind,payload in items)
    return data.encode().splitlines(keepends=True)

class NativeBenchmarkContractTest(unittest.TestCase):
    def measure(self,lines,max_tokens=16):
        start=time.monotonic()
        return bench.measure_stream(lines,start,max_tokens,start+10)
    def completed(self,**kwargs):
        return {"finishReason":"max_tokens","generatedTokens":16,"totalDurationMs":50,"tokensPerSecond":320,**kwargs}
    def test_real_event_fields_and_engine_usage(self):
        result=self.measure(wire(("token",{"text":"hello world"}),("token",{"text":"!"}),("done",self.completed())))
        self.assertEqual(2,result["textEventCount"])
        self.assertEqual(16,result["generatedTokens"])
        self.assertEqual(320,result["engineTokensPerSecond"])
        self.assertNotIn("firstTokenMs",result)
        self.assertNotIn("prompt",result)
    def test_unknown_native_usage_remains_null(self):
        result=self.measure(wire(("token",{"text":"many words"}),("done",self.completed(generatedTokens=None,tokensPerSecond=None))))
        self.assertIsNone(result["generatedTokens"])
        self.assertIsNone(result["engineTokensPerSecond"])
    def test_premature_end_and_truncated_frame_fail(self):
        for lines in (wire(("token",{"text":"hello"})),[b"event: token\n",b'data: {"text":"hello"}\n']):
            with self.assertRaises(bench.BenchmarkError):self.measure(lines)
    def test_error_after_text_fails_without_returning_partial_success(self):
        with self.assertRaises(bench.BenchmarkError):self.measure(wire(("token",{"text":"partial"}),("error",{"tag":"native","message":"private"})))
    def test_invalid_engine_usage_fails(self):
        for usage in ({"generatedTokens":-1},{"generatedTokens":0},{"generatedTokens":17},{"generatedTokens":True},{"generatedTokens":1.5},{"tokensPerSecond":float("nan")},{"totalDurationMs":None}):
            with self.subTest(usage=usage),self.assertRaises(bench.BenchmarkError):
                self.measure(wire(("token",{"text":"hello"}),("done",self.completed(**usage))))
    def test_cancelled_or_empty_completion_is_not_benchmark_success(self):
        with self.assertRaises(bench.BenchmarkError):self.measure(wire(("token",{"text":"hello"}),("done",self.completed(finishReason="cancelled"))))
        with self.assertRaises(bench.BenchmarkError):self.measure(wire(("done",self.completed())))
    def test_oversized_and_expired_stream_fail(self):
        with self.assertRaises(bench.BenchmarkError):self.measure([b"data: "+b"x"*65536+b"\n"])
        with self.assertRaises(bench.BenchmarkError):bench.measure_stream([b": alive\n"],time.monotonic(),16,0)
    def test_sse_comments_and_multiple_data_lines(self):
        events=list(bench.events([b": keepalive\n",b"event: token\n",b'data: {\n',b'data: "text":"hello"}\n',b"\n"],time.monotonic()+10))
        self.assertEqual([("token",{"text":"hello"})],events)
    def test_loopback_http_and_https_only_without_secrets(self):
        for base in ("http://127.0.0.1:18080","http://[::1]:8080","https://example.com"):
            self.assertEqual(base,bench.validate_base(base))
        for base in ("http://10.0.0.2:8080","http://localhost.attacker.invalid","https://secret@example.com","https://example.com/?token=x"):
            with self.assertRaises(bench.BenchmarkError):bench.validate_base(base)
    def test_redirect_is_not_followed(self):
        with self.assertRaises(bench.BenchmarkError):bench.NoRedirect().redirect_request(None,None,302,None,None,"https://elsewhere.invalid")
    def test_readiness_uses_model_endpoint_and_exact_engine(self):
        responses={"/v1/health":{"status":"ok","engineTag":"llama-native-local"},"/v1/model":{"loaded":True,"contextSize":512}}
        with patch.object(bench,"get_json",side_effect=lambda base,route:responses[route]):
            _,model=bench.wait_ready("http://127.0.0.1","llama-native-local")
            self.assertEqual(512,model["contextSize"])
            with self.assertRaises(bench.BenchmarkError):bench.wait_ready("http://127.0.0.1","runanywhere")
    def test_request_matches_camel_case_wire_and_no_prompt_export(self):
        class Response:
            headers=type("Headers",(),{"get_content_type":lambda _:"text/event-stream"})()
            def __init__(self):self.lines=iter(wire(("token",{"text":"hello"}),("done",NativeBenchmarkContractTest().completed())))
            def __enter__(self):return self
            def __exit__(self,*args):pass
            def readline(self,limit):return next(self.lines,b"")
        with patch.object(bench.OPENER,"open",return_value=Response()) as open_call:
            result=bench.stream_infer("http://127.0.0.1:18080","private test prompt",16)
            body=json.loads(open_call.call_args.args[0].data)
            self.assertEqual(16,body["maxTokens"])
            self.assertNotIn("max_tokens",body)
            self.assertNotIn("private test prompt",json.dumps(result))

if __name__=="__main__":unittest.main()
