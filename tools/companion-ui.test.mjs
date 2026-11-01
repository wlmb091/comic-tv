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
test('TV mirror and capture requests are removed',()=>{assert.doesNotMatch(html,/\/screen|refreshScreen|id="screen"/)});
test('reader arrows pan without changing page or sending remote keys',async()=>{
  const h=harness(); await h.run("tvReader={active:true,page:4,pageCount:10,zoom:2,minZoom:1,maxZoom:4}; directional('right')");
  const cmd=h.requests.find(r=>r.url.endsWith('/command')); assert.deepEqual(JSON.parse(cmd.options.body),{action:'reader-pan',x:.12,y:0});
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
test('reader is minimal and pinch stays available',()=>{assert.doesNotMatch(html,/id="(?:openTV|fit|zoom|pageRange)"/);assert.match(html,/touch-action:pan-x pan-y pinch-zoom/);assert.match(html,/100dvh/);assert.match(html,/id="tvZoom"/);assert.match(html,/id="tvPage"/)});



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
test('mode tabs come first, then pad, thumb column, gesture sliders and the Inverter/Girar/Teclado row',()=>{
 const order=['id="modePad"','id="touchpad"','id="pageSide"','id="sensitivityExtras"','id="tvPage"','id="tvZoom"','id="sensitivity"','id="mirrorSide"','id="rotateScreen"','id="showKeyboard"'].map(mark=>html.indexOf(mark));assert.deepEqual(order,[...order].sort((a,b)=>a-b));
 assert.doesNotMatch(html,/Controle da TV/);
});
const flush=h=>h.run('(async()=>{for(let i=0;i<30;i++)await Promise.resolve()})()');
test('each TV context opens in its mode and a manual choice holds for that context',()=>{
 const h=harness();h.run("tvReader={active:false};renderTVState()");assert.equal(h.run('arrowsShown'),true);
 h.run("tvReader={active:true,page:0,pageCount:3,zoom:1,minZoom:1,maxZoom:3};renderTVState()");assert.equal(h.run('arrowsShown'),false);
 h.run("tvReader={active:false,browser:true};renderTVState()");assert.equal(h.run('arrowsShown'),false);
 h.run("tvReader={active:false};renderTVState();$('modePad').onclick()");assert.equal(h.run('arrowsShown'),false);
 h.run("tvReader={active:true,page:0,pageCount:3,zoom:1,minZoom:1,maxZoom:3};renderTVState();tvReader={active:false};renderTVState()");assert.equal(h.run('arrowsShown'),false);
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
 h.run("fetch=online");await h.run("refreshReaderState()");assert.equal(h.run("$('notice').textContent"),'');assert.match(h.run("$('connected').textContent"),/TV conectada/);
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
 assert.deepEqual(sent(h),[{action:'reader-pan',x:.4,y:-.4},{action:'pointer',dx:.1,dy:-.1}]);
});
test('Menu is explicit app Home, not an Android menu key',()=>{assert.match(html,/data-command="home"/);assert.doesNotMatch(html,/data-command="menu"/)});
test('keyboard viewport follows visible top without affecting reader pinch',()=>{
 const h=harness(),values={};h.run("section='control';window.visualViewport={scale:1,height:390,offsetTop:120}");h.run('document.documentElement.style.setProperty=(key,value)=>{globalThis.v=globalThis.v||{};v[key]=value};viewport()');
 assert.equal(h.run("v['--viewport-top']"),'120px');assert.equal(h.run("v['--viewport-height']"),'390px');
});

test('zero axis stays zero and a pinch can resume a one-finger pan',async()=>{
 const h=harness();h.run("tvReader={active:true,zoom:1,minZoom:1,maxZoom:3}");pointer(h,'down',1,20,20);pointer(h,'down',2,120,20);pointer(h,'move',2,220,20);h.run('flushPad()');await h.run('(async()=>{for(let i=0;i<20;i++)await Promise.resolve()})()');pointer(h,'up',2,220,20);pointer(h,'move',1,50,20);h.run('flushPad()');await h.run('(async()=>{for(let i=0;i<20;i++)await Promise.resolve()})()');
 const pan=sent(h).find(c=>c.action==='reader-pan');assert.ok(pan);assert.equal(pan.y,0);assert.equal(pan.x,.1);
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
test('browser arrows and OK drive the page, leaving the cursor to the touchpad',()=>{
 const h=harness();h.run("tvReader={active:false,browser:true};sensitivities.pointer=2;directional('right');$('padOK').onclick()");assert.deepEqual(sent(h),[{action:'browser-right'},{action:'browser-ok'}]);
});
test('opening the keyboard in the browser keeps the touchpad',()=>{
 const h=harness();const pressed=[];h.elements.set('modeArrows',{...h.run("$('modeArrows')"),setAttribute(name,value){pressed.push(value)}});
 h.run("tvReader={active:false,browser:true};$('showKeyboard').onclick()");assert.deepEqual(pressed,[]);
});
test('opening the keyboard switches to arrows and a sent text leaves them ready',async()=>{
 const h=harness();const pressed=[];h.elements.set('modeArrows',{...h.run("$('modeArrows')"),setAttribute(name,value){pressed.push(value)}});
 h.run("$('showKeyboard').onclick()");assert.deepEqual(pressed,['true']);
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
function layoutLog(h,state){h.run("globalThis.log=[];for(const id of ['sideColumn','pageSide','scrollSide','control'])$(id).classList.toggle=(name,on)=>log.push(id+':'+name+':'+on)");h.run(state+";controlLayout()");return JSON.parse(h.run('JSON.stringify(log)'))}
test('thumb column: Próxima/Anterior in the reader, Rolagem da Página elsewhere, hidden for a page that cannot scroll',()=>{
 assert.deepEqual(layoutLog(harness(),"tvReader={active:true,page:1,pageCount:5}"),['sideColumn:hidden:false','control:noSide:false','pageSide:hidden:false','scrollSide:hidden:true']);
 assert.deepEqual(layoutLog(harness(),"tvReader={active:false,browser:true,browserScrollable:true}"),['sideColumn:hidden:false','control:noSide:false','pageSide:hidden:true','scrollSide:hidden:false']);
 assert.deepEqual(layoutLog(harness(),"tvReader={active:false,browser:true,browserScrollable:false}"),['sideColumn:hidden:true','control:noSide:true','pageSide:hidden:true','scrollSide:hidden:true']);
 assert.deepEqual(layoutLog(harness(),"tvReader={active:false,browser:false}"),['sideColumn:hidden:false','control:noSide:false','pageSide:hidden:true','scrollSide:hidden:false']);
});
test('outside a browser page the slider scrolls the TV screen by the distance dragged, with no jump on touch',()=>{
 const h=harness();h.run("tvReader={active:false,browser:false}");
 h.run("$('scrollTrack').onpointerdown({pointerId:1,clientY:50,preventDefault(){}})");
 assert.equal(sent(h).length,0);
 h.run("$('scrollTrack').onpointermove({clientY:150});$('scrollTrack').onpointerup()");
 const [scroll]=sent(h);assert.deepEqual(scroll,{action:'scroll',x:.5,y:.5,delta:600});
});
test('Inverter mirrors only the side of the thumb column and never the commands',()=>{
 const h=harness();h.run("globalThis.log=[];$('control').classList.toggle=(name,on)=>log.push(name+':'+on)");
 h.run("$('mirrorSide').onclick()");assert.equal(h.run('mirrored'),true);h.run("$('mirrorSide').onclick()");assert.equal(h.run('mirrored'),false);
 assert.deepEqual(JSON.parse(h.run('JSON.stringify(log)')),['mirrored:true','mirrored:false']);assert.equal(sent(h).length,0);
});
