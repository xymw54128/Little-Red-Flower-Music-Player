// Serves only the dated design preview; no App data or signing files are exposed.
const http=require('node:http');
const fs=require('node:fs/promises');
const path=require('node:path');
const root=path.resolve(__dirname,'../docs/previews/2026-10-14');
const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.json':'application/json; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.woff2':'font/woff2','.ttf':'font/ttf','.txt':'text/plain; charset=utf-8','.md':'text/plain; charset=utf-8'};
const server=http.createServer(async(req,res)=>{
  try{
    if(req.method!=='GET'&&req.method!=='HEAD'){res.writeHead(405);return res.end();}
    const url=new URL(req.url,'http://localhost');
    const target=path.resolve(root,'.'+decodeURIComponent(url.pathname));
    if(target.toLowerCase()!==root.toLowerCase()&&!target.toLowerCase().startsWith((root+path.sep).toLowerCase())){res.writeHead(403);return res.end();}
    const stat=await fs.stat(target);
    const file=stat.isDirectory()?path.join(target,'index.html'):target;
    const actual=await fs.realpath(file);
    if(!actual.toLowerCase().startsWith((root+path.sep).toLowerCase())){res.writeHead(403);return res.end();}
    const type=types[path.extname(file).toLowerCase()];
    if(!type){res.writeHead(403);return res.end();}
    const data=await fs.readFile(file);
    res.writeHead(200,{'Content-Type':type,'Content-Length':data.length,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
    res.end(req.method==='HEAD'?undefined:data);
  }catch(e){res.writeHead(e.code==='ENOENT'?404:400);res.end('Preview file unavailable');}
});
server.listen(8897,'127.0.0.1',()=>console.log('Design preview: http://127.0.0.1:8897/'));
