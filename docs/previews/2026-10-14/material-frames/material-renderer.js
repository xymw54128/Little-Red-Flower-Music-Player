/* Original procedural material study. Inspired by cited material principles;
   no third-party shader/code dependency. The cover itself is never painted over. */
(()=>{
 const canvas=document.querySelector('.material-canvas');
 const album=document.querySelector('.album-art');
 const cover=document.querySelector('.art-frame');
 const button=document.querySelector('.art-button');
 const media=matchMedia('(prefers-reduced-motion: reduce)');
 const gl=canvas.getContext('webgl',{alpha:true,premultipliedAlpha:false,antialias:false,preserveDrawingBuffer:true,powerPreference:'low-power'});
 let mode='crystal',clock=3.8,draws=0,raf=0,last=0,demoUntil=0,demoStart=0;
 let mouseX=-.45,mouseY=.45,targetX=mouseX,targetY=mouseY,side=358,coverSize=330,textureReady=false;
 let program,buffer,texture,uniforms={},lost=false;
 const modes={crystal:0,platinum:1,pearl:2,original:-1};
 const vertex='attribute vec2 a_position;varying vec2 v_uv;void main(){v_uv=(a_position+1.0)*0.5;gl_Position=vec4(a_position,0.0,1.0);}';
 const fragment=`precision highp float;
 varying vec2 v_uv;
 uniform float u_side,u_cover,u_mode,u_time;
 uniform vec2 u_mouse;
 uniform vec3 u_accent;
 uniform sampler2D u_image;
 float roundBox(vec2 p,vec2 b,float r){vec2 q=abs(p)-b+r;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;}
 vec2 surfaceDirection(vec2 p,vec2 b,float r){vec2 q=abs(p)-b+r;vec2 n=max(q,0.0);if(length(n)>.0001)return normalize(n)*sign(p);return q.x>q.y?vec2(sign(p.x),0.0):vec2(0.0,sign(p.y));}
 float noise(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
 vec3 film(float x){return .62+.38*cos(6.2831853*(x+vec3(.04,.36,.69)));}
 vec3 sampleCover(vec2 p){return texture2D(u_image,clamp(p/u_cover+.5,vec2(.005),vec2(.995))).rgb;}
 void main(){
  vec2 p=(v_uv-.5)*u_side;
  float d=roundBox(p,vec2(u_cover*.5),22.0);
  float width=11.5;
  if(u_mode<-.5||d<0.0||d>width+.8){gl_FragColor=vec4(0.0);return;}
  vec2 outward=surfaceDirection(p,vec2(u_cover*.5),22.0);
  float t=clamp(d/width,0.0,1.0);
  float angle=(t-.5)*2.55;
  vec3 normal=normalize(vec3(outward*sin(angle),cos(angle)));
  vec3 light=normalize(vec3(-.55+u_mouse.x*.7+.13*sin(u_time*.31),.8+u_mouse.y*.65+.11*cos(u_time*.27),1.15));
  vec3 halfLight=normalize(light+vec3(0.0,0.0,1.0));
  float diffuse=max(dot(normal,light),0.0);
  float shine=pow(max(dot(normal,halfLight),0.0),44.0);
  float fresnel=pow(1.0-normal.z,2.4);
  float innerLine=exp(-pow((d-.85)/.44,2.0));
  float lip=exp(-pow((d-10.45)/.43,2.0));
  float grain=(noise(p*1.9)-.5)*.005;
  vec3 color;
  if(u_mode<.5){
   float bend=3.0+14.0*sin(t*3.14159265)+u_mouse.x*outward.x*2.2;
   vec2 inGlass=p-outward*(d+bend);
   vec3 reflected=sampleCover(inGlass);
   float dispersion=(.7+1.4*fresnel);
   reflected.r=sampleCover(inGlass+outward*dispersion).r;
   reflected.b=sampleCover(inGlass-outward*dispersion).b;
   color=mix(vec3(.80,.86,.90),reflected,.36);
   color*=.78+.21*diffuse;
   color+=vec3(.44)*shine+vec3(.14)*fresnel;
   color+=vec3(.52)*lip;
   color*=1.0-.37*innerLine;
   float sideWall=exp(-pow((d-3.1)/1.25,2.0));
   color=mix(color,reflected*.66+vec3(.15),sideWall*.31);
   color+=film(dot(outward,light.xy)*.23+t*.14)*fresnel*.055;
  }else if(u_mode<1.5){
   // Broad satin shoulder and a separate polished bevel share one light source.
   float outerFace=smoothstep(4.6,6.2,d);
   float bevel=clamp((d-1.3)/3.8,0.0,1.0);
   vec3 faceNormal=normalize(vec3(outward*(bevel-.45)*1.65,.74));
   vec3 ray=reflect(vec3(0.0,0.0,-1.0),faceNormal);
   float environment=.22+.66*pow(max(dot(ray,normalize(vec3(light.xy,1.4))),0.0),2.0);
   float band=exp(-pow((dot(ray.xy,vec2(.72,.69))-.19-u_mouse.x*.2)/.14,2.0));
   float darkBand=exp(-pow((dot(ray.xy,vec2(-.43,.9))+.27+u_mouse.y*.16)/.18,2.0));
   vec3 polish=vec3(.25,.26,.265)+vec3(.51,.53,.54)*environment+vec3(.28)*band-vec3(.11)*darkBand;
   vec3 satin=vec3(.68,.69,.685)*(.76+.26*diffuse)+vec3(.22)*pow(max(dot(normal,halfLight),0.0),10.0);
   satin+=vec3(sin(dot(p,outward.yx)*7.0)*.004+grain);
   color=mix(polish,satin,outerFace);
   color*=1.0-.44*innerLine;
   color+=vec3(.36)*exp(-pow((d-4.7)/.38,2.0))+vec3(.22)*lip;
  }else{
   // Restrained thin-film colour appears in local viewing angles, not an RGB orbit.
   float phase=dot(outward,light.xy)*.55+t*.31+u_mouse.x*.13-u_mouse.y*.1;
   vec3 pearl=film(phase);
   float micro=sin(dot(p,vec2(.52,.85))*2.3+u_mouse.x*2.0)*.003;
   float angled=smoothstep(.08,.6,abs(dot(outward,light.xy)));
   float pearlLight=(.055+.13*angled)*(.2+.8*shine);
   color=mix(vec3(.92,.905,.88),pearl,pearlLight);
   color*=.88+.13*diffuse;
   color+=vec3(.22)*shine+vec3(.08)*fresnel+micro+grain;
   color*=1.0-.25*innerLine;
   color+=vec3(.15)*lip;
   color=mix(color,u_accent*.4+vec3(.64),.025);
  }
  color=clamp(color+grain,0.0,1.0);
  float alpha=smoothstep(0.0,.75,d)*(1.0-smoothstep(width-.65,width+.65,d));
  gl_FragColor=vec4(color,alpha);
 }`;
 function compile(type,source){const shader=gl.createShader(type);gl.shaderSource(shader,source);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(shader));return shader;}
 function active(){return !document.hidden&&!phone.classList.contains('lyrics-open')&&mode!=='original'&&!lost;}
 function automatic(){return active()&&phone.dataset.playing==='true'&&!media.matches;}
 function measure(){coverSize=cover.getBoundingClientRect().width;side=coverSize+28;const ratio=Math.min(devicePixelRatio||1,1.5);const size=Math.round(side*ratio);if(canvas.width!==size||canvas.height!==size){canvas.width=size;canvas.height=size;}if(gl)gl.viewport(0,0,size,size);}
 function accent(){const raw=getComputedStyle(document.documentElement).getPropertyValue('--accent').trim();const m=raw.match(/^#([0-9a-f]{6})$/i);return m?[0,2,4].map(i=>parseInt(m[1].slice(i,i+2),16)/255):[.81,.24,.30];}
 function paint(){if(!gl||lost||!program)return;gl.clear(gl.COLOR_BUFFER_BIT);gl.useProgram(program);gl.uniform1f(uniforms.u_side,side);gl.uniform1f(uniforms.u_cover,coverSize);gl.uniform1f(uniforms.u_mode,modes[mode]);gl.uniform1f(uniforms.u_time,clock);gl.uniform2f(uniforms.u_mouse,mouseX,mouseY);gl.uniform3fv(uniforms.u_accent,accent());gl.drawArrays(gl.TRIANGLE_STRIP,0,4);draws++;canvas.dataset.draws=String(draws);}
 function unsettled(){return Math.abs(targetX-mouseX)+Math.abs(targetY-mouseY)>.002;}
 function frame(now){raf=0;if(!active()){last=0;return;}const dt=last?Math.min((now-last)/1000,.06):1/60;last=now;if(automatic())clock+=dt;if(demoUntil>now&&!media.matches){const phase=(now-demoStart)/2800;targetX=Math.sin(phase*6.2831853)*.95;targetY=Math.cos(phase*6.2831853)*.72;}else if(demoUntil){demoUntil=0;targetX=-.45;targetY=.45;}const ease=media.matches?1:1-Math.exp(-dt*15);mouseX+=(targetX-mouseX)*ease;mouseY+=(targetY-mouseY)*ease;paint();if(automatic()||unsettled()||demoUntil>now)raf=requestAnimationFrame(frame);else last=0;}
 function sync(){document.body.classList.toggle('material-inactive',document.hidden||phone.classList.contains('lyrics-open'));if(!program||!active()){cancelAnimationFrame(raf);raf=0;last=0;return;}if(media.matches){mouseX=targetX;mouseY=targetY;demoUntil=0;}paint();if((automatic()||unsettled()||demoUntil>performance.now())&&!raf)raf=requestAnimationFrame(frame);}
 function setMode(next){mode=Object.hasOwn(modes,next)?next:'crystal';measure();sync();}
 function updateTexture(){if(!gl||!texture)return;try{const surface=document.createElement('canvas');surface.width=surface.height=512;const ctx=surface.getContext('2d');ctx.filter=album.style.filter||'none';ctx.drawImage(album,0,0,512,512);gl.bindTexture(gl.TEXTURE_2D,texture);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,surface);textureReady=true;sync();}catch(error){textureReady=false;canvas.dataset.texture='fallback';}}
 function demo(){if(!active())return;if(media.matches){targetX=.75;targetY=.65;sync();return;}demoStart=performance.now();demoUntil=demoStart+2800;sync();}
 function pointer(event){const r=button.getBoundingClientRect();targetX=Math.max(-1.3,Math.min(1.3,(event.clientX-r.left-r.width/2)/(r.width/2)));targetY=Math.max(-1.3,Math.min(1.3,-(event.clientY-r.top-r.height/2)/(r.height/2)));sync();}
 function reset(){targetX=-.45;targetY=.45;sync();}
 document.body.dataset.renderer='fallback';
 if(gl){try{const vs=compile(gl.VERTEX_SHADER,vertex),fs=compile(gl.FRAGMENT_SHADER,fragment);program=gl.createProgram();gl.attachShader(program,vs);gl.attachShader(program,fs);gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.deleteShader(vs);gl.deleteShader(fs);gl.useProgram(program);buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,1,1]),gl.STATIC_DRAW);const position=gl.getAttribLocation(program,'a_position');gl.enableVertexAttribArray(position);gl.vertexAttribPointer(position,2,gl.FLOAT,false,0,0);for(const name of ['u_side','u_cover','u_mode','u_time','u_mouse','u_accent'])uniforms[name]=gl.getUniformLocation(program,name);texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([190,209,200,255]));gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);gl.uniform1i(gl.getUniformLocation(program,'u_image'),0);gl.clearColor(0,0,0,0);document.body.dataset.renderer='webgl';measure();album.decode().then(updateTexture).catch(()=>{});}catch(error){program=null;canvas.dataset.error=error.message;document.body.dataset.renderer='fallback';}}
 button.addEventListener('pointermove',pointer);button.addEventListener('pointerdown',pointer);button.addEventListener('pointerleave',reset);window.addEventListener('pointercancel',reset);
 document.addEventListener('visibilitychange',sync);media.addEventListener('change',sync);new ResizeObserver(()=>{measure();sync();}).observe(cover);
 canvas.addEventListener('webglcontextlost',event=>{event.preventDefault();lost=true;document.body.dataset.renderer='fallback';sync();});
 window.addEventListener('pagehide',()=>{cancelAnimationFrame(raf);raf=0;});
 window.materialPreview={setMode,sync,demo,updateTexture,getState:()=>({mode,renderer:document.body.dataset.renderer,clock,draws,active:active(),auto:automatic(),mouseX,mouseY,textureReady})};
 sync();
})();
