const fs=require('node:fs');const path=require('node:path');const cp=require('node:child_process');const crypto=require('node:crypto');
const config=JSON.parse(fs.readFileSync('.toolchain/env.json','utf8'));
const version='1.1.8';const versionCode='29';
const artifactStem=`小红花-${version}`;
const apk=path.resolve('android/app/build/outputs/apk/release/app-release.apk');
const output=path.resolve('dist');fs.mkdirSync(output,{recursive:true});
function check(exe,args) {const result=cp.spawnSync(exe,args,{encoding:'utf8',env:{...process.env,JAVA_HOME:config.javaHome}});if(result.error)throw result.error;if(result.status!==0)throw new Error(result.stdout+result.stderr);return result.stdout+result.stderr;}
const signing=check(path.join(config.javaHome,'bin/java.exe'),['-jar',path.join(config.sdk,'build-tools/35.0.0/lib/apksigner.jar'),'verify','--verbose','--print-certs',apk]);
const certificate=text=>text.match(/Signer #1 certificate SHA-256 digest: (\w+)/)?.[1];
const upgradeCertificates=['0.1.0','0.1.5','1.0.0','1.0.1','1.0.2','1.0.3','1.0.4','1.0.5','1.0.6','1.0.7','1.0.8','1.0.9','1.0.10','1.0.11','1.1.0','1.1.1','1.1.2','1.1.3','1.1.5','1.1.6','1.1.6-弹层修订版','1.1.7'].map(version=>{
  const previous=path.join(output,`小红花-${version}.apk`);
  if(!fs.existsSync(previous))throw new Error('Upgrade baseline APK missing: '+version);
  const oldSigning=check(path.join(config.javaHome,'bin/java.exe'),['-jar',path.join(config.sdk,'build-tools/35.0.0/lib/apksigner.jar'),'verify','--print-certs',previous]);
  const matches=!!certificate(signing)&&certificate(signing)===certificate(oldSigning);
  if(!matches)throw new Error('Release certificate differs from installed '+version+' baseline');
  return {version,sameSigningCertificate:matches};
});
const sameSigningCertificate=upgradeCertificates.every(baseline=>baseline.sameSigningCertificate);
check(path.join(config.sdk,'build-tools/35.0.0/zipalign.exe'),['-c','-P','16','4',apk]);
const badging=check(path.join(config.sdk,'build-tools/35.0.0/aapt2.exe'),['dump','badging',apk]);
const metadata=badging.split(/\r?\n/).filter(line=>/^(package:|sdkVersion:|minSdkVersion:|targetSdkVersion:|application-label:|native-code:)/.test(line));
if(badging.includes('application-debuggable'))throw new Error('Release APK is debuggable');
const manifest=check(path.join(config.sdk,'build-tools/35.0.0/aapt2.exe'),['dump','xmltree','--file','AndroidManifest.xml',apk]);
if(manifest.includes('E: instrumentation')||manifest.includes('androidx.compose.ui.test')||manifest.includes('CollectionDocumentsProvider')||manifest.includes('com.xiaohonghua.music.test.collectionprovider')||/android:testOnly[^\n]*0xffffffff/.test(manifest))throw new Error('Test components present in release APK');
if(badging.includes("uses-permission: name='android.permission.INTERNET'"))throw new Error('Offline release unexpectedly requests network access');
if(badging.includes("uses-permission: name='android.permission.RECORD_AUDIO'"))throw new Error('Decorative playback animations must not request microphone access');
const entries=check('tar.exe',['-tf',apk]).split(/\r?\n/);
// Release resource optimization shortens file paths; resolve names through the resource table.
const resourceBlocks=check(path.join(config.sdk,'build-tools/35.0.0/aapt2.exe'),['dump','resources',apk]).split(/\r?\n\s*resource /);
function resourceFile(type,name) {
  const block=resourceBlocks.find(value=>value.split(/\r?\n/,1)[0].endsWith(' '+type+'/'+name));
  const entry=block?.match(/\(file\) (res\/\S+)/)?.[1];
  if(!entry||!entries.includes(entry))throw new Error('Resource missing from APK: '+type+'/'+name);
  return entry;
}
const iconPath=resourceFile('mipmap','ic_launcher');
const iconXml=check(path.join(config.sdk,'build-tools/35.0.0/aapt2.exe'),['dump','xmltree','--file',iconPath,apk]);
if(!['adaptive-icon','background','foreground','monochrome'].every(tag=>iconXml.includes('E: '+tag)))throw new Error('Launcher icon layers missing from compiled APK');
const iconResources={path:iconPath,adaptive:true,monochrome:true};
const launchIconPath=resourceFile('drawable','ic_flower_center');
const launchIconXml=check(path.join(config.sdk,'build-tools/35.0.0/aapt2.exe'),['dump','xmltree','--file',launchIconPath,apk]);
if((launchIconXml.match(/E: path/g)||[]).length!==1||!launchIconXml.toLowerCase().includes('#fff6d88c'))throw new Error('Launch flower center is not the original gold-only foreground');
const launchIcon={path:launchIconPath,goldCenterOnly:true};
const notificationIconPath=resourceFile('drawable','ic_notification_flower');
const notificationIconXml=check(path.join(config.sdk,'build-tools/35.0.0/aapt2.exe'),['dump','xmltree','--file',notificationIconPath,apk]);
if(!notificationIconXml.includes('E: vector')||!notificationIconXml.toLowerCase().includes('#ffffffff')||notificationIconXml.includes('E: background'))throw new Error('Notification flower must be a transparent white vector');
const notificationIcon={path:notificationIconPath,monochrome:true,foregroundOnly:true};
if(resourceBlocks.some(block=>block.split(/\r?\n/,1)[0].endsWith(' drawable/ic_cover_poppy')))throw new Error('Retired blue poppy is still packaged');
const headerIcons=['ic_flower','ic_header_playlists','ic_header_settings'].map(name=>{
  const entry=resourceFile('drawable',name);
  const xml=check(path.join(config.sdk,'build-tools/35.0.0/aapt2.exe'),['dump','xmltree','--file',entry,apk]);
  if(!xml.includes('E: path')||xml.includes('E: background'))throw new Error('Invalid header foreground: '+name);
  return {drawable:name,path:entry,foregroundOnly:true};
});
const overlayWindowAnimations=['overlay_dialog_enter','overlay_dialog_exit'].map(name=>{
  const entry=resourceFile('anim',name);
  const xml=check(path.join(config.sdk,'build-tools/35.0.0/aapt2.exe'),['dump','xmltree','--file',entry,apk]);
  if(!xml.includes('E: alpha')||!xml.includes('E: translate')||xml.includes('E: scale'))throw new Error('Invalid overlay motion: '+name);
  return {name,path:entry,alpha:true,translation:true,scale:false};
});
if(!resourceBlocks.some(block=>block.split(/\r?\n/,1)[0].endsWith(' style/AppOverlayDialogAnimation')))throw new Error('Overlay window animation style missing');
const playlistCovers=['ic_cover_peach','ic_cover_daisy','ic_cover_tulip','ic_cover_lily','ic_cover_iris','ic_cover_sunflower','ic_cover_camellia','ic_cover_hydrangea','ic_cover_lotus','ic_cover_cornflower','ic_cover_favorite'].map(name=>{
  const entry=resourceFile('drawable',name);
  const xml=check(path.join(config.sdk,'build-tools/35.0.0/aapt2.exe'),['dump','xmltree','--file',entry,apk]);
  if(!xml.includes('E: vector')||(xml.match(/E: path/g)||[]).length<2)throw new Error('Flower cover missing vector paths: '+name);
  return {drawable:name,path:entry,vector:true};
});
const fontSources=JSON.parse(fs.readFileSync('android/app/src/main/assets/font-licenses/sources.json','utf8'));
const expectedFontFiles=['lxgw_wenkai.ttf','flower_serif.ttf','flower_xiaowei.ttf','flower_zenmaru.ttf','flower_kiwi.ttf','flower_kurenaido.ttf','flower_longcang.ttf'];
if(JSON.stringify(fontSources.map(s=>s.file).sort())!==JSON.stringify(expectedFontFiles.slice().sort()))throw new Error('Selected production font set is incomplete');
if(resourceBlocks.some(block=>[' font/flower_cute',' font/flower_handdrawn'].some(name=>block.split(/\r?\n/,1)[0].endsWith(name))))throw new Error('Retired decorative fonts are still packaged');
const fonts=fontSources.map(s=>{
  const entry=resourceFile('font',path.parse(s.file).name);
  if(!entries.includes('assets/font-licenses/'+s.file+'.OFL.txt'))throw new Error('Font license missing: '+s.file);
  const data=cp.spawnSync('tar.exe',['-xOf',apk,entry],{maxBuffer:64*1024*1024});
  if(data.status!==0)throw new Error('Cannot read bundled font: '+s.file);
  const digest=crypto.createHash('sha256').update(data.stdout).digest('hex');
  if(digest!==s.sha256)throw new Error('Bundled font differs from recorded source: '+s.file);
  return {file:s.file,path:entry,version:s.version,bytes:data.stdout.length,sha256:digest,licenseBundled:true};
});
const elfRoot=path.resolve('.toolchain/apk-check');fs.mkdirSync(elfRoot,{recursive:true});
const lib='lib/arm64-v8a/libandroidx.graphics.path.so';
check('tar.exe',['-xf',apk,'-C',elfRoot,lib]);
const elf=fs.readFileSync(path.join(elfRoot,lib));
const offset=Number(elf.readBigUInt64LE(32));const entrySize=elf.readUInt16LE(54);const count=elf.readUInt16LE(56);const aligns=[];
for(let i=0;i<count;i++){const pos=offset+i*entrySize;if(elf.readUInt32LE(pos)===1)aligns.push(Number(elf.readBigUInt64LE(pos+48)));}
if(aligns.some(a=>a<16384))throw new Error('Native ARM64 library lacks 16KB segment alignment');
const dest=path.join(output,`${artifactStem}.apk`);
const testFolder=path.resolve('android/app/build/test-results/testReleaseUnitTest');
const suites=fs.readdirSync(testFolder).filter(name=>name.endsWith('.xml')).map(name=>{
  const tag=fs.readFileSync(path.join(testFolder,name),'utf8').match(/<testsuite[^>]+>/)?.[0]||'';
  const read=key=>tag.match(new RegExp(key+'="([^"]*)"'))?.[1];
  return {name:read('name'),tests:Number(read('tests')),failures:Number(read('failures')),errors:Number(read('errors'))};
});
if(!suites.length||suites.some(s=>s.failures||s.errors))throw new Error('Unit test report missing or failing');
const lint=fs.readFileSync('android/app/build/intermediates/lint_intermediate_text_report/release/lintReportRelease/lint-results-release.txt','utf8').match(/(\d+) errors, (\d+) warnings/);
if(!lint||Number(lint[1])!==0)throw new Error('Lint errors or missing report');
if(!metadata.some(m=>m.includes("name='com.xiaohonghua.music'")&&m.includes(`versionName='${version}'`)&&m.includes(`versionCode='${versionCode}'`)))throw new Error('APK identity or version mismatch');
fs.copyFileSync(apk,dest);
const report={date:new Date().toISOString(),apk:dest,buildType:'release',debuggable:false,testComponentsAbsent:true,networkPermissionAbsent:true,recordAudioPermissionAbsent:true,size:fs.statSync(dest).size,sha256:crypto.createHash('sha256').update(fs.readFileSync(dest)).digest('hex'),metadata,signing:signing.trim(),sameSigningCertificateAs010:sameSigningCertificate,sameSigningCertificateAs111:upgradeCertificates.find(baseline=>baseline.version==='1.1.1').sameSigningCertificate,sameSigningCertificateAs112:upgradeCertificates.find(baseline=>baseline.version==='1.1.2').sameSigningCertificate,sameSigningCertificateAs113:upgradeCertificates.find(baseline=>baseline.version==='1.1.3').sameSigningCertificate,sameSigningCertificateAs115:upgradeCertificates.find(baseline=>baseline.version==='1.1.5').sameSigningCertificate,sameSigningCertificateAs116:upgradeCertificates.find(baseline=>baseline.version==='1.1.6').sameSigningCertificate,sameSigningCertificateAs116Overlay:upgradeCertificates.find(baseline=>baseline.version==='1.1.6-弹层修订版').sameSigningCertificate,sameSigningCertificateAs117:upgradeCertificates.find(baseline=>baseline.version==='1.1.7').sameSigningCertificate,upgradeCertificates,iconResources,launchIcon,notificationIcon,headerIcons,overlayWindowAnimations,playlistCovers,fonts,zipAlign16k:true,arm64LoadSegmentAlignments:aligns,unitTests:suites,lint:{errors:Number(lint[1]),warnings:Number(lint[2])},acceptance:'1.1.8 code 29: single opaque lyric glyph pass with shared draw-stage color and scale weights; cover preview samples the live media clock at frame cadence only while visible and playing, and uses exact LRC timestamps. Song details show labeled title, artist, composer and album in a clear flower-themed layout, with persisted COMPOSER/TCOM metadata and backward-compatible defaults. Audio decoding, DSP, volume and speed configuration unchanged. Original intermittent GPU symptoms and device timing require native verification',nativeTests:'Instrumented compilation and execution status is recorded separately in ANDROID-1.1.8-UI-CHECK.json; compilation alone does not confirm runtime behavior or native frame performance.'};
fs.writeFileSync(path.join(output,'APK-CHECK.json'),JSON.stringify(report,null,2));
fs.writeFileSync(path.join(output,`${artifactStem}-CHECK.json`),JSON.stringify(report,null,2));
fs.writeFileSync(path.join(output,`${artifactStem}.apk.sha256`),report.sha256+'  '+path.basename(dest)+'\n');
console.log(JSON.stringify({apk:dest,buildType:report.buildType,debuggable:report.debuggable,testComponentsAbsent:true,sizeMB:report.size/1048576,sha256:report.sha256,metadata,upgradeCertificates,iconResources,headerIcons,overlayWindowAnimations,playlistCovers,fonts,zipAlign16k:true,arm64LoadSegmentAlignments:aligns,unitTests:suites,lint:report.lint,acceptance:report.acceptance,nativeTests:report.nativeTests},null,2));
