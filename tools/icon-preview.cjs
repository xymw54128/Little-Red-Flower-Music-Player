const fs=require('node:fs');
const path=require('node:path');
const sharp=require('C:/Users/xymw/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const root=path.resolve('android/app/src/main/res');
const output=path.resolve('dist/icon-preview');
fs.mkdirSync(output,{recursive:true});
function paths(file) {
  const xml=fs.readFileSync(path.join(root,file),'utf8');
  return [...xml.matchAll(/<path\b[^>]+\/>/g)].map(([tag])=>{
    const read=name=>tag.match(new RegExp('android:'+name+'="([^"]+)"'))?.[1];
    return `<path fill="${read('fillColor')}" fill-rule="${read('fillType')==='evenOdd'?'evenodd':'nonzero'}" d="${read('pathData')}"/>`;
  }).join('');
}
const mode=process.argv[2]||'before';
const artwork=paths('drawable/ic_flower.xml');
if(mode==='before') {
  sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">${artwork}</svg>`)).png().toFile(path.join(output,'before.png'));
} else {
  const monochrome=paths('drawable/ic_flower_monochrome.xml');
  const masks={circle:'<circle cx="54" cy="54" r="36"/>',square:'<rect x="18" y="18" width="72" height="72" rx="16"/>',squircle:'<path d="M54,18 C83,18 90,25 90,54 C90,83 83,90 54,90 C25,90 18,83 18,54 C18,25 25,18 54,18Z"/>'};
  const svg=(mask,mono=false)=>`<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="288" height="288"><defs><clipPath id="mask">${masks[mask]}</clipPath></defs><g clip-path="url(#mask)"><rect width="108" height="108" fill="${mono?'#E7E9F5':'#F8F8F6'}"/>${mono?monochrome.replaceAll('#CE3C4C','#44547C'):artwork}</g></svg>`;
  Promise.all(Object.keys(masks).map(mask=>sharp(Buffer.from(svg(mask))).png().toFile(path.join(output,mask+'.png'))).concat([
    sharp(Buffer.from(svg('circle',true))).png().toFile(path.join(output,'monochrome.png')),
    sharp(Buffer.from(svg('circle'))).resize(48,48).png().toFile(path.join(output,'small-48.png')),
    sharp(Buffer.from(svg('square'))).resize(24,24).png().toFile(path.join(output,'small-24.png'))
  ])).then(()=>console.log('Icon mask previews written to '+output));
}
