import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {readBuildVersion,validateManifest} from '../distribution/tools/update-contract.mjs';

const root=process.env.RUNNER_TEMP;
if(!root || !path.isAbsolute(root))throw Error('Missing runner scratch directory');
const folder=fs.mkdtempSync(path.join(root,'comic-publish-'));
const version=readBuildVersion(fs.readFileSync('app/build.gradle.kts','utf8'));
if(!/^[0-9]+$/.test(process.env.GITHUB_RUN_ID??'') || !/^[0-9]+$/.test(process.env.GITHUB_RUN_ATTEMPT??''))throw Error('Invalid run identity');
const branch=`incoming/v${version.versionName}-${process.env.GITHUB_RUN_ID}-${process.env.GITHUB_RUN_ATTEMPT}`;
const key=path.join(folder,'publisher');const hosts=path.join(folder,'known_hosts');
const quote=value=>"'"+value.replaceAll("'","'\\''")+"'";
try {
  const secret=process.env.COMIC_TV_PUBLISH_SSH_KEY;if(!secret?.startsWith('-----BEGIN OPENSSH PRIVATE KEY-----'))throw Error('Missing scoped deploy key');
  fs.writeFileSync(key,secret,{mode:0o600});
  const meta=await fetch('https://api.github.com/meta',{redirect:'error',headers:{Accept:'application/vnd.github+json'}});
  if(!meta.ok)throw Error('Cannot obtain GitHub host keys');const info=await meta.json();
  const keys=info.ssh_keys?.filter(k=>k.startsWith('ssh-ed25519 '));if(!keys?.length)throw Error('Missing GitHub Ed25519 host key');
  fs.writeFileSync(hosts,keys.map(k=>`github.com ${k}`).join('\n')+'\n');
  const env={...process.env,GIT_SSH_COMMAND:`ssh -i ${quote(key)} -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=${quote(hosts)}`};
  const repo=path.join(folder,'channel');
  execFileSync('git',['clone','--depth','1','git@github.com:wlmb091/comic-tv-updates.git',repo],{env,stdio:['ignore','pipe','pipe']});
  const git=args=>execFileSync('git',['-C',repo,...args],{env,encoding:'utf8',stdio:['ignore','pipe','pipe']});
  git(['checkout','-b',branch]);
  const target=path.join(repo,'incoming');fs.mkdirSync(target);
  const prepared=path.join(root,'comic-release');
  const m=validateManifest(JSON.parse(fs.readFileSync(path.join(prepared,'update.json'),'utf8')));
  if(m.versionCode!==version.versionCode || m.versionName!==version.versionName)throw Error('Prepared version differs');
  // Deliberate two-file copy. Private checkout, key and credentials are never staged.
  for(const name of [`Comic-TV-${version.versionName}.apk`,'update.json'])fs.copyFileSync(path.join(prepared,name),path.join(target,name));
  git(['config','user.name','Comic TV Publisher']);git(['config','user.email','comic-tv-publisher@users.noreply.github.com']);
  git(['add','--','incoming']);
  const staged=git(['diff','--cached','--name-only']).trim().split('\n').sort();
  if(JSON.stringify(staged)!==JSON.stringify([`incoming/Comic-TV-${version.versionName}.apk`,'incoming/update.json'].sort()))throw Error('Unexpected files staged');
  git(['commit','-m',`Publish Comic TV ${version.versionName}`]);git(['push','origin',`HEAD:refs/heads/${branch}`]);
  console.log(JSON.stringify({staged:true,branch}));
} finally {fs.rmSync(folder,{recursive:true,force:true});}
