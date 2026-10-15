// One-time host asset preparation. Generated assets and fonts are included in the source package.
const fs = require('node:fs');
const path = require('node:path');
let sharp;
try { sharp = require('sharp'); } catch { sharp = require('C:/Users/xymw/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp'); }
const root = path.resolve(__dirname, '../..');
const resources = path.join(root, 'ios/Xiaohonghua/Resources');
const assets = path.join(resources, 'Assets.xcassets');
function json(file, value) { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n'); }
function paths(name) {
  const xml = fs.readFileSync(path.join(root, 'android/app/src/main/res/drawable', name + '.xml'), 'utf8');
  return [...xml.matchAll(/<path\b([^>]+)\/?\s*>/g)].map(match => {
    const values = Object.fromEntries([...match[1].matchAll(/android:(\w+)="([^"]+)"/g)].map(m => [m[1], m[2]]));
    const fill = values.fillColor === '@android:color/transparent' || values.fillColor === '#00000000' ? 'none' : values.fillColor || 'none';
    return `<path d="${values.pathData}" fill="${fill}"${values.strokeColor ? ` stroke="${values.strokeColor}" stroke-width="${values.strokeWidth}" stroke-linecap="round" stroke-linejoin="round"` : ''}/>`;
  }).join('');
}
async function imageSet(name, body, viewBox = '18 18 72 72') {
  const folder = path.join(assets, name + '.imageset');
  fs.mkdirSync(folder, {recursive: true});
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="768" height="768" viewBox="${viewBox}">${body}</svg>`;
  await sharp(Buffer.from(svg)).png().toFile(path.join(folder, name + '.png'));
  json(path.join(folder, 'Contents.json'), {images: [{filename: name + '.png', idiom: 'universal'}], info: {author: 'xcode', version: 1}, properties: {'preserves-vector-representation': false, 'template-rendering-intent': 'original'}});
}
(async () => {
  fs.mkdirSync(resources, {recursive: true});
  json(path.join(assets, 'Contents.json'), {info: {author: 'xcode', version: 1}});
  const flower = paths('ic_flower');
  await imageSet('Flower', flower);
  await imageSet('FlowerPlaylists', paths('ic_header_playlists'));
  await imageSet('FlowerSettings', paths('ic_header_settings'));
  await imageSet('FlowerCenter', '<circle cx="54" cy="54" r="7.5" fill="#F6D88C"/>', '46.5 46.5 15 15');
  const appIcon = path.join(assets, 'AppIcon.appiconset');
  fs.mkdirSync(appIcon, {recursive: true});
  await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 108 108"><rect width="108" height="108" fill="#F8F8F6"/>${flower}</svg>`)).flatten({background: '#F8F8F6'}).png().toFile(path.join(appIcon, 'AppIcon.png'));
  json(path.join(appIcon, 'Contents.json'), {images: [{filename: 'AppIcon.png', idiom: 'universal', platform: 'ios', size: '1024x1024'}], info: {author: 'xcode', version: 1}});
  const fonts = JSON.parse(fs.readFileSync(path.join(root, 'android/app/src/main/assets/font-licenses/sources.json'), 'utf8'));
  for (const {file: name} of fonts) {
    fs.copyFileSync(path.join(root, 'android/app/src/main/res/font', name), path.join(resources, name));
    fs.copyFileSync(path.join(root, 'android/app/src/main/assets/font-licenses', name + '.OFL.txt'), path.join(resources, name + '.OFL.txt'));
  }
  fs.copyFileSync(path.join(root, 'android/app/src/main/assets/font-licenses/sources.json'), path.join(resources, 'font-sources.json'));
  const coverNames = ['peach','daisy','tulip','lily','iris','sunflower','camellia','hydrangea','lotus','cornflower','favorite'];
  for (const name of coverNames) await imageSet('FlowerCover' + name[0].toUpperCase() + name.slice(1), paths('ic_cover_' + name), '0 0 108 108');
  const fixtures = path.join(root, 'ios/Tests/CoreTests/Fixtures');
  fs.mkdirSync(fixtures, {recursive: true});
  for (const name of ['tone.flac', 'tone-24bit-96k.flac', 'tone.wav']) {
    fs.copyFileSync(path.join(root, 'android/app/src/androidTest/assets', name), path.join(fixtures, name));
  }
  console.log(JSON.stringify({assets: 'ios/Xiaohonghua/Resources/Assets.xcassets', fonts: fonts.length, playlistCovers: coverNames.length, source: 'Existing Android flower vectors and recorded multilingual OFL fonts'}, null, 2));
})().catch(error => { console.error(error); process.exitCode = 1; });
