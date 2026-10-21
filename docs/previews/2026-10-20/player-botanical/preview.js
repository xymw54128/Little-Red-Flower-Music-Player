'use strict';
// Independent visual prototype. All media and state stay local; no audio is played.
const designs=[
  {id:'A',name:'疏朗花枝',caption:'两束花枝错落在对角，细线轻轻收住封面。红花、奶白小花与灰绿叶片，留出大面积呼吸空间。'},
  {id:'B',name:'沿框藤蔓',caption:'细藤顺着边框转折，几朵小花自然停在枝间。更完整的植物画框，仍让封面成为主角。'},
  {id:'C',name:'彩色野花',caption:'蓝、紫、金色小野花从边角探出，搭配轻盈叶枝。色彩更活泼，保留小红花的柔和气质。'}
];
const tracks=[
  {title:'春日来信',artist:'浪雨林',album:'花开的时候'},
  {title:'一朵花的时间',artist:'浪雨林',album:'慢慢生长'},
  {title:'沿着风的方向',artist:'浪雨林',album:'花开的时候'}
];
const lyrics=['风经过花窗','把昨天留在远方','一束光落在手上','把春天唱给你听','让每一朵花都记得你','也记得温柔的自己','向着风慢慢生长','陪你走过每个季节'];
const timestamps=[0,18,44,70,96,130,162,192];
const params=new URLSearchParams(location.search);
const focus=designs.find(d=>d.id===params.get('focus')?.toUpperCase());
if(focus)document.body.classList.add('focus');
if(params.get('embed')==='1')document.body.classList.add('embed');
const icon=id=>'<svg class="icon" aria-hidden="true"><use href="#'+id+'"/></svg>';
const titleBloomPath="M41.152,35.018 A13,13 0,0 1 66.848,35.018 Q67.013,36.089 68.082,35.915 A13,13 0,0 1 76.023,60.354 Q75.056,60.841 75.551,61.805 A13,13 0,0 1 54.763,76.908 Q54,76.139 53.237,76.908 A13,13 0,0 1 32.449,61.805 Q32.944,60.841 31.977,60.354 A13,13 0,0 1 39.918,35.915 Q40.987,36.089 41.152,35.018 Z";
function titleSprig(mirrored){
  const side=mirrored?'right':'left';
  const flower=(x,y,rotation,scale,color)=>'<g data-sprig-blossom style="color:'+color+'" transform="translate('+x+' '+y+') rotate('+rotation+') scale('+scale+')"><g transform="translate(-54 -54)"><path d="'+titleBloomPath+'" fill="currentColor"/><circle cx="54" cy="54" r="7.5" fill="#F6D88C"/></g></g>';
  const leaf=(x,y,rotation,scale,color)=>'<path class="title-leaf" d="M0 0C-4-1-7-5-7-9C-2-9 2-5 0 0Z" transform="translate('+x+' '+y+') rotate('+rotation+') scale('+scale+')" fill="'+color+'"/>';
  return '<svg class="title-sprig" data-sprig-side="'+side+'" viewBox="0 0 36 36" aria-hidden="true"><g data-sprig-shape transform="'+(mirrored?'translate(36 36) scale(-1 -1)':'')+'"><path class="title-stem" d="M34 29C25 29 17 22 12 10M20 25Q23 22 24 16"/>'+(mirrored?'<path class="title-twig" d="M15 20Q9 21 8 23"/>':'')+leaf(20,25,-20,.75,'#55AD70')+leaf(28,28,90,.6,'#7DBD68')+(mirrored?flower(12,10,-10,.215,'#4F9DE8')+flower(24,16,12,.18,'#F0B83D')+flower(8,23,5,.155,'#9D79D7'):flower(12,10,-12,.225,'#CE3C4C')+flower(24,16,14,.18,'#ED798C'))+'</g></svg>';
}

