function pcmWav(bytes,rate,float=false){
 if(!Buffer.isBuffer(bytes)||!Number.isInteger(rate)||rate<8000||rate>48000||!bytes.length||bytes.length>4*1024*1024||bytes.length%(float?4:2))throw Error('Invalid speech PCM');
 let pcm=bytes;
 if(float){pcm=Buffer.alloc(bytes.length/2);for(let i=0;i<bytes.length/4;i++){const value=bytes.readFloatLE(i*4);if(!Number.isFinite(value))throw Error('Non-finite speech samples');pcm.writeInt16LE(Math.max(-32768,Math.min(32767,Math.round(value*32768))),i*2);}}
 const h=Buffer.alloc(44);h.write('RIFF');h.writeUInt32LE(pcm.length+36,4);h.write('WAVEfmt ',8);h.writeUInt32LE(16,16);h.writeUInt16LE(1,20);h.writeUInt16LE(1,22);h.writeUInt32LE(rate,24);h.writeUInt32LE(rate*2,28);h.writeUInt16LE(2,32);h.writeUInt16LE(16,34);h.write('data',36);h.writeUInt32LE(pcm.length,40);return Buffer.concat([h,pcm]);
}
module.exports={pcmWav};
