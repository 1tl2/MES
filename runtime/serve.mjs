import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.join(path.dirname(fileURLToPath(import.meta.url)),'web');
const upstream=new URL(process.env.API_TARGET || 'http://127.0.0.1:18082');
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.ico':'image/x-icon','.json':'application/json'};
http.createServer((req,res)=>{
 if(req.url.startsWith('/api/')){
  const proxy=http.request({hostname:upstream.hostname,port:upstream.port,path:req.url,method:req.method,headers:{...req.headers,host:upstream.host}},r=>{res.writeHead(r.statusCode,r.headers);r.pipe(res)});
  proxy.on('error',()=>{res.writeHead(502,{'Content-Type':'text/plain; charset=utf-8'});res.end('后端未就绪，请检查数据库、Redis及后台日志。')});req.pipe(proxy);return;
 }
 let pathname;try{pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname)}catch{res.writeHead(400);res.end();return}
 let file=path.resolve(root,'.'+pathname);
 if(file!==root&&!file.startsWith(root+path.sep)){res.writeHead(403);res.end();return}
 if(!fs.existsSync(file)||fs.statSync(file).isDirectory())file=path.join(root,'index.html');
 res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream'});fs.createReadStream(file).pipe(res);
}).listen(Number(process.env.WEB_PORT||18084),'127.0.0.1',()=>console.log('MES: http://127.0.0.1:'+(process.env.WEB_PORT||18084)));
