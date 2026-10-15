// Visual prototypes only. Reuse the verified demo transport; no real audio or App data.
const photo='assets/daylight-cover.png';
const motion=()=>!matchMedia('(prefers-reduced-motion: reduce)').matches;
const transitions=new Map();
function tween(node,frames,duration=340){const old=transitions.get(node);old?.cancel();transitions.delete(node);if(!motion())return;const animation=node.animate(frames,{duration,easing:'cubic-bezier(.18,.72,.22,1)'});transitions.set(node,animation);animation.onfinish=()=>{if(transitions.get(node)===animation)transitions.delete(node);};}
function details(){
 const previous=tracks[(trackIndex+tracks.length-1)%tracks.length],next=tracks[(trackIndex+1)%tracks.length];
 $('.previous-title').textContent=previous.title;$('.next-title').textContent=next.title;$('.up-next-title').textContent=next.title;
 $('.previous-cover img').style.filter='hue-rotate('+previous.tint+')';$('.next-cover img').style.filter='hue-rotate('+next.tint+')';
 $('.previous-cover').setAttribute('aria-label','播放上一张封面');$('.next-cover').setAttribute('aria-label','播放下一张封面');
 $('.seek-tooltip').textContent=time(position);$('.seek-wrap').style.setProperty('--seek-percent',position/228*100+'%');
}
function setLayout(layout){if(!['stage','lyric','shelf'].includes(layout))layout='stage';document.body.dataset.layout=layout;$$('.layout-choice').forEach(n=>{const on=n.dataset.layout===layout;n.classList.toggle('selected',on);n.setAttribute('aria-pressed',String(on));});$('#preview-hint').textContent={stage:'海风全幅：封面成为主画面。试试切歌、收藏与进度拖动。',lyric:'一行入耳：当前一句成为视觉中心。轻触文字，展开完整歌词。',shelf:'流动唱片架：点两侧封面切歌；轻触中间封面或左滑查看歌词。'}[layout];details();}
$$('.layout-choice').forEach(n=>n.onclick=()=>setLayout(n.dataset.layout));
const priorTrack=setTrack;
setTrack=function(index){const direction=index>trackIndex?1:-1;priorTrack(index);$('.album-art').src=photo;details();tween($('.art-button'),[{opacity:.2,transform:'translateX('+(direction*24)+'px) scale(.97)'},{opacity:1,transform:'translateX(0) scale(1)'}],400);tween($('.track-heading'),[{opacity:.15,transform:'translateX('+(direction*12)+'px)'},{opacity:1,transform:'translateX(0)'}],330);};
const priorProgress=updateProgress;
updateProgress=function(){priorProgress();$('.seek-tooltip').textContent=time(position);$('.seek-wrap').style.setProperty('--seek-percent',position/228*100+'%');};
const priorLyrics=updateLyrics;
updateLyrics=function(force=false){const before=$('.preview-line').textContent;priorLyrics(force);if(before!==$('.preview-line').textContent){tween($('.preview-line'),[{opacity:.15,transform:'translateY(9px)'},{opacity:1,transform:'translateY(0)'}],300);tween($('.following-line'),[{opacity:.2,transform:'translateY(6px)'},{opacity:1,transform:'translateY(0)'}],300);}};
$('.previous-cover').onclick=()=>setTrack(trackIndex-1);$('.next-cover').onclick=()=>setTrack(trackIndex+1);
$('.source-entry').onclick=()=>$('.queue').click();$('.up-next').onclick=()=>$('.queue').click();
const priorQueue=$('.queue').onclick;
$('.queue').onclick=()=>{priorQueue();$$('.sheet-song img').forEach(n=>n.src=photo);};
$('#seek').addEventListener('pointerdown',()=>$('.seek-wrap').classList.add('seeking'));
function endSeeking(){$('.seek-wrap').classList.remove('seeking');}
window.addEventListener('pointerup',endSeeking);window.addEventListener('pointercancel',endSeeking);
// Keep animations mutually exclusive when a person rapidly changes tracks.
document.addEventListener('visibilitychange',()=>{if(document.hidden){transitions.forEach(a=>a.cancel());transitions.clear();}});
setLayout(params.get('layout')||'stage');$('.album-art').src=photo;details();
