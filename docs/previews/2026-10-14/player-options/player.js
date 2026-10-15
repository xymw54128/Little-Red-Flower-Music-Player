/* Independent visual prototype. No audio, imports, or App data are changed. */
const $=s=>document.querySelector(s), $$=s=>Array.from(document.querySelectorAll(s));
const params=new URLSearchParams(location.search);
$$('img').forEach(n=>n.draggable=false);
if(params.get('embed')==='1')document.body.classList.add('embed');
const tracks=[
  {title:'春日来信',artist:'浪雨林',album:'花开的时候',tint:'0deg'},
  {title:'一朵花的时间',artist:'浪雨林',album:'慢慢生长',tint:'34deg'},
  {title:'沿着风的方向',artist:'浪雨林',album:'花开的时候',tint:'-24deg'}
];
const lyrics=['风经过花窗','把昨天留在远方','一束光落在手上','把春天唱给你听','让每一朵花都记得你','也记得温柔的自己','向着风慢慢生长','陪你走过每个季节'];
const lyricStarts=[0,18,44,70,96,130,162,192];
let trackIndex=0,position=77,playing=false,dragging=false,mode=0,activeLine=-1,lastFrame=0,browsingUntil=0,feedbackTimer;
const phone=$('#phone'),seek=$('#seek'),scroll=$('.lyrics-scroll');
$('#lyric-lines').innerHTML=lyrics.map(t=>`<div class="lyric-line">${t}</div>`).join('');
function time(sec){sec=Math.floor(Math.max(0,sec));return `${Math.floor(sec/60)}:${String(sec%60).padStart(2,'0')}`;}
function feedback(text){clearTimeout(feedbackTimer);const n=$('.feedback');n.textContent=text;n.classList.add('visible');feedbackTimer=setTimeout(()=>n.classList.remove('visible'),1400);}
function currentLyric(){let index=0;for(let i=0;i<lyricStarts.length;i++)if(position>=lyricStarts[i])index=i;return index;}
function centerLyrics(animate=true){const row=$$('.lyric-line')[activeLine];if(!row)return;scroll.scrollTo({top:row.offsetTop+row.offsetHeight/2-scroll.clientHeight/2,behavior:animate&&!matchMedia('(prefers-reduced-motion: reduce)').matches?'smooth':'instant'});}
function updateLyrics(force=false){const index=currentLyric();if(index!==activeLine){activeLine=index;$$('.lyric-line').forEach((n,i)=>n.classList.toggle('current',i===index));$('.preview-line').textContent=lyrics[index];$('.previous-line').textContent=lyrics[Math.max(0,index-1)];$('.following-line').textContent=lyrics[Math.min(lyrics.length-1,index+1)];if(phone.classList.contains('lyrics-open')&&performance.now()>browsingUntil)centerLyrics(!force);}else if(force)centerLyrics(false);}
function updateProgress(){seek.value=position;seek.style.setProperty('--progress',`${position/228*100}%`);$('#elapsed').textContent=time(position);$('#remaining').textContent='−'+time(228-position);updateLyrics();}
function setPlaying(value){playing=value;phone.dataset.playing=String(value);$('.play-button').setAttribute('aria-label',value?'暂停':'播放');$('.play-button').setAttribute('aria-pressed',String(value));}
function setScheme(id){if(!['A','B','C','D'].includes(id))id='A';document.body.dataset.scheme=id;$$('.scheme').forEach(n=>{const chosen=n.dataset.scheme===id;n.classList.toggle('selected',chosen);n.setAttribute('aria-pressed',String(chosen));});const hints={A:'点击播放看唱片缓转；暂停会停在当前角度。',B:'点击播放看封面轻微呼吸；控件与封面保持清晰分区。',C:'点击播放看花形轻轻舒展；品牌花朵与封面融为一体。',D:'点击播放看封面轻浮；三行歌词随进度连续切换。'};$('#preview-hint').textContent=hints[id]+' 点击封面或左滑进入歌词。';}
function setPage(page){const open=page==='lyrics';if(open){activeLine=currentLyric();$$('.lyric-line').forEach((n,i)=>n.classList.toggle('current',i===activeLine));centerLyrics(false);browsingUntil=0;}phone.classList.toggle('lyrics-open',open);$('.lyrics-view').setAttribute('aria-hidden',String(!open));$('.cover-view').setAttribute('aria-hidden',String(open));$('.lyrics-view').inert=!open;$('.cover-view').inert=open;$('#header-label').textContent=open?`${tracks[trackIndex].title} - ${tracks[trackIndex].artist}`:'正在播放';$$('[data-page]').forEach(n=>{const selected=n.dataset.page===page;n.classList.toggle('active',selected);n.setAttribute('aria-selected',String(selected));});}
function setTrack(index){trackIndex=(index+tracks.length)%tracks.length;const t=tracks[trackIndex];$('.song-title').textContent=t.title;$('.song-subtitle').textContent=`${t.artist} · ${t.album}`;$('.album-art').style.filter=`hue-rotate(${t.tint})`;$('.album-art').alt=t.title+'示例封面';position=0;activeLine=-1;updateProgress();if(phone.classList.contains('lyrics-open')){$('#header-label').textContent=`${t.title} - ${t.artist}`;centerLyrics(false);}}
function openSheet(title,html){$('#sheet-title').textContent=title;$('#sheet-content').innerHTML=html;$('.sheet-backdrop').hidden=false;$('.sheet').hidden=false;$('.sheet .dismiss').focus();}
function closeSheet(){$('.sheet').hidden=true;$('.sheet-backdrop').hidden=true;}
$$('.scheme').forEach(n=>n.onclick=()=>setScheme(n.dataset.scheme));
$('.play-button').onclick=()=>setPlaying(!playing);
$('.previous').onclick=()=>setTrack(trackIndex-1);$('.next').onclick=()=>setTrack(trackIndex+1);
$('.favorite').onclick=()=>{const n=$('.favorite'),on=n.getAttribute('aria-pressed')!=='true';n.setAttribute('aria-pressed',String(on));n.setAttribute('aria-label',on?'取消喜欢':'加入我喜欢');n.classList.remove('pop');void n.offsetWidth;n.classList.add('pop');};
$('.mode').onclick=()=>{const modes=['顺序播放','列表循环','单曲循环','随机播放'];mode=(mode+1)%modes.length;$('#mode-label').textContent=modes[mode];$('.mode').setAttribute('aria-label',modes[mode]);$('.mode').style.color=mode?'var(--accent)':'';};
$('.art-button').onclick=()=>setPage('lyrics');$('.lyric-peek').onclick=()=>setPage('lyrics');
$$('[data-page]').forEach(n=>n.onclick=()=>setPage(n.dataset.page));
$('.close-player').onclick=()=>{if(phone.classList.contains('lyrics-open'))setPage('cover');else feedback('这是播放页独立预览');};
$('.flower-button').onclick=()=>{const b=$('.flower-button');b.classList.remove('spinning');void b.offsetWidth;b.classList.add('spinning');};
$('.queue').onclick=()=>{openSheet('播放队列',tracks.map((t,i)=>`<button class="sheet-song ${i===trackIndex?'current':''}" data-track="${i}"><img src="../assets/spring-letter.svg" alt="" style="filter:hue-rotate(${t.tint})"><div><span>${t.title}</span><small>${t.artist} · ${t.album}</small></div></button>`).join('')+'<p class="sheet-note">点击歌曲试试切歌，当前进度会重置。</p>');$$('[data-track]').forEach(n=>n.onclick=()=>{setTrack(Number(n.dataset.track));closeSheet();});};
$('.more').onclick=()=>{openSheet('歌曲选项','<button class="sheet-option" data-action="add">添加到歌单</button><button class="sheet-option" data-action="info">歌曲信息</button>');$('[data-action="add"]').onclick=()=>{openSheet('选择歌单','<button class="sheet-option" data-list="我喜欢">我喜欢</button><button class="sheet-option" data-list="春日收藏">春日收藏</button>');$$('[data-list]').forEach(n=>n.onclick=()=>{closeSheet();if(n.dataset.list==='我喜欢'){$('.favorite').setAttribute('aria-pressed','true');$('.favorite').setAttribute('aria-label','取消喜欢');$('.favorite').classList.add('pop');}});};$('[data-action="info"]').onclick=()=>{const t=tracks[trackIndex];openSheet('歌曲信息',`<p class="sheet-note">${t.title}<br>${t.artist} · ${t.album}<br>FLAC / 16 bit / 44.1 kHz<br>3:48</p><p class="sheet-note">示例封面与歌词为本地原创演示素材。</p>`);};};
$('.dismiss').onclick=closeSheet;$('.sheet-backdrop').onclick=closeSheet;
document.addEventListener('keydown',e=>{if(e.key==='Escape')closeSheet();});
seek.addEventListener('pointerdown',()=>dragging=true);seek.addEventListener('input',()=>{position=Number(seek.value);updateProgress();});seek.addEventListener('change',()=>{dragging=false;centerLyrics();});window.addEventListener('pointerup',()=>dragging=false);
let pointerStart=null,swallowTap=false;
$('#view-area').addEventListener('pointerdown',e=>{swallowTap=false;pointerStart={x:e.clientX,y:e.clientY};});
$('#view-area').addEventListener('pointerup',e=>{if(!pointerStart)return;const dx=e.clientX-pointerStart.x,dy=e.clientY-pointerStart.y;pointerStart=null;if(Math.abs(dx)>55&&Math.abs(dx)>Math.abs(dy)*1.3){swallowTap=true;setPage(dx<0?'lyrics':'cover');}});
// A completed swipe must not also trigger the album's click-to-open action.
$('#view-area').addEventListener('click',e=>{if(swallowTap){swallowTap=false;e.preventDefault();e.stopPropagation();}},true);
scroll.addEventListener('pointerdown',()=>browsingUntil=Infinity);window.addEventListener('pointerup',()=>{if(browsingUntil===Infinity)browsingUntil=performance.now()+1500;});scroll.addEventListener('wheel',()=>browsingUntil=performance.now()+1500,{passive:true});
// Native touch scrolling cancels the pointer stream instead of sending pointerup.
window.addEventListener('pointercancel',()=>{dragging=false;pointerStart=null;swallowTap=false;if(browsingUntil===Infinity)browsingUntil=performance.now()+1500;});
scroll.addEventListener('scroll',()=>{if(browsingUntil&&browsingUntil!==Infinity)browsingUntil=performance.now()+1500;},{passive:true});
$('#theme').onchange=e=>{const colors={red:'#ce3c4c',green:'#588675',blue:'#537bb5',amber:'#9f761c'};document.documentElement.style.setProperty('--accent',colors[e.target.value]);};
function frame(now){if(lastFrame&&playing&&!dragging&&!document.hidden){position=Math.min(228,position+(now-lastFrame)/1000);updateProgress();if(position===228){if(mode===2)position=0;else setTrack(trackIndex+1);}}if(browsingUntil&&browsingUntil!==Infinity&&now>browsingUntil&&phone.classList.contains('lyrics-open')){browsingUntil=0;centerLyrics();}lastFrame=now;requestAnimationFrame(frame);}
document.addEventListener('visibilitychange',()=>lastFrame=0);
setScheme(params.get('scheme')||document.body.dataset.defaultScheme||'A');updateProgress();setPage('cover');requestAnimationFrame(frame);
