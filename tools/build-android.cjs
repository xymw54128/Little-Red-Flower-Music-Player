const fs=require('node:fs');
const path=require('node:path');
const cp=require('node:child_process');
const root=path.resolve('.toolchain');
const config=JSON.parse(fs.readFileSync(path.join(root,'env.json'),'utf8'));
const env={...process.env,JAVA_HOME:config.javaHome,ANDROID_HOME:config.sdk,GRADLE_USER_HOME:path.join(root,'gradle-cache')};
const args=process.argv.slice(2);
const key=path.resolve('android/signing/xiaohonghua-debug.jks');
if(!fs.existsSync(key)) {
  if(args.some(arg=>/release/i.test(arg))) throw new Error('Release signing key is missing. Restore the existing key; do not generate a replacement.');
  fs.mkdirSync(path.dirname(key),{recursive:true});
  const r=cp.spawnSync(path.join(config.javaHome,'bin/keytool.exe'),['-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Xiaohonghua Local Debug,O=Local,C=CN','-keyalg','RSA','-keysize','2048','-validity','10000'],{env,stdio:'inherit'});
  if(r.status!==0) process.exit(r.status||1);
}
const gradle=path.join(config.gradle,'bin/gradle.bat');
const proc=cp.spawn('cmd.exe',['/d','/s','/c',`""${gradle}" --no-daemon --console=plain ${args.length?args.join(' '):':app:assembleDebug :app:testDebugUnitTest :app:lintDebug'}"`],{env,cwd:path.resolve('android'),stdio:'inherit',windowsVerbatimArguments:true});
proc.on('exit',code=>process.exitCode=code||0);
