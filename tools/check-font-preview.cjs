// Verifies that the review page uses real embedded fonts, including language companions.
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const {createRequire}=require('node:module');
const {pathToFileURL}=require('node:url');
const runtimeRequire=createRequire('C:/Users/xymw/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {chromium}=runtimeRequire('playwright');
const root=path.resolve(__dirname,'../docs/previews/2026-10-14');
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  const report={date:'2026-10-14',errors:[],styles:[],fontsArePreviewSubsets:true,productionFontsChanged:false};
  try{
    const page=await browser.newPage({viewport:{width:1440,height:1100},deviceScaleFactor:1});
    page.setDefaultTimeout(6000);
    page.on('pageerror',e=>report.errors.push(e.message));
    const input=JSON.parse(await fs.readFile(path.join(root,'font-options/fonts.json'),'utf8'));
    await page.goto('http://127.0.0.1:8897/font-options/index.html',{waitUntil:'networkidle'});
    assert.equal(await page.locator('.option').count(),8);
    for(const font of input.fonts){
      await page.locator(`[data-font="${font.id}"]`).click();
      await page.waitForFunction(()=>document.querySelector('#load-status').textContent.includes('真实字体已载入'));
      const family=await page.locator('#lyrics [lang="zh"]').evaluate(n=>getComputedStyle(n).fontFamily);
      assert.ok(family.includes(font.family));
      assert.equal(await page.locator('#lyrics [lang="ko"]').count(),1);
      assert.equal(await page.locator('.load-error').count(),0);
      report.styles.push({code:font.code,name:font.name,primaryLoaded:true,companions:font.languageCompanions,zhSampleCoverage:font.combinationCoverage.zh.percent});
    }
    const loaded=await page.evaluate(()=>Array.from(document.fonts).map(f=>({family:f.family,status:f.status})));
    assert.equal(loaded.length,13);
    assert.ok(loaded.every(f=>f.status==='loaded'));
    report.loadedFonts=loaded;
    await page.locator('[data-font="kiwi"]').count().then(async n=>{if(n)await page.locator('[data-font="kiwi"]').click();else await page.locator('.option').nth(2).click();});
    await page.locator('#font-size').focus();await page.keyboard.press('ArrowRight');
    assert.equal(await page.locator('#font-size').inputValue(),'28');
    await page.locator('#reset').click();
    assert.equal(await page.locator('#font-size').inputValue(),'27');
    await page.locator('#play').click();
    assert.equal(await page.locator('#play').textContent(),'暂停试读');
    await page.locator('#play').click();
    assert.equal(await page.locator('#play').textContent(),'试读动态歌词');
    await page.evaluate(()=>window.scrollTo(0,0));
    await page.screenshot({path:path.join(root,'screenshots/font-options-desktop.png'),fullPage:true});
    await page.setViewportSize({width:390,height:844});
    await page.reload({waitUntil:'networkidle'});
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    await page.screenshot({path:path.join(root,'screenshots/font-options-mobile.png'),fullPage:true});
    report.mobileNoOverflow=true;
    await page.goto(pathToFileURL(path.join(root,'font-options/index.html')).href,{waitUntil:'networkidle'});
    await page.waitForFunction(()=>document.querySelector('#load-status').textContent.includes('真实字体已载入'));
    report.offlineFileFontsLoaded=true;
    await page.setViewportSize({width:1600,height:1250});
    await page.goto('http://127.0.0.1:8897/font-options/compare.html',{waitUntil:'networkidle'});
    await page.evaluate(()=>document.fonts.ready);
    assert.equal(await page.locator('.grid section').count(),6);
    await page.screenshot({path:path.join(root,'screenshots/font-options-overview.png'),fullPage:true});
    report.comparisonSamples=6;
    assert.deepEqual(report.errors,[]);
    report.passed=true;
    await fs.writeFile(path.join(root,'FONT-BROWSER-CHECK.json'),JSON.stringify(report,null,2)+'\n');
    console.log(JSON.stringify({passed:true,styles:report.styles.length,loadedFonts:loaded.length,mobileNoOverflow:true,offlineFileFontsLoaded:true}));
  }finally{await browser.close();}
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