function botanicalFrame(id){
  // Each frame owns its SVG definitions, so the three options can be viewed together.
  const prefix='plant-'+id;
  const use=(shape,transform,cls='')=>'<use href="#'+prefix+'-'+shape+'" transform="'+transform+'" class="'+cls+'"/>';
  const leaf=(x,y,rotation,scale=1,light=false)=>use('leaf','translate('+x+' '+y+') rotate('+rotation+') scale('+scale+')',light?'light':'');
  const rose=(x,y,rotation,scale=1)=>use('rose','translate('+x+' '+y+') rotate('+rotation+') scale('+scale+')');
  const daisy=(x,y,rotation,scale=1)=>use('daisy','translate('+x+' '+y+') rotate('+rotation+') scale('+scale+')');
  const wild=(x,y,rotation,scale,color)=>'<g style="color:'+color+'">'+use('wild','translate('+x+' '+y+') rotate('+rotation+') scale('+scale+')')+'</g>';
  const defs='<defs>'+
    '<g id="'+prefix+'-leaf"><path class="botanical-leaf" d="M0 0C-4-7-1-16 5-21C13-12 10-3 0 0Z"/><path class="leaf-vein" d="M1-2Q5-10 5-18"/></g>'+
    '<g id="'+prefix+'-rose"><path class="rose-petal" d="M-1-2C-13-10-10-20-2-17C5-24 15-15 10-7C23-3 15 9 7 8C5 20-7 18-8 10C-23 12-23-3-12-4C-15-13-6-16-1-2Z"/>'+
    '<path class="theme-petal" d="M0-3C-6-12-13-7-8-1C-18 3-10 11-4 5C-3 16 9 12 6 5C18 5 15-8 6-4C10-15-1-17 0-3Z"/>'+
    '<path class="petal-detail" d="M-7-3Q-4-7 0-5M6-4Q10-1 8 3M-5 6Q0 10 4 6"/><circle class="gold-center" r="3.1"/><circle cx="-.9" cy="-.8" r=".8" fill="#fff4ce"/></g>'+
    '<g id="'+prefix+'-daisy"><path class="cream-petal" d="M0-2C-10-7-7-17-1-11C1-22 12-16 6-7C18-12 23-1 10 2C24 5 18 15 6 7C8 22-4 24-4 10C-13 20-21 10-9 3C-23 1-19-11-7-5Z"/><circle class="gold-center" r="3.7"/><path d="M-1-1h.1M1.4.8h.1" stroke="#f8e3a0" stroke-width="1.5"/></g>'+
    '<g id="'+prefix+'-wild"><path fill="currentColor" d="M0-2C-8-5-9-13-3-13C1-22 10-16 6-6C18-13 23-3 10 1C23 5 16 16 6 7C7 21-5 20-5 8C-15 19-24 9-10 2C-23-1-17-13-6-6Z"/><path class="petal-detail" d="M-6-5-2-2M4-8 2-3M8 4 3 2M0 10V4M-9 4-4 2"/><circle class="gold-center" r="3.2"/></g></defs>';
  let art='';
  if(id==='A'){
    art='<rect class="frame-line" x="17" y="17" width="326" height="326" rx="26"/>'+
    '<g class="ornament-sway"><path class="branch" d="M244 14C271 18 293 4 317 11C337 17 345 33 346 57"/>'+
    '<path class="branch-fine" d="M302 10Q313-3 326 2M339 29Q355 22 355 12"/>'+
    leaf(284,14,-79,.72,true)+leaf(308,10,25,.92)+leaf(334,24,-37,.8,true)+leaf(344,43,47,.72)+rose(325,10,13,.94)+daisy(348,47,-13,.49)+'</g>'+
    '<g class="ornament-sway second"><path class="branch" d="M15 267C8 291 7 322 23 338C38 354 64 348 94 345"/>'+
    '<path class="branch-fine" d="M12 315Q2 308 3 295M33 345Q27 359 42 358"/>'+
    leaf(12,295,-34,.79,true)+leaf(13,318,-68,.91)+leaf(29,342,-128,.75,true)+leaf(63,348,81,.75)+daisy(18,334,9,.87)+rose(50,348,-21,.47)+'</g>';
  }else if(id==='B'){
    art='<rect class="frame-line" x="17" y="17" width="326" height="326" rx="26"/>'+
    '<g class="ornament-sway"><path class="branch" d="M111 15C76 12 41 9 22 22C8 35 14 68 12 99C10 130 13 155 17 175"/>'+
    '<path class="branch-fine" d="M16 62Q1 53 3 36M45 13Q39 1 27 3M13 117Q27 104 24 92"/>'+
    leaf(87,14,72,.64,true)+leaf(63,13,-70,.72)+leaf(34,18,16,.8,true)+leaf(12,73,-44,.77)+leaf(12,109,37,.86,true)+leaf(14,146,-41,.63)+rose(18,31,-15,.87)+daisy(12,126,18,.44)+'</g>'+
    '<g class="ornament-sway second"><path class="branch" d="M220 345C254 348 301 351 326 339C350 327 348 297 346 268C345 245 345 220 341 201"/>'+
    '<path class="branch-fine" d="M285 347Q290 359 306 356M346 291Q358 284 357 270M345 242Q333 233 335 222"/>'+
    leaf(249,348,81,.7,true)+leaf(275,349,-72,.72)+leaf(313,345,29,.92)+leaf(344,311,138,.72,true)+leaf(346,278,-36,.82)+leaf(344,235,35,.65,true)+rose(338,332,31,.88)+daisy(344,255,-18,.49)+'</g>'+
    '<path class="branch-fine" d="M256 14Q297 8 326 18Q344 26 345 58"/>'+leaf(294,12,15,.59,true)+daisy(332,22,7,.56);
  }else{
    art='<path class="frame-line" d="M19 97V43Q19 19 43 19H267M341 94V265M265 341H96M19 269V183"/>'+
    '<g class="ornament-sway"><path class="branch" d="M13 316C22 322 31 335 51 344C67 353 91 349 113 344M25 332Q12 304 6 290M35 338Q33 315 24 303M55 345Q57 326 68 316"/>'+
    '<path class="branch-fine" d="M41 340Q36 353 25 357M77 348Q88 332 85 323"/>'+
    leaf(18,317,-67,.92,true)+leaf(30,334,-127,.9)+leaf(54,345,78,.85,true)+leaf(87,348,64,.6)+
    wild(16,298,-18,.82,'#789dbb')+wild(33,325,14,.7,'#a68ab7')+daisy(59,345,13,.74)+wild(77,326,-15,.5,'#d6b569')+rose(19,346,-12,.44)+'</g>'+
    '<g class="ornament-sway second"><path class="branch" d="M270 13C297 8 330 12 341 30C350 44 348 67 345 85"/>'+
    '<path class="branch-fine" d="M317 14Q323 1 333 4M344 50Q358 45 356 31"/>'+
    leaf(297,12,-72,.76,true)+leaf(334,24,42,.85)+leaf(347,65,28,.72,true)+daisy(334,17,-13,.88)+wild(349,47,17,.52,'#9c8bb3')+'</g>';
  }
  return '<svg class="decoration" viewBox="0 0 360 360" aria-hidden="true">'+defs+art+'</svg>';
}
function musicFlower(){
  const colors=['#d85d67','#e79a5d','#d7bc59','#86aa7a','#69a8af','#798fc2','#ad86b5'];
  return '<svg class="music-flower" viewBox="0 0 30 30" aria-hidden="true"><g class="music-flower-rotor">'+colors.map((c,i)=>'<path d="M15 15C8 9 10 1 16 2C23 3 22 10 15 15Z" fill="'+c+'" transform="rotate('+i*360/7+' 15 15)"/>').join('')+'<circle cx="15" cy="15" r="3.1" fill="#e4c779"/></g></svg>';
}
document.querySelector('.designs').innerHTML=designs.filter(d=>!focus||d.id===focus.id).map(d=>
  '<article class="design-card" data-design="'+d.id+'"><header class="design-header"><h2><b>'+d.id+'</b>'+d.name+'</h2><a href="?focus='+d.id+'">单独查看</a></header>'+
  '<div class="phone" data-playing="false"><div class="status-bar" aria-hidden="true"><span>9:41</span><div class="status-right"><svg viewBox="0 0 20 14"><path d="M2 12V9m4 3V6m4 6V3m4 9V1"/></svg><svg viewBox="0 0 20 14"><path d="M2 5q8-7 16 0M5 8q5-5 10 0M8 11q2-2 4 0"/></svg><i class="battery"></i></div></div>'+
  '<header class="player-header"><button class="icon-button close-player" aria-label="收起播放器">'+icon('down')+'</button><div class="header-title">'+titleSprig(false)+'<span class="header-text"><span class="header-label">正在播放</span></span>'+titleSprig(true)+'</div><button class="flower-button" aria-label="旋转小红花"><img src="assets/flower.svg" alt=""></button></header>'+
  '<div class="view-area"><div class="page-track"><section class="cover-view" aria-label="歌曲播放页"><div class="hero"><button class="artwork" aria-label="查看歌词"><img class="album-art" src="assets/daylight-cover.png" alt="春日来信演示封面">'+botanicalFrame(d.id)+'</button></div>'+
  '<div class="track-heading"><div><h2 class="song-title">春日来信</h2><p class="song-subtitle">浪雨林 · 花开的时候</p></div><button class="icon-button favorite" aria-label="加入我喜欢" aria-pressed="false">'+icon('heart')+'</button></div>'+
  '<div class="quality"><span class="quality-tag">FLAC</span><span>16 bit / 44.1 kHz</span>'+musicFlower()+'</div><button class="lyric-peek" aria-label="打开完整歌词"><span class="preview-line">把春天唱给你听</span></button></section>'+
  '<section class="lyrics-view" aria-label="完整歌词" aria-hidden="true" inert><div class="lyrics-scroll" tabindex="0"><div class="lyric-spacer"></div>'+lyrics.map(t=>'<div class="lyric-line">'+t+'</div>').join('')+'<div class="lyric-spacer"></div></div></section></div></div>'+
  '<div class="page-switch" role="tablist" aria-label="播放器页面"><button class="tab" data-page="cover" role="tab" aria-selected="true" aria-label="歌曲封面"><span></span></button><button class="tab" data-page="lyrics" role="tab" aria-selected="false" aria-label="歌词"><span></span></button></div>'+
  '<footer class="transport"><div class="seek-wrap"><input class="seek" type="range" min="0" max="228" value="77" step=".1" aria-label="播放进度"></div><div class="time-row"><span class="elapsed">1:17</span><span class="duration">3:48</span></div>'+
  '<div class="controls"><button class="icon-button mode" aria-label="顺序播放">'+icon('mode')+'</button><button class="icon-button previous" aria-label="上一首">'+icon('previous')+'</button><button class="play" aria-label="播放" aria-pressed="false">'+icon('play').replace('class="icon"','class="icon play-icon"')+icon('pause').replace('class="icon"','class="icon pause-icon"')+'</button><button class="icon-button next" aria-label="下一首">'+icon('next')+'</button><button class="icon-button queue" aria-label="播放队列">'+icon('queue')+'</button></div>'+
  '<div class="transport-foot"><span class="mode-label">顺序播放</span><button class="icon-button more" aria-label="歌曲更多选项">'+icon('more')+'</button></div></footer><div class="gesture-line" aria-hidden="true"></div><div class="sheet-backdrop" hidden></div><section class="sheet" role="dialog" aria-modal="true" aria-label="播放队列" hidden><header><h3>播放队列</h3><button class="icon-button dismiss" aria-label="关闭面板">×</button></header><div class="sheet-content"></div></section></div>'+
  '<p class="design-caption">'+d.caption+'</p></article>').join('');

