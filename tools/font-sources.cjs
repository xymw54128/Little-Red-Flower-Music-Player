// Fetch only pinned official OFL inputs, then build deterministic multilingual faces.
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const cp=require('node:child_process');
const root=path.resolve(__dirname,'..');
const cache=path.join(root,'.toolchain/font-downloads');
const resources=path.join(root,'android/app/src/main/res/font');
const licenses=path.join(root,'android/app/src/main/assets/font-licenses');
const inputs=JSON.parse(fs.readFileSync(path.join(__dirname,'lyric-font-inputs.json'),'utf8'));
const gh=process.env.GH_BIN||'gh';
const sha=data=>crypto.createHash('sha256').update(data).digest('hex');
function api(route,raw=false) {
 const result=cp.spawnSync(gh,['api',route,...(raw?['-H','Accept: application/vnd.github.raw+json']:[])],{maxBuffer:64*1024*1024,timeout:240000});
 // Do not print binary or base64 partial response bodies after a transport error.
 if(result.status!==0)throw new Error(result.stderr?.toString().trim()||result.error?.message||'GitHub request failed');
 return raw?result.stdout:JSON.parse(result.stdout.toString('utf8'));
}
function rawInput(url) {
 if(!url.startsWith('https://raw.githubusercontent.com/'))throw new Error('Only official raw GitHub input URLs are accepted');
 const [owner,repo,ref,...parts]=url.slice('https://raw.githubusercontent.com/'.length).split('/');
 const metadata=api('repos/'+owner+'/'+repo+'/contents/'+parts.join('/')+'?ref='+ref);
 return metadata.encoding==='base64'?Buffer.from(metadata.content,'base64'):api('repos/'+owner+'/'+repo+'/git/blobs/'+metadata.sha,true);
}
try {
 fs.mkdirSync(cache,{recursive:true});
 for(const input of inputs) {
  const file=path.join(cache,input.file);
  if(!fs.existsSync(file)||sha(fs.readFileSync(file))!==input.sha256) {
   const existing=path.join(resources,input.file);
   if(fs.existsSync(existing)&&sha(fs.readFileSync(existing))===input.sha256)fs.copyFileSync(existing,file);
   else if(input.blob)fs.writeFileSync(file,api('repos/'+input.url.split('/')[3]+'/'+input.url.split('/')[4]+'/git/blobs/'+input.blob,true));
   else if(input.url.startsWith('https://github.com/')) {
    const [owner,repo,,,tag,name]=input.url.slice('https://github.com/'.length).split('/');
    const result=cp.spawnSync(gh,['release','download',tag,'--repo',owner+'/'+repo,'--pattern',name,'--output',file,'--clobber'],{stdio:'inherit',timeout:240000});
    if(result.status!==0)throw new Error('Official release download failed: '+input.file);
   } else fs.writeFileSync(file,rawInput(input.url));
  }
  if(sha(fs.readFileSync(file))!==input.sha256)throw new Error('Font SHA256 mismatch: '+input.file);
  const license=path.join(cache,input.file+'.OFL.txt');
  if(!fs.existsSync(license)||sha(fs.readFileSync(license))!==input.licenseSha256) {
   const existing=path.join(licenses,input.file+'.OFL.txt');
   if(fs.existsSync(existing)&&sha(fs.readFileSync(existing))===input.licenseSha256)fs.copyFileSync(existing,license);
   else fs.writeFileSync(license,rawInput(input.license));
  }
  if(sha(fs.readFileSync(license))!==input.licenseSha256)throw new Error('License SHA256 mismatch: '+input.file);
  console.log('Verified',input.file,input.bytes,'bytes');
 }
 const python=process.env.PYTHON_BIN||'C:/Users/xymw/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
 const result=cp.spawnSync(python,['tools/build-lyric-fonts.py'],{cwd:root,stdio:'inherit',env:{...process.env,PYTHONIOENCODING:'utf-8',PYTHONPATH:path.join(root,'.toolchain/python-packages')}});
 if(result.error)throw result.error;
 process.exitCode=result.status;
} catch(error){console.error(error.message);process.exitCode=1;}
