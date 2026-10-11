const fs = require('node:fs');
const path = require('node:path');

// 默认使用示例自己的 npm ci 依赖；本仓库也可复用被忽略的临时验证工具目录。
const candidates = [
  path.join(__dirname, 'node_modules'),
  path.resolve(__dirname, '../../.toolchain/cocos-lyrics/node_modules'),
];
const dependencies = candidates.find(root => fs.existsSync(path.join(root, 'typescript/lib/typescript.js')) &&
  fs.existsSync(path.join(root, '@cocos/creator-types/engine.d.ts')));
if (!dependencies) {
  console.error('请在 examples/cocos-lyrics 中运行 npm ci --ignore-scripts，再运行 npm run check。');
  process.exit(1);
}
const ts = require(path.join(dependencies, 'typescript'));
const program = ts.createProgram([
  path.join(__dirname, 'LyricsCore.ts'),
  path.join(__dirname, 'LyricsController.ts'),
  path.join(dependencies, '@cocos/creator-types/engine.d.ts'),
], {
  target: ts.ScriptTarget.ES2020,
  module: ts.ModuleKind.ESNext,
  moduleResolution: ts.ModuleResolutionKind.Node10,
  experimentalDecorators: true,
  useDefineForClassFields: false,
  strict: true,
  skipLibCheck: true,
  noEmit: true,
});
const diagnostics = ts.getPreEmitDiagnostics(program);
if (diagnostics.length) {
  console.error(ts.formatDiagnosticsWithColorAndContext(diagnostics, {
    getCanonicalFileName: filename => filename,
    getCurrentDirectory: () => __dirname,
    getNewLine: () => '\n',
  }));
  process.exit(1);
}
console.log('TypeScript 检查通过：LyricsCore + LyricsController / 官方 Cocos Creator 3.8.6 类型。');
