import {readFile,readdir} from 'node:fs/promises';

// Only a Chromium root process launched for this exact per-user profile may be
// terminated. Never use a name-wide pkill: other visitors have their own tabs.
export function isProfileChromium(argv,profileDir){
  return argv.some(arg=>arg===`--user-data-dir=${profileDir}`) &&
    /(?:^|\/)(?:chromium|chrome)(?:-browser)?$/.test(argv[0]||'') &&
    !argv.some(arg=>arg.startsWith('--type='));
}

async function processRows(){
  const rows=[];
  for(const name of await readdir('/proc')){
    if(!/^\d+$/.test(name))continue;
    try{
      const [cmdline,status]=await Promise.all([
        readFile(`/proc/${name}/cmdline`),readFile(`/proc/${name}/status`,'utf8'),
      ]);
      const uid=Number(/^Uid:\s+(\d+)/m.exec(status)?.[1]);
      if(uid!==process.getuid())continue;
      rows.push({pid:Number(name),ppid:Number(/^PPid:\s+(\d+)/m.exec(status)?.[1]),argv:cmdline.toString('utf8').split('\0').filter(Boolean)});
    }catch{ /* A process can exit while /proc is scanned. */ }
  }
  return rows;
}

export async function terminateProfileChromium(profileDir){
  const rows=await processRows();
  const roots=rows.filter(row=>isProfileChromium(row.argv,profileDir)).map(row=>row.pid);
  if(!roots.length)return;
  const ids=new Set(roots);
  for(let changed=true;changed;){
    changed=false;
    for(const row of rows)if(ids.has(row.ppid)&&!ids.has(row.pid)){ids.add(row.pid);changed=true;}
  }
  // Recheck each PID's command line before signalling; a short-lived process
  // could have exited and its number could already belong to another app.
  const signal=async name=>{
    for(const row of rows.filter(row=>ids.has(row.pid)).reverse())try{
      const now=(await readFile(`/proc/${row.pid}/cmdline`)).toString('utf8').split('\0').filter(Boolean);
      if(now.length===row.argv.length&&now.every((arg,i)=>arg===row.argv[i]))process.kill(row.pid,name);
    }catch(e){if(e.code!=='ENOENT'&&e.code!=='ESRCH')throw e;}
  };
  await signal('SIGTERM');
  await new Promise(resolve=>setTimeout(resolve,500));
  await signal('SIGKILL');
  for(let attempt=0;attempt<10;attempt++){
    if(!(await processRows()).some(row=>isProfileChromium(row.argv,profileDir)))return;
    await new Promise(resolve=>setTimeout(resolve,100));
  }
  throw new Error('The previous Chromium profile did not stop');
}
