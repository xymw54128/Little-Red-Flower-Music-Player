// Builds on the original B prototype; all changes stay outside the Android App.
const reducedMotion=()=>matchMedia('(prefers-reduced-motion: reduce)').matches;
const activeTweens=new Map();
function animateElement(node,frames,options){
  activeTweens.get(node)?.cancel();
  if(reducedMotion())return;
  const animation=node.animate(frames,options);
  activeTweens.set(node,animation);
  animation.onfinish=()=>{if(activeTweens.get(node)===animation)activeTweens.delete(node);};
}
function updateGalleryDetails(){
  const next=tracks[(trackIndex+1)%tracks.length];
  $('.next-title').textContent=next.title;
  $('.up-next img').style.filter=`hue-rotate(${next.tint})`;
  const index=`${String(trackIndex+1).padStart(2,'0')} / ${String(tracks.length).padStart(2,'0')}`;
  $('.cover-index').textContent=index;
  $('.queue-position').textContent=index;
  $('.cover-verse').textContent=$('.preview-line').textContent;
  $('.seek-tooltip').textContent=time(position);
  $('.seek-wrap').style.setProperty('--seek-percent',`${position/228*100}%`);
}
function setVariant(id){
  if(!['depth','rhythm','letter'].includes(id))id='depth';
  setScheme('B');
  document.body.dataset.variant=id;
  $$('.variant').forEach(n=>{const selected=n.dataset.variant===id;n.classList.toggle('selected',selected);n.setAttribute('aria-pressed',String(selected));});
  $('#preview-hint').textContent={depth:'B1：点击播放看封面与叠层轻浮，试试收藏、切歌与进度浮标。',rhythm:'B2：点击播放看轻量声纹。这里是视觉示意，不分析实际音频。',letter:'B3：封面像一张花笺，歌词写在照片下方；轻触封面可展开。'}[id];
  updateGalleryDetails();
}
$$('.variant').forEach(n=>n.onclick=()=>setVariant(n.dataset.variant));
const originalTrackChange=setTrack;
setTrack=function(index){
  const target=$('.art-button');
  const was=getComputedStyle(target);
  const opacity=Number(was.opacity);
  originalTrackChange(index);
  updateGalleryDetails();
  animateElement(target,[{opacity:Math.min(.5,opacity),transform:'translateX(13px) scale(.975)'},{opacity:1,transform:'translateX(0) scale(1)'}],{duration:300,easing:'cubic-bezier(.2,.7,.2,1)'});
  animateElement($('.track-heading'),[{opacity:.25,transform:'translateY(5px)'},{opacity:1,transform:'translateY(0)'}],{duration:240,easing:'ease-out'});
};
const originalPlayChange=setPlaying;
setPlaying=function(value){
  originalPlayChange(value);
  const button=$('.play-button');
  button.classList.remove('respond');
  void button.offsetWidth;
  button.classList.add('respond');
};
const originalLyricUpdate=updateLyrics;
updateLyrics=function(force=false){
  const before=$('.preview-line').textContent;
  originalLyricUpdate(force);
  const after=$('.preview-line').textContent;
  if(before!==after){
    $('.cover-verse').textContent=after;
    animateElement($('.preview-line'),[{opacity:.25,transform:'translateY(8px)'},{opacity:1,transform:'translateY(0)'}],{duration:250,easing:'ease-out'});
    animateElement($('.following-line'),[{opacity:.25,transform:'translateY(7px)'},{opacity:1,transform:'translateY(0)'}],{duration:300,easing:'ease-out'});
    animateElement($('.cover-verse'),[{opacity:.15,transform:'translateY(5px)'},{opacity:1,transform:'translateY(0)'}],{duration:250,easing:'ease-out'});
  }
};
const originalProgress=updateProgress;
updateProgress=function(){originalProgress();updateGalleryDetails();};
const originalFavorite=$('.favorite').onclick;
$('.favorite').onclick=()=>{
  originalFavorite();
  $$('.favorite-petal').forEach(n=>{n.getAnimations().forEach(a=>a.cancel());n.remove();});
  if(reducedMotion()||$('.favorite').getAttribute('aria-pressed')!=='true')return;
  [-1,0,1].forEach((direction,i)=>{
    const petal=document.createElement('i');petal.className='favorite-petal';petal.setAttribute('aria-hidden','true');$('.favorite').append(petal);
    const animation=petal.animate([{opacity:.8,transform:'translate(0,0) rotate(0deg) scale(.7)'},{opacity:0,transform:`translate(${direction*17}px,${-21-i*3}px) rotate(${direction*75+25}deg) scale(.35)`}],{duration:480,easing:'cubic-bezier(.2,.7,.3,1)'});
    animation.onfinish=()=>petal.remove();
  });
};
$('.up-next').onclick=()=>$('.queue').click();
$('.source-entry').onclick=()=>$('.queue').click();
$('.art-button').addEventListener('pointermove',e=>{
  if(!e.buttons||reducedMotion())return;
  const rect=e.currentTarget.getBoundingClientRect();
  const x=Math.max(-1,Math.min(1,(e.clientX-rect.left-rect.width/2)/(rect.width/2)));
  const y=Math.max(-1,Math.min(1,(e.clientY-rect.top-rect.height/2)/(rect.height/2)));
  e.currentTarget.style.transform=`rotateX(${-y*1.6}deg) rotateY(${x*1.6}deg)`;
});
window.addEventListener('pointerup',()=>{$('.art-button').style.transform='';$('.seek-wrap').classList.remove('seeking');});
window.addEventListener('pointercancel',()=>{$('.art-button').style.transform='';$('.seek-wrap').classList.remove('seeking');});
$('.art-button').addEventListener('pointerleave',()=>{$('.art-button').style.transform='';});
$('#seek').addEventListener('pointerdown',()=>$('.seek-wrap').classList.add('seeking'));
setVariant(params.get('variant')||'depth');
