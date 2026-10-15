// The source B controls and layout stay intact. Only the optical layer is controlled here.
const materialLabels={original:'原版 B：未添加材质。',crystal:'水晶透镜：在封面上移动指针，观察侧壁折色与弧面高光。',platinum:'铂金切面：哑光外肩和镜面内切，用同一束光表现不同反射。',pearl:'珠光镀膜：局部雾粉、浅金和冷青随角度出现，封面主体保持原色。'};
function setMaterial(id){if(!Object.hasOwn(materialLabels,id))id='crystal';setScheme('B');document.body.dataset.material=id;$$('.material-choice').forEach(n=>{const on=n.dataset.material===id;n.classList.toggle('selected',on);n.setAttribute('aria-pressed',String(on));});$('#preview-hint').textContent=materialLabels[id];materialPreview.setMode(id);}
$$('.material-choice').forEach(n=>n.onclick=()=>setMaterial(n.dataset.material));
const priorMaterialPlay=setPlaying;setPlaying=function(value){priorMaterialPlay(value);materialPreview.sync();};
const priorMaterialPage=setPage;setPage=function(page){priorMaterialPage(page);materialPreview.sync();};
const priorMaterialTrack=setTrack;setTrack=function(index){priorMaterialTrack(index);materialPreview.updateTexture();};
$('#theme').addEventListener('change',()=>materialPreview.sync());$('#light-demo').onclick=()=>materialPreview.demo();
setMaterial(params.get('material')||'crystal');if(params.get('playing')==='1')setPlaying(true);
