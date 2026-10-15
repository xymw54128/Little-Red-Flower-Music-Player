// Standalone preview checks. Geometry is compared with the first B, not a redesign.
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const {createRequire}=require('node:module');
const req=createRequire(path.join(require('node:os').homedir(),'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json'));
const {chromium}=req('playwright');
const root=path.resolve(__dirname,'../docs/previews/2026-10-14/cover-frames');
const base='http://127.0.0.1:8897';
const selectors=['.art-frame','.song-title','.song-subtitle','.lyric-peek','.controls','#seek','.favorite'];
const variants=['original','silver','glass','luminous'];
const sizes=[{width:320,height:690},{width:360,height:800},{width:390,height:844},{width:430,height:932}];
async function geometry(page){
  return page.evaluate(selectors=>Object.fromEntries(selectors.map(selector=>{
    const r=document.querySelector(selector).getBoundingClientRect();
    return [selector,{x:r.x,y:r.y,width:r.width,height:r.height}];
  })),selectors);
}
async function frameState(page,frame){
  return page.locator(frame==='luminous'?'.trace-light':'.frame-reflection').evaluate(n=>getComputedStyle(n).animationPlayState);
}
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  const report={date:'2026-10-15',scope:'Original B with absolute-positioned cover frames only',geometryTolerancePx:0.5,errors:[],geometry:[],variants:[],androidFramePerformanceMeasured:false};
  try{
    const page=await browser.newPage({viewport:sizes[2],deviceScaleFactor:1});
    page.setDefaultTimeout(6000);
    page.on('pageerror',e=>report.errors.push(e.message));
    await fs.mkdir(path.join(root,'screenshots'),{recursive:true});
    for(const size of sizes){
      await page.setViewportSize(size);
      await page.goto(`${base}/player-options/index.html?scheme=B&embed=1`,{waitUntil:'networkidle'});
      const original=await geometry(page);
      const originalSource=await page.locator('.album-art').getAttribute('src');
      for(const frame of variants){
        await page.goto(`${base}/cover-frames/index.html?frame=${frame}&embed=1`,{waitUntil:'networkidle'});
        assert.equal(await page.locator('body').getAttribute('data-scheme'),'B');
        assert.equal(await page.locator('body').getAttribute('data-frame'),frame);
        assert.equal(await page.locator('.album-art').getAttribute('src'),originalSource);
        const current=await geometry(page);
        let largestDelta=0;
        for(const selector of selectors)for(const key of ['x','y','width','height']){
          const delta=Math.abs(current[selector][key]-original[selector][key]);
          largestDelta=Math.max(largestDelta,delta);
          assert.ok(delta<=0.5,`${frame} ${size.width}×${size.height} ${selector}.${key}: ${current[selector][key]} vs ${original[selector][key]}`);
        }
        assert.equal(await page.locator('.frame-shell').evaluate(n=>getComputedStyle(n).position),'absolute');
        assert.equal(await page.locator('.frame-shell').evaluate(n=>getComputedStyle(n).pointerEvents),'none');
        report.geometry.push({...size,frame,largestDeltaPx:largestDelta,original,current});
      }
    }
    await page.setViewportSize(sizes[2]);
    for(const frame of variants.slice(1)){
      await page.goto(`${base}/cover-frames/index.html?frame=${frame}&embed=1`,{waitUntil:'networkidle'});
      assert.equal(await frameState(page,frame),'paused');
      await page.getByRole('button',{name:'播放',exact:true}).click();
      assert.equal(await frameState(page,frame),'running');
      await page.getByRole('button',{name:'暂停',exact:true}).click();
      assert.equal(await frameState(page,frame),'paused');
      await page.getByRole('button',{name:'加入我喜欢',exact:true}).click();
      assert.equal(await page.locator('.favorite').getAttribute('aria-pressed'),'true');
      await page.getByRole('button',{name:'下一首',exact:true}).click();
      assert.equal(await page.locator('.song-title').textContent(),'一朵花的时间');
      await page.getByRole('button',{name:'上一首',exact:true}).click();
      assert.equal(await page.locator('.song-title').textContent(),'春日来信');
      await page.locator('#seek').focus();await page.keyboard.press('ArrowRight');
      assert.ok(Number(await page.locator('#seek').inputValue())>0);
      await page.getByRole('button',{name:'播放队列',exact:true}).click();
      assert.equal(await page.locator('.sheet').isVisible(),true);
      await page.locator('[data-track="2"]').click();
      assert.equal(await page.locator('.song-title').textContent(),'沿着风的方向');
      await page.getByRole('button',{name:'播放',exact:true}).click();
      await page.getByRole('button',{name:'查看歌词',exact:true}).click();
      assert.equal(await page.locator('.phone').evaluate(n=>n.classList.contains('lyrics-open')),true);
      assert.equal(await frameState(page,frame),'paused');
      await page.getByRole('tab',{name:'歌曲封面',exact:true}).click();
      assert.equal(await frameState(page,frame),'running');
      // Exercise the visibility handler deterministically; this is not an OS background performance test.
      await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,value:true});document.dispatchEvent(new Event('visibilitychange'));});
      assert.equal(await frameState(page,frame),'paused');
      await page.evaluate(()=>{delete document.hidden;document.dispatchEvent(new Event('visibilitychange'));});
      assert.equal(await frameState(page,frame),'running');
      await page.reload({waitUntil:'networkidle'});
      await page.locator('.phone').screenshot({path:path.join(root,`screenshots/${frame}.png`)});
      report.variants.push({frame,playPause:true,favorite:true,previousNext:true,seek:true,queue:true,lyrics:true,lyricsStopsFrameMotion:true,simulatedHiddenPageStopsFrameMotion:true});
    }
    await page.setViewportSize({width:1370,height:980});
    await page.goto(`${base}/cover-frames/index.html?frame=luminous`,{waitUntil:'networkidle'});
    await page.locator('#theme').selectOption('blue');
    assert.equal(await page.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--accent')),'#537bb5');
    assert.equal(await page.locator('.trace-light').evaluate(n=>getComputedStyle(n).stroke),'rgb(83, 123, 181)');
    const traceTiming=await page.evaluate(()=>['.trace-light','.trace-tip'].map(selector=>{
      const animation=document.querySelector(selector).getAnimations()[0];
      const keyframes=animation.effect.getKeyframes();
      const timing=animation.effect.getTiming();
      return {selector,start:parseFloat(keyframes[0].strokeDashoffset),end:parseFloat(keyframes[keyframes.length-1].strokeDashoffset),duration:timing.duration,iterations:String(timing.iterations),easing:timing.easing,delay:timing.delay};
    }));
    assert.deepEqual(traceTiming.map(n=>[n.start,n.end]),[[0,-100],[-12,-112]]);
    for(const timing of traceTiming){assert.equal(timing.duration,6500);assert.equal(timing.iterations,'Infinity');assert.equal(timing.easing,'linear');}
    assert.equal(traceTiming[0].delay,traceTiming[1].delay);
    report.continuousTraceTiming=traceTiming;
    await page.emulateMedia({reducedMotion:'reduce'});
    await page.getByRole('button',{name:'播放',exact:true}).click();
    for(const selector of ['.frame-reflection','.frame-aura','.trace-light','.trace-tip'])assert.equal(await page.locator(selector).evaluate(n=>getComputedStyle(n).animationName),'none');
    report.themeAndReducedMotion=true;
    await page.emulateMedia({reducedMotion:'no-preference'});
    for(const [file,image] of [['compare.html','overview.png'],['details.html','details.png']]){
      await page.setViewportSize({width:1420,height:file==='details.html'?620:1100});
      await page.goto(`${base}/cover-frames/${file}`,{waitUntil:'networkidle'});
      assert.ok(await page.locator('iframe').count()>=3);
      await page.screenshot({path:path.join(root,`screenshots/${image}`),fullPage:true});
    }
    assert.deepEqual(report.errors,[]);report.passed=true;
    await fs.writeFile(path.join(root,'CHECK.json'),JSON.stringify(report,null,2)+'\n');
    console.log(JSON.stringify({passed:true,geometryChecks:report.geometry.length,maxGeometryDeltaPx:Math.max(...report.geometry.map(n=>n.largestDeltaPx)),variants:3,errors:report.errors}));
  }finally{await browser.close();}
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
