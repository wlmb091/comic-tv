import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createManifest, readBuildVersion, validateManifest } from './update-release.mjs';
import { publishRelease } from '../distribution/tools/publish-release.mjs';

test('version and bounded manifest agree with client contract',()=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'comic-update-'));
  try {
    const apk=path.join(root,'app.apk');fs.writeFileSync(apk,'apk-bytes');
    const version=readBuildVersion('versionCode = 57\nversionName = "1.1.18"\nminSdk = 26');
    const manifest=createManifest(version,apk,'Novidades');
    assert.equal(manifest.sizeBytes,9);assert.match(manifest.sha256,/^[a-f0-9]{64}$/);
    assert.equal(manifest.apkUrl,'https://github.com/wlmb091/comic-tv-updates/releases/download/v1.1.18/Comic-TV-1.1.18.apk');
    assert.deepEqual(validateManifest(manifest),manifest);
    for(const bad of [{...manifest,versionCode:0},{...manifest,apkUrl:'https://evil.test/app.apk'},{...manifest,sizeBytes:64*1024*1024+1}])assert.throws(()=>validateManifest(bad));
    assert.throws(()=>readBuildVersion('versionCode = 57\nversionName = "$(secret)"\nminSdk = 26'));
  } finally {fs.rmSync(root,{recursive:true,force:true});}
});
test('release stays a draft until both assets finish and never overwrites published release',async()=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'comic-publish-'));
  try {
    const apk=path.join(root,'app.apk');fs.writeFileSync(apk,'apk-bytes');
    const m=createManifest({versionCode:57,versionName:'1.1.18',minSdk:26},apk,'Notes');
    const calls=[];
    const api={latest:async()=>null,byTag:async()=>null,
      create:async(value)=>{calls.push(['create',value.draft]);return{id:1,upload_url:'https://uploads.github.com/repos/wlmb091/comic-tv-updates/releases/1/assets'};},
      upload:async(_,name)=>{calls.push(['upload',name]);}, publish:async()=>{calls.push(['publish']);}};
    await publishRelease(m,apk,api);
    assert.deepEqual(calls.map(c=>c[0]),['create','upload','upload','publish']);assert.equal(calls[0][1],true);
    calls.length=0;
    await publishRelease(m,apk,{...api,byTag:async()=>({draft:false})});assert.equal(calls.length,0);
    await publishRelease(m,apk,{...api,latest:async()=>({...m,versionCode:58})});assert.equal(calls.length,0);
    let published=false;
    await assert.rejects(()=>publishRelease(m,apk,{...api,upload:async()=>{throw Error('upload failed');},publish:async()=>{published=true;}}));
    assert.equal(published,false);
  } finally {fs.rmSync(root,{recursive:true,force:true});}
});
