// Original test tones, stored as standards-compliant uncompressed FLAC frames.
// No music recordings or encoder dependencies are needed for playback tests.
const fs=require('node:fs');const path=require('node:path');const crypto=require('node:crypto');
const folder=path.resolve('android/app/src/androidTest/assets');fs.mkdirSync(folder,{recursive:true});
function crc(bytes,width,poly) {let value=0;for(const byte of bytes){value^=byte<<(width-8);for(let i=0;i<8;i++)value=((value<<1)^((value&(1<<(width-1)))?poly:0))&((1<<width)-1);}return value;}
function flac(name,rate,bits,channels,blocks) {
  const blockSize=4096;const samples=blockSize*blocks;const width=bits/8;
  const pcm=Buffer.alloc(samples*channels*width);const amplitude=bits===24?50000:400;
  for(let i=0;i<samples;i++)for(let channel=0;channel<channels;channel++)pcm.writeIntLE(Math.round(Math.sin(2*Math.PI*(220+channel*110)*i/rate)*amplitude),(i*channels+channel)*width,width);
  const info=Buffer.alloc(34);info.writeUInt16BE(blockSize,0);info.writeUInt16BE(blockSize,2);
  info.writeBigUInt64BE((BigInt(rate)<<44n)|(BigInt(channels-1)<<41n)|(BigInt(bits-1)<<36n)|BigInt(samples),10);
  crypto.createHash('md5').update(pcm).digest().copy(info,18);
  const comments=['TITLE='+name,'ARTIST=小红花','ALBUM=功能验证','LYRICS=[00:00.00]音乐在这里开花\n[00:01.25]这是一段原创测试音\n[00:03.50]歌词跟随真实播放时间\n[00:05.70]上下滑动自由浏览'];
  const vorbis=[];function le(n){const b=Buffer.alloc(4);b.writeUInt32LE(n);return b;}
  vorbis.push(le(0),le(comments.length));for(const text of comments){const b=Buffer.from(text);vorbis.push(le(b.length),b);}
  const comment=Buffer.concat(vorbis);const metadata=[Buffer.from('fLaC'),Buffer.from([0,0,0,34]),info,Buffer.from([132,comment.length>>>16,(comment.length>>>8)&255,comment.length&255]),comment];
  for(let frame=0;frame<blocks;frame++) {
    const number=Buffer.from(String.fromCodePoint(frame));
    const head=Buffer.concat([Buffer.from([255,248,192,((channels-1)<<4)|((bits===24?6:4)<<1)]),number]);
    const parts=[head,Buffer.from([crc(head,8,7)])];
    for(let channel=0;channel<channels;channel++) {
      const sub=Buffer.alloc(1+blockSize*width);sub[0]=2;
      for(let i=0;i<blockSize;i++)sub.writeIntBE(pcm.readIntLE(((frame*blockSize+i)*channels+channel)*width,width),1+i*width,width);
      parts.push(sub);
    }
    const body=Buffer.concat(parts);const check=Buffer.alloc(2);check.writeUInt16BE(crc(body,16,0x8005));metadata.push(body,check);
  }
  const file=path.join(folder,bits===24?'tone-24bit-96k.flac':'tone.flac');fs.writeFileSync(file,Buffer.concat(metadata));console.log(path.basename(file),fs.statSync(file).size,rate,bits,channels,samples/rate);
}
flac('测试音 · 春风',44100,16,1,240);
flac('测试音 · 高解析',96000,24,2,120);
const frames=44100*12;const wav=Buffer.alloc(44+frames*2);wav.write('RIFF');wav.writeUInt32LE(wav.length-8,4);wav.write('WAVEfmt ',8);wav.writeUInt32LE(16,16);wav.writeUInt16LE(1,20);wav.writeUInt16LE(1,22);wav.writeUInt32LE(44100,24);wav.writeUInt32LE(88200,28);wav.writeUInt16LE(2,32);wav.writeUInt16LE(16,34);wav.write('data',36);wav.writeUInt32LE(frames*2,40);
for(let i=0;i<frames;i++)wav.writeInt16LE(Math.round(Math.sin(2*Math.PI*330*i/44100)*400),44+i*2);
fs.writeFileSync(path.join(folder,'tone.wav'),wav);
