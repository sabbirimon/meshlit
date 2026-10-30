const test=require('node:test'),assert=require('node:assert/strict');
const {pcmWav}=require('./natural-audio.cjs');
test('natural voice float audio is normalized with a truthful WAV header',()=>{
 const samples=Buffer.alloc(8);samples.writeFloatLE(-1,0);samples.writeFloatLE(0.5,4);
 const wav=pcmWav(samples,24000,true);assert.equal(wav.readUInt32LE(24),24000);assert.equal(wav.readUInt32LE(40),4);assert.equal(wav.readInt16LE(44),-32768);assert.equal(wav.readInt16LE(46),16384);
 samples.writeFloatLE(NaN,0);assert.throws(()=>pcmWav(samples,24000,true));
 assert.throws(()=>pcmWav(Buffer.alloc(3),24000));assert.throws(()=>pcmWav(Buffer.alloc(4),0));
});
