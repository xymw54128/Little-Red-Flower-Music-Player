// Render the actual Android vector paths, including their group transforms.
const fs=require('node:fs');
const path=require('node:path');
const sharp=require('C:/Users/xymw/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const output=path.resolve('dist/header-icon-preview');
fs.mkdirSync(output,{recursive:true});
const icons=[['library','音乐库','ic_flower'],['playlists','我的歌单','ic_header_playlists'],['settings','设置','ic_header_settings']];
function artwork(name) {
  const xml=fs.readFileSync(`android/app/src/main/res/drawable/${name}.xml`,'utf8');
  return xml.replace(/<!--[\s\S]*?-->/g,'').replace(/<vector[^>]+>|<\/vector>/g,'')
    .replace(/<group\b([^>]+)>/g,(_,attrs)=>{
      const read=(key,fallback)=>Number(attrs.match(new RegExp(`android:${key}="([^"]+)"`))?.[1]??fallback);
      const x=read('pivotX',0),y=read('pivotY',0);
      return `<g transform="translate(${read('translateX',0)} ${read('translateY',0)}) translate(${x} ${y}) rotate(${read('rotation',0)}) scale(${read('scaleX',1)} ${read('scaleY',1)}) translate(${-x} ${-y})">`;
    }).replace(/<\/group>/g,'</g>').replace(/<path\b([^>]+)\/>/g,(_,attrs)=>{
      const read=key=>attrs.match(new RegExp(`android:${key}="([^"]+)"`))?.[1];
      const stroke=read('strokeColor');
      const outline=stroke?` stroke="${stroke}" stroke-width="${read('strokeWidth')}" stroke-linejoin="${read('strokeLineJoin')||'miter'}"`:'';
      return `<path fill="${read('fillColor')}"${outline} d="${read('pathData')}"/>`;
    });
}
(async()=>{
  const results=[];
  for(const [page,label,name] of icons) {
    const art=artwork(name);
    // A 24dp UI Image scales the transparent foreground by 1.5 around its center.
    const svg=`<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="216" height="216">${art}</svg>`;
    const png=await sharp(Buffer.from(svg)).png().toBuffer();
    fs.writeFileSync(path.join(output,`${page}.png`),png);
    await sharp(png).resize(24,24).png().toFile(path.join(output,`${page}-24.png`));
    const {data,info}=await sharp(png).ensureAlpha().raw().toBuffer({resolveWithObject:true});
    let pixels=0;
    for(let i=3;i<data.length;i+=4)if(data[i]>0)pixels++;
    const alpha=(x,y)=>data[(y*info.width+x)*4+3];
    if(alpha(0,0)||alpha(info.width-1,0)||alpha(0,info.height-1)||alpha(info.width-1,info.height-1))throw new Error('Icon has a background');
    if(!pixels)throw new Error('Icon is empty');
    results.push({page,drawable:name,transparentCorners:true,paintedPixels:pixels});
  }
  const row=icons.map(([,label,name],i)=>`<g transform="translate(${i*300+25} 65)"><svg width="48" height="48" viewBox="18 18 72 72">${artwork(name)}</svg><text x="66" y="36" fill="#272B29" font-family="Microsoft YaHei" font-size="30" font-weight="600">${label}</text></g>`).join('');
  await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="900" height="170"><rect width="900" height="170" fill="#F8F8F6"/>${row}</svg>`)).png().toFile(path.join(output,'headers.png'));
  fs.writeFileSync(path.join(output,'CHECK.json'),JSON.stringify(results,null,2)+'\n');
  console.log(JSON.stringify(results,null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
