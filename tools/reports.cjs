const fs=require('node:fs');
const path=require('node:path');
const lint='android/app/build/intermediates/lint_intermediate_text_report/release/lintReportRelease/lint-results-release.txt';
if(fs.existsSync(lint))console.log(fs.readFileSync(lint,'utf8'));
const tests='android/app/build/test-results/testReleaseUnitTest';
if(fs.existsSync(tests))for(const file of fs.readdirSync(tests).filter(n=>n.endsWith('.xml')))console.log(file,fs.readFileSync(path.join(tests,file),'utf8').match(/<testsuite[^>]+>/)?.[0]);
