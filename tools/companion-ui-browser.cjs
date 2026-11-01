// Usage: NODE_PATH=<directory containing playwright> node tools/companion-ui-browser.cjs
const { chromium } = require('playwright');
const { readFileSync, mkdirSync, writeFileSync } = require('node:fs');
const assert = require('node:assert/strict');
const html=readFileSync('app/src/main/assets/companion/index.html','utf8');
const output='docs/result/companion-147';mkdirSync(output,{recursive:true});
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 const context=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
 const page=await context.newPage(),commands=[],errors=[];
 let active=true,browserPage=false;
 page.on('pageerror',e=>errors.push(e.message));
 await page.route('http://companion.test/**',async route=>{
  const url=new URL(route.request().url()),p=url.pathname;
  if(p==='/s/demo')return route.fulfill({contentType:'text/html',body:html});
  if(p.endsWith('/library'))return route.fulfill({json:{books:[{id:'one',title:'Órbita · Edição de teste',pages:25,current:9,lastRead:100,local:true}]}});
  if(p.endsWith('/reader-state'))return route.fulfill({json:{active,browser:browserPage,page:4,pageCount:25,zoom:2,minZoom:1,maxZoom:3}});
  if(p.includes('/cover/')||p.includes('/page/'))return route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="600" height="900"><rect width="600" height="900" fill="#183653"/><circle cx="400" cy="280" r="140" fill="#dcbf5b"/><text x="30" y="90" fill="white" font-size="38">COMIC TV · Fixture</text></svg>'});
  if(p.endsWith('/command'))commands.push(route.request().postDataJSON());
  return route.fulfill({body:'OK'});
 });
 await page.goto('http://companion.test/s/demo');
 await page.locator('#books .book').waitFor();
 await page.locator('[data-tab=control]').click();await page.locator('#tvPrev').waitFor({state:'visible'});
 const checks=[];
 async function bounds(name){await page.waitForFunction(()=>document.querySelector('nav').getBoundingClientRect().bottom<=innerHeight+1);const sizes=await page.evaluate(()=>{const r=document.querySelector('nav').getBoundingClientRect(),c=document.querySelector('#control').getBoundingClientRect();return {width:innerWidth,scrollWidth:document.documentElement.scrollWidth,height:innerHeight,navBottom:r.bottom,controlBottom:c.bottom,navTop:r.top}});assert.equal(sizes.width,sizes.scrollWidth);assert.ok(sizes.navBottom<=sizes.height+1);assert.ok(sizes.controlBottom<=sizes.navTop+1);checks.push({name,...sizes});}
 await bounds('portrait-control');await page.screenshot({path:output+'/portrait-control.png'});
 await page.locator('#tvNext').click();await page.waitForTimeout(150);
 assert.ok(commands.some(c=>c.action==='reader-turn'&&c.delta===1));assert.ok(!commands.some(c=>c.action==='right'));
 const pad=await page.locator('#touchpad').boundingBox();await page.mouse.move(pad.x+50,pad.y+100);await page.mouse.down();await page.mouse.move(pad.x+110,pad.y+130);await page.mouse.up();await page.waitForTimeout(150);
 // Trusted multi-touch reaches the real PointerEvent/capture path.
 const cdp=await context.newCDPSession(page);
 async function touch(type,points){await cdp.send('Input.dispatchTouchEvent',{type,touchPoints:points.map(([id,x,y])=>({id,x,y}))})}
 const pr=await page.locator('#touchpad').boundingBox(),px=pr.x+45,py=pr.y+70;
 await touch('touchStart',[[1,px,py]]);await touch('touchStart',[[1,px,py],[2,px+80,py]]);await touch('touchMove',[[1,px,py],[2,px+140,py]]);await touch('touchEnd',[]);await page.waitForTimeout(200);
 assert.ok(commands.some(c=>c.action==='reader-pinch'&&c.zoom>2));
 // A keyboard viewport may shrink AND pan independently of the layout viewport.
 await page.locator('#showKeyboard').click();await page.locator('#keyboard').fill('Batman');
 await page.evaluate(()=>{window.realViewport=window.visualViewport;Object.defineProperty(window,'visualViewport',{configurable:true,value:{height:380,offsetTop:130,scale:1}});viewport()});
 const kb=await page.locator('#keyboardForm').boundingBox(),shell=await page.locator('body').boundingBox();assert.equal(Math.round(shell.y),130);assert.ok(kb.y>=130&&kb.y+kb.height<=511);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),390);await page.screenshot({path:output+'/keyboard-viewport.png'});
 await page.locator('#sendText').click();await page.waitForTimeout(100);assert.ok(commands.some(c=>c.action==='text'&&c.text==='Batman'));
 assert.ok(await page.locator('#arrows').isVisible());assert.ok(!(await page.locator('#keyboardForm').isVisible()));
 await page.evaluate(()=>{Object.defineProperty(window,'visualViewport',{configurable:true,value:realViewport});viewport()});await bounds('keyboard-closed');
 const arrowsBox=await page.locator('#arrows').boundingBox(),rightBox=await page.locator('[data-direction=right]').boundingBox();assert.ok(rightBox.width>=arrowsBox.width/3-8);await page.screenshot({path:output+'/portrait-arrows.png'});
 await page.locator('[data-command=home]').click();await page.waitForTimeout(100);assert.ok(commands.some(c=>c.action==='home'));
 active=false;browserPage=true;await page.evaluate(()=>refreshReaderState());
 const br=await page.locator('#touchpad').boundingBox(),bx=br.x+45,by=br.y+70;
 await touch('touchStart',[[1,bx,by]]);await touch('touchStart',[[1,bx,by],[2,bx+80,by]]);await touch('touchMove',[[1,bx,by+30],[2,bx+80,by+30]]);await touch('touchEnd',[]);await page.waitForTimeout(200);assert.ok(commands.some(c=>c.action==='browser-scroll'));browserPage=false;
 active=false;await page.evaluate(()=>refreshReaderState());await page.locator('#scrollTrack').waitFor({state:'visible'});await page.locator('[data-direction=down]').evaluate(el=>el.dispatchEvent(new MouseEvent('click',{detail:0})));await page.waitForTimeout(100);assert.ok(commands.some(c=>c.action==='down'));
 active=true;await page.evaluate(()=>refreshReaderState());await page.setViewportSize({width:844,height:390});await bounds('landscape-control');await page.screenshot({path:output+'/landscape-control.png'});
 await page.setViewportSize({width:390,height:600});await bounds('short-portrait-control');
 await page.locator('#navToggle').click();await page.locator('[data-tab=library]').click();await page.locator('#books button').first().click();await page.locator('#readerImage').waitFor({state:'visible'});await page.locator('#next').click();await page.waitForTimeout(100);assert.match(await page.locator('#pageLabel').textContent(),/11 de 25/);assert.ok(!commands.some(c=>c.action==='reader-page'));await page.screenshot({path:output+'/portrait-reader.png'});
 await page.locator('#navToggle').click();await page.locator('[data-tab=files]').click();await page.locator('#fileInput').setInputFiles({name:'Teste.cbz',mimeType:'application/zip',buffer:Buffer.from('fixture')});await page.locator('#transfers [data-state=done]').waitFor();assert.equal(await page.locator('#originals a').count(),1);await page.screenshot({path:output+'/portrait-files.png'});await page.locator('#clearTransfers').click();assert.equal(await page.locator('#transfers .fileCard').count(),0);assert.equal(await page.locator('#originals a').count(),1);
 assert.deepEqual(errors,[]);writeFileSync(output+'/ui-report.json',JSON.stringify({environment:'Microsoft Edge headless; fixtures; mobile viewport/touch. No physical Safari or TV test.',checks,commands,errors},null,2));console.log(JSON.stringify({checks:checks.length,commands:commands.length,errors}));await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
