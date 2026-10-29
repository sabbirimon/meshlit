const {test}=require('node:test');const assert=require('node:assert/strict');
const {parseHarnessEndpoint:parse}=require('./harness-endpoint.cjs');
test('separates launch credential from saved endpoint',()=>{
 const r=parse('https://harness.example/ui/?token=abcdefghijklmnop');
 assert.equal(r.endpoint,'https://harness.example/ui/');assert.equal(r.origin,'https://harness.example');
 assert.equal(r.launchUrl,'https://harness.example/ui/?token=abcdefghijklmnop');
});
test('accepts TLS hosts and numeric loopback only for HTTP',()=>{
 for(const url of ['https://192.168.1.2/','http://127.0.0.1:3080/','http://[::1]:3080/']) assert.ok(parse(url));
 for(const url of ['http://192.168.1.2/','http://localhost/','http://127.1/','http://2130706433/','file:///tmp/test','javascript:alert(1)','https://x:0/']) assert.throws(()=>parse(url));
});
test('rejects credentials, token confusion and non-root paths',()=>{
 for(const url of ['https://user:pass@host/','https://host/#token=x','https://host/?token=short','https://host/?token=abcdefghijklmnop&token=abcdefghijklmnop','https://host/?key=abcdefghijklmnop','https://host/../','https://host/%2f/','https://host/api','https://host/ space/','https://host/\\evil'])assert.throws(()=>parse(url));
});
