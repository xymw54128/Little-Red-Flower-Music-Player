const fs=require('node:fs');const path=require('node:path');const cp=require('node:child_process');
const root=path.resolve('.toolchain');const cfg=JSON.parse(fs.readFileSync(path.join(root,'env.json'),'utf8'));
const env={...process.env,JAVA_HOME:cfg.javaHome,ANDROID_HOME:cfg.sdk,ANDROID_USER_HOME:path.join(root,'android-user'),ANDROID_AVD_HOME:path.join(root,'avds')};
const action=process.argv[2];
if(['start','check'].includes(action)) {
  const alias='C:/Users/xymw/.codex/visualizations/2026/10/07/01a1161e-a877-7440-a1d7-fa0827c9cd77/android-sdk';
  if(!fs.existsSync(alias)) fs.symlinkSync(cfg.sdk,alias,'junction');
  if(fs.realpathSync(alias)!==fs.realpathSync(cfg.sdk)) throw new Error('SDK alias points to an unexpected directory');
  cfg.sdk=alias;env.ANDROID_HOME=alias;
  const avdAlias='C:/Users/xymw/.codex/visualizations/2026/10/07/01a1161e-a877-7440-a1d7-fa0827c9cd77/android-avds';
  fs.mkdirSync(path.join(root,'avds'),{recursive:true});
  if(!fs.existsSync(avdAlias)) fs.symlinkSync(path.join(root,'avds'),avdAlias,'junction');
  env.ANDROID_AVD_HOME=avdAlias;
}
if(action==='inspect') {
  console.log('RAM GB',require('node:os').totalmem()/1024**3,'CPU',require('node:os').cpus().length);
  console.log('SDK binaries',fs.readdirSync(path.join(cfg.sdk,'cmdline-tools/latest/bin')));
  const exe=path.join(cfg.sdk,'cmdline-tools/latest/bin/android.exe');
  if(fs.existsSync(exe)) {const p=cp.spawnSync(exe,['--no-metrics','sdk','--help'],{env,encoding:'utf8'});console.log(p.stdout,p.stderr);}
} else if(action==='install') {
  const exe=path.join(cfg.sdk,'cmdline-tools/latest/bin/android.exe');
  const p=cp.spawn(exe,['--no-metrics',`--sdk=${cfg.sdk}`,'sdk','install','emulator','system-images;android-36;aosp_atd;x86_64'],{env,stdio:['pipe','inherit','inherit']});
  p.stdin.end('y\n'.repeat(100));p.on('exit',code=>process.exitCode=code||0);
} else if(action==='check') {
  const p=cp.spawnSync(path.join(cfg.sdk,'emulator/emulator.exe'),['-accel-check'],{env,encoding:'utf8'});if(p.error)throw p.error;console.log(p.stdout,p.stderr);
} else if(action==='start') {
  const avd=path.join(env.ANDROID_AVD_HOME,'xiaohonghua.avd');fs.mkdirSync(avd,{recursive:true});
  fs.writeFileSync(path.join(env.ANDROID_AVD_HOME,'xiaohonghua.ini'),`avd.ini.encoding=UTF-8\npath=${avd}\ntarget=android-36\n`);
  fs.writeFileSync(path.join(avd,'config.ini'),`AvdId=xiaohonghua\navd.ini.encoding=UTF-8\nabi.type=x86_64\nhw.cpu.arch=x86_64\nhw.cpu.ncore=4\nhw.ramSize=2048\nhw.lcd.width=1080\nhw.lcd.height=2400\nhw.lcd.density=420\nhw.gpu.enabled=yes\nhw.gpu.mode=swiftshader\nhw.keyboard=yes\nhw.mainKeys=no\nhw.audioInput=no\nhw.audioOutput=no\ndisk.dataPartition.size=2G\nimage.sysdir.1=${path.join(cfg.sdk,'system-images/android-36/aosp_atd/x86_64')}\ntag.id=aosp_atd\ntag.display=Automated Test Device\nskin.name=1080x2400\n`);
  const log=fs.openSync(path.join(root,'emulator.log'),'a');
  const p=cp.spawn(path.join(cfg.sdk,'emulator/emulator.exe'),['-avd','xiaohonghua','-no-window','-gpu','swiftshader_indirect','-feature','-Vulkan','-accel','off','-no-snapshot','-no-audio','-no-boot-anim','-show-kernel','-verbose','-port','5554'],{env,stdio:['ignore',log,log],windowsHide:true,detached:true});
  fs.writeFileSync(path.join(root,'emulator.pid'),String(p.pid));p.unref();console.log('Headless Android 16 emulator started',p.pid);
} else if(action==='log') console.log(fs.readFileSync(path.join(root,'emulator.log'),'utf8').slice(-7000).replace(/Sending adb public key \[[^\r\n]+/g,'Sending adb public key [omitted]').replace(/androidboot.qemu.adb.pubkey=[^\r\n]+/g,'androidboot.qemu.adb.pubkey=[omitted]'));
else if(action==='ps') {
  const p=cp.spawnSync('C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe',['-NoProfile','-Command','Get-Process -Name qemu*,emulator -ErrorAction SilentlyContinue | Select-Object Id,ProcessName,CPU,WorkingSet,StartTime | ConvertTo-Json'],{encoding:'utf8'});console.log(p.stdout,p.stderr);
}
else if(action==='diagnose') {
  const p=cp.spawnSync('C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe',['-NoProfile','-Command',"Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'qemu|emulator|mke2fs|e2fsdroid' } | Select-Object Name,ProcessId,ParentProcessId,CommandLine | ConvertTo-Json"],{encoding:'utf8'});console.log(p.stdout,p.stderr);
}
else if(action==='help') {
  const p=cp.spawnSync(path.join(cfg.sdk,'emulator/emulator.exe'),['-help-all'],{env,encoding:'utf8'});
  console.log((p.stdout+'\n'+p.stderr).split(/\r?\n/).filter(line=>/netsim|bluetooth|no-qt|no-window|gpu|qt-hide/.test(line)).join('\n'));
}
else if(action==='console') {
  const net=require('node:net');const socket=net.createConnection(5554,'127.0.0.1');let output='';
  socket.on('data',data=>{output+=data.toString();if(output.includes('OK')){console.log(output.slice(0,1200));socket.end();}});
  socket.on('error',console.error);setTimeout(()=>socket.destroy(),3000);
}
else if(action==='stop') {
  const pid=Number(fs.readFileSync(path.join(root,'emulator.pid'),'utf8'));if(!Number.isInteger(pid)||pid<=0)throw new Error('Invalid emulator PID');
  const check=cp.spawnSync('C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe',['-NoProfile','-Command',`Get-Process -Id ${pid} -ErrorAction SilentlyContinue | Select-Object ProcessName | ConvertTo-Json`],{encoding:'utf8'});
  if(JSON.parse(check.stdout||'{}').ProcessName!=='emulator')throw new Error('The recorded PID is not our emulator');
  const p=cp.spawnSync('taskkill.exe',['/PID',String(pid),'/T','/F'],{encoding:'utf8'});console.log(p.stdout,p.stderr);
}
