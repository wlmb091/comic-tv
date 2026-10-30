import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const html = readFileSync(new URL('../app/src/main/assets/companion/index.html', import.meta.url), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
function harness() {
  const elements = new Map(), requests = [];
  function element() { return { value:'', style:{setProperty(){}}, dataset:{}, children:[], classList:{toggle(){},add(){},remove(){}}, setAttribute(){}, append(...n){this.children.push(...n)}, replaceChildren(...n){this.children=n}, remove(){}, addEventListener(){}, setPointerCapture(){}, releasePointerCapture(){}, getBoundingClientRect(){return {width:300,height:300}}, files:[] }; }
  const context = vm.createContext({location:{pathname:'/token'},document:{hidden:false,body:element(),documentElement:element(),getElementById(id){if(!elements.has(id))elements.set(id,element());return elements.get(id)},querySelectorAll(){return []},createElement:element,addEventListener(){}},window:{innerHeight:800,addEventListener(){}},sessionStorage:{getItem(){return null},setItem(){}},fetch:async(url,options)=>{requests.push({url,options});return {ok:true,json:async()=>url.endsWith('/reader-state')?{active:false}:{books:[]}}},setTimeout(){return 1},clearTimeout(){},setInterval(){},clearInterval(){},scrollTo(){},AbortController,URL,console,Date,Math,Number,JSON,Promise});
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
