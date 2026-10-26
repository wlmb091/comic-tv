import fs from 'node:fs';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {readBuildVersion,createManifest,fetchCurrentManifest} from '../distribution/tools/update-contract.mjs';
export {readBuildVersion,createManifest,validateManifest} from '../distribution/tools/update-contract.mjs';
if(process.argv[1] && import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href) {
  const v=readBuildVersion(fs.readFileSync('app/build.gradle.kts','utf8'));
  if(process.argv[2]==='inspect') {
    const current=await fetchCurrentManifest(), needed=!current || current.versionCode<v.versionCode;
    if(process.env.GITHUB_OUTPUT)fs.appendFileSync(process.env.GITHUB_OUTPUT,`needed=${needed}\nversion=${v.versionName}\ncode=${v.versionCode}\n`);
    console.log(JSON.stringify({...v,publicationNeeded:needed}));
  } else if(process.argv[2]==='manifest') {
    const m=createManifest(v,process.argv[3],fs.readFileSync(`docs/releases/${v.versionName}.md`,'utf8'));
    fs.writeFileSync(process.argv[4],JSON.stringify(m,null,2)+'\n');
    console.log(JSON.stringify({version:m.versionName,size:m.sizeBytes,sha256:m.sha256}));
  } else throw Error('Use inspect or manifest <apk> <json>');
}
