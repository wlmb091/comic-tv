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
test('reader pinch zooms proportionally without pan or click',()=>{
 const h=harness();h.run("tvReader={active:true,zoom:1.5,minZoom:1,maxZoom:3};");
 pointer(h,'down',1,20,20);pointer(h,'down',2,120,20);pointer(h,'move',2,220,20);h.run('flushPad()');
 assert.deepEqual(sent(h),[{action:'reader-zoom',zoom:3}]);
 pointer(h,'up',2,220,20);pointer(h,'up',1,20,20);assert.equal(sent(h).length,1);
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
test('opening the keyboard switches to arrows and a sent text leaves them ready',async()=>{
 const h=harness();const pressed=[];h.elements.set('modeArrows',{...h.run("$('modeArrows')"),setAttribute(name,value){pressed.push(value)}});
 h.run("$('showKeyboard').onclick()");assert.deepEqual(pressed,['true']);
 h.run("$('keyboard').value='Batman';$('keyboardForm').onsubmit({preventDefault(){}})");
 await new Promise(resolve=>setImmediate(resolve));await new Promise(resolve=>setImmediate(resolve));
 assert.deepEqual(sent(h).filter(c=>c.action==='text'),[{action:'text',text:'Batman'}]);assert.equal(h.run("$('keyboard').value"),'');
});
