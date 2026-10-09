const cp = require('node:child_process');
const path = require('node:path');
const python = 'C:/Users/xymw/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
const result = cp.spawnSync(python, process.argv.slice(2), {
  stdio: 'inherit',
  env: {...process.env, PYTHONIOENCODING: 'utf-8', PYTHONPATH: [path.resolve('.toolchain/python-packages'), path.resolve('.toolchain/ios-python')].join(path.delimiter)}
});
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
