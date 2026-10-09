// Download unchanged official releases and their redistribution licenses.
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..');
const fonts=path.join(root,'android/app/src/main/res/font');
const licenses=path.join(root,'android/app/src/main/assets/font-licenses');
const cache=path.join(root,'.toolchain/font-downloads');
async function download(url,file) {
  if(url.startsWith('https://raw.githubusercontent.com/')) {
    const [owner,repo,ref,...parts]=url.substring('https://raw.githubusercontent.com/'.length).split('/');
    const content=gh(`repos/${owner}/${repo}/contents/${parts.join('/')}?ref=${ref}`);
    const data=content.encoding === 'base64' ? content : gh(`repos/${owner}/${repo}/git/blobs/${content.sha}`);
    if(data.encoding !== 'base64') throw new Error('Unexpected Git blob encoding');
    fs.writeFileSync(file,Buffer.from(data.content,'base64'));
  } else {
    const [owner,repo,,,tag,name]=url.substring('https://github.com/'.length).split('/');
    const r=spawnSync('C:/Users/xymw/gh-cli/bin/gh.exe',['release','download',tag,'--repo',`${owner}/${repo}`,'--pattern',name,'--output',file,'--clobber'],{encoding:'utf8',timeout:240000});
    if(r.status !== 0) throw new Error(r.stderr);
  }
  console.log(path.basename(file),fs.statSync(file).size);
}
function gh(api) {
  const r=spawnSync('C:/Users/xymw/gh-cli/bin/gh.exe',['api',api],{encoding:'utf8',maxBuffer:100*1024*1024,timeout:180000});
  if(r.status !== 0) throw new Error(r.stderr);
  return JSON.parse(r.stdout);
}
(async()=>{
  for(const dir of [fonts,licenses,cache]) fs.mkdirSync(dir,{recursive:true});
  const serifCommit=gh('repos/adobe-fonts/source-han-serif/commits/release').sha;
  const sources=[
    {file:'lxgw_wenkai.ttf',version:'1.522',url:'https://github.com/lxgw/LxgwWenKai/releases/download/v1.522/LXGWWenKai-Regular.ttf',license:'https://raw.githubusercontent.com/lxgw/LxgwWenKai/v1.522/OFL.txt'},
    {file:'source_han_serif_cn.otf',version:'2.003',url:`https://raw.githubusercontent.com/adobe-fonts/source-han-serif/${serifCommit}/SubsetOTF/CN/SourceHanSerifCN-Regular.otf`,license:`https://raw.githubusercontent.com/adobe-fonts/source-han-serif/${serifCommit}/LICENSE.txt`},
    {file:'zhuque_fangsong.ttf',version:'0.212 (upstream preview)',url:'https://github.com/TrionesType/zhuque/releases/download/v0.212/ZhuqueFangsong-v0.212.zip',license:'https://raw.githubusercontent.com/TrionesType/zhuque/v0.212/LICENSE.txt'}
  ];
  for(const s of sources) {
    await download(s.license,path.join(licenses,`${s.file}.OFL.txt`));
    if(s.url.endsWith('.zip')) {
      const zip=path.join(cache,'ZhuqueFangsong-v0.212.zip');
      await download(s.url,zip);
      const list=spawnSync('tar',['-tf',zip],{encoding:'utf8'});
      if(list.status !== 0) throw new Error(list.stderr);
      const entry=list.stdout.split(/\r?\n/).find(n=>/\.ttf$/i.test(n) && !n.includes('..'));
      if(!entry) throw new Error('No TTF in official release');
      const data=spawnSync('tar',['-xOf',zip,entry],{maxBuffer:64*1024*1024});
      if(data.status !== 0) throw new Error(data.stderr.toString());
      fs.writeFileSync(path.join(fonts,s.file),data.stdout);
      console.log(entry,data.stdout.length);
    } else await download(s.url,path.join(fonts,s.file));
    s.sha256=crypto.createHash('sha256').update(fs.readFileSync(path.join(fonts,s.file))).digest('hex');
    s.bytes=fs.statSync(path.join(fonts,s.file)).size;
  }
  fs.writeFileSync(path.join(licenses,'sources.json'),JSON.stringify(sources,null,2)+'\n');
})().catch(e=>{console.error(e);process.exitCode=1;});
