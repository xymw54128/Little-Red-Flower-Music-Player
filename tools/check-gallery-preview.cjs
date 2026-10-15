// Checks standalone gallery proposals. It does not measure Android frame performance.
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const {createRequire}=require('node:module');
const req=createRequire(path.join(require('node:os').homedir(),'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json'));
const {chromium}=req('playwright');
const root=path.resolve(__dirname,'../docs/previews/2026-10-14');
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  const report={date:'2026-10-14',scope:'B gallery refinement browser prototypes',errors:[],variants:[],sizes:[],androidFramePerformanceMeasured:false};
  try{
    const page=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:1});
    page.setDefaultTimeout(6000);
    page.on('pageerror',e=>report.errors.push(e.message));
    await fs.mkdir(path.join(root,'screenshots'),{recursive:true});
    for(const variant of ['depth','rhythm','letter']){
      await page.goto(`http://127.0.0.1:8897/gallery-refinements/index.html?variant=${variant}&embed=1`,{waitUntil:'networkidle'});
      assert.equal(await page.locator('body').getAttribute('data-scheme'),'B');
      assert.equal(await page.locator('body').getAttribute('data-variant'),variant);
      await page.getByRole('button',{name:'播放',exact:true}).click();
      assert.equal(await page.locator('.phone').getAttribute('data-playing'),'true');
      if(variant==='rhythm')assert.equal(await page.locator('.gallery-waveform i').first().evaluate(n=>getComputedStyle(n).animationPlayState),'running');
      await page.getByRole('button',{name:'暂停',exact:true}).click();
      assert.equal(await page.locator('.phone').getAttribute('data-playing'),'false');
      if(variant==='rhythm')assert.equal(await page.locator('.gallery-waveform i').first().evaluate(n=>getComputedStyle(n).animationPlayState),'paused');
      await page.getByRole('button',{name:'加入我喜欢',exact:true}).click();
      assert.equal(await page.locator('.favorite').getAttribute('aria-pressed'),'true');
      await page.getByRole('button',{name:'下一首',exact:true}).click();
      await page.getByRole('button',{name:'下一首',exact:true}).click();
      assert.equal(await page.locator('.song-title').textContent(),'沿着风的方向');
      assert.equal(await page.locator('.queue-position').textContent(),'03 / 03');
      assert.equal(await page.locator('.next-title').textContent(),'春日来信');
      await page.getByRole('button',{name:'查看下一首和播放队列',exact:true}).click();
      assert.equal(await page.locator('.sheet').isVisible(),true);
      await page.locator('[data-track="0"]').click();
      await page.locator('#seek').focus();await page.keyboard.press('ArrowRight');
      assert.equal(await page.locator('.seek-tooltip').isVisible(),true);
      await page.getByRole('button',{name:'查看歌词',exact:true}).click();
      await page.waitForTimeout(400);
      assert.equal(await page.locator('.phone').evaluate(n=>n.classList.contains('lyrics-open')),true);
      // Native vertical touch scrolling sends pointercancel, not pointerup.
      await page.evaluate(()=>{
        position=77;updateProgress();
        const lyrics=document.querySelector('.lyrics-scroll');
        lyrics.scrollTop=0;
        lyrics.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerType:'touch'}));
        document.querySelector('.art-button').style.transform='rotateY(2deg)';
        document.querySelector('.seek-wrap').classList.add('seeking');
        lyrics.dispatchEvent(new PointerEvent('pointercancel',{bubbles:true,pointerType:'touch'}));
      });
      assert.equal(await page.evaluate(()=>Number.isFinite(browsingUntil)&&pointerStart===null&&!dragging&&!swallowTap),true);
      assert.equal(await page.locator('.seek-wrap').evaluate(n=>n.classList.contains('seeking')),false);
      assert.equal(await page.locator('.art-button').evaluate(n=>n.style.transform),'');
      await page.waitForTimeout(1950);
      assert.equal(await page.evaluate(()=>browsingUntil===0&&Math.abs(scroll.scrollTop-($$('.lyric-line')[activeLine].offsetTop+$$('.lyric-line')[activeLine].offsetHeight/2-scroll.clientHeight/2))<2),true);
      await page.getByRole('tab',{name:'歌曲封面',exact:true}).click();
      await page.reload({waitUntil:'networkidle'});
      await page.locator('.phone').screenshot({path:path.join(root,`screenshots/gallery-${variant}.png`)});
      report.variants.push({variant,playPause:true,favorite:true,rapidTrackChanges:true,queueIndex:true,nextTrackEntry:true,seekTooltip:true,lyrics:true,touchCancelReturnsToCurrent:true});
    }
    for(const size of [{width:320,height:690},{width:360,height:800},{width:390,height:844},{width:430,height:932}]){
      await page.setViewportSize(size);
      for(const variant of ['depth','rhythm','letter']){
        await page.goto(`http://127.0.0.1:8897/gallery-refinements/index.html?variant=${variant}&embed=1`,{waitUntil:'networkidle'});
        const bounds=await page.evaluate(()=>{
          const art=document.querySelector('.art-frame').getBoundingClientRect();
          const controls=document.querySelector('.controls').getBoundingClientRect();
          const heading=document.querySelector('.track-heading').getBoundingClientRect();
          const dots=document.querySelector('.page-switch').getBoundingClientRect();
          const quality=document.querySelector('.quality');
          const peek=document.querySelector('.lyric-peek');
          const qualityVisible=getComputedStyle(quality).display==='none'||quality.getBoundingClientRect().bottom<=dots.top+2;
          const peekVisible=getComputedStyle(peek).display==='none'||peek.getBoundingClientRect().bottom<=dots.top+2;
          return {overflow:document.documentElement.scrollWidth>innerWidth,artInside:art.left>=-2&&art.right<=innerWidth+2,controlsInside:controls.bottom<=innerHeight,headingVisible:heading.bottom<=dots.top+2,qualityVisible,peekVisible};
        });
        assert.equal(bounds.overflow,false);assert.equal(bounds.artInside,true,`${variant} ${size.width} art`);assert.equal(bounds.controlsInside,true);assert.equal(bounds.headingVisible,true,`${variant} ${size.height} heading`);assert.equal(bounds.qualityVisible,true,`${variant} ${size.height} quality`);assert.equal(bounds.peekVisible,true,`${variant} ${size.height} lyric preview`);
        report.sizes.push({...size,variant,...bounds});
      }
    }
    await page.setViewportSize({width:1370,height:980});
    await page.goto('http://127.0.0.1:8897/gallery-refinements/index.html',{waitUntil:'networkidle'});
    await page.locator('#theme').selectOption('blue');
    assert.equal(await page.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--accent')),'#537bb5');
    await page.emulateMedia({reducedMotion:'reduce'});
    await page.getByRole('button',{name:'播放',exact:true}).click();
    assert.equal(await page.locator('.album-art').evaluate(n=>getComputedStyle(n).animationName),'none');
    report.themeAndReducedMotion=true;
    await page.emulateMedia({reducedMotion:'no-preference'});
    await page.setViewportSize({width:1420,height:1100});
    await page.goto('http://127.0.0.1:8897/gallery-refinements/compare.html',{waitUntil:'networkidle'});
    assert.equal(await page.locator('iframe').count(),3);
    await page.screenshot({path:path.join(root,'screenshots/gallery-overview.png'),fullPage:true});
    assert.deepEqual(report.errors,[]);report.passed=true;
    await fs.writeFile(path.join(root,'GALLERY-PREVIEW-CHECK.json'),JSON.stringify(report,null,2)+'\n');
    console.log(JSON.stringify({passed:true,variants:3,viewportChecks:report.sizes.length,errors:report.errors}));
  }finally{await browser.close();}
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
