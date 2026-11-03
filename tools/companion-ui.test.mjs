import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const html = readFileSync(new URL('../app/src/main/assets/companion/index.html', import.meta.url), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
function harness() {
  const elements = new Map(), requests = [], ids=new Set([...html.matchAll(/id="([^"]+)"/g)].map(m=>m[1]));
  function element() { return { value:'', style:{setProperty(){}}, dataset:{}, children:[], classList:{toggle(){},add(){},remove(){}}, setAttribute(){}, append(...n){this.children.push(...n)}, replaceChildren(...n){this.children=n}, remove(){}, addEventListener(){}, setPointerCapture(){}, releasePointerCapture(){}, getBoundingClientRect(){return {width:300,height:300}}, files:[] }; }
  const context = vm.createContext({location:{pathname:'/token'},document:{hidden:false,body:element(),documentElement:element(),getElementById(id){if(!ids.has(id))return null;if(!elements.has(id))elements.set(id,element());return elements.get(id)},querySelectorAll(){return []},createElement:element,addEventListener(){}},window:{innerHeight:800,addEventListener(){}},sessionStorage:{getItem(){return null},setItem(){}},fetch:async(url,options)=>{requests.push({url,options});return {ok:true,json:async()=>url.endsWith('/reader-state')?{active:false}:{books:[]}}},setTimeout(){return 1},clearTimeout(){},setInterval(){},clearInterval(){},scrollTo(){},AbortController,URL,console,Date,Math,Number,JSON,Promise});
  vm.runInContext(script,context);
  return {elements, requests, run: code=>vm.runInContext(code,context)};
}
test('the editor opens from the right bar, without sync, and leaves by its own button',async()=>{
 const h=harness();assert.doesNotMatch(html,/editHandle/);
 await h.run("current={id:'c'};readerSide('right',true)");
 assert.deepEqual(JSON.parse(h.run("JSON.stringify($('menuRows').children.map(c=>c.dataset.label))")),['Editar a página','Ajustes de imagem','Reproduzir o desenho na TV','Exportar como CBZ']);
 await h.run("$('menuRows').children[0].onclick()");assert.equal(h.run('sideOpen.left'),true);assert.equal(h.run('sideOpen.right'),false);assert.equal(h.run('ed.open'),true);
 assert.equal(h.run("$('drawRail').children[0].id"),'drawExit');
 await h.run("$('drawRail').children[0].onclick()");assert.equal(h.run('sideOpen.left'),false);assert.equal(h.run('ed.open'),false);
 h.run("readerSide('left',true);readerSide('right',true)");assert.equal(h.run('sideOpen.left'),false);assert.equal(h.run('ed.open'),false);
 assert.match(html,/body\.leftOpen #menuHandle,body\.leftOpen \.readerSide\.right,body\.leftOpen \.readerSide\.sliders,body\.rightOpen \.readerSide\.left\{display:none\}/);
 assert.doesNotMatch(html,/body:not\(\.syncActive\) \.readerHandle/);
});
test('text pages offer the visible stretch instead of the image tools',async()=>{
 const h=harness();await h.run("current={id:'c'};textShown=true;readerSide('right',true)");
 assert.deepEqual(JSON.parse(h.run("JSON.stringify($('menuRows').children.map(c=>c.dataset.label))")),['Editar o trecho visível']);
});
test('in the editor the image stops at its own edge and the phone may zoom past the TV',()=>{
 const h=harness();const range=()=>JSON.parse(h.run('JSON.stringify(viewRange(200,600))'));
 h.run("view={s:2,x:0,y:0}");assert.deepEqual(range(),{x:100,y:300},'the reader keeps the whole-area travel');assert.equal(h.run('maxZoom()'),3);
 // A page 200x300 in a 200x600 area: at 200% it overflows sideways and fits on height, with no empty travel.
 h.run("pg.cur.naturalWidth=400;pg.cur.naturalHeight=600;ed.open=true");assert.equal(h.run('maxZoom()'),6);assert.deepEqual(range(),{x:100,y:0});
 h.run("view={s:3,x:0,y:0}");assert.deepEqual(range(),{x:200,y:150});
 h.run("view={s:3,x:900,y:-900};clampView(200,600)");assert.equal(h.run('view.x'),200);assert.equal(h.run('view.y'),-150);
});
test('editing, the TV gets the page point at the center of the phone, capped at its own zoom',async()=>{
 const h=harness();h.run("globalThis.setTimeout=f=>{f();return 1};current={id:'c'};syncOn=true;tvReader={active:true,comicId:'c'};$('pager').getBoundingClientRect=()=>({left:0,top:0,width:200,height:600});pg.cur.naturalWidth=400;pg.cur.naturalHeight=600;ed.open=true;view={s:6,x:-300,y:450};mirrorView();flushLive()");
 await new Promise(resolve=>setImmediate(resolve));
 const sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).pop().options.body);
 assert.equal(sent.action,'reader-view');assert.equal(sent.zoom,3);assert.equal(sent.x,.75);assert.equal(sent.y,.25);
 assert.match(html,/"?reader-view"?/);
});
test('adjustments match the TV math, vector by vector',()=>{
 const h=harness();for(const [values,input,expected] of ADJUST_VECTORS){const out=JSON.parse(h.run('JSON.stringify(adjRgb('+JSON.stringify(values)+','+input.join(',')+'))'));out.forEach((c,i)=>assert.ok(Math.abs(c-expected[i])<.0005,JSON.stringify([values,input,out,expected])))}
 assert.deepEqual(JSON.parse(h.run("JSON.stringify(adjParse('1:'+new Array(34).fill(0).join(',')))")).length,34);assert.equal(h.run("adjParse('x')"),null);assert.equal(h.run("adjParse('1:1,2')"),null);
 assert.equal(h.run('adjIdentity(adjZero())'),true);assert.equal(h.run("adjEncode(adjZero()).startsWith('1:0,0,')"),true);
});
test('the table gives the same colors as the direct math, and zero changes nothing',()=>{
 const h=harness();const near=JSON.parse(h.run("(()=>{const v=adjZero();v[2]=50;v[3]=30;v[8]=20;v[13+3]=40;const input=new Uint8ClampedArray([200,120,40,255,12,200,90,128]),out=new Uint8ClampedArray(8);adjApply(adjLut(v),input,out);return JSON.stringify([[...out],adjRgb(v,200/255,120/255,40/255).map(c=>c*255),adjRgb(v,12/255,200/255,90/255).map(c=>c*255)])})()"));
 for(const i of [0,1,2])assert.ok(Math.abs(near[0][i]-near[1][i])<4);for(const i of [0,1,2])assert.ok(Math.abs(near[0][4+i]-near[2][i])<4);assert.equal(near[0][7],128);
 const same=JSON.parse(h.run("(()=>{const input=new Uint8ClampedArray([200,120,40,255,3,250,90,255]),out=new Uint8ClampedArray(8);adjApply(adjLut(adjZero()),input,out);return JSON.stringify([...out])})()"));
 assert.deepEqual(same,[200,120,40,255,3,250,90,255]);
});
test('the paper tap and Auto bring yellowed paper back toward neutral',()=>{
 const h=harness();const paper=JSON.parse(h.run('JSON.stringify(adjNeutral(.95,.9,.7))'));assert.ok(paper.temperature<0,'yellow paper needs a cooler page');
 const check=JSON.parse(h.run('(()=>{const n=adjNeutral(.95,.9,.7),v=adjZero();v[0]=n.temperature;v[1]=n.tint;return JSON.stringify(adjRgb(v,.95,.9,.7))})()'));assert.ok(Math.abs(check[0]-check[2])<.03&&Math.abs(check[0]-check[1])<.03,JSON.stringify(check));
 const auto=JSON.parse(h.run("(()=>{const d=[];for(let i=0;i<1000;i++)d.push(i<40?30:240,i<40?30:230,i<40?30:190,255);return JSON.stringify(adjAuto(new Uint8ClampedArray(d),adjZero()))})()"));
 assert.ok(auto[7]<0,'dull ink goes darker');assert.ok(auto[6]>0,'dull paper goes lighter');assert.ok(auto[0]<0,'the brightest part is the paper');
});
test('a drawn stretch replaces its text and the rest keeps its place',()=>{
 const h=harness();const pieces=JSON.parse(h.run("JSON.stringify(textPieces('abcdefghij',[{start:2,end:5,page:50000,drawn:true},{start:7,end:9,page:50001,drawn:false}]))"));
 assert.deepEqual(pieces,[{at:0,text:'ab'},{row:{start:2,end:5,page:50000,drawn:true}},{at:5,text:'fghij'}]);
 assert.deepEqual(JSON.parse(h.run("JSON.stringify(textPieces('abc',[]))")),[{at:0,text:'abc'}]);
 const lines=JSON.parse(h.run("JSON.stringify(textWrap('um dois tres\\n\\nquatro',7,s=>s.length))"));assert.deepEqual(lines,['um dois','tres','','quatro']);
 assert.deepEqual(JSON.parse(h.run("JSON.stringify(textWrap('abcdefghij',4,s=>s.length))")),['abcd','efgh','ij']);
});
test('the library search is an icon until touched',()=>{
 const h=harness();h.run("globalThis.seen=[];$('searchBox').classList.toggle=(n,on)=>seen.push(n+':'+on);$('searchBox').classList.contains=()=>seen[seen.length-1]==='open:true';$('searchIcon').onclick()");assert.deepEqual(JSON.parse(h.run('JSON.stringify(seen)')),['open:true']);
 h.run("$('search').value='x';$('searchIcon').onclick()");assert.equal(h.run("$('search').value"),'');assert.match(html,/id="search" class="hidden"/);
});
test('zoom and scroll sliders moved to the right bar behind one icon',async()=>{
 assert.match(html,/id="readerSliders" class="readerSide sliders"/);assert.doesNotMatch(html,/slidersHandle/);
 const h=harness();await h.run("current={id:'c'};syncOn=true;tvReader={active:true,comicId:'c',page:1,pageCount:5,readerMenu:{layout:'Normal',fit:'Página'}};readerSide('right',true)");
 const icon="$('menuRows').children.find(c=>c.dataset.label==='Zoom e rolagem')";
 await h.run(icon+".onclick()");assert.equal(h.run('slidersOpen'),true);
 await h.run("toggleMore('more')");assert.equal(h.run('slidersOpen'),false);
 await h.run(icon+".onclick()");assert.equal(h.run('slidersOpen'),true);assert.equal(h.run('menuMore'),null);
 h.run("readerSide('right',false)");assert.equal(h.run('slidersOpen'),false);
});
const ADJUST_VECTORS=[[[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.95,0.9,0.7],[0.95,0.9,0.7]],[[0,0,100,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.1,0.1,0.1],[0.1519,0.1519,0.1519]],[[0,0,-150,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.8,0.2,0.15],[0.499399,0.110347,0.077926]],[[60,-40,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.2,0.6,0.9],[0.229457,0.641785,0.768116]],[[0,0,0,70,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.5,0.5,0.5],[0.5,0.5,0.5]],[[0,0,0,-50,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.3,0.75,0.3],[0.36,0.675,0.36]],[[0,0,0,0,-60,50,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.95,0.9,0.7],[0.927168,0.861703,0.650531]],[[0,0,0,0,0,0,40,-30,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.1,0.1,0.1],[0.030303,0.030303,0.030303]],[[0,0,0,0,0,0,0,0,60,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.8,0.2,0.15],[1,0.12563,0.04563]],[[0,0,0,0,0,0,0,0,-100,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.2,0.6,0.9],[0.53662,0.53662,0.53662]],[[0,0,0,0,0,0,0,0,0,80,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.5,0.5,0.5],[0.5,0.5,0.5]],[[0,0,0,0,0,0,0,0,0,-40,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.3,0.75,0.3],[0.428736,0.698736,0.428736]],[[0,0,0,0,0,0,0,0,0,0,50,60,30,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.95,0.9,0.7],[0.95,0.9,0.7]],[[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-70,50,-40,0,0,0,0,0,0,0,0,0,0,0,0],[0.1,0.1,0.1],[0.1,0.1,0.1]],[[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,100,-60,80,0,0,0,0,0,0],[0.8,0.2,0.15],[0.8,0.2,0.15]],[[-30,15,35,25,-20,30,20,-25,10,25,20,30,0,0,0,0,-15,40,10,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0.2,0.6,0.9],[0.120988,0.683779,1]]];
test('the drawing math stays inside the layer limits',()=>{
 const h=harness(),size=(w,h2)=>JSON.parse(h.run('JSON.stringify(drawLayerSize('+w+','+h2+'))'));
 assert.deepEqual(size(800,1200),{w:800,h:1200});
 const big=size(4000,6000);assert.ok(Math.max(big.w,big.h)<=2048);assert.ok(big.w*big.h<=4000000);assert.ok(Math.abs(big.w/big.h-4000/6000)<.01);
 const fit=JSON.parse(h.run('JSON.stringify(drawPageFit(100,200,300,300))'));assert.deepEqual(fit,{left:75,top:0,width:150,height:300});
 assert.deepEqual(JSON.parse(h.run('JSON.stringify(drawSteps(10,4,0))')),{steps:[4,8],carry:2});
 assert.deepEqual(JSON.parse(h.run('JSON.stringify(drawSteps(10,4,2))')),{steps:[2,6,10],carry:0});
 assert.deepEqual(JSON.parse(h.run('JSON.stringify(drawSteps(1,4,0))')),{steps:[],carry:1});
 assert.equal(h.run('drawTaper(0,10,.9)'),.09999999999999998);assert.equal(h.run('drawTaper(10,10,.9)'),1);assert.equal(h.run('drawTaper(5,0,.9)'),1);assert.equal(h.run('drawTaper(5,10,0)'),1);
 assert.equal(h.run('Math.round(drawSliderToSize(drawSizeToSlider(.02))*1000)'),20);
 for(const color of ['#e02020','#000000','#ffffff','#1f6fe0'])assert.equal(h.run("(()=>{const c=drawHsbFromHex('"+color+"');return drawHexFromHsb(c.h,c.s,c.b)})()"),color);
});
test('the editor tree has every approved branch and leaves out what was excluded',()=>{
 const h=harness(),tree=JSON.parse(h.run('JSON.stringify(DRAW_TOOLS)'));
 assert.deepEqual(tree.map(t=>t.id),['hand','zoom','brush','eraser','picker','fill','shape','select','transform','text','frame','smudge','special','ruler','filter']);
 assert.deepEqual(tree.find(t=>t.id==='shape').subs.map(s=>s.name),['Linha','Retângulo','Círculo','Elipse','Polígono','Curva','Polilinha']);
 for(const name of ['Animação','Inteligência','Nuvem','Assinatura','Aparar','Redimensionar'])assert.ok(!JSON.stringify(tree).includes(name),name);
 const missing=h.run("DRAW_TOOLS.flatMap(t=>t.subs).filter(s=>!DRAW_PRESETS[s.id]&&!DRAW_MODES[s.id]&&!['tScale','tPersp','tMesh'].includes(s.id)).map(s=>s.id).join()");assert.equal(missing,'');
 h.run("drawEditor(true)");const rail=JSON.parse(h.run("JSON.stringify($('drawRail').children.map(c=>c.id))"));for(const id of ['drawTool-brush','drawTool-filter','drawUndo','drawRedo','drawSwatch','drawSaveNow','drawPanelsToggle'])assert.ok(rail.includes(id),id);
 assert.ok(rail.indexOf('drawSaveNow')<rail.indexOf('drawTool-picker'));
 h.run("drawSelect('ink','brush')");assert.equal(h.run('ed.tool'),'ink');assert.equal(h.run('ed.mode'),'draw');assert.equal(h.run('ed.lastSub.brush'),'ink');
 h.run("drawSelect('hand','hand')");assert.equal(h.run('ed.mode'),'hand');h.run("drawSelect('zoomOut','zoom')");assert.equal(h.run('ed.mode'),'zoom');
 for(const [id,group,mode] of [['fillBucket','fill','fill'],['selWand','select','wand'],['shapeRect','shape','shape'],['textBalloon','text','text'],['rRadial','ruler','ruler'],['frameSplit','frame','split'],['mosaic','special','draw']]){h.run("drawSelect('"+id+"','"+group+"')");assert.equal(h.run('ed.mode'),mode,id)}
 h.run("drawSetting('ink','size',.05);drawSelect('round','brush')");assert.equal(h.run("drawTool('ink').size"),.05);assert.equal(h.run("drawTool('round').size"),.012);
});
test('flood fill stops at lines, crosses small gaps and expands',()=>{
 const h=harness();h.run(`globalThis.grid=(w,h2,wall)=>{const d=new Uint8Array(w*h2*4);for(let y=0;y<h2;y++)for(let x=0;x<w;x++){const i=(y*w+x)*4,v=wall(x,y)?0:255;d[i]=d[i+1]=d[i+2]=v;d[i+3]=255}return d};globalThis.count=m=>m.reduce((a,b)=>a+b,0)`);
 h.run("globalThis.closed=grid(10,10,(x,y)=>x===5)");assert.equal(h.run("count(drawFloodMask(closed,10,10,1,1,{tolerance:10}))"),50);assert.equal(h.run("count(drawFloodMask(closed,10,10,5,5,{tolerance:10}))"),10);
 h.run("globalThis.gap=grid(21,21,(x,y)=>x===10&&y!==10)");assert.equal(h.run("count(drawFloodMask(gap,21,21,1,1,{tolerance:10}))"),421);
 const stopped=h.run("(()=>{const m=drawFloodMask(gap,21,21,1,1,{tolerance:10,gap:1});return [m[10*21+15],m[1*21+15],m[1*21+5]].join()})()");assert.equal(stopped,'0,0,1');
 assert.equal(h.run("count(drawFloodMask(closed,10,10,1,1,{tolerance:10,expand:1}))"),60);
 assert.equal(h.run("JSON.stringify(drawMaskBounds(drawFloodMask(closed,10,10,1,1,{tolerance:10}),10,10))"),'{"x":0,"y":0,"w":5,"h":10}');
});
test('filters change only what they promise',()=>{
 const h=harness(),run=(id,pixels,params,w=pixels.length/4,h2=1)=>JSON.parse(h.run("(()=>{const d=new Uint8ClampedArray("+JSON.stringify(pixels)+");DRAW_FILTERS."+id+".run(d,"+w+","+h2+","+JSON.stringify(params)+");return JSON.stringify([...d])})()"));
 assert.deepEqual(run('invert',[10,20,30,200],{}),[245,235,225,200]);
 assert.deepEqual(run('gray',[100,100,100,255],{}),[100,100,100,255]);
 assert.deepEqual(run('blackWhite',[250,250,250,255,5,5,5,255],{limit:128}),[255,255,255,255,0,0,0,255]);
 assert.deepEqual(run('brightness',[100,100,100,255],{bri:20,con:0}),[120,120,120,255]);
 assert.deepEqual(run('balance',[100,100,100,255],{r:10,g:-10,b:0}),[110,90,100,255]);
 assert.deepEqual(run('hsl',[200,50,50,255],{h:0,s:0,l:0}),[200,50,50,255]);
 assert.deepEqual(run('lines',[255,255,255,255,0,0,0,255],{strength:100}),[0,0,0,0,0,0,0,255]);
 assert.deepEqual(run('pixelate',[0,0,0,255,100,100,100,255],{block:2},2,1),[50,50,50,255,50,50,50,255]);
 assert.deepEqual(run('posterize',[100,100,100,255],{levels:2}),[0,0,0,255]);
 assert.deepEqual(run('gaussian',[80,80,80,255,80,80,80,255,80,80,80,255],{radius:1}),[80,80,80,255,80,80,80,255,80,80,80,255]);
 assert.equal(h.run("drawFilterScale('pixelate',{block:10},.5).block"),5);assert.equal(h.run("drawFilterScale('gaussian',{radius:1},.25).radius"),1);
});
test('rulers keep the stroke on their guide',()=>{
 const h=harness(),project=(type,a,b,start,p)=>JSON.parse(h.run("JSON.stringify(drawRulerProject("+JSON.stringify({type,a,b})+","+JSON.stringify(start)+","+JSON.stringify(p)+"))"));
 assert.deepEqual(project('rStraight',{x:0,y:0},{x:10,y:0},{x:5,y:5},{x:9,y:30}),{x:9,y:5});
 assert.deepEqual(project('rRadial',{x:0,y:0},{x:1,y:1},{x:10,y:0},{x:20,y:9}),{x:20,y:0});
 const round=project('rCircle',{x:0,y:0},{x:10,y:0},{x:10,y:0},{x:0,y:30});assert.ok(Math.abs(round.x)<1e-6);assert.ok(Math.abs(round.y-10)<1e-6);
 assert.deepEqual(project('rSymmetry',{x:0,y:0},{x:0,y:1},{x:1,y:1},{x:3,y:4}),{x:3,y:4});
 assert.equal(h.run("JSON.stringify(drawMirror({x:10,y:0},{x:10,y:5},3,4))"),'{"x":17,"y":4}');
});
test('perspective and mesh maps reach their control points',()=>{
 const h=harness();h.run("globalThis.q=[{x:0,y:0},{x:10,y:1},{x:12,y:9},{x:-1,y:8}];globalThis.map=drawHomography(q)");
 for(const [u,v,i] of [[0,0,0],[1,0,1],[1,1,2],[0,1,3]]){assert.ok(h.run("Math.hypot(map("+u+","+v+").x-q["+i+"].x,map("+u+","+v+").y-q["+i+"].y)")<1e-6)}
 h.run("globalThis.points=[];for(let j=0;j<=3;j++)for(let i=0;i<=3;i++)points.push({x:i*10,y:j*10});globalThis.mesh=drawMeshMap(points,3)");assert.equal(h.run("JSON.stringify(mesh(.5,.5))"),'{"x":15,"y":15}');assert.equal(h.run("JSON.stringify(mesh(1,1))"),'{"x":30,"y":30}');
});
test('white can become transparent and baked pixels become opaque',()=>{
 const h=harness();assert.equal(h.run("(()=>{const d=new Uint8ClampedArray([255,255,255,255,0,0,0,255]);drawClearWhiteData(d);return [d[3],d[7],d[4]].join()})()"),'0,255,0');
 assert.equal(h.run("(()=>{const d=new Uint8ClampedArray([1,2,3,9,4,5,6,9]);drawCoverAlpha(d,[0,0,0,0,0,0,0,7]);return [d[3],d[7]].join()})()"),'0,255');
});
test('a layer stack undoes structure without losing its pixels',()=>{
 const h=harness();h.run("ed.lw=2;ed.lh=2;drawEditor(true);const make=n=>({name:n,visible:true,opacity:1,blend:'source-over',alphaLock:false,clip:false,locked:false});globalThis.a=make('a');globalThis.b=make('b');ed.layers=[a,b];ed.active=1;globalThis.before=drawStateTake();b.opacity=.5;ed.layers=[b];ed.active=0;drawStateRestore(before)");
 assert.equal(h.run('ed.layers.length'),2);assert.equal(h.run('ed.layers[1]===b'),true);assert.equal(h.run('b.opacity'),1);assert.equal(h.run('ed.active'),1);
 assert.equal(h.run("(()=>{ed.layers=[];return drawLayer()===undefined})()"),true);
});
test('leaving the editor asks only when there is something to save',async()=>{
 const h=harness();h.run("globalThis.went=0;const go=()=>went++;globalThis.go=go;sideOpen.left=true;ed.at=-1;ed.savedAt=-1;drawGuard(go)");assert.equal(h.run('went'),1);
 h.run("globalThis.shown=null;$('drawDialog').classList.remove=n=>shown=n;$('drawDialog').classList.add=()=>shown='closed';ed.at=0;drawGuard(go)");assert.equal(h.run('went'),1);assert.equal(h.run('shown'),'hidden');
 h.run("$('drawDialogKeep').onclick()");assert.equal(h.run('went'),1);assert.equal(h.run('shown'),'closed');assert.equal(h.run('ed.pending'),null);
 h.run("ed.savedAt=0;drawGuard(go)");assert.equal(h.run('went'),2);
 h.run("sideOpen.left=false;ed.at=5;drawGuard(go)");assert.equal(h.run('went'),3);
 assert.match(html,/id="drawDialogSave"[^>]*>Salvar</);assert.match(html,/id="drawDialogDiscard"[^>]*>Descartar</);assert.match(html,/id="drawDialogKeep"[^>]*>Continuar editando</);
});
test('history keeps order and forgets what is ahead after a new stroke',()=>{
 const h=harness();h.run("drawHistoryPush({name:'a',x:0,y:0,w:1,h:1});drawHistoryPush({name:'b',x:0,y:0,w:1,h:1});ed.savedAt=1;ed.at=0;drawHistoryPush({name:'c',x:0,y:0,w:1,h:1})");
 assert.deepEqual(JSON.parse(h.run('JSON.stringify(ed.hist.map(i=>i.name))')),['a','c']);assert.equal(h.run('ed.at'),1);assert.equal(h.run('ed.savedAt'),-2);assert.equal(h.run('drawDirty()'),true);
 assert.deepEqual(JSON.parse(h.run("JSON.stringify($('drawHistory').children.map(c=>c.textContent))")),['Abrir','a','c']);
});
test('stroke messages keep their order and join the points still waiting',()=>{
 const h=harness();h.run("ed.book={id:'c'};ed.page=2;drawOut.busy=true;drawSend({stroke:{kind:'begin',points:[.1,.1]}});drawSend({stroke:{kind:'move',points:[.2,.2]}});drawSend({stroke:{kind:'move',points:[.3,.3]}});drawSend({stroke:{kind:'end',points:[]}})");
 const q=JSON.parse(h.run('JSON.stringify(drawOut.queue.map(i=>i.stroke))'));assert.deepEqual(q.map(i=>i.kind),['begin','move','end']);assert.deepEqual(q[1].points,[.2,.2,.3,.3]);assert.equal(q[0].id,'c');assert.equal(q[0].page,2);
});
test('reader menu offers the TV options and sends only known ones',async()=>{
 const h=harness();await h.run("current={id:'c'};syncOn=true;tvReader={active:true,comicId:'c',page:1,pageCount:5,readerMenu:{layout:'Normal',fit:'Página',filter:'Original',interval:10,chapters:true,editions:true,cover:true,rotatable:true}};readerSide('right',true)");
 const labels=JSON.parse(h.run("JSON.stringify($('menuRows').children.map(c=>c.dataset.label))"));
 assert.deepEqual(labels,['Continuar','Editar a página','Ajustes de imagem','Reproduzir o desenho na TV','Exportar como CBZ','Escolher página','Capítulo anterior','Próximo capítulo','Capítulos / edições','Visualização: Normal','Ajuste: Página','Zoom e rolagem','Imagem e mais opções','Sair da leitura']);
 h.run("toggleMore('more')");const more=JSON.parse(h.run("JSON.stringify($('menuMore').children.map(c=>c.textContent))"));for(const label of ['Usar página atual como capa da saga','Filtro: Original','Iniciar leitura automática','Intervalo: 10s'])assert.ok(more.includes(label),label);
 await h.run("$('menuRows').children.find(c=>c.dataset.label==='Sair da leitura').onclick()");assert.deepEqual(JSON.parse(h.requests.find(r=>r.url.endsWith('/command')).options.body),{action:'reader-option',text:'exit'});
});
test('double tap zooms at the point, again returns, and only mirrors when synced',async()=>{
 const h=harness();await h.run("$('pager').getBoundingClientRect=()=>({left:0,top:0,width:400,height:800});doubleTapZoom(100,200)");
 assert.equal(h.run('view.s'),2);assert.equal(h.run('view.x'),100);assert.equal(h.run('view.y'),200);assert.equal(h.requests.filter(r=>r.url.endsWith('/command')).length,0);
 h.run('doubleTapZoom(100,200)');assert.equal(h.run('view.s'),1);
 h.run("current={id:'c'};syncOn=true;tvReader={active:true,comicId:'c'};doubleTapZoom(100,200)");await new Promise(resolve=>setTimeout(resolve,20));
 assert.deepEqual(JSON.parse(h.requests.find(r=>r.url.endsWith('/command')).options.body),{action:'reader-pinch',zoom:2,x:.25,y:.25,dx:0,dy:0});
});
test('a switch decides its group on each TV screen, starting from the approved layout',async()=>{
 const s=harness();s.run("tvReader={active:true,page:2,pageCount:5};controlLayout()");assert.equal(s.run("groupOn('arrows')"),false);assert.equal(s.run("groupOn('pad')"),true);
 s.run("CONTROL_GROUPS.reader.arrows=true");await s.run("directional('right')");assert.deepEqual(JSON.parse(s.requests.find(r=>r.url.endsWith('/command')).options.body),{action:'reader-arrow',text:'right'});
 s.run("tvReader={active:false}");assert.equal(s.run("groupOn('arrows')"),true);assert.equal(s.run("groupOn('sliders')"),false);
 s.run("CONTROL_GROUPS.navigation.pages=true;controlLayout()");assert.equal(s.run("$('tvPrev').disabled"),false);
 s.requests.length=0;await s.run("pageStep(1)");assert.equal(s.requests.filter(r=>r.url.endsWith('/command')).length,0);
 const h=harness();h.run("for(const key of Object.keys(CONTROL_GROUPS.reader))CONTROL_GROUPS.reader[key]=false;tvReader={active:true,page:2,pageCount:5};$('padOK').disabled=false;controlLayout()");
 for(const id of ['tvPrev','tvNext','btnLeft','btnRight'])assert.equal(h.run("$('"+id+"').disabled"),true,id);
 assert.equal(h.run("$('padOK').disabled"),false);assert.equal(h.run("$('btnBack').disabled||false"),false);assert.equal(h.run("$('btnMenu').disabled||false"),false);
 assert.match(html,/role="switch" data-group="arrows"/);assert.match(html,/data-group="pad"/);assert.match(html,/data-group="pages"/);assert.match(html,/data-group="sliders"/);
});
test('library shows sagas once and keeps every other edition as its own card',()=>{
 const h=harness();h.run("books=[{id:'a',title:'A',pages:1},{id:'b',title:'B',pages:1},{id:'c',title:'C',pages:1}];sagas=[{cover:'a',title:'Saga',ids:['a','b']}];render()");
 assert.equal(h.run("$('books').children.length"),2);h.run("$('search').value='b';render()");assert.equal(h.run("$('books').children.length"),1);
});
test('Abrir site appears with the TV browser and sends the spoken text',async()=>{
 const h=harness();h.run("globalThis.shown=null;$('openSite').classList.toggle=(n,on)=>shown=!on;tvReader={cursorAvailable:true};cursorUI()");assert.equal(h.run('shown'),true);
 await h.run("$('keyboard').value=' uol ponto com ';$('openSite').onclick()");await new Promise(resolve=>setTimeout(resolve,20));
 assert.deepEqual(JSON.parse(h.requests.find(r=>r.url.endsWith('/command')).options.body),{action:'browser-open',text:'uol ponto com'});
});
test('TV mirror and capture requests are removed',()=>{assert.doesNotMatch(html,/\/screen|refreshScreen|id="screen"/)});
test('arrows are off in the reader and in the browser',async()=>{
  const h=harness();await h.run("tvReader={active:true,page:4,pageCount:10,zoom:2,minZoom:1,maxZoom:4}; directional('right')");await h.run("tvReader={active:false,browser:true}; directional('right')");
  assert.equal(h.requests.filter(r=>r.url.endsWith('/command')).length,0);
});
test('outside reader arrows navigate and touchpad uses pointer',async()=>{
  const h=harness();await h.run("tvReader={active:false}; directional('down'); movePad(.1,-.2)");
  const actions=h.requests.filter(r=>r.url.endsWith('/command')).map(r=>JSON.parse(r.options.body));assert.deepEqual(actions,[{action:'down'},{action:'pointer',dx:.1,dy:-.2}]);
});
test('page and zoom commands clamp to actual reader bounds',async()=>{
  const h=harness();await h.run("tvReader={active:true,page:0,pageCount:8,zoom:1,minZoom:1,maxZoom:3}; tvPage(500); tvZoom(0)");
  const actions=h.requests.filter(r=>r.url.endsWith('/command')).map(r=>JSON.parse(r.options.body));assert.deepEqual(actions,[{action:'reader-page',page:7},{action:'reader-zoom',zoom:1}]);
});
test('clear history keeps active transfers and originals intact',()=>{
  const h=harness();h.run("transfers=[{done:true},{done:false}]; clearTransfers()");assert.equal(h.run('transfers.length'),1);assert.equal(h.requests.filter(r=>/delete|remove/.test(r.url)).length,0);
});
test('reader-only sliders do nothing outside the TV reader',async()=>{
  const h=harness();await h.run("tvReader={active:false}; tvPage(2); tvZoom(2)");assert.equal(h.requests.filter(r=>r.url.endsWith('/command')).length,0);
});
test('pinch does not resize the reading layout under the fingers',()=>{
 const h=harness();h.run("window.visualViewport={scale:2,height:200}; document.documentElement.style.setProperty=()=>{throw Error('pinch resized layout')}; viewport()");
});
test('reader is minimal and its own pinch replaces the browser one',()=>{assert.doesNotMatch(html,/id="(?:openTV|fit|zoom|pageRange)"/);assert.match(html,/#pager\{[^}]*touch-action:none/);assert.match(html,/100dvh/);assert.match(html,/id="tvZoom"/);assert.match(html,/id="tvPage"/)});



test('new text section resets its own scroll container',async()=>{
  const h=harness();h.run("$('pageText').scrollTop=900");
  await h.run("current={id:'book',pages:3};page=1;fetch=async()=>({ok:true,headers:{get:()=> 'text/plain'},text:async()=> 'Next section'});loadPage()");
  assert.equal(h.elements.get('pageText').scrollTop,0);
  assert.equal(h.elements.get('pageText').textContent,'Next section');
});


function pointer(h,kind,id,x,y){return h.run(`$('touchpad').onpointer${kind}({pointerId:${id},button:0,pointerType:'touch',clientX:${x},clientY:${y},preventDefault(){}})`)}
function sent(h){return h.requests.filter(r=>r.url.endsWith('/command')).map(r=>JSON.parse(r.options.body))}
test('reader pinch sends zoom with the point under the fingers, without click',()=>{
 const h=harness();h.run("tvReader={active:true,zoom:1.5,minZoom:1,maxZoom:3};");
 pointer(h,'down',1,20,20);pointer(h,'down',2,120,20);pointer(h,'move',2,220,20);h.run('flushPad()');
 const [pinch]=sent(h);assert.equal(pinch.action,'reader-pinch');assert.equal(pinch.zoom,3);assert.equal(pinch.x,.4);assert.equal(pinch.y,20/300);assert.equal(pinch.dy,0);
 pointer(h,'up',2,220,20);pointer(h,'up',1,20,20);assert.equal(sent(h).length,1);
});
test('two fingers moving together pan the reader while zoom stays',()=>{
 const h=harness();h.run("tvReader={active:true,zoom:2,minZoom:1,maxZoom:3};");
 pointer(h,'down',1,50,50);pointer(h,'down',2,150,50);pointer(h,'move',1,50,110);pointer(h,'move',2,150,110);h.run('flushPad()');
 const [pinch]=sent(h);assert.equal(pinch.action,'reader-pinch');assert.ok(Math.abs(pinch.zoom-2)<.4);assert.ok(pinch.dy>0);assert.equal(pinch.dx,0);
});
test('a horizontal flick turns the phone page, unless pinched or mostly vertical',()=>{
 const h=harness();const delta=code=>h.run('swipeTurn('+code+')');
 assert.equal(delta('-120,5,200,1'),1);assert.equal(delta('120,5,200,1'),-1);
 assert.equal(delta('-120,5,200,2'),0);assert.equal(delta('-120,100,200,1'),0);assert.equal(delta('-30,0,200,1'),0);assert.equal(delta('-120,5,1500,1'),0);
});
test('a slow drag past a third of the page turns it, and a short one does not',()=>{
 const h=harness();const delta=code=>h.run('swipeTurn('+code+')');
 assert.equal(delta('-120,5,3000,1,300'),1);assert.equal(delta('120,5,3000,1,300'),-1);assert.equal(delta('-50,5,3000,1,300'),0);assert.equal(delta('-120,5,3000,2,300'),0);
});
test('the panel opens with every bar folded, keeps only the gear on top, and Sincronizado is a text button in the page bar',()=>{
 assert.match(html,/<body class="tabsFolded">/);assert.doesNotMatch(html,/COMIC <span>TV/);assert.doesNotMatch(html,/menuBtn/);
 assert.match(html,/<div class="readerTools">.*id="pageLabel".*id="connected" class="syncText".*id="next"/);assert.match(html,/id="readerTitle" class="srOnly"/);
 for(const name of ['Amber','Flix','Prime','Max','Cinema']){const h=harness();h.run("applyTheme('"+name+"')");assert.equal(h.run("$('navToggle').textContent"),'⌄',name)}
 const c=harness();c.run("applyTheme('Flix');tab('control')");assert.equal(c.run("$('navToggle').textContent"),'⌄');
});
test('library titles stop at two lines so the buttons line up',()=>{assert.match(html,/\.book h3\{[^}]*-webkit-line-clamp:2[^}]*min-height:2\.8em/);assert.match(html,/\.book \.actions\{margin-top:auto\}/)});
test('with sync on, a drag and a pinch on the phone reach the TV while the fingers move',async()=>{
 const h=harness();h.run("globalThis.setTimeout=()=>1;syncOn=true;current={id:'b1',pages:9};page=2;tvReader={active:true,comicId:'b1',page:2,pageCount:9};liveAt=0;mirrorDrag(-.4)");await flush(h);
 assert.deepEqual(sent(h),[{action:'reader-drag',x:-.4}]);
 const off=harness();off.run("syncOn=false;current={id:'b1',pages:9};tvReader={active:true,comicId:'b1'};liveAt=0;mirrorDrag(-.4)");await flush(off);assert.equal(sent(off).length,0);
 const p=harness();p.run("syncOn=true;current={id:'b1',pages:9};tvReader={active:true,comicId:'b1'};liveAt=0;startPinch([{clientX:100,clientY:100},{clientX:200,clientY:100}]);pinchMove([{clientX:50,clientY:100},{clientX:250,clientY:100}])");await flush(p);
 const [pinch]=sent(p);assert.equal(pinch.action,'reader-pinch');assert.equal(pinch.zoom,2);assert.equal(p.run('view.s'),2);
});
test('turning past the last phone page does not reload it',()=>{
 const h=harness();h.run("current={id:'book',pages:3};page=2;globalThis.loads=0;loadPage=()=>{loads++};turn(1)");assert.equal(h.run('loads+page'),2);
});
test('every action the phone sends is on the server allowlist',()=>{
 const server=readFileSync(new URL('../core/src/main/kotlin/com/pelican/comictv/transfer/CompanionServer.kt',import.meta.url),'utf8');
 const allowed=new Set([...server.match(/c\.action !in setOf\(([^)]*)\)/)[1].matchAll(/"([^"]+)"/g)].map(m=>m[1]));
 const used=[...html.matchAll(/data-command="([^"]+)"/g)].map(m=>m[1]);
 for(const m of script.matchAll(/(?:command|queuePad)\('([a-z-]+)'(?!\+)/g))used.push(m[1]);
 for(const direction of ['up','down','left','right','ok'])used.push('browser-'+direction);
 for(const action of used)assert.ok(allowed.has(action),action);
});
test('layout order follows the approved image, with the gesture bar on top and Voltar, Menu, Anterior and Próxima around the arrows',()=>{
 const order=['id="sensitivityExtras"','id="tvPage"','id="sensitivity"','id="mirrorSide"','id="rotateScreen"','id="showKeyboard"','id="btnBack"','data-direction="up"','id="btnMenu"','data-direction="left"','id="padOK"','data-direction="right"','id="tvPrev"','data-direction="down"','id="tvNext"','id="touchpad"','id="btnLeft"','id="btnRight"','id="zoomLabel"','id="zoomTrack"','id="tvPageLabel"','id="scrollTrack"','id="scrollCaption"'].map(mark=>html.indexOf(mark));assert.ok(order.every(at=>at>=0));assert.deepEqual(order,[...order].sort((a,b)=>a-b));
 assert.doesNotMatch(html,/id="(?:modePad|modeArrows|resetZoom|zoomControl|sideColumn|pageSide|pageButtons)"|class="topActions"/);assert.doesNotMatch(html,/Controle da TV/);assert.doesNotMatch(html,/#tvPrev\{transform/);
});
const flush=h=>h.run('(async()=>{for(let i=0;i<30;i++)await Promise.resolve()})()');
test('each control does what the approved table says in the reader',()=>{
 const h=harness();h.run("tvReader={active:true,page:4,pageCount:10,zoom:1,minZoom:1,maxZoom:3};$('tvPrev').onclick();$('tvNext').onclick();$('btnLeft').onclick();$('btnRight').onclick();$('padOK').onclick();directional('up')");
 assert.deepEqual(sent(h),[{action:'reader-turn',delta:-1},{action:'reader-turn',delta:1},{action:'reader-turn',delta:-1},{action:'reader-turn',delta:1},{action:'ok'}]);
});
test('each control does what the approved table says in the browser',()=>{
 const h=harness();h.run("tvReader={active:false,browser:true};$('tvPrev').onclick();$('tvNext').onclick();$('btnLeft').onclick();$('btnRight').onclick();$('padOK').onclick();directional('right')");
 assert.deepEqual(sent(h),[{action:'browser-history',delta:-1},{action:'browser-history',delta:1},{action:'browser-click'},{action:'browser-context'},{action:'browser-ok'}]);
});
test('each control does what the approved table says in app navigation',()=>{
 const h=harness();h.run("tvReader={active:false};$('tvPrev').onclick();$('tvNext').onclick();$('btnLeft').onclick();$('btnRight').onclick();$('padOK').onclick();directional('up')");
 assert.deepEqual(sent(h),[{action:'ok'},{action:'ok-long'},{action:'ok'},{action:'up'}]);
});
test('zoom and page sliders act before release and a poll leaves a held slider alone',async()=>{
 const h=harness();h.run("tvReader={active:true,page:1,pageCount:9,zoom:1,minZoom:1,maxZoom:3};$('tvZoom').value='2';$('tvZoom').oninput()");await flush(h);
 assert.deepEqual(sent(h),[{action:'reader-zoom',zoom:2}]);
 h.run("tvReader.zoom=1;renderTVState()");assert.equal(h.run("$('tvZoom').value"),'2');
 h.run("liveAt=0;$('tvPage').value='4';$('tvPage').oninput()");await flush(h);assert.deepEqual(sent(h).at(-1),{action:'reader-page',page:4});
});
test('browser page slider scrolls while dragged, and only a page with its own scroll takes it',async()=>{
 const h=harness();const drag="$('scrollTrack').onpointerdown({pointerId:1,clientY:150,preventDefault(){}})";
 h.run("tvReader={active:false,browser:true,browserScrollable:false};"+drag);await flush(h);assert.deepEqual(sent(h),[]);
 h.run("tvReader.browserScrollable=true;"+drag);await flush(h);assert.equal(sent(h)[0].action,'browser-scroll-to');assert.equal(sent(h)[0].dy,.5);
});
test('browser pinch zooms at the fingers and moves with them',()=>{
 const h=harness();h.run("tvReader={active:false,browser:true}");pointer(h,'down',1,50,50);pointer(h,'down',2,100,50);pointer(h,'move',2,250,50);h.run('flushPad()');
 const [pinch]=sent(h);assert.equal(pinch.action,'browser-pinch');assert.ok(pinch.zoom>1);assert.equal(pinch.x,.5);assert.equal(pinch.dy,0);
});
test('a brief connection failure stays silent, a sequence warns and any answer clears it',async()=>{
 const h=harness();await flush(h);h.run("globalThis.online=fetch;fetch=async()=>{throw new TypeError('down')}");
 await h.run("refreshReaderState()");await h.run("refreshReaderState()");assert.equal(h.run("$('notice').textContent")||'','');
 await h.run("refreshReaderState()");assert.match(h.run("$('notice').textContent"),/Tentando de novo/);assert.match(h.run("$('connected').textContent"),/Sem conexão/);
 h.run("fetch=online");await h.run("refreshReaderState()");assert.equal(h.run("$('notice').textContent"),'');assert.match(h.run("$('connected').textContent"),/Sincronizar/);
});
test('sync follows the TV book and page, and a phone turn reaches the TV',async()=>{
 const h=harness();h.run("globalThis.loads=0;loadPage=()=>{loads++};section='reader';syncOn=true;books=[{id:'b1',title:'HQ',pages:9}];tvReader={active:true,comicId:'b1',page:3,pageCount:9};syncFollow()");
 assert.equal(h.run('current.title'),'HQ');assert.equal(h.run('page'),3);assert.equal(h.run('loads'),1);
 h.run("turn(1)");await flush(h);assert.deepEqual(sent(h),[{action:'reader-page',page:4}]);
 h.run("tvReader.page=3;syncFollow()");assert.equal(h.run('page'),4);
 h.run("tvReader.page=4;syncQuietUntil=0;syncFollow();tvReader.page=7;syncFollow()");assert.equal(h.run('page'),7);
});
test('turning sync on with the TV outside the reader opens the phone book on the TV at its page',async()=>{
 const h=harness();h.run("current={id:'b1',title:'HQ',pages:9};page=5");await h.run("setSync(true)");await flush(h);assert.deepEqual(sent(h),[{action:'open',id:'b1'}]);
 h.run("tvReader={active:true,comicId:'b1',page:0,pageCount:9};syncFollow()");await flush(h);assert.deepEqual(sent(h).at(-1),{action:'reader-page',page:5});assert.equal(h.run('page'),5);
});
test('sync off leaves the phone reader independent',()=>{
 const h=harness();h.run("current={id:'b1',title:'HQ',pages:9};page=2;tvReader={active:true,comicId:'b1',page:7,pageCount:9};syncFollow();loadPage=()=>{};turn(1)");assert.equal(h.run('page'),3);assert.deepEqual(sent(h),[]);
});
test('the server policy allows the inline anti-sleep clip',()=>{
 const server=readFileSync(new URL('../core/src/main/kotlin/com/pelican/comictv/transfer/CompanionServer.kt',import.meta.url),'utf8');
 assert.match(server,/media-src data:/);assert.match(script,/AWAKE_VIDEO='data:video\/mp4;base64,/);
});

test('browser two fingers scroll, not click or cursor movement',()=>{
 const h=harness();h.run("tvReader={active:false,browser:true};");
 pointer(h,'down',1,30,30);pointer(h,'down',2,130,30);pointer(h,'move',1,30,60);pointer(h,'move',2,130,60);h.run('flushPad()');
 assert.ok(sent(h).some(c=>c.action==='browser-scroll'));assert.ok(sent(h).every(c=>!['ok','pointer'].includes(c.action)));
});
test('reader and cursor sensitivity are independent and bounded',()=>{
 const h=harness();h.run("sensitivities={reader:2,pointer:.5,zoom:1,scroll:1};tvReader={active:true};movePad(.2,-.2);tvReader={active:false};movePad(.2,-.2)");
 assert.deepEqual(sent(h),[{action:'reader-pan',x:-.4,y:.4},{action:'pointer',dx:.1,dy:-.1}]);
});
test('Menu is explicit app Home, not an Android menu key',()=>{assert.match(html,/data-command="home"/);assert.doesNotMatch(html,/data-command="menu"/)});
test('keyboard viewport follows visible top without affecting reader pinch',()=>{
 const h=harness(),values={};h.run("section='control';window.visualViewport={scale:1,height:390,offsetTop:120}");h.run('document.documentElement.style.setProperty=(key,value)=>{globalThis.v=globalThis.v||{};v[key]=value};viewport()');
 assert.equal(h.run("v['--viewport-top']"),'120px');assert.equal(h.run("v['--viewport-height']"),'390px');
});

test('zero axis stays zero and a pinch can resume a one-finger pan',async()=>{
 const h=harness();h.run("tvReader={active:true,zoom:1,minZoom:1,maxZoom:3}");pointer(h,'down',1,20,20);pointer(h,'down',2,120,20);pointer(h,'move',2,220,20);h.run('flushPad()');await h.run('(async()=>{for(let i=0;i<20;i++)await Promise.resolve()})()');pointer(h,'up',2,220,20);pointer(h,'move',1,50,20);h.run('flushPad()');await h.run('(async()=>{for(let i=0;i<20;i++)await Promise.resolve()})()');
 const pan=sent(h).find(c=>c.action==='reader-pan');assert.ok(pan);assert.equal(pan.y,0);assert.equal(pan.x,-.1);
});
test('pointer cancellation never confirms a click',()=>{
 const h=harness();h.run("tvReader={active:false,browser:true}");pointer(h,'down',1,20,20);pointer(h,'cancel',1,20,20);pointer(h,'up',1,20,20);assert.deepEqual(sent(h),[]);
});

test('browser pointer stays scoped to its page even if TV changes screen',()=>{
 const h=harness();h.run("tvReader={active:false,browser:true};movePad(.1,0)");assert.deepEqual(sent(h),[{action:'browser-pointer',dx:.1,dy:0}]);
});

test('tap then drag keeps browser press move release in order',async()=>{
 const h=harness();h.run("tvReader={active:false,browser:true}");pointer(h,'down',1,30,30);pointer(h,'up',1,30,30);await h.run('(async()=>{for(let i=0;i<20;i++)await Promise.resolve()})()');pointer(h,'down',2,30,30);pointer(h,'move',2,60,30);pointer(h,'up',2,60,30);await h.run('(async()=>{for(let i=0;i<40;i++)await Promise.resolve()})()');
 assert.deepEqual(sent(h).map(c=>c.action),['browser-click','browser-press','browser-pointer','browser-release']);
});
test('the touchpad still moves the mouse in the browser, scaled by its sensitivity',()=>{
 const h=harness();h.run("tvReader={active:false,browser:true};sensitivities.pointer=2;movePad(.1,0)");assert.deepEqual(sent(h),[{action:'browser-pointer',dx:.2,dy:0}]);
});
test('the keyboard is always on',()=>{
 for(const state of ["{active:true,page:0,pageCount:3}","{active:false,browser:true}","{active:false}"]){const h=harness();h.run("tvReader="+state+";controlLayout()");assert.ok(!h.run("$('showKeyboard').disabled"),state)}
});
test('a sent text clears the field and closes the keyboard',async()=>{
 const h=harness();h.run("$('showKeyboard').onclick()");
 h.run("$('keyboard').value='Batman';$('keyboardForm').onsubmit({preventDefault(){}})");
 await new Promise(resolve=>setImmediate(resolve));await new Promise(resolve=>setImmediate(resolve));
 assert.deepEqual(sent(h).filter(c=>c.action==='text'),[{action:'text',text:'Batman'}]);assert.equal(h.run("$('keyboard').value"),'');
});
test('cursor size and icon controls appear only while the TV browser lends them',async()=>{
 const h=harness();h.run("tvReader={active:false,browser:true,cursorAvailable:false,cursorSize:100,cursorStyle:0};cursorUI()");
 h.run("tvReader={active:false,browser:true,cursorAvailable:true,cursorSize:175,cursorStyle:2};cursorUI()");
 assert.equal(h.run("cursorPrefs.size"),175);assert.equal(h.run("cursorPrefs.style"),2);
 await h.run("cursorPrefs={size:150,style:1};saveCursor()");
 assert.deepEqual(sent(h).at(-1),{action:'cursor',cursorSize:150,cursorStyle:1});
});
test('cursor UI never overrides a value the user just changed',()=>{
 const h=harness();h.run("tvReader={cursorAvailable:true,cursorSize:100,cursorStyle:0};cursorPrefs={size:200,style:2};cursorDirty=Date.now()+1000;cursorUI()");
 assert.equal(h.run("cursorPrefs.size"),200);assert.equal(h.run("cursorPrefs.style"),2);
});
function states(h,state){h.run("tvReader="+state+";controlLayout()");return JSON.parse(h.run("JSON.stringify(['tvPrev','tvNext','btnLeft','btnRight'].map(id=>$(id).disabled))"))}
test('what does not apply is dimmed in place, per the edited table',()=>{
 assert.deepEqual(states(harness(),"{active:true,page:0,pageCount:5}"),[true,false,true,false]);
 assert.deepEqual(states(harness(),"{active:true,page:4,pageCount:5}"),[false,true,false,true]);
 assert.deepEqual(states(harness(),"{active:false,browser:true,browserCanBack:true,browserCanForward:false}"),[false,true,false,false]);
 assert.deepEqual(states(harness(),"{active:false}"),[true,true,true,true]);
 const h=harness();h.run("tvReader={active:true,page:1,pageCount:5};controlLayout()");assert.equal(h.run("$('tvPageLabel').textContent"),'2 / 5');
 h.run("tvReader={active:false,browser:true};controlLayout()");assert.equal(h.run("$('tvPageLabel').textContent"),'');
});
test('arrows and touchpad swap with the context: arrows only in app navigation, touchpad everywhere else',()=>{
 for(const [state,arrowsOff,padOff] of [["{active:true,page:0,pageCount:5}",true,false],["{active:false,browser:true}",true,false],["{active:false}",false,true]]){
  const h=harness();h.run("globalThis.keys=[{},{}];document.querySelectorAll=s=>s==='[data-direction]'?keys:[];globalThis.pad=[];$('touchpad').classList.toggle=(n,on)=>pad.push(on);tvReader="+state+";controlLayout()");
  assert.equal(h.run('keys[0].disabled'),arrowsOff,state);assert.equal(h.run('pad[0]'),padOff,state);
 }
});
function sliderLog(h,state){h.run("globalThis.log=[];for(const id of ['zoomSide','scrollSide'])$(id).classList.toggle=(name,on)=>log.push(id+':'+name+':'+on)");h.run("tvReader="+state+";controlLayout()");return JSON.parse(h.run('JSON.stringify(log)'))}
test('sliders follow their switch: on by default in the reader and browser, off in app navigation',()=>{
 assert.deepEqual(sliderLog(harness(),"{active:true,page:0,pageCount:5,readerScrollable:true}"),['zoomSide:disabled:false','scrollSide:disabled:false']);
 assert.deepEqual(sliderLog(harness(),"{active:true,page:0,pageCount:5,readerScrollable:false}"),['zoomSide:disabled:false','scrollSide:disabled:false']);
 assert.deepEqual(sliderLog(harness(),"{active:false,browser:true,browserScrollable:false}"),['zoomSide:disabled:false','scrollSide:disabled:false']);
 assert.deepEqual(sliderLog(harness(),"{active:false}"),['zoomSide:disabled:true','scrollSide:disabled:true']);
});
test('reader scroll slider sets the vertical position, top to bottom, only when the page is enlarged',()=>{
 const h=harness();h.run("tvReader={active:true,page:0,pageCount:5,readerScrollable:false};$('scrollTrack').onpointerdown({pointerId:1,clientY:150,preventDefault(){}})");assert.equal(sent(h).length,0);
 h.run("tvReader={active:true,page:0,pageCount:5,readerScrollable:true};$('scrollTrack').onpointerdown({pointerId:1,clientY:150,preventDefault(){}})");
 assert.deepEqual(sent(h),[{action:'reader-scroll-to',dy:.5}]);
});
test('zoom slider grows from the bottom, from 100% to 300%',()=>{
 const h=harness();h.run("tvReader={active:true,page:0,pageCount:5,zoom:1,minZoom:1,maxZoom:3};$('tvZoom').min=1;$('tvZoom').max=3;$('zoomTrack').onpointerdown({pointerId:1,clientY:17,preventDefault(){}})");
 assert.deepEqual(sent(h)[0],{action:'reader-zoom',zoom:3});assert.equal(h.run("$('zoomThumb').style.top"),'calc((100% - 34px)*0)');assert.equal(h.run("$('zoomLabel').textContent"),'300%');
 const low=harness();low.run("tvReader={active:true,page:0,pageCount:5,zoom:2,minZoom:1,maxZoom:3};$('tvZoom').min=1;$('tvZoom').max=3;$('zoomTrack').onpointerdown({pointerId:1,clientY:283,preventDefault(){}})");
 assert.deepEqual(sent(low)[0],{action:'reader-zoom',zoom:1});assert.equal(low.run("$('zoomLabel').textContent"),'100%');
 const nav=harness();nav.run("tvReader={active:false};$('zoomTrack').onpointerdown({pointerId:1,clientY:17,preventDefault(){}})");assert.equal(sent(nav).length,0);
});
test('the bottom bar folds in every tab',()=>{
 for(const name of ['library','reader','control','files']){const h=harness();h.run("globalThis.log=[];document.body.classList.toggle=(n,on)=>log.push(n+':'+on);tab('"+name+"')");assert.ok(JSON.parse(h.run('JSON.stringify(log)')).includes('tabsFolded:true'),name)}
 assert.doesNotMatch(html,/(^|\n)#navToggle\{display:none\}/);assert.doesNotMatch(html,/body\.controlMode[^{]*#navToggle\{display:none/);
});
test('the TV state is read every 300 ms on the Controle tab, so the sliders follow the page, and every 2 s elsewhere so the theme follows the TV',()=>{
 assert.match(script,/section==='control'\|\|ticks%7===0\)refreshReaderState\(\)\},300\)/);
});
test('in app navigation the scroll slider and the touchpad send nothing',()=>{
 const h=harness();h.run("tvReader={active:false,browser:false}");
 h.run("$('scrollTrack').onpointerdown({pointerId:1,clientY:50,preventDefault(){}});$('scrollTrack').onpointermove({clientY:150});$('scrollTrack').onpointerup()");
 pointer(h,'down',1,20,20);pointer(h,'move',1,120,20);pointer(h,'up',1,120,20);h.run('flushPad()');
 assert.equal(sent(h).length,0);
});
test('Inverter mirrors only the side of the thumb column and never the commands',()=>{
 const h=harness();h.run("globalThis.log=[];$('control').classList.toggle=(name,on)=>log.push(name+':'+on)");
 h.run("$('mirrorSide').onclick()");assert.equal(h.run('mirrored'),true);h.run("$('mirrorSide').onclick()");assert.equal(h.run('mirrored'),false);
 assert.deepEqual(JSON.parse(h.run('JSON.stringify(log)')),['mirrored:true','mirrored:false']);assert.equal(sent(h).length,0);
});
test('the phone always shows the TV theme, and choosing one here changes the TV',async()=>{
 const h=harness();
 assert.equal(h.run("document.body.dataset.theme"),'amber');
 h.run("tvReader={theme:'Prime'};applyTheme(tvReader.theme)");assert.equal(h.run("document.body.dataset.theme"),'prime');
 h.run("chooseTheme('Max')");assert.equal(h.run("document.body.dataset.theme"),'max');
 assert.deepEqual(sent(h),[{action:'theme',text:'Max'}]);
 // A poll that still carries the old theme must not undo the choice before the TV has applied it.
 h.run("themeHold=Date.now()+4000");h.run("globalThis.polled=false");
 assert.ok(html.includes("Date.now()>themeHold)applyTheme(tvReader.theme)"));
 assert.equal(h.run("THEMES.map(t=>t[0]).join()"),'Amber,Flix,Prime,Max,Cinema');
});
test('settings opens from the gear in Arquivos only and keeps Arquivos highlighted',()=>{
 const h=harness();h.run("globalThis.log=[];$('gear').classList.toggle=(n,on)=>log.push('gear:'+n+':'+on);tab('files')");
 assert.ok(JSON.parse(h.run('JSON.stringify(log)')).includes('gear:hidden:false'));
 h.run("log.length=0;tab('library')");assert.ok(JSON.parse(h.run('JSON.stringify(log)')).includes('gear:hidden:true'));
 assert.match(html,/id="gear"[^>]*aria-label="Configurações"/);assert.match(html,/\$\('gear'\)\.onclick=\(\)=>tab\('settings'\)/);
});
test('themes change colors only in Controle: no layout rule targets the control while it is open',()=>{
 const css=html.slice(html.indexOf('/* ===== Temas do painel'),html.indexOf('</style>'));
 assert.doesNotMatch(css,/\.completeGrid|\.dpad|#arrows|#touchpad|#clickButtons|#sliderColumn|\.vslider|\.thumb|\.track|\.bottomActions/);
 for(const line of css.split('\n').filter(l=>/^body\[data-theme=\w+\][^{]*\{/.test(l)&&/(nav|header|main|#navTabs|#books|\.continue|\.cover|\.readerTools)/.test(l.split('{')[0])))
  assert.ok(/:not\(\.controlMode\)/.test(line.split('{')[0])||/\.continue|#books|\.cover|\.readerTools|\.menuOpen/.test(line.split('{')[0]),line.slice(0,80));
});

test('the phone no longer sets a theme-color, so the iPhone bars follow the page instead of a stale tint',()=>{
 assert.ok(!html.includes('theme-color'));assert.ok(!html.includes('themeColor'));
 const h=harness();h.run("applyTheme('Prime')");assert.equal(h.run("document.body.dataset.theme"),'prime');
});
test('images load through a small queue, retry with growing waits, can be reloaded by a tap and are kept for the next render',async()=>{
 const h=harness(),tick=()=>new Promise(resolve=>setImmediate(resolve));
 h.run("globalThis.calls=[];globalThis.gate=[];globalThis.timers=[];setTimeout=fn=>{timers.push(fn);return 1};clearTimeout=()=>{};URL.createObjectURL=()=>'blob:ok';fetch=url=>{if(!url.includes('/cover/'))return new Promise(()=>{});calls.push(url);return new Promise(resolve=>gate.push(ok=>resolve(ok?{ok:true,blob:async()=>({})}:{ok:false})))}");
 h.run("globalThis.imgs=[1,2,3,4,5].map(i=>{const img={dataset:{title:'T'+i},addEventListener(type,fn){this.click=fn},removeAttribute(){}};fillImage(img,'/cover/b'+i);return img})");
 await tick();assert.equal(h.run('calls.length'),3,'three local images at a time');
 h.run("gate.splice(0).forEach(g=>g(true))");await tick();await tick();assert.equal(h.run('calls.length'),5);
 assert.equal(h.run("imgs[0].src"),'blob:ok');
 h.run("gate.splice(0).forEach(g=>g(true))");await tick();await tick();
 // the same cover on the next render needs no request
 h.run("globalThis.again={dataset:{},addEventListener(){}};fillImage(again,'/cover/b1')");assert.equal(h.run('again.src'),'blob:ok');assert.equal(h.run('calls.length'),5);
 // refused six times: the last wait ends in a tap-to-reload message, and the tap asks again
 h.run("globalThis.bad={dataset:{title:'Capa'},addEventListener(type,fn){this.click=fn},removeAttribute(){}};fillImage(bad,'/cover/bad')");
 for(let i=0;i<6;i++){await tick();h.run("gate.splice(0).forEach(g=>g(false))");await tick();await tick();h.run("timers.splice(0).forEach(fn=>fn())")}
 await tick();assert.match(h.run('bad.alt'),/toque para recarregar/);
 const before=h.run('calls.length');let stopped=false;h.run("bad.click({stopImmediatePropagation(){globalThis.stopped=true},preventDefault(){}})");await tick();
 assert.equal(h.run('stopped'),true);assert.equal(h.run('calls.length'),before+1);
});
test('source covers load one at a time and never stay in the local lane',()=>{
 assert.match(script,/net:\{queue:\[\],active:0,max:1\}/);assert.match(script,/fillImage\(img,'\/source-cover\/'\+ref,key,'net'\)/);
});
test('Novidades and Catálogo show six titles and a Ver mais card, and Ver mais lists the sample then asks the TV for more',async()=>{
 const h=harness(),titles=n=>Array.from({length:n},(_,i)=>({ref:'l'+i,title:'T'+i}));
 const reply=data=>h.run(`fetch=async url=>{(globalThis.urls??=[]).push(url);return {ok:true,json:async()=>(${JSON.stringify(data)})}}`);
 reply({source:'S',latest:titles(10),catalog:titles(5).map(t=>({...t,ref:'c'+t.ref.slice(1)})),latestMore:false,catalogMore:false});
 await h.run('loadSources(true)');
 assert.equal(h.run("$('latestRow').children.length"),7,'six titles plus the Ver mais card');
 assert.equal(h.run("$('catalogRow').children.length"),5,'a short row with nothing more has no card');
 reply({source:'S',latest:titles(3),catalog:titles(5),latestMore:true,catalogMore:false});
 await h.run('loadSources(true)');assert.equal(h.run("$('latestRow').children.length"),4,'the source says there is another page');
 reply({source:'S',latest:titles(10),catalog:[],latestMore:true,catalogMore:false});await h.run('loadSources(true)');
 h.run("openMore('l')");assert.equal(h.run('libView'),'more');assert.equal(h.run('moreItems.length'),10);assert.equal(h.run('moreHas'),true);
 assert.equal(h.run("$('moreTitle').textContent"),'Novidades');
 reply({source:'S',items:[{ref:'l10',title:'T10'},{ref:'l9',title:'T9'}],more:false});
 await h.run('loadMore()');
 assert.ok(h.run('urls').some(u=>u.endsWith('/token/home-more/l/10')));
 assert.equal(h.run('moreItems.length'),11,'a title already listed is not repeated');assert.equal(h.run('moreHas'),false);
 assert.equal(h.run("$('moreNote').textContent"),'Não há mais títulos.');
 // the TV changed source while Ver mais was open: back to the library with a notice
 h.run("moreHas=true;libView='more'");reply({source:'Outra',items:[],more:false});await h.run('loadMore()');
 assert.equal(h.run('libView'),'home');
});
test('the round shortcuts follow the TV: chapters and automatic in the reader, dark mode in the browser',()=>{
 const h=harness(),state=()=>JSON.parse(h.run("JSON.stringify(['quickPrev','quickAuto','quickNext','quickDark'].map(id=>$(id).disabled))"));
 h.run("tvReader={active:false,browser:false};controlLayout()");assert.deepEqual(state(),[true,true,true,true]);
 h.run("tvReader={active:true,page:0,pageCount:3,readerMenu:{chapters:true,automatic:true}};controlLayout()");assert.deepEqual(state(),[false,false,false,true]);
 h.run("tvReader={active:true,page:0,pageCount:3,readerMenu:{chapters:false}};controlLayout()");assert.deepEqual(state(),[true,false,true,true]);
 h.run("tvReader={active:false,browser:true,browserDark:true};controlLayout()");assert.deepEqual(state(),[true,true,true,false]);
 h.run("$('quickDark').onclick()");assert.equal(JSON.parse(h.requests.at(-1).options.body).action,'browser-dark');
 h.run("tvReader={active:true,page:0,pageCount:3,readerMenu:{chapters:true}};$('quickNext').onclick()");assert.deepEqual(JSON.parse(h.requests.at(-1).options.body),{action:'reader-option',text:'chapter-next'});
});
test('a pressed key lights in the color of the theme, and only keys of the Controle',()=>{
 assert.match(html,/#control button\.lit,#control \.quick\.on\{background:var\(--accent\);color:var\(--ax\)/);
 assert.match(html,/body\{user-select:none;-webkit-user-select:none;-webkit-touch-callout:none\}input,textarea,#pageText\{user-select:text/);
 assert.doesNotMatch(html,/height:calc\(25px \+ env\(safe-area-inset-bottom\)\)/);
});
test('the phone offers the sounds of the TV and its own, with five choices each',()=>{
 const h=harness();h.run("tvReader={soundsOn:false,soundsVolume:30,soundsStyle:2};section='settings';soundsUI()");
 assert.equal(h.run("$('tvSoundOn').checked"),false);assert.equal(h.run("$('tvSoundLabel').textContent"),'30%');assert.equal(h.run("$('tvSoundStyles').children.length"),5);assert.equal(h.run("$('tvSoundStyles').children[2].className"),'on');
 h.run("$('tvSoundStyles').children[4].onclick()");assert.deepEqual(JSON.parse(h.requests.at(-1).options.body),{action:'sounds',soundsOn:false,soundsVolume:30,soundsStyle:4,delta:1});
 assert.equal(h.run('PHONE_SOUNDS.length'),5);assert.equal(h.run("$('phoneSoundStyles').children.length"),5);
 h.run("phoneBeep(true);phoneVibrate();phoneHaptic()");
});
test('a preset without paper keeps the temperature and tint of the edition',()=>{
 const h=harness(),values=h.run("(()=>{const v=adjZero();v[0]=40;v[1]=-10;v[2]=50;const own=adjEncode(v);const now=adjZero();now[0]=7;now[1]=8;return JSON.stringify([adjPresetValues({values:own,paper:true},now).slice(0,3),adjPresetValues({values:own,paper:false},now).slice(0,3),adjPresetValues({values:'x',paper:true},now).slice(0,3)])})()");
 assert.deepEqual(JSON.parse(values),[[40,-10,50],[7,8,50],[7,8,0]]);
});
test('with the adjustments open the page does not follow the finger',()=>{
 assert.match(script,/addEventListener\('touchstart',e=>\{if\(adj\.open\)\{if\(adj\.picking&&e\.touches\.length===1\)startOne/);
 assert.match(script,/addEventListener\('touchmove',e=>\{if\(e\.cancelable\)e\.preventDefault\(\);if\(adj\.open\)\{/);
 assert.match(script,/gesturestart',e=>\{if\(adj\.open/);
});
test('a live preview of a drawing grows with the action and the final patch covers it all',()=>{
 const h=harness();assert.equal(h.run("JSON.stringify(drawRectJoin(null,{x:1,y:2,w:3,h:4}))"),'{"x":1,"y":2,"w":3,"h":4}');assert.equal(h.run("JSON.stringify(drawRectJoin({x:1,y:2,w:3,h:4},{x:0,y:5,w:2,h:9}))"),'{"x":0,"y":2,"w":4,"h":12}');
 h.run("syncOn=true;current={id:'c'};tvReader={active:true,comicId:'c'};ed.layers=[{}]");assert.equal(h.run("ed.stroke={live:true,tool:{}};drawLiveWanted()"),false);assert.equal(h.run("ed.stroke={live:true,tool:{taperAmount:.5}};drawLiveWanted()"),true);assert.equal(h.run("ed.stroke={direct:true,tool:{}};drawLiveWanted()"),true);assert.equal(h.run("ed.stroke=null;ed.float={};ed.modal={kind:'filter'};drawLiveWanted()"),false);assert.equal(h.run("ed.modal={kind:'transform'};drawLiveWanted()"),true);
});
test('a layer folder hides its layers and takes its opacity along',()=>{
 const h=harness();h.run("ed.folders=[{id:9,name:'P',visible:false,opacity:.5,open:true}];globalThis.inside={visible:true,folder:9};globalThis.out={visible:true,folder:null}");
 assert.equal(h.run('drawShown(inside)'),false);assert.equal(h.run('drawShown(out)'),true);h.run("ed.folders[0].visible=true");assert.equal(h.run('drawShown(inside)'),true);
 h.run("const make=(n,folder)=>({name:n,folder});ed.layers=[make('a',9),make('b',null),make('c',null)];ed.active=2;drawFolderJoin(9)");assert.equal(h.run("ed.layers.map(l=>l.name).join()"),'a,c,b');assert.equal(h.run('ed.active'),1);
 h.run("ed.layers.forEach(l=>{l.folder=null});drawFolderTidy()");assert.equal(h.run('ed.folders.length'),0);
});


test('TV phone commands seek the actual TV timeline and never request video bytes',async()=>{
 const h=harness();
 h.run("tvPlay={active:true,positionMs:45000,durationMs:120000}");
 await h.run("$('tvBack30').onclick()");
 let sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(sent.action,'tv-seek');assert.equal(sent.positionMs,15000);
 await h.run("$('tvForward30').onclick()");
 sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(sent.positionMs,75000);
 await h.run("$('tvPause').onclick()");
 sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(sent.action,'tv-pause');
 assert.ok(!h.requests.some(r=>/torrent:|magnet:|\.mp4/.test(r.url)));
});

test('TV addon confirmation consumes the reviewed nonce and clears the private URL',async()=>{
 const h=harness();
 h.run("tvPendingNonce='reviewed-nonce';tvAllowPreview=true;$('tvAddonLink').value='https://example.org/secret/manifest.json'");
 await h.run("$('tvAddonInstall').onclick()");
 const sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(sent.action,'tv-addon-install');assert.equal(sent.id,'reviewed-nonce');assert.equal(sent.text,undefined);
 assert.equal(h.elements.get('tvAddonLink').value,'');
 assert.equal(h.run('tvAllowPreview'),false);assert.equal(h.run('tvPendingNonce'),'');
});

test('editing the TV addon URL invalidates the local confirmation',async()=>{
 const h=harness();
 h.run("tvPendingNonce='old-nonce';tvAllowPreview=true;$('tvAddonLink').oninput()");
 assert.equal(h.run('tvPendingNonce'),'');assert.equal(h.run('tvAllowPreview'),false);
 const before=h.requests.filter(r=>r.url.endsWith('/command')).length;
 await h.run("$('tvAddonInstall').onclick()");
 assert.equal(h.requests.filter(r=>r.url.endsWith('/command')).length,before);
 assert.equal(h.elements.get('tvAddonInstall').disabled,true);
});

test('TV subtitles: choosing a track, nudging the sync and sending a file from the phone',async()=>{
 const h=harness();
 h.run("tvPlay={active:true,playerReady:true,canPlay:true,subtitleDelayMs:0,subtitles:[{id:'off',label:'Desligadas',selected:false},{id:'x:0',label:'Português (Brasil) · OpenSubtitles',selected:true}],audio:[{id:'a:1',label:'Japonês · AAC',selected:true},{id:'a:2',label:'Inglês · AC-3',selected:false}]};tvTracksUI()");
 assert.equal(h.elements.get('tvSubtitleList').children.length,2);
 await h.elements.get('tvSubtitleList').children[0].onclick();
 let sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(sent.action,'tv-subtitle');assert.equal(sent.text,'off');
 await h.elements.get('tvAudioList').children[1].onclick();
 sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(sent.action,'tv-audio');assert.equal(sent.text,'a:2');
 await h.run("$('tvDelayPlus').onclick()");
 sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(sent.action,'tv-subtitle-delay');assert.equal(sent.delta,5);
 assert.equal(h.elements.get('tvDelayLabel').textContent,'Sincronia +0,5 s');
 await h.run("$('tvDelayZero').onclick()");
 sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(sent.action,'tv-subtitle-delay');assert.equal(sent.delta,0);
 await h.run("$('tvSubtitleSearch').onclick()");
 sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(sent.action,'tv-subtitle-search');
 h.run("tvPlay.subtitleNote='2 legendas novas encontradas.';tvTracksUI()");
 assert.equal(h.elements.get('tvSubtitleStatus').textContent,'2 legendas novas encontradas.');
 h.run("$('tvSubtitleFile').files=[{name:'episodio.pt-BR.ass',size:2048}]");
 await h.run("$('tvSubtitleFile').onchange()");
 const upload=h.requests.find(r=>r.url.includes('/tv-subtitle?name='));
 assert.ok(upload,'the file goes to the TV subtitle endpoint');assert.match(upload.url,/episodio\.pt-BR\.ass/);
 assert.equal(upload.options.headers['Content-Type'],'application/octet-stream');
});

test('TV subtitles are hidden while an external player has the video',()=>{
 const h=harness();
 h.run("$('tvTracks')");let hidden;h.elements.get('tvTracks').classList.toggle=(name,value)=>{if(name==='hidden')hidden=value};
 h.run("tvPlay={active:true,playerReady:true,external:'VLC',subtitles:[]};tvTracksUI()");
 assert.equal(hidden,true);
});

test('video settings on the phone send the whole preference set to the TV',async()=>{
 const h=harness();h.run("globalThis.setTimeout=f=>{f();return 1}");
 h.run("tvVideo={player:'internal',subtitles:{auto:true,preferBrazil:true,originalAssStyle:true,sizePercent:100,color:0,background:1,edge:1,bottomPercent:8}};tvPlayers=['internal','ask','vlc'];tvPrefsUI()");
 assert.equal(h.elements.get('tvPlayerSelect').children.length,3);
 await h.elements.get('tvSubLang').children[1].onclick();
 let sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(sent.action,'tv-prefs');assert.equal(JSON.parse(sent.text).subtitles.preferBrazil,false);
 h.run("$('tvPlayerSelect').value='vlc'");await h.run("$('tvPlayerSelect').onchange()");
 sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(JSON.parse(sent.text).player,'vlc');assert.equal(JSON.parse(sent.text).subtitles.preferBrazil,false);
 assert.equal(h.elements.get('tvSubRemember').checked,true,'a TV without the new field keeps the subtitle memory on');
 assert.equal(h.elements.get('tvDecoder').children.length,3);
 await h.elements.get('tvDecoder').children[2].onclick();
 sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(JSON.parse(sent.text).decoder,'software');assert.equal(JSON.parse(sent.text).player,'vlc');
 h.run("$('tvSubRemember').checked=false");await h.run("$('tvSubRemember').onchange()");
 sent=JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body);
 assert.equal(JSON.parse(sent.text).subtitles.remember,false);assert.equal(JSON.parse(sent.text).player,'vlc');
});

test('the source filter on the phone changes the TV choice for every title, and the next source is one tap',async()=>{
 const h=harness();h.run("globalThis.setTimeout=f=>{f();return 1}");
 h.run("tvVideo={player:'internal',subtitles:{},sourceAddon:'',sourceQuality:'',sourceAudio:''};tvAddonNames=['Torrentio','Brazuca'];tvFilterUI()");
 assert.deepEqual(h.elements.get('tvFilterAddon').children.map(b=>b.textContent),['Todas','Torrentio','Brazuca']);
 assert.deepEqual(h.elements.get('tvFilterAudio').children.map(b=>b.textContent),['Todas','Dublado','Dual áudio','Inglês']);
 await h.elements.get('tvFilterAudio').children[1].onclick();
 let sent=sent2(h);assert.equal(sent.action,'tv-prefs');assert.equal(JSON.parse(sent.text).sourceAudio,'Dublado');
 await h.elements.get('tvFilterQuality').children[0].onclick();
 sent=sent2(h);assert.equal(JSON.parse(sent.text).sourceQuality,'');assert.equal(JSON.parse(sent.text).sourceAudio,'Dublado');
 h.run("$('tvPublicTrackers').checked=false");await h.run("$('tvPublicTrackers').onchange()");
 assert.equal(JSON.parse(sent2(h).text).publicTrackers,false);
 await h.run("$('tvNextSource').onclick()");assert.equal(sent2(h).action,'tv-next');
});
function sent2(h){return JSON.parse(h.requests.filter(r=>r.url.endsWith('/command')).at(-1).options.body)}

test('a list of add-ons goes to the TV, adult ones start unchecked and only the checked ones are installed',async()=>{
 const h=harness();
 h.run("$('tvListText').value='https://a/manifest.json\\nhttps://b/manifest.json\\nhttps://c/manifest.json'");
 await h.run("$('tvListCheck').onclick()");
 const post=h.requests.find(r=>r.url.endsWith('/tv-addon-list'));assert.ok(post);assert.match(post.options.body,/https:\/\/b\/manifest\.json/);
 h.run("tvListUI({nonce:'n1',busy:false,message:'',items:[{n:0,name:'A',status:'Pronto para instalar',installable:true,adult:false,configure:'',result:''},{n:1,name:'Adulto',status:'Pronto para instalar · conteúdo adulto',installable:true,adult:true,configure:'',result:''},{n:2,name:'C',status:'Precisa de configuração',installable:false,adult:false,configure:'https://c/configure',result:''}]})");
 const rows=h.elements.get('tvListItems').children;assert.equal(rows.length,3);
 assert.equal(rows[0].children[0].checked,true);assert.equal(rows[1].children[0].checked,false);
 assert.equal(h.elements.get('tvListInstall').textContent,'Instalar 1 complemento');
 await h.run("$('tvListInstall').onclick()");
 const sent=sent2(h);assert.equal(sent.action,'tv-addon-batch');assert.equal(sent.id,'n1');assert.equal(sent.text,'0');
});

test('TV appearance from the phone sends only what changed, and reset asks first',async()=>{
 const h=harness();
 h.run("tvLook={orientation:'Landscape',orientations:[{id:'Landscape',label:'Paisagem'},{id:'Portrait90',label:'Retrato 90°'}],carouselAutoplay:true,carouselMotion:true,carouselImagePreset:'Vintage',presets:[{id:'Original',label:'Desligado'},{id:'Vintage',label:'HQ antiga'}],carouselImageIntensity:2,coverBackground:false,textPercent:100,highContrast:false};tvLookUI()");
 assert.equal(h.elements.get('tvOrientation').children.length,2);
 await h.elements.get('tvOrientation').children[1].onclick();
 assert.deepEqual(JSON.parse(sent2(h).text),{orientation:'Portrait90'});
 h.run("$('tvCarouselMotion').checked=false");await h.run("$('tvCarouselMotion').onchange()");
 assert.deepEqual(JSON.parse(sent2(h).text),{carouselMotion:false});
 const before=h.requests.length;h.run("$('tvAppearanceReset').onclick()");assert.equal(h.requests.length,before,'the first tap only asks');
 await h.run("$('tvResetYes').onclick()");assert.equal(sent2(h).text,'reset');
 h.run("$('phoneNeon').checked=false;localStorage={setItem(){}};$('phoneNeon').onchange()");assert.equal(h.run('phoneSound.neon'),false);
 assert.match(html,/#control button\.tap\{border-color:var\(--accent\)/);
});


test('preparation artwork switch preserves the player, subtitles and source filters',async()=>{
 const h=harness();h.run("globalThis.setTimeout=f=>{f();return 1}");
 h.run("tvVideo={player:'vlc',publicTrackers:false,sourceQuality:'1080p',sourceAudio:'Dublado',subtitles:{auto:false}};tvPlayers=['internal','vlc'];tvPrefsUI()");
 assert.equal(h.elements.get('tvPreparationArtwork').checked,true);
 h.run("$('tvPreparationArtwork').checked=false");await h.run("$('tvPreparationArtwork').onchange()");
 const prefs=JSON.parse(sent2(h).text);
 assert.equal(prefs.preparationArtwork,false);assert.equal(prefs.player,'vlc');
 assert.equal(prefs.publicTrackers,false);assert.equal(prefs.sourceQuality,'1080p');
 assert.equal(prefs.sourceAudio,'Dublado');assert.equal(prefs.subtitles.auto,false);
});