const reduceMotion=matchMedia('(prefers-reduced-motion: reduce)');
const players=[];
const time=s=>Math.floor(s/60)+':'+String(Math.floor(s%60)).padStart(2,'0');
for(const phone of document.querySelectorAll('.phone')){
  const find=s=>phone.querySelector(s),all=s=>[...phone.querySelectorAll(s)];
  let track=0,position=77,playing=false,mode=0,active=-1,view='cover',browseUntil=0,dragging=false,flowerAnimation;
  let rotation=0,speed=0,target=0,startSpeed=0,transitionTime=0,transitionDuration=0;
  const scroll=find('.lyrics-scroll');
  function centerLyrics(animate=true){const row=all('.lyric-line')[active];if(row)scroll.scrollTo({top:row.offsetTop+row.offsetHeight/2-scroll.clientHeight/2,behavior:animate&&!reduceMotion.matches?'smooth':'instant'});}
  function update(){
    find('.seek').value=position;find('.seek').style.setProperty('--progress',position/228*100+'%');find('.elapsed').textContent=time(position);
    const index=timestamps.findLastIndex(t=>position>=t);
    if(index!==active){active=index;all('.lyric-line').forEach((n,i)=>n.classList.toggle('current',i===active));find('.preview-line').textContent=lyrics[active]||'·';if(view==='lyrics'&&!browseUntil)centerLyrics();}
  }
  function setPage(page){view=page;browseUntil=0;phone.classList.toggle('lyrics-open',page==='lyrics');find('.cover-view').inert=page==='lyrics';find('.lyrics-view').inert=page!=='lyrics';find('.cover-view').setAttribute('aria-hidden',String(page==='lyrics'));find('.lyrics-view').setAttribute('aria-hidden',String(page!=='lyrics'));find('.header-label').textContent=page==='lyrics'?tracks[track].title+' - '+tracks[track].artist:'正在播放';find('.more').hidden=page==='lyrics';all('.tab').forEach(n=>n.setAttribute('aria-selected',String(n.dataset.page===page)));if(page==='lyrics')centerLyrics(false);}
  function setTrack(next){track=(next+tracks.length)%tracks.length;position=0;const t=tracks[track];find('.song-title').textContent=t.title;find('.song-subtitle').textContent=t.artist+' · '+t.album;find('.album-art').alt=t.title+'演示封面';find('.favorite').setAttribute('aria-pressed','false');find('.favorite').setAttribute('aria-label','加入我喜欢');update();setPage(view);}
  function setPlaying(value){playing=value;phone.dataset.playing=String(value);find('.play').setAttribute('aria-pressed',String(value));find('.play').setAttribute('aria-label',value?'暂停':'播放');startSpeed=speed;target=value?48:0;transitionTime=0;transitionDuration=value?240:900;}
  function closeSheet(){find('.sheet').hidden=true;find('.sheet-backdrop').hidden=true;find('.queue').focus();}
  find('.play').onclick=()=>setPlaying(!playing);find('.next').onclick=()=>setTrack(track+1);find('.previous').onclick=()=>setTrack(track-1);
  find('.artwork').onclick=()=>setPage('lyrics');find('.lyric-peek').onclick=()=>setPage('lyrics');all('.tab').forEach(n=>n.onclick=()=>setPage(n.dataset.page));find('.close-player').onclick=()=>setPage('cover');
  find('.favorite').onclick=()=>{const n=find('.favorite'),value=n.getAttribute('aria-pressed')!=='true';n.setAttribute('aria-pressed',String(value));n.setAttribute('aria-label',value?'取消喜欢':'加入我喜欢');n.classList.remove('pop');void n.offsetWidth;n.classList.add('pop');};
  find('.flower-button').onclick=()=>{const img=find('.flower-button img');if(reduceMotion.matches)return;const transform=getComputedStyle(img).transform;flowerAnimation?.cancel();flowerAnimation=img.animate([{transform},{transform:'rotate(1080deg)'}],{duration:2000,easing:'cubic-bezier(.16,.65,.16,1)'});};
  find('.mode').onclick=()=>{mode=(mode+1)%4;const name=['顺序播放','列表循环','单曲循环','随机播放'][mode];find('.mode-label').textContent=name;find('.mode').setAttribute('aria-label',name);};
  find('.queue').onclick=()=>{find('.sheet h3').textContent='播放队列';find('.sheet').setAttribute('aria-label','播放队列');find('.sheet-content').innerHTML=tracks.map((t,i)=>'<button class="sheet-option" data-track="'+i+'">'+t.title+'<small>'+t.artist+' · '+t.album+'</small></button>').join('');all('[data-track]').forEach(n=>n.onclick=()=>{setTrack(Number(n.dataset.track));closeSheet();});find('.sheet').hidden=false;find('.sheet-backdrop').hidden=false;find('.dismiss').focus();};
  find('.more').onclick=()=>{find('.sheet h3').textContent='歌曲信息';find('.sheet').setAttribute('aria-label','歌曲信息');find('.sheet-content').textContent=tracks[track].title+' / '+tracks[track].artist+' / FLAC 16 bit 44.1 kHz';find('.sheet').hidden=false;find('.sheet-backdrop').hidden=false;find('.dismiss').focus();};
  find('.dismiss').onclick=closeSheet;find('.sheet-backdrop').onclick=closeSheet;phone.addEventListener('keydown',e=>{if(e.key==='Escape')closeSheet();});
  find('.seek').oninput=e=>{position=Number(e.target.value);update();if(view==='lyrics')centerLyrics();};find('.seek').onpointerdown=()=>dragging=true;
  scroll.addEventListener('pointerdown',()=>browseUntil=Infinity);scroll.addEventListener('wheel',()=>browseUntil=performance.now()+1500,{passive:true});
  scroll.addEventListener('scroll',()=>{if(browseUntil&&browseUntil!==Infinity)browseUntil=performance.now()+1500;},{passive:true});
  let pointerStart=null,swallowClick=false;
  find('.view-area').addEventListener('pointerdown',e=>{pointerStart={x:e.clientX,y:e.clientY};swallowClick=false;});
  find('.view-area').addEventListener('pointerup',e=>{if(!pointerStart)return;const dx=e.clientX-pointerStart.x,dy=e.clientY-pointerStart.y;pointerStart=null;if(Math.abs(dx)>55&&Math.abs(dx)>Math.abs(dy)*1.3){swallowClick=true;setPage(dx<0?'lyrics':'cover');}});
  find('.view-area').addEventListener('click',e=>{if(swallowClick){swallowClick=false;e.stopPropagation();e.preventDefault();}},true);
  for(const event of ['pointerup','pointercancel'])window.addEventListener(event,()=>{dragging=false;pointerStart=null;if(browseUntil===Infinity)browseUntil=performance.now()+1500;});
  const titleBox=find('.header-text'),titleLabel=find('.header-label');
  function measureTitle(){
    const overflow=Math.max(0,titleLabel.scrollWidth-titleBox.clientWidth);
    titleBox.dataset.overflow=String(overflow>1);
    titleBox.style.setProperty('--title-pan-distance',-overflow+'px');
    titleBox.style.setProperty('--title-pan-duration',Math.max(6,overflow/24+2.4)+'s');
  }
  const titleResize=new ResizeObserver(measureTitle);titleResize.observe(titleBox);titleResize.observe(titleLabel);
  const measure=()=>{const hero=find('.hero');phone.style.setProperty('--cover-size',Math.max(0,Math.min(hero.clientWidth-4,hero.clientHeight/1.125,400))+'px');};
  new ResizeObserver(measure).observe(find('.hero'));measure();update();setPage(params.get('page')==='lyrics'?'lyrics':'cover');
  players.push({phone,resetMotion(){speed=0;startSpeed=0;transitionTime=0;transitionDuration=0;target=playing?48:0;flowerAnimation?.cancel();},tick(delta,now){
    if(playing&&!dragging){position=Math.min(228,position+delta/1000);if(position>=228){if(mode===2)position=0;else setTrack(track+1);}update();}
    if(!reduceMotion.matches){
      if(transitionDuration&&transitionTime<transitionDuration){const old=transitionTime/transitionDuration,next=Math.min(1,(transitionTime+delta)/transitionDuration),integral=t=>t*t*t-.5*t*t*t*t,step=(next-old)*transitionDuration;rotation+=(startSpeed*step+(target-startSpeed)*transitionDuration*(integral(next)-integral(old))+target*(delta-step))/1000;transitionTime=Math.min(transitionDuration,transitionTime+delta);speed=startSpeed+(target-startSpeed)*next*next*(3-2*next);}else{speed=target;rotation+=speed*delta/1000;}
      rotation%=360;find('.music-flower-rotor').setAttribute('transform','rotate('+rotation+' 15 15)');
    }
    if(browseUntil&&browseUntil!==Infinity&&now>browseUntil&&view==='lyrics'){browseUntil=0;centerLyrics();}
  }});
}
document.querySelector('#theme').onchange=e=>{const colors={red:'#ce3c4c',green:'#588675',blue:'#537bb5'};document.documentElement.style.setProperty('--accent',colors[e.target.value]);};
let lastFrame=0;
function frame(now){const delta=lastFrame?Math.min(now-lastFrame,64):0;lastFrame=now;if(!document.hidden)for(const player of players)player.tick(delta,now);requestAnimationFrame(frame);}
document.addEventListener('visibilitychange',()=>{lastFrame=0;for(const player of players)player.phone.dataset.hidden=String(document.hidden);});
reduceMotion.addEventListener('change',()=>players.forEach(p=>p.resetMotion()));
requestAnimationFrame(frame);
