// Prototype-only frame selection; the original B layout and demo controls are reused.
function setFrame(frame){if(!['original','silver','glass','luminous'].includes(frame))frame='silver';setScheme('B');document.body.dataset.frame=frame;$$('.frame-choice').forEach(n=>{const selected=n.dataset.frame===frame;n.classList.toggle('selected',selected);n.setAttribute('aria-pressed',String(selected));});$('#preview-hint').textContent={original:'原版 B：封面、文字和控件的对照基准。',silver:'钛银细边：细金属倒角。点播放，窄高光会沿边框掠过。',glass:'晶透棱边：透明双层折边。点播放，观察边缘的反光。',luminous:'主题流光：一小段光沿封面边缘慢行。可切换主题色，暂停会冻结。'}[frame];}
$$('.frame-choice').forEach(n=>n.onclick=()=>setFrame(n.dataset.frame));
function frameVisibility(){phone.classList.toggle('frame-inactive',document.hidden||phone.classList.contains('lyrics-open'));}
const originalPage=setPage;setPage=function(page){originalPage(page);frameVisibility();};
document.addEventListener('visibilitychange',frameVisibility);
setFrame(params.get('frame')||'silver');frameVisibility();
if(params.get('playing')==='1')setPlaying(true);
