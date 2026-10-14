// Browser acceptance for the standalone design preview, not Android device testing.
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const {createRequire}=require('node:module');
const runtimeRequire=createRequire('C:/Users/xymw/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json');
const {chromium}=runtimeRequire('playwright');
const root=path.resolve(__dirname,'../docs/previews/2026-10-14');
const base='http://127.0.0.1:8897';
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  const report={date:'2026-10-14',scope:'standalone browser prototype',androidFramePerformanceMeasured:false,schemes:[],sizes:[],errors:[]};
  try{
    const page=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:1});
    page.setDefaultTimeout(6000);
    page.on('pageerror',e=>report.errors.push(e.message));
    await fs.mkdir(path.join(root,'screenshots'),{recursive:true});
    for(const scheme of ['A','B','C','D']){
      await page.goto(`${base}/player-options/index.html?scheme=${scheme}&embed=1`,{waitUntil:'networkidle'});
      await page.getByRole('button',{name:'播放',exact:true}).click();
      assert.equal(await page.locator('.phone').getAttribute('data-playing'),'true');
      await page.getByRole('button',{name:'暂停',exact:true}).click();
      assert.equal(await page.locator('.phone').getAttribute('data-playing'),'false');
      await page.getByRole('button',{name:'加入我喜欢',exact:true}).click();
      assert.equal(await page.locator('.favorite').getAttribute('aria-pressed'),'true');
      await page.getByRole('button',{name:'查看歌词',exact:true}).click();
      await page.waitForTimeout(420);
      const center=await page.evaluate(()=>{
        const s=document.querySelector('.lyrics-scroll').getBoundingClientRect();
        const r=document.querySelector('.lyric-line.current').getBoundingClientRect();
        return Math.abs(r.top+r.height/2-s.top-s.height/2);
      });
      assert.ok(center<2,`Lyric center ${scheme}: ${center}`);
      await page.getByRole('tab',{name:'歌曲封面',exact:true}).click();
      await page.waitForTimeout(380);
      await page.mouse.move(310,270);await page.mouse.down();
      await page.mouse.move(90,270,{steps:12});await page.mouse.up();
      await page.waitForTimeout(400);
      assert.equal(await page.locator('.phone').evaluate(n=>n.classList.contains('lyrics-open')),true);
      await page.mouse.move(180,310);await page.mouse.wheel(0,230);
      await page.waitForTimeout(2050);
      const idleCenter=await page.evaluate(()=>{
        const s=document.querySelector('.lyrics-scroll').getBoundingClientRect();
        const r=document.querySelector('.lyric-line.current').getBoundingClientRect();
        return Math.abs(r.top+r.height/2-s.top-s.height/2);
      });
      assert.ok(idleCenter<2,`Idle lyric center ${scheme}: ${idleCenter}`);
      await page.mouse.move(90,270);await page.mouse.down();
      await page.mouse.move(310,270,{steps:12});await page.mouse.up();
      await page.waitForTimeout(400);
      assert.equal(await page.locator('.phone').evaluate(n=>n.classList.contains('lyrics-open')),false);
      await page.getByRole('button',{name:'下一首',exact:true}).click();
      assert.equal(await page.locator('.song-title').textContent(),'一朵花的时间');
      await page.getByRole('button',{name:'播放队列',exact:true}).click();
      await page.locator('[data-track="2"]').click();
      assert.equal(await page.locator('.song-title').textContent(),'沿着风的方向');
      await page.locator('#seek').focus();
      await page.keyboard.press('ArrowRight');
      assert.ok(Number(await page.locator('#seek').inputValue())>0);
      await page.getByRole('button',{name:'歌曲更多选项',exact:true}).click();
      await page.getByRole('button',{name:'添加到歌单',exact:true}).click();
      await page.locator('[data-list="春日收藏"]').click();
      assert.equal(await page.locator('.sheet').isVisible(),false);
      await page.reload({waitUntil:'networkidle'});
      await page.locator('.phone').screenshot({path:path.join(root,`screenshots/player-${scheme}.png`)});
      report.schemes.push({scheme,playPause:true,favorite:true,lyricCenterErrorPx:center,idleCenterErrorPx:idleCenter,swipeBothWays:true,nextAndQueue:true,keyboardSeek:true,choosePlaylist:true});
    }
    for(const size of [{width:320,height:690},{width:360,height:800},{width:390,height:844},{width:430,height:932}]){
      await page.setViewportSize(size);
      for(const scheme of ['A','B','C','D']){
        await page.goto(`${base}/player-options/index.html?scheme=${scheme}&embed=1`,{waitUntil:'networkidle'});
        const bounds=await page.evaluate(()=>{
          const art=document.querySelector('.art-frame').getBoundingClientRect();
          const controls=document.querySelector('.controls').getBoundingClientRect();
          return {horizontalOverflow:document.documentElement.scrollWidth>innerWidth,artInside:art.left>=-1&&art.right<=innerWidth+1,controlsInside:controls.top>=0&&controls.bottom<=innerHeight};
        });
        assert.equal(bounds.horizontalOverflow,false);
        assert.equal(bounds.artInside,true,`${scheme} ${size.width} art clipped`);
        assert.equal(bounds.controlsInside,true,`${scheme} ${size.height} controls clipped`);
        report.sizes.push({...size,scheme,...bounds});
      }
    }
    await page.setViewportSize({width:1370,height:980});
    await page.goto(`${base}/player-options/index.html`,{waitUntil:'networkidle'});
    await page.locator('#theme').selectOption('blue');
    assert.equal(await page.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--accent')),'#537bb5');
    report.themeSwitch=true;
    await page.emulateMedia({reducedMotion:'reduce'});
    assert.equal(await page.locator('.art-frame').evaluate(n=>getComputedStyle(n).animationName),'none');
    report.reducedMotion=true;
    await page.emulateMedia({reducedMotion:'no-preference'});
    await page.setViewportSize({width:1764,height:1100});
    await page.goto(`${base}/player-options/compare.html`,{waitUntil:'networkidle'});
    assert.equal(await page.locator('iframe').count(),4);
    await page.screenshot({path:path.join(root,'screenshots/player-options-overview.png'),fullPage:true});
    assert.deepEqual(report.errors,[]);
    report.passed=true;
    await fs.writeFile(path.join(root,'PLAYER-PREVIEW-CHECK.json'),JSON.stringify(report,null,2)+'\n');
    console.log(JSON.stringify({passed:true,schemes:report.schemes.length,viewportChecks:report.sizes.length,themeSwitch:true,reducedMotion:true,errors:report.errors}));
  }finally{await browser.close();}
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
