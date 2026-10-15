// Standalone preview checks: retain the first B layout and render only its outer rim.
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const {createRequire}=require('node:module');
const req=createRequire(path.join(require('node:os').homedir(),'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json'));
const {chromium}=req('playwright');
const root=path.resolve(__dirname,'../docs/previews/2026-10-14/material-frames');
const base='http://127.0.0.1:8897';
const selectors=['.art-frame','.song-title','.song-subtitle','.lyric-peek','.controls','#seek','.favorite'];
const materials=['original','crystal','platinum','pearl'];
const sizes=[{width:320,height:690},{width:360,height:800},{width:390,height:844},{width:430,height:932}];
const previewUrl=material=>`${base}/material-frames/index.html?material=${material}&embed=1`;
async function geometry(page){
  return page.evaluate(selectors=>Object.fromEntries(selectors.map(selector=>{
    const r=document.querySelector(selector).getBoundingClientRect();
    return [selector,{x:r.x,y:r.y,width:r.width,height:r.height}];
  })),selectors);
}
async function state(page){return page.evaluate(()=>window.materialPreview.getState());}
async function waitForPreview(page){
  await page.waitForFunction(()=>window.materialPreview&&document.querySelector('.material-canvas'));
}
// preserveDrawingBuffer keeps this real WebGL output readable between frames.
// The sample hashes include only the outer 30% band; the album pixels are not read.
async function pixels(page){
  return page.evaluate(()=>{
    window.materialPreview.sync();
    const canvas=document.querySelector('.material-canvas');
    const gl=canvas.getContext('webgl')||canvas.getContext('experimental-webgl');
    if(!gl)throw new Error('The material preview did not create a WebGL context');
    const width=gl.drawingBufferWidth,height=gl.drawingBufferHeight;
    const rgba=new Uint8Array(width*height*4);
    gl.readPixels(0,0,width,height,gl.RGBA,gl.UNSIGNED_BYTE,rgba);
    let centerMaxAlpha=0,rimVisiblePixels=0,hash=2166136261,colorSum=0;
    for(let y=0;y<height;y++)for(let x=0;x<width;x++){
      const i=(y*width+x)*4;
      if(x>=width*.4&&x<width*.6&&y>=height*.4&&y<height*.6)centerMaxAlpha=Math.max(centerMaxAlpha,rgba[i+3]);
      if(x<width*.3||x>=width*.7||y<height*.3||y>=height*.7){
        if(rgba[i+3]>0)rimVisiblePixels++;
        for(let c=0;c<4;c++)hash=Math.imul(hash^rgba[i+c],16777619)>>>0;
        colorSum+=rgba[i]+rgba[i+1]+rgba[i+2];
      }
    }
    return {width,height,centerMaxAlpha,rimVisiblePixels,rimHash:hash,rimColorSum:colorSum,glError:gl.getError()};
  });
}
async function expectClockStopped(page,label){
  // Allow a final queued frame to settle before comparing stable timestamps.
  await page.waitForTimeout(100);
  const before=await state(page);
  await page.waitForTimeout(220);
  const after=await state(page);
  assert.ok(Math.abs(after.clock-before.clock)<.00001,`${label}: automatic material clock kept running`);
  return {before,after};
}
async function expectClockRunning(page,label){
  const before=await state(page);
  await page.waitForFunction(previous=>window.materialPreview.getState().clock>previous,before.clock);
  const after=await state(page);
  assert.ok(after.draws>before.draws,`${label}: material clock advanced without a rendered frame`);
  return {before,after};
}
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  const report={date:'2026-10-15',scope:'Original B with original WebGL cover-rim materials only',geometryTolerancePx:.5,geometry:[],materials:[],errors:[],androidFramePerformanceMeasured:false};
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
      for(const material of materials){
        await page.goto(previewUrl(material),{waitUntil:'networkidle'});
        await waitForPreview(page);
        assert.equal(await page.locator('body').getAttribute('data-scheme'),'B');
        assert.equal(await page.locator('body').getAttribute('data-material'),material);
        assert.equal(await page.locator('.album-art').getAttribute('src'),originalSource);
        const current=await geometry(page);
        let largestDelta=0;
        for(const selector of selectors)for(const key of ['x','y','width','height']){
          const delta=Math.abs(current[selector][key]-original[selector][key]);
          largestDelta=Math.max(largestDelta,delta);
          assert.ok(delta<=.5,`${material} ${size.width}×${size.height} ${selector}.${key}: ${current[selector][key]} vs ${original[selector][key]}`);
        }
        assert.equal(await page.locator('.material-canvas').evaluate(n=>getComputedStyle(n).position),'absolute');
        assert.equal(await page.locator('.material-canvas').evaluate(n=>getComputedStyle(n).pointerEvents),'none');
        if(material!=='original')assert.equal(await page.locator('body').getAttribute('data-renderer'),'webgl');
        report.geometry.push({...size,material,largestDeltaPx:largestDelta,original,current});
      }
    }
    await page.setViewportSize(sizes[2]);
    for(const material of materials.slice(1)){
      await page.goto(previewUrl(material),{waitUntil:'networkidle'});
      await waitForPreview(page);
      const entry={material,renderer:(await state(page)).renderer};
      assert.equal(entry.renderer,'webgl');
      entry.initialPixels=await pixels(page);
      assert.equal(entry.initialPixels.glError,0);
      assert.equal(entry.initialPixels.centerMaxAlpha,0,`${material}: rendered material must not cover the album center`);
      assert.ok(entry.initialPixels.rimVisiblePixels>100,`${material}: the border has no visible WebGL pixels`);
      entry.initialPause=await expectClockStopped(page,`${material} initial pause`);
      const art=await page.locator('.art-button').boundingBox();
      await page.mouse.move(art.x+art.width*.18,art.y+art.height*.25);
      await page.waitForTimeout(180);
      const firstState=await state(page),firstPixels=await pixels(page);
      await page.mouse.move(art.x+art.width*.82,art.y+art.height*.7);
      await page.waitForTimeout(180);
      const secondState=await state(page),secondPixels=await pixels(page);
      assert.ok(Math.abs(firstState.mouseX-secondState.mouseX)>.01||Math.abs(firstState.mouseY-secondState.mouseY)>.01,`${material}: pointer input did not move the material light`);
      assert.notEqual(firstPixels.rimHash,secondPixels.rimHash,`${material}: pointer light moved but edge pixels did not respond`);
      assert.equal(secondPixels.centerMaxAlpha,0);
      entry.pointer={firstState,secondState,firstPixels,secondPixels};
      await page.getByRole('button',{name:'播放',exact:true}).click();
      entry.play=await expectClockRunning(page,`${material} playback`);
      await page.getByRole('button',{name:'暂停',exact:true}).click();
      entry.pause=await expectClockStopped(page,`${material} pause`);
      await page.getByRole('button',{name:'加入我喜欢',exact:true}).click();
      assert.equal(await page.locator('.favorite').getAttribute('aria-pressed'),'true');
      await page.getByRole('button',{name:'下一首',exact:true}).click();
      assert.equal(await page.locator('.song-title').textContent(),'一朵花的时间');
      await page.getByRole('button',{name:'上一首',exact:true}).click();
      assert.equal(await page.locator('.song-title').textContent(),'春日来信');
      await page.locator('#seek').focus();
      const seekBefore=Number(await page.locator('#seek').inputValue());
      await page.keyboard.press('ArrowRight');
      assert.ok(Number(await page.locator('#seek').inputValue())>seekBefore);
      await page.getByRole('button',{name:'播放队列',exact:true}).click();
      assert.equal(await page.locator('.sheet').isVisible(),true);
      await page.locator('[data-track="2"]').click();
      assert.equal(await page.locator('.song-title').textContent(),'沿着风的方向');
      await page.getByRole('button',{name:'播放',exact:true}).click();
      await expectClockRunning(page,`${material} resume`);
      await page.getByRole('button',{name:'查看歌词',exact:true}).click();
      assert.equal(await page.locator('.phone').evaluate(n=>n.classList.contains('lyrics-open')),true);
      entry.lyrics=await expectClockStopped(page,`${material} lyrics`);
      await page.getByRole('tab',{name:'歌曲封面',exact:true}).click();
      await expectClockRunning(page,`${material} return to cover`);
      // This deterministic event tests our handler; it is not an OS background/FPS measurement.
      await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,value:true});document.dispatchEvent(new Event('visibilitychange'));});
      entry.simulatedHidden=await expectClockStopped(page,`${material} simulated hidden`);
      await page.evaluate(()=>{delete document.hidden;document.dispatchEvent(new Event('visibilitychange'));});
      await expectClockRunning(page,`${material} simulated visible`);
      await page.emulateMedia({reducedMotion:'reduce'});
      entry.reducedMotion=await expectClockStopped(page,`${material} reduced motion`);
      entry.reducedMotion.animationPlayStates=await page.evaluate(()=>['.album-art','.playing-indicator i'].map(selector=>({selector,states:[...document.querySelectorAll(selector)].map(node=>getComputedStyle(node).animationPlayState)})));
      for(const group of entry.reducedMotion.animationPlayStates){
        assert.ok(group.states.length>0,`${material}: missing reduced-motion target ${group.selector}`);
        for(const playState of group.states)assert.equal(playState,'paused',`${material}: ${group.selector} did not pause for reduced motion`);
      }
      await page.emulateMedia({reducedMotion:'no-preference'});
      await page.reload({waitUntil:'networkidle'});
      await waitForPreview(page);
      await page.mouse.move(0,0);
      await page.locator('.phone').screenshot({path:path.join(root,`screenshots/${material}.png`)});
      await page.locator('.material-canvas').screenshot({path:path.join(root,`screenshots/${material}-detail.png`)});
      await page.getByRole('button',{name:'播放',exact:true}).click();
      await expectClockRunning(page,`${material} before simulated context loss`);
      const lossExtension=await page.evaluate(()=>{
        const gl=document.querySelector('.material-canvas').getContext('webgl');
        const extension=gl.getExtension('WEBGL_lose_context');
        if(!extension)return false;
        extension.loseContext();return true;
      });
      entry.simulatedContextLoss={extensionAvailable:lossExtension,tested:false,recoveryTested:false};
      if(lossExtension){
        await page.waitForFunction(()=>document.body.dataset.renderer==='fallback');
        assert.equal(await page.locator('.material-fallback').isVisible(),true);
        assert.notEqual(await page.locator('.material-fallback').evaluate(node=>getComputedStyle(node).backgroundImage),'none');
        const stopped=await expectClockStopped(page,`${material} simulated WebGL context loss`);
        assert.equal(stopped.after.active,false);
        assert.equal(stopped.after.auto,false);
        entry.simulatedContextLoss={extensionAvailable:true,tested:true,cssFallbackVisible:true,...stopped,recoveryTested:false};
      }
      entry.interactions={favorite:true,previousNext:true,seek:true,queue:true,lyrics:true};
      report.materials.push(entry);
    }
    await page.setViewportSize({width:1370,height:980});
    await page.goto(`${base}/material-frames/index.html?material=crystal`,{waitUntil:'networkidle'});
    await waitForPreview(page);
    await page.locator('#theme').selectOption('blue');
    assert.equal(await page.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--accent')),'#537bb5');
    const demoBefore=await state(page);
    await page.locator('#light-demo').click();
    await page.waitForTimeout(180);
    const demoAfter=await state(page);
    assert.ok(demoAfter.draws>demoBefore.draws,'The light demo did not render');
    report.themeAndLightDemo={theme:'blue',before:demoBefore,after:demoAfter};
    for(const [file,image,height] of [['compare.html','overview.png',1100],['details.html','details.png',650]]){
      await page.setViewportSize({width:1420,height});
      await page.goto(`${base}/material-frames/${file}`,{waitUntil:'networkidle'});
      assert.ok(await page.locator('iframe').count()>=3);
      for(const frame of page.frames().slice(1))await waitForPreview(frame);
      await page.screenshot({path:path.join(root,`screenshots/${image}`)});
    }
    assert.deepEqual(report.errors,[]);
    report.passed=true;
    await fs.writeFile(path.join(root,'CHECK.json'),JSON.stringify(report,null,2)+'\n');
    console.log(JSON.stringify({passed:true,geometryChecks:report.geometry.length,maxGeometryDeltaPx:Math.max(...report.geometry.map(n=>n.largestDeltaPx)),materials:3,realWebGL:true,centerTransparent:true,pointerPixelsRespond:true,errors:report.errors}));
  }catch(error){
    report.passed=false;report.failure=error.message;
    await fs.writeFile(path.join(root,'CHECK.json'),JSON.stringify(report,null,2)+'\n');
    throw error;
  }finally{await browser.close();}
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
