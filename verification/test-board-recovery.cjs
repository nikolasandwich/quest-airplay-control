// Executes the actual shipped inline script with a deterministic clock and stalled HTTP.
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const path=require('path');
const html=fs.readFileSync(process.argv[2]||path.join(__dirname,'../app/src/main/assets/calibration-board.html'),'utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
async function run(language='en',expected=/unavailable/){
 let now=0,id=0,calls=0,aborted=0;const timers=new Map(),elements={};
 const schedule=(fn,delay,interval=0)=>{const n=++id;timers.set(n,{fn,at:now+delay,interval});return n;};
 const document={documentElement:{lang:'en'},getElementById(name){return elements[name]||(elements[name]={textContent:'',style:{}});}};
 const context={document,Date:{now:()=>now},AbortController,innerWidth:1280,innerHeight:720,addEventListener(){},
 setTimeout:(fn,d)=>schedule(fn,d),clearTimeout:n=>timers.delete(n),setInterval:(fn,d)=>schedule(fn,d,d),
 fetch:(_url,options)=>++calls===1?Promise.resolve({ok:true,json:async()=>({language,active:true,stage:0,baseline:true,session:'test',ageMs:0,status:'ready'})}):new Promise((_,reject)=>{options.signal?.addEventListener('abort',()=>{aborted++;reject(Error('aborted'));});})};
 vm.runInNewContext(script,context);
 for(let i=0;i<10;i++)await Promise.resolve();
 while(now<6000){
   const next=[...timers.entries()].sort((a,b)=>a[1].at-b[1].at)[0];if(!next)break;
   const [n,t]=next;now=t.at;timers.delete(n);if(t.interval)timers.set(n,{...t,at:now+t.interval});t.fn();
   for(let i=0;i<10;i++)await Promise.resolve();
 }
 assert.match(elements.connection?.textContent||'',expected,'stalled HTTP must not keep a live/synchronized status');
 assert(aborted>=2,'stalled fetches must be cancelled and retried');
 assert(!/id=["']target["']/.test(html),'withdrawn wizard must not display a pursuit target');
 assert(!/pointermove|step\+\+/.test(script),'pointer movement cannot advance or reposition a target');
 console.log('PASS: '+language+' deadlines, stale status, retries, and no pursuit target');
}
(async()=>{for(const [language,pattern] of [['en-US',/unavailable/],['de-DE',/nicht verfügbar/],['fr-CA',/indisponible/],['zh-CN',/暂不可用/],['es-ES',/unavailable/]])await run(language,pattern);})().catch(e=>{console.error(e.message);process.exitCode=1;});
