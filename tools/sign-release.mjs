import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {readBuildVersion,createManifest} from '../distribution/tools/update-contract.mjs';
import {verifySignedApk} from '../distribution/tools/apk-tools.mjs';

const root=process.env.RUNNER_TEMP;
if(!root || !path.isAbsolute(root))throw Error('Missing runner scratch directory');
const folder=fs.mkdtempSync(path.join(root,'comic-sign-'));
const key=path.join(folder,'signing.jks');
const output=path.join(root,'comic-release');fs.mkdirSync(output,{recursive:true});
const version=readBuildVersion(fs.readFileSync('app/build.gradle.kts','utf8'));
const unsigned=path.join(root,'comic-unsigned','app-release-unsigned.apk');
if(!fs.statSync(unsigned).isFile() || fs.statSync(unsigned).size>67108864)throw Error('Invalid unsigned artifact');
const buildTools=path.join(process.env.ANDROID_HOME ?? '', 'build-tools','35.0.0');
try {
  const bytes=Buffer.from(process.env.COMIC_TV_SIGNING_KEYSTORE ?? '','base64');if(bytes.length<100 || bytes.length>131072)throw Error('Missing signing key');
  fs.writeFileSync(key,bytes,{mode:0o600});
  const aligned=path.join(folder,'aligned.apk');
  execFileSync(path.join(buildTools,'zipalign'),['-P','16','-f','4',unsigned,aligned]);
  const apk=path.join(output,`Comic-TV-${version.versionName}.apk`);
  execFileSync(path.join(buildTools,'apksigner'),['sign','--ks',key,'--ks-key-alias','androiddebugkey','--ks-pass','env:COMIC_TV_SIGNING_STORE_PASSWORD','--key-pass','env:COMIC_TV_SIGNING_KEY_PASSWORD','--out',apk,aligned],{stdio:['ignore','pipe','pipe']});
  verifySignedApk(apk,version,buildTools);
  const manifest=createManifest(version,apk,fs.readFileSync(`docs/releases/${version.versionName}.md`,'utf8'));
  fs.writeFileSync(path.join(output,'update.json'),JSON.stringify(manifest,null,2)+'\n');
  console.log(JSON.stringify({signed:true,version:version.versionName,sha256:manifest.sha256}));
} finally {fs.rmSync(folder,{recursive:true,force:true});}
