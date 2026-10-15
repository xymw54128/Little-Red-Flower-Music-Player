// Browser checks for independent player proposals, not Android frame measurements.
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const {createRequire}=require('node:module');
const req=createRequire(path.join(require('node:os').homedir(),'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/package.json'));
const {chromium}=req('playwright');
const root=path.resolve(__dirname,'../docs/previews/2026-10-14/player-reimagined');
const base='http://127.0.0.1:8897/player-reimagined';
const layouts=['stage','lyric','shelf'];
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
  const report={date:'2026-10-15',scope:'Reimagined player browser prototypes',errors:[],layouts:[],sizes:[],longMetadata:[],androidFramePerformanceMeasured:false};
  await fs.mkdir(path.join(root,'screenshots'),{recursive:true});
  try{
    const page=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:1});
    page.setDefaultTimeout(6000);
    page.on('pageerror',e=>report.errors.push(e.message));
    const open=async layout=>{
      await page.goto(`${base}/index.html?layout=${layout}&embed=1`,{waitUntil:'networkidle'});
      assert.equal(await page.locator('body').getAttribute('data-layout'),layout);
    };
    for(const layout of layouts){
      await open(layout);
      await page.getByRole('button',{name:'播放',exact:true}).click();
      assert.equal(await page.locator('.phone').getAttribute('data-playing'),'true');
      await page.getByRole('button',{name:'暂停',exact:true}).click();
      assert.equal(await page.locator('.phone').getAttribute('data-playing'),'false');
      await page.getByRole('button',{name:'加入我喜欢',exact:true}).click();
      assert.equal(await page.locator('.favorite').getAttribute('aria-pressed'),'true');
      await page.getByRole('button',{name:'取消喜欢',exact:true}).click();
      assert.equal(await page.locator('.favorite').getAttribute('aria-pressed'),'false');
      await page.getByRole('button',{name:'下一首',exact:true}).click();
      await page.getByRole('button',{name:'下一首',exact:true}).click();
      assert.equal(await page.locator('.song-title').textContent(),'沿着风的方向');
      assert.equal(await page.evaluate(()=>trackIndex),2);
      if(layout==='shelf'){
        await page.locator('.side-cover.next-cover').click();
        assert.equal(await page.locator('.song-title').textContent(),'春日来信');
        await page.locator('.side-cover.previous-cover').click();
        assert.equal(await page.locator('.song-title').textContent(),'沿着风的方向');
      }
      await page.locator('.queue').click();
      assert.equal(await page.locator('.sheet').isVisible(),true);
      await page.locator('.sheet [data-track="0"]').click();
      assert.equal(await page.locator('.song-title').textContent(),'春日来信');
      assert.equal(await page.locator('.sheet').isVisible(),false);
      await page.locator('.mode').click();
      assert.equal(await page.locator('.mode').getAttribute('aria-label'),'列表循环');
      const beforeKey=Number(await page.locator('#seek').inputValue());
      await page.locator('#seek').focus();
      await page.keyboard.press('ArrowRight');
      assert.ok(Number(await page.locator('#seek').inputValue())>beforeKey,'Keyboard seeking must advance the position');
      const seek=await page.locator('#seek').boundingBox();
      await page.mouse.move(seek.x+seek.width*.2,seek.y+seek.height/2);
      await page.mouse.down();
      await page.mouse.move(seek.x+seek.width*.7,seek.y+seek.height/2,{steps:8});
      await page.mouse.up();
      assert.ok(Number(await page.locator('#seek').inputValue())>100,'Dragging must change the position');
      assert.equal(await page.evaluate(()=>dragging),false);
      await page.locator('.art-button').click();
      assert.equal(await page.locator('.phone').evaluate(n=>n.classList.contains('lyrics-open')),true);
      assert.equal(await page.locator('#header-label').textContent(),'春日来信 - 浪雨林');
      await page.evaluate(()=>{
        position=77;updateProgress();
        const lyrics=document.querySelector('.lyrics-scroll');
        lyrics.scrollTop=0;
        lyrics.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerType:'touch',clientX:180,clientY:260}));
        dragging=true;
        lyrics.dispatchEvent(new PointerEvent('pointercancel',{bubbles:true,pointerType:'touch'}));
      });
      assert.equal(await page.evaluate(()=>Number.isFinite(browsingUntil)&&pointerStart===null&&!dragging&&!swallowTap),true);
      await page.waitForFunction(()=>{
        const row=$$('.lyric-line')[activeLine];
        return browsingUntil===0&&row&&Math.abs(scroll.scrollTop-(row.offsetTop+row.offsetHeight/2-scroll.clientHeight/2))<2;
      },null,{timeout:5000});
      await page.getByRole('tab',{name:'歌曲封面',exact:true}).click();
      // A horizontal swipe changes pages; a cancelled vertical touch does not.
      await page.evaluate(()=>{
        const view=document.querySelector('#view-area');
        view.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerType:'touch',clientX:270,clientY:280}));
        view.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,pointerType:'touch',clientX:120,clientY:282}));
      });
      assert.equal(await page.locator('.phone').evaluate(n=>n.classList.contains('lyrics-open')),true);
      await page.getByRole('tab',{name:'歌曲封面',exact:true}).click();
      await open(layout);
      await page.locator('.phone').screenshot({path:path.join(root,`screenshots/${layout}.png`)});
      report.layouts.push({layout,playPause:true,favorite:true,rapidTrackChanges:true,sideCoverSwitch:layout==='shelf',queue:true,playMode:true,keyboardSeek:true,pointerSeek:true,lyricsByClickAndSwipe:true,touchCancelReturnsToCurrent:true});
    }
    for(const size of [{width:320,height:690},{width:360,height:800},{width:390,height:844},{width:430,height:932}]){
      await page.setViewportSize(size);
      for(const layout of layouts){
        await open(layout);
        const bounds=await page.evaluate(()=>{
          const rect=selector=>document.querySelector(selector).getBoundingClientRect();
          const art=rect('.art-button'),heading=rect('.track-heading'),controls=rect('.controls'),seek=rect('#seek');
          const inside=r=>r.width>0&&r.height>0&&r.left>=-2&&r.right<=innerWidth+2&&r.top>=0&&r.bottom<=innerHeight+2;
          return {overflow:document.documentElement.scrollWidth>innerWidth,artInside:inside(art),headingInside:inside(heading),controlsInside:inside(controls),seekInside:inside(seek)};
        });
        assert.equal(bounds.overflow,false,`${layout} ${size.width} horizontal overflow`);
        for(const key of ['artInside','headingInside','controlsInside','seekInside'])assert.equal(bounds[key],true,`${layout} ${size.width}x${size.height} ${key}`);
        report.sizes.push({...size,layout,...bounds});
        if(size.width===320){
          const long=await page.evaluate(()=>{
            document.querySelector('.song-title').textContent='在星光与春风之间一直一直唱给你听 The Longest Spring Letter';
            document.querySelector('.song-subtitle').textContent='一位名字特别长的歌手 Artist Name · 那张值得慢慢听完的专辑 Album Title';
            const rect=selector=>document.querySelector(selector).getBoundingClientRect();
            const title=rect('.song-title'),subtitle=rect('.song-subtitle'),favorite=rect('.favorite'),controls=rect('.controls');
            const overlap=(a,b)=>a.left<b.right-1&&a.right>b.left+1&&a.top<b.bottom-1&&a.bottom>b.top+1;
            const inside=r=>r.width>0&&r.height>0&&r.left>=-2&&r.right<=innerWidth+2&&r.top>=0&&r.bottom<=innerHeight+2;
            return {overflow:document.documentElement.scrollWidth>innerWidth,titleInside:inside(title),subtitleInside:inside(subtitle),favoriteInside:inside(favorite),controlsInside:inside(controls),titleOverFavorite:overlap(title,favorite),subtitleOverFavorite:overlap(subtitle,favorite),metadataOverControls:overlap(title,controls)||overlap(subtitle,controls)||overlap(favorite,controls)};
          });
          report.longMetadata.push({layout,...long});
          assert.equal(long.overflow,false,`${layout} long metadata overflow`);
          for(const key of ['titleInside','subtitleInside','favoriteInside','controlsInside'])assert.equal(long[key],true,`${layout} long metadata ${key}`);
          for(const key of ['titleOverFavorite','subtitleOverFavorite','metadataOverControls'])assert.equal(long[key],false,`${layout} long metadata ${key}`);
        }
      }
    }
    await page.setViewportSize({width:1370,height:980});
    await page.goto(`${base}/index.html`,{waitUntil:'networkidle'});
    for(const layout of layouts){
      await page.locator(`.layout-choice[data-layout="${layout}"]`).click();
      assert.equal(await page.locator('body').getAttribute('data-layout'),layout);
    }
    await page.locator('#theme').selectOption('blue');
    assert.equal(await page.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--accent')),'#537bb5');
    report.layoutPickerAndTheme=true;
    await page.emulateMedia({reducedMotion:'reduce'});
    await page.getByRole('button',{name:'播放',exact:true}).click();
    await page.waitForTimeout(100);
    assert.equal(await page.locator('.album-art').evaluate(n=>getComputedStyle(n).animationName),'none');
    assert.equal(await page.evaluate(()=>document.getAnimations().some(a=>a.playState==='running'&&a.effect.getComputedTiming().iterations===Infinity&&document.querySelector('.phone').contains(a.effect.target))),false);
    report.reducedMotion=true;
    await page.emulateMedia({reducedMotion:'no-preference'});
    await page.setViewportSize({width:1420,height:1100});
    await page.goto(`${base}/compare.html`,{waitUntil:'networkidle'});
    assert.equal(await page.locator('iframe').count(),3);
    await page.screenshot({path:path.join(root,'screenshots/overview.png'),fullPage:true});
    assert.deepEqual(report.errors,[]);
    report.passed=true;
    console.log(JSON.stringify({passed:true,layouts:layouts.length,viewportChecks:report.sizes.length,errors:report.errors}));
  }catch(e){
    report.passed=false;
    report.failure=e.message;
    throw e;
  }finally{
    await fs.writeFile(path.join(root,'CHECK.json'),JSON.stringify(report,null,2)+'\n');
    await browser.close();
  }
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
