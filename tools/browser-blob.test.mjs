import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const install = fs.readFileSync('app/src/main/assets/browser-blob.js', 'utf8');
function page(links = []) {
  const original = new Map(), timers = new Map(); let next = 0, fetches = 0;
  const window = {};
  const context = vm.createContext({window, location:{origin:'https://mega.nz'}, URL,
    document:{querySelectorAll:()=>links},
    fetch: async url => { fetches++; const blob=original.get(url); if(!blob)throw Error('revoked'); return {blob:async()=>blob}; },
    setTimeout: callback => {timers.set(++next,callback);return next;},clearTimeout:id=>timers.delete(id)});
  vm.runInContext(install,context);
  return {api:window.__comicTvBlobV1, original, timers, fetches:()=>fetches};
}
test('name and Blob survive provider revocation while confirmation is open', async()=>{
  const url='blob:https://mega.nz/temporary';
  const p=page([{href:url,download:'Godzilla — edição 01.cbr'}]);
  const blob=new Blob(['RAR data']);p.original.set(url,blob);
  assert.deepEqual(JSON.parse(p.api.prepare(url,'key',100)),{ready:true,name:'Godzilla — edição 01.cbr'});
  p.original.delete(url);
  const held=await p.api.take('key',url);
  assert.equal(held.name,'Godzilla — edição 01.cbr');assert.equal(await held.blob.text(),'RAR data');
  assert.equal(p.fetches(),1);p.api.release('key');
  await assert.rejects(p.api.take('key',url),/expired/);
});
test('retention is bounded, cancel releases a slot and origin is enforced',()=>{
  const p=page();const url='blob:https://mega.nz/a';p.original.set(url,new Blob(['a']));
  for(let i=0;i<4;i++)assert.equal(JSON.parse(p.api.prepare(url,`${i}`,100)).ready,true);
  assert.equal(JSON.parse(p.api.prepare(url,'overflow',100)).ready,false);
  assert.equal(p.fetches(),4);p.api.release('0');
  assert.equal(JSON.parse(p.api.prepare(url,'next',100)).ready,true);
  p.api.release('next');
  assert.equal(JSON.parse(p.api.prepare('blob:https://other.test/a','cross',100)).ready,false);
  assert.equal(p.fetches(),5);
});
test('inactive references expire, paused references have a new deadline',async()=>{
  const p=page();const url='blob:https://mega.nz/a';p.original.set(url,new Blob(['a']));
  p.api.prepare(url,'key',100);await p.api.take('key',url);
  for(const timer of [...p.timers.values()])timer(); // active transfer stays alive
  assert.equal(await (await p.api.take('key',url)).blob.text(),'a');
  p.api.park('key');for(const timer of [...p.timers.values()])timer();
  await assert.rejects(p.api.take('key',url),/expired/);
});
test('a Blob above the approved byte limit cannot be streamed',async()=>{
  const p=page();const url='blob:https://mega.nz/a';p.original.set(url,new Blob(['large']));
  p.api.prepare(url,'key',2);await assert.rejects(p.api.take('key',url),/expired/);
});

// Execute the very same stream script embedded in Android, with a local fake native port.
const kotlin=fs.readFileSync('app/src/main/java/com/pelican/comictv/browser/BrowserBlobTransfer.kt','utf8');
const raw=kotlin.match(/val script = """([\s\S]*?)"""\.replace/)[1];
async function stream(stop=false){
  const token='test-token', url='blob:https://mega.nz/file';let listener, closed=false;
  const packets=[];let done;
  const finished=new Promise(resolve=>{done=resolve;});
  const blob=new Blob([new Uint8Array(400000)],{type:'application/octet-stream'});
  const port={onmessage:null,close(){closed=true;done();},postMessage(value){
    packets.push(value);
    if(!value.includes('|E|')&&!value.includes('|X|'))queueMicrotask(()=>port.onmessage({data:token+(stop?'|S':'|A')}));
  }};
  const window={addEventListener(_name,fn){listener=fn;},removeEventListener(){},
    __comicTvBlobV1:{take:async()=>({blob,name:'Edição especial.cbr'})}};
  const script=raw.replaceAll('TOKEN',JSON.stringify(token)).replaceAll('URL',JSON.stringify(url)).replaceAll('KEY',JSON.stringify('retained-key'));
  assert.equal(vm.runInNewContext(script,{window,TextEncoder,Uint8Array,String,JSON,Promise,setTimeout,clearTimeout,
    btoa:value=>Buffer.from(value,'binary').toString('base64')}),'ready');
  listener({data:token,ports:[port]});await finished;return {packets,closed};
}
test('native stream retains Unicode metadata and transfers bounded chunks with acknowledgements',async()=>{
  const {packets,closed}=await stream();assert.equal(closed,true);
  const first=packets[0].split('|');const metadata=JSON.parse(Buffer.from(first[3],'base64').toString('utf8'));
  assert.equal(metadata.name,'Edição especial.cbr');assert.equal(first[2],'400000');
  const chunks=packets.filter(x=>x.includes('|C|')).map(x=>x.split('|')[3]);
  assert.ok(chunks.every(x=>x.length<=262144));assert.equal(chunks.reduce((n,x)=>n+Buffer.from(x,'base64').length,0),400000);
  assert.ok(packets.at(-1).includes('|E|3'));
});
test('native cancellation stops the stream before any content chunk',async()=>{
  const {packets,closed}=await stream(true);assert.equal(closed,true);assert.equal(packets.length,1);
});
