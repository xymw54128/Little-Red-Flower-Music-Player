// Resource/timing preview only; this is not a capture of Android rendering or frame performance.
const fs=require('node:fs');
const path=require('node:path');
const cp=require('node:child_process');
const sharp=require('C:/Users/xymw/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const output=path.resolve('dist/startup-preview');
fs.mkdirSync(output,{recursive:true});
const glyphs=cp.spawnSync('C:/Users/xymw/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',['-c',`
import json
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
font=TTFont('android/app/src/main/res/font/lxgw_wenkai.ttf')
glyphs=font.getGlyphSet()
cmap=font.getBestCmap()
scale=24/font['head'].unitsPerEm
x=0
paths=[]
for char in '小红花 · 音乐播放器':
    name=cmap[ord(char)]
    pen=SVGPathPen(glyphs)
    glyphs[name].draw(pen)
    paths.append({'x':x,'d':pen.getCommands()})
    x+=font['hmtx'][name][0]*scale+1.2
print(json.dumps({'width':x-1.2,'scale':scale,'paths':paths},ensure_ascii=False))
`],{encoding:'utf8',env:{...process.env,PYTHONIOENCODING:'utf-8',PYTHONPATH:path.resolve('.toolchain/python-packages')}});
if(glyphs.error||glyphs.status!==0)throw glyphs.error||new Error(glyphs.stderr);
const wordmark=JSON.parse(glyphs.stdout);
const letters=wordmark.paths.map(g=>`<path transform="translate(${g.x} 0) scale(${wordmark.scale} ${-wordmark.scale})" d="${g.d}"/>`).join('');
const label=`<g fill="#CE3C4C" transform="skewX(-10)">${letters}</g>`;
const xml=fs.readFileSync('android/app/src/main/res/drawable/ic_flower.xml','utf8');
const flower=[...xml.matchAll(/<path android:fillColor="([^"]+)" android:pathData="([^"]+)"/g)].map(m=>`<path fill="${m[1]}" d="${m[2]}"/>`).join('');
const ease=(time,start,duration)=>{const t=Math.max(0,Math.min(1,(time-start)/duration));return t*t*t*(t*(t*6-15)+10);};
const times=[0,350,620,1040,1900,2640];
function frame(time) {
    const flight=ease(time,1500,840),rest=1-flight;
    const start={x:196.5,y:426},end={x:40,y:78};
    const c1={x:start.x+393*.23,y:start.y-852*.18},c2={x:end.x+393*.20,y:end.y+852*.10};
    const x=start.x*rest**3+3*c1.x*rest**2*flight+3*c2.x*rest*flight**2+end.x*flight**3;
    const y=start.y*rest**3+3*c1.y*rest**2*flight+3*c2.y*rest*flight**2+end.y*flight**3;
    const scale=(1+(36/288-1)*flight)*288/108;
    const rotation=72*ease(time,1120,340)+1368*flight+72*ease(time,2340,300);
    const clips=Array.from({length:5},(_,i)=>{
        const a=(-126+i*72)*Math.PI/180,b=a+72*Math.PI/180,r=96;
        return `<clipPath id="petal${i}"><path d="M54 54 L${54+r*Math.cos(a)} ${54+r*Math.sin(a)} A96 96 0 0 1 ${54+r*Math.cos(b)} ${54+r*Math.sin(b)} Z"/></clipPath>`;
    }).join('');
    const growing=time >= 1080?flower:Array.from({length:5},(_,i)=>{
        const p=ease(time,220+i*150,260);
        return `<g transform="translate(54 54) scale(${p}) translate(-54 -54)"><g clip-path="url(#petal${i})">${flower}</g></g>`;
    }).join('')+'<circle cx="54" cy="54" r="7.5" fill="#F6D88C"/>';
    const tracks=Array.from({length:7},(_,i)=>`<rect x="24" y="${326+i*58}" width="42" height="42" rx="13" fill="#E5E9E3"/><rect x="80" y="${334+i*58}" width="${150-i*9}" height="9" rx="4" fill="#C8CEC6"/><rect x="80" y="${353+i*58}" width="98" height="6" rx="3" fill="#DFE3DC"/>`).join('');
    const header=time >= 2640?`<g transform="translate(22 60) scale(.333333)">${flower}</g>`:'';
    return `<svg xmlns="http://www.w3.org/2000/svg" width="393" height="852" viewBox="0 0 393 852"><defs>${clips}</defs>
    <rect width="393" height="852" fill="#F8F8F6"/>${header}<text x="66" y="88" font-family="Microsoft YaHei" font-weight="600" font-size="27" fill="#272B29">音乐库</text><text x="350" y="88" font-size="28" fill="#272B29">+</text>
    <rect x="24" y="123" width="66" height="34" rx="10" fill="#E7DDF3"/><text x="57" y="146" text-anchor="middle" font-family="Microsoft YaHei" font-size="16" fill="#272B29">歌曲</text>
    <text x="120" y="146" font-family="Microsoft YaHei" font-size="16" fill="#272B29">专辑</text><text x="207" y="146" font-family="Microsoft YaHei" font-size="16" fill="#272B29">歌手</text>
    <rect x="24" y="179" width="345" height="54" rx="16" fill="none" stroke="#90948F"/><text x="43" y="212" font-family="Microsoft YaHei" font-size="16" fill="#696F6B">搜索歌曲、歌手或专辑</text>
    <rect x="24" y="268" width="128" height="38" rx="19" fill="#E7DDF3"/><text x="44" y="293" font-family="Microsoft YaHei" font-size="16" fill="#272B29">▷ 播放全部</text>${tracks}
    <rect y="762" width="393" height="90" fill="#F8F8F6"/><text x="64" y="812" text-anchor="middle" font-family="Microsoft YaHei" font-size="15" fill="#696F6B">音乐库</text><text x="196" y="812" text-anchor="middle" font-family="Microsoft YaHei" font-size="15" fill="#696F6B">歌单</text><text x="329" y="812" text-anchor="middle" font-family="Microsoft YaHei" font-size="15" fill="#696F6B">设置</text>
    <rect width="393" height="852" fill="#F8F8F6" opacity="${1-ease(time,1480,300)}"/>
    <g opacity="${ease(time,220,240)*(1-ease(time,1660,320))}" transform="translate(${(393-wordmark.width)/2} 560)">${label}</g>
    ${time < 2640?`<g transform="translate(${x} ${y}) rotate(${rotation}) scale(${scale}) translate(-54 -54)">${growing}</g>`:''}</svg>`;
}
(async()=>{
    const tiles=[];
    for(let index=0;index<times.length;index++) {
        const time=times[index],svg=frame(time);
        fs.writeFileSync(path.join(output,`${time}.svg`),svg);
        const png=await sharp(Buffer.from(svg)).resize(220).png().toBuffer();
        fs.writeFileSync(path.join(output,`${time}.png`),png);
        tiles.push({input:png,left:24+(index%3)*244,top:44+Math.floor(index/3)*536});
    }
    await sharp({create:{width:756,height:1100,channels:4,background:'#E9ECE7'}}).composite(tiles).png().toFile(path.join(output,'sequence.png'));
    const wordmarkSvg=`<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="220" viewBox="0 0 330 70"><rect width="330" height="70" fill="#F8F8F6"/><g transform="translate(${(330-wordmark.width)/2} 44)">${label}</g></svg>`;
    await sharp(Buffer.from(wordmarkSvg)).png().toFile(path.join(output,'wordmark.png'));
    console.log(JSON.stringify({sequence:path.join(output,'sequence.png'),wordmark:path.join(output,'wordmark.png'),times,source:'Original Android flower vector and unmodified bundled LXGW WenKai glyphs',limitation:'Resource and timing preview, not an Android screenshot or frame-rate measurement'},null,2));
})().catch(error=>{console.error(error);process.exitCode=1;});
