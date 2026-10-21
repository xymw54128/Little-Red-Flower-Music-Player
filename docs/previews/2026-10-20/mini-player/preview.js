'use strict';
const designs=[
  {id:'A',name:'花瓣唱片',caption:'<strong>把封面托成一朵花。</strong>浅红花瓣承托静止封面，细圆弧记录进度。轻巧、柔和，最接近现有界面。'},
  {id:'B',name:'花枝书签',caption:'<strong>让进度沿着花枝生长。</strong>细茎与两片小叶收在下沿，小红花跟随播放进度，留出干净的信息和操作区。'},
  {id:'C',name:'花心播放键',caption:'<strong>点一下，花开成音乐。</strong>五瓣红花直接成为播放键，点击时轻轻展开、散出三片花瓣，暂停后收拢。'},
  {id:'D',name:'七色花岛',caption:'<strong>一座绛红色的音乐小岛。</strong>七色小花停在封面一角，播放时旋转，暂停后缓停；暖金细线留住进度。'}
];
const tracks=[
  {title:'Salt',artist:'Ava Max',duration:180,cover:'photo'},
  {title:'春日来信',artist:'小红花精选',duration:216,cover:'spring-letter.svg'},
  {title:'沿着风的方向',artist:'小红花精选',duration:204,cover:'wind.svg'}
];
const params=new URLSearchParams(location.search);
const focus=designs.find(d=>d.id===params.get('focus')?.toUpperCase());
const refined=focus?.id==='A'&&params.get('refined')==='1';
if(refined){
  focus.name='七色花瓣';
  focus.caption='<strong>封面成为花心，绿叶收在边框。</strong>圆形进度紧贴封面，七色花瓣沿边缘旋转、逐片轻跳；暂停后用 0.9 秒缓慢停住。';
  document.body.classList.add('refined');
  document.querySelector('.intro').textContent='在 A 方案上细化：进度环贴合封面，七色花瓣逐片接力轻跳，两簇绿叶点缀圆润边框。';
  document.querySelector('.demo-controls small').textContent='播放时钟驱动的节奏示意，非音频节拍检测；本页无实际音频。';
}
if(focus){document.body.classList.add('focus');document.title=refined?'七色花瓣 · 迷你播放栏':'小红花 · '+focus.name;document.querySelector('h1').textContent=refined?'七色花瓣 · 迷你播放栏':focus.name;}
const icon=id=>'<svg aria-hidden="true"><use href="#'+id+'"/></svg>';
const nav='<nav class="nav" aria-label="首页效果示意"><div class="nav-item active"><div class="nav-symbol"><svg viewBox="0 0 24 24"><path d="M5 3h14v16H5zM2 6v16h14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M10 7v8c-4-2-5 4-1 3 2 0 3-2 3-4V9l4-1V5z"/></svg></div><span>音乐库</span></div><div class="nav-item"><div class="nav-symbol"><svg viewBox="0 0 24 24"><path d="M2 5h13v2H2zM2 9h13v2H2zM2 13h9v2H2zM17 5h4v3h-2v9c0 4-7 4-7 0 0-2 3-4 5-2z"/></svg></div><span>歌单</span></div><div class="nav-item"><div class="nav-symbol"><svg viewBox="0 0 24 24"><path d="m10 2 4 0 .8 3 2.2 1.3 3-.7 2 3.5-2.2 2.2v2.5L22 16l-2 3.5-3-.8-2.2 1.3-.8 3h-4l-.8-3L7 18.7l-3 .8L2 16l2.2-2.2v-2.5L2 9l2-3.5 3 .8L9.2 5Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><circle cx="12" cy="12.5" r="3.4" fill="none" stroke="currentColor" stroke-width="1.7"/></svg></div><span>设置</span></div></nav>';
function ornament(id){
  if(refined&&id==='A'){
    const colors=['#D85D67','#E79A5D','#D7BC59','#86AA7A','#69A8AF','#798FC2','#AD86B5'];
    const petals=colors.map((color,i)=>'<g transform="rotate('+(i*360/7)+' 32 32)"><g class="orbit-petal" data-petal><path d="M21.4 14C17.8 12.3 17.9 8.9 21.7 5.5C27.2 .8 36.8 .8 42.3 5.5C46.1 8.9 46.2 12.3 42.6 14C37 16.3 27 16.3 21.4 14Z" fill="'+color+'"/><path d="M21.4 14C17.8 12.3 17.9 8.9 21.7 5.5C27.2 .8 36.8 .8 42.3 5.5C46.1 8.9 46.2 12.3 42.6 14C37 16.3 27 16.3 21.4 14Z" fill="#fff" opacity=".15" transform="translate(32 12) scale(.54 .78) translate(-32 -12)"/></g></g>').join('');
    return '<span class="calyx rainbow-calyx" data-rotor aria-hidden="true"><svg viewBox="0 0 64 64">'+petals+'</svg></span><svg class="cover-ring" viewBox="0 0 64 64" aria-hidden="true"><circle class="ring-base" cx="32" cy="32" r="20.8"/><circle class="ring-value" cx="32" cy="32" r="20.8" pathLength="100"/></svg>';
  }
  if(id==='A')return '<svg class="calyx" viewBox="0 0 100 100" aria-hidden="true"><use href="#flower"/></svg><svg class="cover-ring" viewBox="0 0 100 100" aria-hidden="true"><circle class="ring-base" cx="50" cy="50" r="45"/><circle class="ring-value" cx="50" cy="50" r="45" pathLength="100"/></svg><svg class="a-leaf" viewBox="0 0 30 20" aria-hidden="true"><path d="M2 17C3 4 20 0 28 2C25 14 14 21 2 17Z"/></svg>';
  if(id==='B')return '<span class="b-bud" aria-hidden="true">'+icon('flower')+'</span>';
  if(id==='D')return '<span class="rainbow-badge" aria-hidden="true"><span class="rotor" data-rotor style="display:block;width:100%;height:100%">'+icon('rainbow')+'</span></span>';
  return '';
}
function extra(id){
  if(refined&&id==='A')return '<svg class="frame-leaves frame-leaves-upper" viewBox="0 0 64 18" aria-hidden="true"><path class="leaf-stem" d="M18 1C28 1 34 9 44 12"/><path class="leaf-soft" d="M23 4C25-2 33-2 37 0C36 7 29 10 23 4Z"/><path class="leaf-deep" d="M35 8C42 1 49 3 52 7C46 13 39 14 35 8Z"/><path class="leaf-vein" d="M27 4.5L35 1.5M38 8L48 7"/></svg><svg class="frame-leaves frame-leaves-lower" viewBox="0 0 70 18" aria-hidden="true"><path class="leaf-stem" d="M29 17C38 17 45 15 59 8"/><path class="leaf-soft" d="M43 15C35 7 26 8 24 11C29 16 36 17 43 15Z"/><path class="leaf-deep" d="M45 14C46 4 56 3 62 4C60 12 53 16 45 14Z"/><path class="leaf-vein" d="M35 14L28 11M48 12L57 7"/></svg>';
  if(id==='B')return '<div class="stem-rail" aria-hidden="true"><svg viewBox="0 0 320 25" preserveAspectRatio="none"><path class="stem" d="M2 19C45 19 57 13 98 15S164 21 206 17S271 13 318 14"/><path class="stem-progress" pathLength="100" d="M2 19C45 19 57 13 98 15S164 21 206 17S271 13 318 14"/><path class="stem-leaf" d="M43 18C35 7 25 8 22 11C27 19 34 21 43 18ZM66 16C70 6 80 4 86 6C82 15 74 18 66 16Z"/></svg><span class="stem-bloom">'+icon('flower')+'</span></div>';
  if(id==='C')return '<span class="c-petal one"></span><span class="c-petal two"></span><span class="c-petal three"></span>';
  if(id==='D')return '<div class="island-rail" aria-hidden="true"><span class="island-progress"></span></div>';
  return '';
}
function playDecoration(id){return id==='C'?'<span class="flower-button" aria-hidden="true"><svg class="play-petals" viewBox="0 0 100 100"><use href="#flower"/></svg></span>':'';}
document.querySelector('.designs').innerHTML=designs.filter(d=>!focus||d.id===focus.id).map(d=>'<article class="design-card scheme-'+d.id.toLowerCase()+'" data-scheme="'+d.id+'"><header class="design-label"><div class="design-name"><span class="letter">'+d.id+'</span><h2>'+d.name+'</h2></div><a class="expand" href="?focus='+d.id+'">'+icon('i-expand')+'放大体验</a></header><div class="scene-frame"><div class="scene"><img class="reference" src="reference.jpg" alt="音乐库页面参考，仅用于展示播放栏在首页中的位置" draggable="false"><div class="bar-region"><section class="mini-player" aria-label="'+d.name+'迷你播放栏"><div class="art-slot">'+ornament(d.id)+'<span class="cover-img" role="img" aria-label="Salt 歌曲封面"></span></div><div class="metadata"><h3 class="track-title">Salt</h3><p class="artist">Ava Max</p></div><div class="controls"><button class="control prev" type="button" data-action="previous" aria-label="上一首">'+icon('i-previous')+'</button><button class="control play" type="button" data-action="play" aria-label="播放" aria-pressed="false">'+playDecoration(d.id)+icon('i-play')+'</button><button class="control next" type="button" data-action="next" aria-label="下一首">'+icon('i-next')+'</button></div>'+extra(d.id)+'</section></div>'+nav+'</div></div><p class="design-caption">'+d.caption+'</p></article>').join('');

