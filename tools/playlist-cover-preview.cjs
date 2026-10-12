// Preview the shipped Android vector paths; these are asset previews, not device screenshots.
const fs=require('node:fs');
const path=require('node:path');
const sharp=require('C:/Users/xymw/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const root=path.resolve(__dirname,'..');
const drawable=path.join(root,'android/app/src/main/res/drawable');
const dist=path.join(root,'dist');
const flowers=[['桃枝','peach'],['雏菊','daisy'],['郁金香','tulip'],['铃兰','lily'],['鸢尾','iris'],
  ['向日葵','sunflower'],['山茶','camellia'],['绣球','hydrangea'],['荷风','lotus'],['矢车菊','cornflower'],['心之花','favorite']];
function artwork(name) {
  const xml=fs.readFileSync(path.join(drawable,name+'.xml'),'utf8');
  const viewport=xml.match(/android:viewportWidth="([^"]+)"/)[1];
  const attributes=(source,key,fallback)=>source.match(new RegExp(`android:${key}="([^"]+)"`))?.[1]??fallback;
  const color=value=>value?.length===9?'#'+value.slice(3)+value.slice(1,3):value;
  const paths=xml.replace(/<!--[\s\S]*?-->/g,'').replace(/<vector[^>]+>|<\/vector>/g,'')
    .replace(/<group\b([^>]+)>/g,(_,attrs)=>{
      const read=(key,fallback)=>Number(attributes(attrs,key,fallback));
      const x=read('pivotX',0),y=read('pivotY',0);
      return `<g transform="translate(${read('translateX',0)} ${read('translateY',0)}) translate(${x} ${y}) rotate(${read('rotation',0)}) scale(${read('scaleX',1)} ${read('scaleY',1)}) translate(${-x} ${-y})">`;
    }).replace(/<\/group>/g,'</g>').replace(/<path\b([^>]+)\/>/g,(_,attrs)=>{
      const read=(key,fallback)=>attributes(attrs,key,fallback);
      const stroke=read('strokeColor');
      const outline=stroke?` stroke="${color(stroke)}" stroke-opacity="${read('strokeAlpha',1)}" stroke-width="${read('strokeWidth',1)}" stroke-linecap="${read('strokeLineCap','butt')}" stroke-linejoin="${read('strokeLineJoin','miter')}"`:'';
      return `<path fill="${color(read('fillColor','transparent'))}" fill-opacity="${read('fillAlpha',1)}" fill-rule="${read('fillType')==='evenOdd'?'evenodd':'nonzero'}"${outline} d="${read('pathData')}"/>`;
    });
  return {viewport,paths};
}
async function main() {
  fs.mkdirSync(dist,{recursive:true});
  const ui=fs.readFileSync(path.join(root,'android/app/src/main/java/com/xiaohonghua/music/PlaylistCovers.kt'),'utf8');
  if(/coverIcons|ic_cover_poppy|蓝罂粟/.test(ui))throw new Error('Retired cover artwork is still available');
  const referenced=[...ui.matchAll(/R\.drawable\.(ic_cover_[a-z]+)/g)].map(match=>match[1]);
  const expected=flowers.map(([,name])=>'ic_cover_'+name);
  if(JSON.stringify(referenced)!==JSON.stringify(expected))throw new Error('Preview differs from actual cover picker resources');
  const cards=flowers.map(([label,name],i)=>{
    const x=32+(i%4)*220,y=72+Math.floor(i/4)*246;
    const {viewport,paths}=artwork('ic_cover_'+name);
    return `<g transform="translate(${x} ${y})"><svg width="180" height="180" viewBox="0 0 ${viewport} ${viewport}">${paths}</svg><text x="90" y="212" text-anchor="middle" font-family="Microsoft YaHei" font-size="19" fill="#3C4641">${label}</text></g>`;
  }).join('');
  const board=`<svg xmlns="http://www.w3.org/2000/svg" width="924" height="822"><rect width="924" height="822" fill="#F8F8F6"/><text x="32" y="40" font-family="Microsoft YaHei" font-size="22" fill="#272B29">小红花 · 十款花朵与心之花</text>${cards}</svg>`;
  await sharp(Buffer.from(board)).png().toFile(path.join(dist,'歌单花朵封面-1.0.9.png'));
  const {viewport,paths}=artwork('ic_notification_flower');
  const notification=await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="72" height="72" viewBox="0 0 ${viewport} ${viewport}">${paths}</svg>`)).png().toBuffer();
  await sharp(notification).toFile(path.join(dist,'notification-flower-1.0.9.png'));
  const {data,info}=await sharp(notification).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const pixel=(x,y)=>data[(y*info.width+x)*4+3];
  const transparentCorners=[pixel(0,0),pixel(71,0),pixel(0,71),pixel(71,71)].every(alpha=>alpha===0);
  let paintedPixels=0,whiteOnly=true;
  for(let i=0;i<data.length;i+=4)if(data[i+3]) {paintedPixels++;if(data[i]!==255||data[i+1]!==255||data[i+2]!==255)whiteOnly=false;}
  if(!transparentCorners||!whiteOnly||paintedPixels<400||pixel(36,36)!==0)throw new Error('Notification flower must be a visible monochrome alpha mask with a hollow center');
  const result={version:'1.0.9',source:'actual Android VectorDrawable paths',coverCount:flowers.length,
    stablePresetIds:flowers.map((_,i)=>i+8),resources:expected,removedCodeDrawnPresets:[0,1,2,3,4,5,6,7],
    notification:{drawable:'ic_notification_flower',transparentCorners,transparentCenter:pixel(36,36)===0,whiteOnly,paintedPixels},
    deviceScreenshot:false,apkPresence:'verified separately during package validation'};
  fs.writeFileSync(path.join(dist,'ANDROID-1.0.9-COVER-CHECK.json'),JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify(result,null,2));
}
main().catch(error=>{console.error(error);process.exitCode=1;});
