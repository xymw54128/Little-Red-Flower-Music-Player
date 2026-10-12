const {spawnSync}=require('node:child_process');
const path=require('node:path');
const target=path.resolve('.toolchain/python-packages');
const args=process.argv.includes('--install') ? ['-m','pip','install','--target',target,'fonttools==4.66.1'] : ['tools/check-fonts.py'];
const result=spawnSync('C:/Users/xymw/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',args,{stdio:'inherit',env:{...process.env,PYTHONIOENCODING:'utf-8',PYTHONPATH:target}});
if(result.error)throw result.error;
process.exitCode=result.status;
