const fs=require('node:fs');
for(const name of ['jdk17.zip','gradle.zip','sdk-tools.zip','env.json']) {
  const path='.toolchain/'+name;
  console.log(name,fs.existsSync(path)?fs.statSync(path).size:'pending');
}
