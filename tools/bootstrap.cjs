const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const root = path.resolve('.toolchain');
fs.mkdirSync(root, {recursive:true});
async function download(url, name) {
  const dest = path.join(root, name);
  if (fs.existsSync(dest)) return dest;
  console.log('Downloading', name);
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status}: ${url}`);
  const {Readable} = require('node:stream');
  await require('node:stream/promises').pipeline(Readable.fromWeb(response.body), fs.createWriteStream(dest));
  console.log('Downloaded', name, fs.statSync(dest).size);
  return dest;
}
function extract(file, folder) {
  fs.mkdirSync(folder, {recursive:true});
  const r = cp.spawnSync('tar.exe', ['-xf', file, '-C', folder], {stdio:'inherit'});
  if(r.status !== 0) throw new Error('Extraction failed: ' + file);
}
async function install() {
  const assets = await (await fetch('https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows')).json();
  const xml = await (await fetch('https://dl.google.com/android/repository/repository2-1.xml')).text();
  const pkg = xml.match(/<remotePackage path="cmdline-tools;latest"[\s\S]*?<\/remotePackage>/)?.[0];
  const archives = pkg?.match(/<archive>[\s\S]*?<\/archive>/g) || [];
  const windows = archives.find(a=>a.includes('<host-os>windows</host-os>'));
  const sdkUrl = windows?.match(/<url>(.*?)<\/url>/)?.[1];
  if(!sdkUrl) throw new Error('No commandline tools URL');
  const [jdk, gradle, sdk] = await Promise.all([
    download(assets[0].binary.package.link, 'jdk17.zip'),
    download('https://services.gradle.org/distributions/gradle-8.13-bin.zip', 'gradle.zip'),
    download('https://dl.google.com/android/repository/' + sdkUrl, 'sdk-tools.zip')
  ]);
  if(!fs.existsSync(path.join(root,'jdk'))) extract(jdk, path.join(root,'jdk'));
  if(!fs.existsSync(path.join(root,'gradle-8.13'))) extract(gradle, root);
  if(!fs.existsSync(path.join(root,'sdk/cmdline-tools/latest'))) {
    extract(sdk,path.join(root,'sdk/cmdline-tools/staging'));
    fs.renameSync(path.join(root,'sdk/cmdline-tools/staging/cmdline-tools'),path.join(root,'sdk/cmdline-tools/latest'));
  }
  const javaHome = path.join(root,'jdk',fs.readdirSync(path.join(root,'jdk')).find(p=>p.startsWith('jdk')));
  fs.writeFileSync(path.join(root,'env.json'),JSON.stringify({javaHome, sdk:path.join(root,'sdk'), gradle:path.join(root,'gradle-8.13')},null,2));
  const env={...process.env,JAVA_HOME:javaHome,ANDROID_HOME:path.join(root,'sdk'),GRADLE_USER_HOME:path.join(root,'gradle-cache')};
  const manager=path.join(root,'sdk/cmdline-tools/latest/bin/sdkmanager.bat');
  function runManager(args,input) {
    return new Promise((resolve,reject)=>{
      const proc=cp.spawn('cmd.exe',['/d','/s','/c',`""${manager}" --sdk_root="${env.ANDROID_HOME}" ${args}"`],{env,stdio:['pipe','inherit','inherit'],windowsVerbatimArguments:true});
      proc.stdin.end(input);
      proc.on('exit',code=>code===0?resolve():reject(new Error('SDK manager exit '+code)));
    });
  }
  await runManager('--licenses','y\n'.repeat(100));
  await runManager('"platforms;android-36" "build-tools;35.0.0" "platform-tools"','y\n'.repeat(100));
  fs.writeFileSync(path.resolve('android/local.properties'),'sdk.dir='+env.ANDROID_HOME.replace(/\\/g,'/').replace(/:/g,'\\:')+'\n');
  console.log('Android toolchain ready.');
}
async function fastInstall() {
  const destination=path.join(root,'gradle-fast.zip');
  const probe=await fetch('https://downloads.gradle.org/distributions/gradle-8.13-bin.zip',{headers:{Range:'bytes=0-0'}});
  const total=Number(probe.headers.get('content-range')?.split('/')[1]);
  const finalUrl=probe.url;
  await probe.body.cancel();
  if(!total) throw new Error('Gradle server does not support ranged download');
  const current=path.join(root,'gradle.zip');
  const base=fs.existsSync(current)?Math.min(fs.statSync(current).size,total):0;
  const out=fs.openSync(destination,'w');
  fs.ftruncateSync(out,total);
  if(base) {
    const source=fs.openSync(current,'r');const buf=Buffer.alloc(1024*1024);
    for(let offset=0;offset<base;) {const read=fs.readSync(source,buf,0,Math.min(buf.length,base-offset),offset);fs.writeSync(out,buf,0,read,offset);offset+=read;}
    fs.closeSync(source);
  }
  console.log('Gradle resume',base,'/',total);
  const size=4*1024*1024;const chunks=[];
  for(let start=base;start<total;start+=size) chunks.push({start,end:Math.min(start+size-1,total-1)});
  let next=0;
  await Promise.all(Array.from({length:8},async()=>{
    while(next<chunks.length) {
      const chunk=chunks[next++];const res=await fetch(finalUrl,{headers:{Range:`bytes=${chunk.start}-${chunk.end}`}});
      if(res.status!==206) throw new Error('Gradle range failed '+res.status);
      const bytes=Buffer.from(await res.arrayBuffer());
      if(bytes.length!==chunk.end-chunk.start+1) throw new Error('Truncated Gradle range');
      fs.writeSync(out,bytes,0,bytes.length,chunk.start);console.log('Gradle part complete',chunk.end);
    }
  }));
  fs.closeSync(out);
  const expected=(await (await fetch('https://downloads.gradle.org/distributions/gradle-8.13-bin.zip.sha256')).text()).trim();
  const actual=require('node:crypto').createHash('sha256').update(fs.readFileSync(destination)).digest('hex');
  if(actual!==expected) throw new Error('Gradle checksum mismatch');
  if(!fs.existsSync(path.join(root,'gradle-8.13'))) extract(destination,root);
  if(!fs.existsSync(path.join(root,'sdk/cmdline-tools/latest'))) {
    extract(path.join(root,'sdk-tools.zip'),path.join(root,'sdk/cmdline-tools/staging'));
    fs.renameSync(path.join(root,'sdk/cmdline-tools/staging/cmdline-tools'),path.join(root,'sdk/cmdline-tools/latest'));
  }
  const javaHome=path.join(root,'microsoft-jdk',fs.readdirSync(path.join(root,'microsoft-jdk')).find(p=>fs.existsSync(path.join(root,'microsoft-jdk',p,'bin/java.exe'))));
  const sdk=path.join(root,'sdk');
  fs.writeFileSync(path.join(root,'env.json'),JSON.stringify({javaHome,sdk,gradle:path.join(root,'gradle-8.13')},null,2));
  const env={...process.env,JAVA_HOME:javaHome,ANDROID_HOME:sdk};
  const manager=path.join(sdk,'cmdline-tools/latest/bin/sdkmanager.bat');
  const proc=cp.spawn('cmd.exe',['/d','/s','/c',`""${manager}" --sdk_root="${sdk}" "platforms;android-36" "build-tools;35.0.0" "platform-tools""`],{env,stdio:['pipe','inherit','inherit'],windowsVerbatimArguments:true});
  proc.stdin.end('y\n'.repeat(100));
  await new Promise((resolve,reject)=>proc.on('exit',code=>code===0?resolve():reject(new Error('SDK manager exit '+code))));
  fs.writeFileSync(path.resolve('android/local.properties'),'sdk.dir='+sdk.replace(/\\/g,'/').replace(/:/g,'\\:')+'\n');
  console.log('Fast Android toolchain ready');
}
if (process.argv[2] === 'probe') {
  for (const url of ['https://dl.google.com/android/repository/repository2-1.xml', 'https://services.gradle.org/versions/current', 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows']) {
    fetch(url).then(async r => console.log(url, r.status, (await r.text()).slice(0,500))).catch(e=>console.error(e.message,e.cause?.message));
  }
} else if(process.argv[2] === 'references') {
  for (const p of ['C:/Users/xymw/.agents/skills/agent-reach/references/search.md','C:/Users/xymw/.agents/skills/agent-reach/references/web.md']) console.log(fs.readFileSync(p,'utf8'));
} else if(process.argv[2] === 'install') install().catch(e=>{console.error(e);process.exitCode=1});
else if(process.argv[2] === 'speed') {
  const urls=['https://downloads.gradle.org/distributions/gradle-8.13-bin.zip','https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip'];
  Promise.all(urls.map(async url=>{
    const start=Date.now();const res=await fetch(url,{headers:{Range:'bytes=0-1048575'}});const reader=res.body.getReader();let count=0;
    while(count<1024*1024) {const chunk=await reader.read();if(chunk.done)break;count+=chunk.value.length;}await reader.cancel();
    console.log(url,res.status,res.url,count,Date.now()-start);
  })).catch(console.error);
}
else if(process.argv[2] === 'fast-jdk') {
  download('https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip','microsoft-jdk17.zip').then(file=>{
    extract(file,path.join(root,'microsoft-jdk'));
    console.log('Microsoft JDK ready');
  }).catch(e=>{console.error(e);process.exitCode=1});
}
else if(process.argv[2] === 'fast-install') fastInstall().catch(e=>{console.error(e);process.exitCode=1});