let playing=false,index=0,position=57.6,angle=0,speed=0,target=0,startSpeed=0,transitionTime=0,transitionDuration=0,lastFrame=0,frameRequest=0;
const reduceMotion=matchMedia('(prefers-reduced-motion: reduce)');
const rotors=[...document.querySelectorAll('[data-rotor]')];
const orbitPetals=[...document.querySelectorAll('[data-petal]')];
const playerBars=[...document.querySelectorAll('.mini-player')];
const slider=document.querySelector('#progress');
const time=value=>Math.floor(value/60)+':'+String(Math.floor(value%60)).padStart(2,'0');
function renderProgress(){
  const fraction=Math.min(1,position/tracks[index].duration);
  for(const bar of playerBars)bar.style.setProperty('--fraction',String(fraction));
  for(const ring of document.querySelectorAll('.ring-value,.stem-progress'))ring.style.strokeDashoffset=String(100-fraction*100);
  for(const bloom of document.querySelectorAll('.stem-bloom')){
    const path=bloom.parentElement.querySelector('.stem');const point=path.getPointAtLength(path.getTotalLength()*fraction);
    bloom.style.left=(point.x/320*100)+'%';bloom.style.bottom=(25-point.y-9)+'px';
  }
  slider.value=String(fraction*100);
  document.querySelector('#time').value=time(position)+' / '+time(tracks[index].duration);
}
function requestFrame(){if(!frameRequest&&!document.hidden)frameRequest=requestAnimationFrame(tick);}
function tick(stamp){
  frameRequest=0;
  const delta=lastFrame?Math.min(stamp-lastFrame,64):0;lastFrame=stamp;
  if(playing)position=Math.min(tracks[index].duration,position+delta/1000);
  if(!reduceMotion.matches){
    const duration=transitionDuration;
    if(duration&&transitionTime<duration){
      const old=transitionTime/duration;const next=Math.min(1,(transitionTime+delta)/duration);
      const integral=t=>t*t*t-.5*t*t*t*t;
      const step=(next-old)*duration;
      angle+=(startSpeed*step+(target-startSpeed)*duration*(integral(next)-integral(old))+target*(delta-step))/1000;
      transitionTime=Math.min(duration,transitionTime+delta);speed=startSpeed+(target-startSpeed)*next*next*(3-2*next);
    }else{speed=target;angle+=speed*delta/1000;}
    angle%=360;for(const rotor of rotors)rotor.style.transform='rotate('+angle+'deg)';
  }else speed=0;
  // One rotation clock hands the pulse to exactly one petal at a time. At 48°/s,
  // each slot lasts ~536 ms; both ends return to rest before the next slot starts.
  // Scale around the covered root, so the visible petals never detach from the ring.
  const activity=reduceMotion.matches?0:Math.min(1,speed/48);
  const petalStep=((angle%360)+360)%360*14/360;
  const activePetal=Math.floor(petalStep)%7;
  const phase=petalStep-Math.floor(petalStep);
  for(const [i,petal]of orbitPetals.entries()){
    const pulse=i===activePetal?Math.sin(Math.PI*phase)**2:0;
    const scale=1+1.5*pulse*activity/10;
    petal.style.transform='scaleY('+scale.toFixed(5)+')';
  }
  renderProgress();
  if(position>=tracks[index].duration){setTrack(index+1);}
  if(playing||(!reduceMotion.matches&&speed>0)){requestFrame();}else lastFrame=0;
}
function updatePlayback(value){
  playing=value;document.body.classList.toggle('playing',playing);
  startSpeed=speed;target=playing&&!reduceMotion.matches?48:0;transitionTime=0;transitionDuration=playing?240:900;
  for(const button of document.querySelectorAll('.control.play')){
    button.setAttribute('aria-label',playing?'暂停':'播放');button.setAttribute('aria-pressed',String(playing));
    button.querySelector(':scope > svg use').setAttribute('href',playing?'#i-pause':'#i-play');
  }
  const demo=document.querySelector('.demo-play');demo.querySelector('use').setAttribute('href',playing?'#i-pause':'#i-play');demo.querySelector('span').textContent=playing?'暂停动效':'播放动效';
  if(!reduceMotion.matches){
    for(const petals of document.querySelectorAll('.play-petals')){
      petals.getAnimations().forEach(a=>a.cancel());
      petals.animate(playing?[{transform:'rotate(-9deg) scale(.9)'},{transform:'rotate(10deg) scale(1.05)',offset:.55},{transform:'rotate(0) scale(1)'}]:[{transform:'scale(1)'},{transform:'rotate(-6deg) scale(.9)',offset:.45},{transform:'rotate(0) scale(1)'}],{duration:320,easing:'cubic-bezier(.22,1,.36,1)'});
    }
    for(const [i,petal]of [...document.querySelectorAll('.c-petal')].entries()){
      petal.getAnimations().forEach(a=>a.cancel());
      const x=[8,-12,17][i%3];
      petal.animate(playing?[{opacity:0,transform:'translate(0,8px) scale(.5)'},{opacity:.8,offset:.25},{opacity:0,transform:'translate('+x+'px,-13px) rotate(35deg) scale(.65)'}]:[{opacity:0,transform:'translate('+x+'px,-12px) rotate(35deg)'},{opacity:.6,offset:.2},{opacity:0,transform:'translate(0,9px) scale(.3)'}],{duration:480,easing:'cubic-bezier(.22,1,.36,1)'});
    }
  }
  requestFrame();
}
function setTrack(next){
  index=(next+tracks.length)%tracks.length;position=0;
  const track=tracks[index];
  document.querySelectorAll('.track-title').forEach(n=>n.textContent=track.title);
  document.querySelectorAll('.artist').forEach(n=>n.textContent=track.artist);
  for(const cover of document.querySelectorAll('.cover-img')){
    cover.setAttribute('aria-label',track.title+' 歌曲封面');
    cover.style.backgroundImage='url('+(track.cover==='photo'?'reference.jpg':track.cover)+')';
    cover.style.backgroundSize=track.cover==='photo'?'923.077% auto':'cover';
    cover.style.backgroundPosition=track.cover==='photo'?'4.86% 88.27%':'center';
  }
  if(!reduceMotion.matches)for(const node of document.querySelectorAll('.metadata,.cover-img')){
    node.getAnimations().forEach(animation=>animation.cancel());
    node.animate([{opacity:.25,transform:'translateY(5px)'},{opacity:1,transform:'translateY(0)'}],{duration:250,easing:'cubic-bezier(.22,1,.36,1)'});
  }
  renderProgress();
}
document.addEventListener('click',event=>{
  const button=event.target.closest('[data-action]');if(!button)return;
  if(button.dataset.action==='play')updatePlayback(!playing);
  if(button.dataset.action==='next')setTrack(index+1);
  if(button.dataset.action==='previous')setTrack(index-1);
});
slider.addEventListener('input',()=>{position=tracks[index].duration*Number(slider.value)/100;renderProgress();});
document.addEventListener('visibilitychange',()=>{if(document.hidden){cancelAnimationFrame(frameRequest);frameRequest=0;lastFrame=0;}else if(playing||speed>0)requestFrame();});
reduceMotion.addEventListener('change',()=>{for(const petal of orbitPetals)petal.style.transform='';speed=0;startSpeed=0;target=playing&&!reduceMotion.matches?48:0;transitionTime=0;transitionDuration=240;lastFrame=0;document.getAnimations().forEach(a=>a.cancel());if(playing)requestFrame();});
renderProgress();
